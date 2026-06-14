import groovy.json.JsonOutput
import java.io.File
import java.time.ZonedDateTime
import java.util.Properties

private fun String.extractCallArguments(name: String): String {
    val match = Regex("""val\s+${Regex.escape(name)}(?:\s*:[^=]+)?\s*=\s*(?:setOf|listOf|mapOf)\s*\(""")
        .find(this)
        ?: throw GradleException("Unable to find $name in source text.")
    val openIndex = this.indexOf('(', match.range.first)
    var depth = 0
    var inString = false
    var escaped = false
    for (index in openIndex until length) {
        val char = this[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) {
                    return substring(openIndex + 1, index)
                }
            }
        }
    }
    throw GradleException("Unable to parse $name call block.")
}

private fun extractStringList(source: String, name: String): List<String> =
    Regex(""""([^"]+)"""")
        .findAll(source.extractCallArguments(name))
        .map { it.groupValues[1] }
        .toList()

private fun extractStringMap(source: String, name: String): Map<String, String> =
    Regex(""""([^"]+)"\s+to\s+"([^"]+)"""")
        .findAll(source.extractCallArguments(name))
        .associate { it.groupValues[1] to it.groupValues[2] }

private fun extractConstString(source: String, name: String): String =
    Regex("""const\s+val\s+${Regex.escape(name)}\s*=\s*"([^"]+)"""")
        .find(source)
        ?.groupValues
        ?.get(1)
        ?: throw GradleException("Unable to find constant $name.")

private fun extractConstBoolean(source: String, name: String): Boolean =
    Regex("""const\s+val\s+${Regex.escape(name)}\s*=\s*(true|false)""")
        .find(source)
        ?.groupValues
        ?.get(1)
        ?.toBooleanStrict()
        ?: throw GradleException("Unable to find boolean constant $name.")

private fun extractJsObjectBlock(source: String, name: String): String {
    val token = "const $name = Object.freeze({"
    val start = source.indexOf(token)
    if (start < 0) {
        throw GradleException("Unable to find JavaScript object $name.")
    }
    val openIndex = source.indexOf('{', start)
    var depth = 0
    var inString = false
    var escaped = false
    for (index in openIndex until source.length) {
        val char = source[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(openIndex + 1, index)
                }
            }
        }
    }
    throw GradleException("Unable to parse JavaScript object $name.")
}

private fun extractJsArrayBlock(source: String, name: String): String {
    val token = "const $name = Object.freeze(["
    val start = source.indexOf(token)
    if (start < 0) {
        throw GradleException("Unable to find JavaScript array $name.")
    }
    val openIndex = source.indexOf('[', start)
    var depth = 0
    var inString = false
    var escaped = false
    for (index in openIndex until source.length) {
        val char = source[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '[' -> depth += 1
            ']' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(openIndex + 1, index)
                }
            }
        }
    }
    throw GradleException("Unable to parse JavaScript array $name.")
}

private fun extractJsStringList(source: String, name: String): List<String> =
    Regex(""""([^"]+)"""")
        .findAll(extractJsArrayBlock(source, name))
        .map { it.groupValues[1] }
        .toList()

private fun extractJsStringMap(source: String, name: String): Map<String, String> =
    Regex(""""([^"]+)"\s*:\s*"([^"]+)"""")
        .findAll(extractJsObjectBlock(source, name))
        .associate { it.groupValues[1] to it.groupValues[2] }

private fun extractJsObjectKeys(source: String, name: String): Set<String> =
    Regex(""""([^"]+)"\s*:""")
        .findAll(extractJsObjectBlock(source, name))
        .map { it.groupValues[1] }
        .toSet()

private fun extractProfileNetworkBuiltins(source: String): List<String> =
    Regex("""networkBuiltins:\s*Object\.freeze\(\[([^\]]*)\]\)""", RegexOption.DOT_MATCHES_ALL)
        .find(source)
        ?.groupValues
        ?.get(1)
        ?.let { block ->
            Regex(""""([^"]+)"""").findAll(block).map { it.groupValues[1] }.toList()
        }
        ?: throw GradleException("Unable to find profile network builtins in bootstrap source.")

private fun extractBridgeMethods(source: String): Map<String, List<String>> {
    val constants = Regex("""const\s+val\s+([A-Z_]+)\s*=\s*"([^"]+)"""")
        .findAll(source)
        .associate { it.groupValues[1] to it.groupValues[2] }
    val methodSets = Regex("""val\s+(\w+Methods)\s*=\s*setOf\(""")
        .findAll(source)
        .associate { match ->
            val name = match.groupValues[1]
            name to Regex(""""([^"]+)"""")
                .findAll(source.extractCallArguments(name))
                .map { it.groupValues[1] }
                .toList()
        }
    return Regex("""([A-Z_]+)\s+to\s+(\w+Methods)""")
        .findAll(source.extractCallArguments("supportedMethodsByModule"))
        .associate { match ->
            val module = constants[match.groupValues[1]]
                ?: throw GradleException("Unable to resolve bridge module ${match.groupValues[1]}.")
            val methods = methodSets[match.groupValues[2]]
                ?: throw GradleException("Unable to resolve bridge method set ${match.groupValues[2]}.")
            module to methods
        }
        .toSortedMap()
}

private fun extractFeatureFlagRegistry(source: String): List<Map<String, Any?>> {
    fun entryField(entry: String, name: String): String =
        Regex("""${Regex.escape(name)}\s*=\s*([^,\n)]+)""")
            .find(entry)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: throw GradleException("Unable to find feature flag field $name in $entry.")

    fun stringValue(raw: String): String? = when {
        raw == "null" -> null
        raw.startsWith("\"") && raw.endsWith("\"") -> raw.removeSurrounding("\"")
        else -> extractConstString(source, raw)
    }

    fun booleanValue(raw: String): Boolean = when (raw) {
        "true" -> true
        "false" -> false
        else -> extractConstBoolean(source, raw)
    }

    val block = source.extractCallArguments("featureFlags")
    return Regex("""NodeRuntimeFeatureFlag\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        .findAll(block)
        .map { match ->
            val entry = match.groupValues[1]
            mapOf(
                "id" to stringValue(entryField(entry, "id")),
                "diagnosticName" to stringValue(entryField(entry, "diagnosticName")),
                "gradleProperty" to stringValue(entryField(entry, "gradleProperty")),
                "defaultEnabled" to booleanValue(entryField(entry, "defaultEnabled")),
                "buildTimeOverrideAllowed" to booleanValue(entryField(entry, "buildTimeOverrideAllowed")),
                "projectJsonOverrideAllowed" to booleanValue(entryField(entry, "projectJsonOverrideAllowed")),
                "packagedApkOverrideAllowed" to booleanValue(entryField(entry, "packagedApkOverrideAllowed")),
                "securityLevel" to stringValue(entryField(entry, "securityLevel")),
                "status" to stringValue(entryField(entry, "status")),
            )
        }
        .toList()
}

private fun loadVersionProperties(rootDir: File): Properties {
    val file = rootDir.resolve("version.properties")
    return Properties().apply {
        file.inputStream().use(::load)
    }
}

private fun normalizeNodeReleaseFlavor(value: String): String = when (value.trim().lowercase()) {
    "nodefull", "node-full", "full" -> "nodeFull"
    "nodemini", "node-mini", "mini" -> "nodeMini"
    "rhinoonly", "rhino-only", "rhino" -> "rhinoOnly"
    else -> value.trim()
}

private fun expectedReleaseAbis(flavor: String, supportedAbis: List<String>, requestedAbis: List<String>): List<String> =
    when (flavor) {
        "nodeFull" -> supportedAbis
        "nodeMini" -> requestedAbis.ifEmpty { listOf("arm64-v8a") }
        "rhinoOnly" -> emptyList()
        else -> emptyList()
    }

private fun jsonText(value: Any?): String = JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun tableRow(vararg values: Any?): String =
    values.joinToString(prefix = "| ", postfix = " |", separator = " | ") { value ->
        value.toString().replace("|", "\\|")
    }

val nodeCompatibilityReportDir = layout.buildDirectory.dir("reports/nodejs")
val nodeCompatibilityJsonReport = nodeCompatibilityReportDir.map { it.file("compatibility-profile.json") }
val nodeCompatibilityMarkdownReport = nodeCompatibilityReportDir.map { it.file("compatibility-profile.md") }

val generateNodeCompatibilityProfile = tasks.register("generateNodeCompatibilityProfile") {
    group = "verification"
    description = "Generates machine-readable and human-readable Embedded Node compatibility profile reports."

    outputs.file(nodeCompatibilityJsonReport)
    outputs.file(nodeCompatibilityMarkdownReport)
    outputs.upToDateWhen { false }

    doLast {
        val runtimeCapabilitiesFile = projectDir.resolve("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt")
        val bridgeProtocolFile = projectDir.resolve("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt")
        val bootstrapSourceFile = projectDir.resolve("src/main/cpp/node_bridge_sources.cpp")
        val appBuildFile = projectDir.resolve("build.gradle.kts")
        val compatibilityDocFile = rootProject.projectDir.resolve("docs/nodejs/COMPATIBILITY_PROFILE.md")
        val runtimeSource = runtimeCapabilitiesFile.readText()
        val bridgeSource = bridgeProtocolFile.readText()
        val bootstrapSource = bootstrapSourceFile.readText()
        val appBuildSource = appBuildFile.readText()
        val versionProperties = loadVersionProperties(rootProject.projectDir)

        val directBuiltinAliases = extractStringMap(runtimeSource, "directBuiltinAliases")
        val limitedBuiltinModuleNames = extractStringList(runtimeSource, "limitedBuiltinModuleNames")
        val disabledBuiltinModuleNames = extractStringList(runtimeSource, "disabledBuiltinModuleNames")
        val commonJsBuiltinModuleNames = extractStringList(runtimeSource, "commonJsBuiltinModuleNames")
        val networkBuiltinModuleNames = extractStringList(runtimeSource, "networkBuiltinModuleNames")
        val disabledProcessApis = extractStringList(runtimeSource, "disabledProcessApis")
        val backendSwitches = extractStringList(runtimeSource, "backendSwitches")
        val routeSuffixes = extractStringList(runtimeSource, "nodeRouteFileSuffixes")
        val commonJsExtensions = extractStringList(runtimeSource, "commonJsModuleExtensions")
        val packageConditions = extractStringList(runtimeSource, "packageJsonExportConditions")
        val featureFlags = extractFeatureFlagRegistry(runtimeSource)
        val bridgeModules = extractBridgeMethods(bridgeSource)

        val bootstrapDirectAliases = extractJsStringMap(bootstrapSource, "__autojs6_require_builtin_allowlist")
        val bootstrapLimitedBuiltins = extractJsStringList(bootstrapSource, "__autojs6_limited_builtin_module_names")
        val bootstrapDenylist = extractJsObjectKeys(bootstrapSource, "__autojs6_require_builtin_denylist")
        val bootstrapDisabledBuiltins = bootstrapDenylist
            .filterNot { it in bootstrapLimitedBuiltins || it in bootstrapDirectAliases.keys }
            .sorted()
        val bootstrapNetworkBuiltins = extractProfileNetworkBuiltins(bootstrapSource)

        val registryMismatches = mutableListOf<String>()
        if (directBuiltinAliases != bootstrapDirectAliases) {
            registryMismatches += "directBuiltinAliases differ between NodeRuntimeCapabilities.kt and node_bridge_sources.cpp"
        }
        if (limitedBuiltinModuleNames.sorted() != bootstrapLimitedBuiltins.sorted()) {
            registryMismatches += "limitedBuiltinModuleNames differ between NodeRuntimeCapabilities.kt and node_bridge_sources.cpp"
        }
        if (disabledBuiltinModuleNames.sorted() != bootstrapDisabledBuiltins) {
            registryMismatches += "disabledBuiltinModuleNames differ; Kotlin=${disabledBuiltinModuleNames.sorted()} bootstrap=$bootstrapDisabledBuiltins"
        }
        if (networkBuiltinModuleNames.sorted() != bootstrapNetworkBuiltins.sorted()) {
            registryMismatches += "networkBuiltinModuleNames differ between NodeRuntimeCapabilities.kt and autojs6:profile"
        }
        if (registryMismatches.isNotEmpty()) {
            throw GradleException(registryMismatches.joinToString(separator = System.lineSeparator()))
        }

        val supportedAbis = extractStringList(appBuildSource, "nodeJsReleaseSupportedAbis")
        val knownAbis = supportedAbis + "x86"
        val releaseFlavor = normalizeNodeReleaseFlavor(providers.gradleProperty("autojs.nodejs.releaseFlavor").orElse("nodeFull").get())
        val requestedReleaseAbis = providers.gradleProperty("autojs.nodejs.releaseAbis").orNull
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        val releaseExpectedAbis = expectedReleaseAbis(releaseFlavor, supportedAbis, requestedReleaseAbis)
        val runtimeBackend = providers.gradleProperty("autojs.nodejs.backend").orElse("embedded").get().trim().lowercase()
        val now = ZonedDateTime.now().toString()

        val directCanonicalBuiltins = directBuiltinAliases.values.toSet()
        val builtinStatuses = (commonJsBuiltinModuleNames + limitedBuiltinModuleNames + disabledBuiltinModuleNames + networkBuiltinModuleNames)
            .distinct()
            .sorted()
            .map { name ->
                val status = when {
                    name in disabledBuiltinModuleNames -> "disabled"
                    name in directCanonicalBuiltins -> "stable"
                    name in limitedBuiltinModuleNames -> "partial"
                    else -> "unsupported"
                }
                mapOf(
                    "name" to name,
                    "status" to status,
                    "nodeAlias" to "node:$name",
                    "networkIo" to if (name in networkBuiltinModuleNames) "disabled-by-default" else "not-network",
                )
            }

        val dangerousApis = buildList {
            disabledProcessApis.sorted().forEach { api ->
                add(
                    mapOf(
                        "name" to "process.$api",
                        "status" to "disabled",
                        "securityLevel" to "high",
                    )
                )
            }
            add(mapOf("name" to "child_process", "status" to "disabled", "securityLevel" to "high"))
            add(mapOf("name" to "native_addon", "status" to "unsupported", "securityLevel" to "critical"))
            add(mapOf("name" to "process.binding", "status" to "disabled", "securityLevel" to "critical"))
            add(mapOf("name" to "process.dlopen", "status" to "disabled", "securityLevel" to "critical"))
            add(mapOf("name" to "worker_threads", "status" to "experimental-disabled", "securityLevel" to "high"))
            add(mapOf("name" to "raw_node_network", "status" to "disabled", "securityLevel" to "high"))
            add(mapOf("name" to "dynamic_import", "status" to "experimental-disabled", "securityLevel" to "medium"))
            add(mapOf("name" to "esm", "status" to "experimental-disabled", "securityLevel" to "medium"))
            add(mapOf("name" to "vm", "status" to "disabled", "securityLevel" to "high"))
            add(mapOf("name" to "inspector", "status" to "disabled", "securityLevel" to "high"))
            add(mapOf("name" to "java_interop", "status" to "unsupported", "securityLevel" to "critical"))
        }.distinctBy { it["name"] }.sortedBy { it["name"].toString() }

        val profile = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-compatibility-profile-v2",
            "generatedAt" to now,
            "sourceOfTruth" to linkedMapOf(
                "runtimeCapabilities" to runtimeCapabilitiesFile.invariantSeparatorsPath,
                "bridgeModules" to bridgeProtocolFile.invariantSeparatorsPath,
                "bootstrap" to bootstrapSourceFile.invariantSeparatorsPath,
                "gradleReleasePolicy" to appBuildFile.invariantSeparatorsPath,
                "humanDocumentation" to compatibilityDocFile.invariantSeparatorsPath,
            ),
            "runtime" to linkedMapOf(
                "profileName" to extractConstString(runtimeSource, "PROFILE_NAME"),
                "engineVersion" to extractConstString(runtimeSource, "ENGINE_VERSION"),
                "nodeHeaderVersion" to extractConstString(runtimeSource, "NODE_HEADER_VERSION"),
                "nodeVersionExpectedPrefix" to extractConstString(runtimeSource, "NODE_VERSION_EXPECTED_PREFIX"),
                "defaultBackend" to runtimeBackend,
                "backendSwitches" to backendSwitches.sorted(),
                "routeSuffixes" to routeSuffixes.sorted(),
                "commonJsModuleExtensions" to commonJsExtensions.sorted(),
                "packageJsonExportConditions" to packageConditions,
                "moduleSourceBudgets" to linkedMapOf(
                    "maxBytesPerSource" to Regex("""RUNTIME_MODULE_SOURCE_MAX_BYTES\s*=\s*([0-9_]+)""")
                        .find(runtimeSource)?.groupValues?.get(1)?.replace("_", "")?.toLong(),
                    "maxBytesTotal" to Regex("""RUNTIME_MODULE_SOURCES_MAX_BYTES\s*=\s*([0-9_]+)""")
                        .find(runtimeSource)?.groupValues?.get(1)?.replace("_", "")?.toLong(),
                    "maxCount" to Regex("""RUNTIME_MODULE_SOURCE_MAX_COUNT\s*=\s*([0-9_]+)""")
                        .find(runtimeSource)?.groupValues?.get(1)?.replace("_", "")?.toLong(),
                ),
            ),
            "statusCategories" to listOf("stable", "partial", "experimental", "disabled", "unsupported", "planned"),
            "featureFlags" to featureFlags.map { flag ->
                val property = flag["gradleProperty"]?.toString()
                val defaultEnabled = flag["defaultEnabled"] as Boolean
                flag + ("effectiveEnabled" to if (property.isNullOrBlank()) {
                    defaultEnabled
                } else {
                    providers.gradleProperty(property)
                        .orElse(defaultEnabled.toString())
                        .get()
                        .toBooleanStrictOrNull()
                        ?: defaultEnabled
                })
            },
            "builtins" to linkedMapOf(
                "directAliases" to directBuiltinAliases.toSortedMap(),
                "limited" to limitedBuiltinModuleNames.sorted(),
                "disabled" to disabledBuiltinModuleNames.sorted(),
                "network" to networkBuiltinModuleNames.sorted(),
                "commonJsKnown" to commonJsBuiltinModuleNames.sorted(),
                "statuses" to builtinStatuses,
            ),
            "autoJsBridgeModules" to bridgeModules.map { (module, methods) ->
                linkedMapOf(
                    "name" to module,
                    "status" to when (module) {
                        "fetch" -> "experimental"
                        "accessibility", "media_projection", "image", "images", "dialogs" -> "partial"
                        else -> "stable"
                    },
                    "methods" to methods.sorted(),
                    "async" to true,
                )
            },
            "dangerousApis" to dangerousApis,
            "packagedApkSupport" to linkedMapOf(
                "status" to "stable",
                "releaseFlavor" to releaseFlavor,
                "expectedAbis" to releaseExpectedAbis,
                "supportedAbis" to supportedAbis,
                "knownAbis" to knownAbis,
                "unsupportedAbis" to listOf("x86"),
                "nativeLibraries" to listOf("libnode.so", "libautojs6-node.so", "libc++_shared.so"),
                "serviceProcess" to ":nodejs_embedded",
                "requiresForegroundServiceSpecialUse" to true,
                "templateApkPresent" to projectDir.resolve("src/main/assets-app/template.apk").isFile,
            ),
            "android" to linkedMapOf(
                "minSdkVersion" to versionProperties.getProperty("MIN_SDK_VERSION"),
                "targetSdkVersion" to versionProperties.getProperty("TARGET_SDK_VERSION"),
                "targetSdkVersionInrt" to versionProperties.getProperty("TARGET_SDK_VERSION_INRT"),
                "compileSdkVersion" to versionProperties.getProperty("COMPILE_SDK_VERSION"),
            ),
        )

        val reportDir = nodeCompatibilityReportDir.get().asFile
        reportDir.mkdirs()
        nodeCompatibilityJsonReport.get().asFile.writeText(jsonText(profile))

        val markdown = buildString {
            appendLine("# AutoJs6 Node.js Compatibility Profile")
            appendLine()
            appendLine("Generated at: `$now`")
            appendLine()
            appendLine("Source of truth:")
            appendLine()
            appendLine("- `NodeRuntimeCapabilities.kt`")
            appendLine("- `NodeBridgeProtocol.kt` / `NodeBridgeModules`")
            appendLine("- `node_bridge_sources.cpp` bootstrap allowlist and denylist")
            appendLine("- `app/build.gradle.kts` release ABI policy")
            appendLine()
            appendLine("## Runtime")
            appendLine()
            appendLine(tableRow("Field", "Value"))
            appendLine(tableRow("---", "---"))
            appendLine(tableRow("Profile", extractConstString(runtimeSource, "PROFILE_NAME")))
            appendLine(tableRow("Engine", extractConstString(runtimeSource, "ENGINE_VERSION")))
            appendLine(tableRow("Expected Node", extractConstString(runtimeSource, "NODE_VERSION_EXPECTED_PREFIX")))
            appendLine(tableRow("Default backend", runtimeBackend))
            appendLine(tableRow("Backend switches", backendSwitches.sorted().joinToString(", ")))
            appendLine(tableRow("Route suffixes", routeSuffixes.sorted().joinToString(", ")))
            appendLine()
            appendLine("## Feature Flags")
            appendLine()
            appendLine(tableRow("Flag", "Default", "Effective", "Status", "Security"))
            appendLine(tableRow("---", "---", "---", "---", "---"))
            @Suppress("UNCHECKED_CAST")
            (profile["featureFlags"] as List<Map<String, Any?>>).forEach { flag ->
                appendLine(tableRow(flag["id"], flag["defaultEnabled"], flag["effectiveEnabled"], flag["status"], flag["securityLevel"]))
            }
            appendLine()
            appendLine("## Builtins")
            appendLine()
            appendLine(tableRow("Builtin", "Status", "Raw network I/O"))
            appendLine(tableRow("---", "---", "---"))
            builtinStatuses.forEach { builtin ->
                appendLine(tableRow(builtin["name"], builtin["status"], builtin["networkIo"]))
            }
            appendLine()
            appendLine("## AutoJs Bridge Modules")
            appendLine()
            appendLine(tableRow("Module", "Status", "Methods"))
            appendLine(tableRow("---", "---", "---"))
            bridgeModules.forEach { (module, methods) ->
                val status = when (module) {
                    "fetch" -> "experimental"
                    "accessibility", "media_projection", "image", "images", "dialogs" -> "partial"
                    else -> "stable"
                }
                appendLine(tableRow(module, status, methods.sorted().joinToString(", ")))
            }
            appendLine()
            appendLine("## Dangerous APIs")
            appendLine()
            appendLine(tableRow("API", "Status", "Security"))
            appendLine(tableRow("---", "---", "---"))
            dangerousApis.forEach { api ->
                appendLine(tableRow(api["name"], api["status"], api["securityLevel"]))
            }
            appendLine()
            appendLine("## Packaged APK")
            appendLine()
            appendLine(tableRow("Field", "Value"))
            appendLine(tableRow("---", "---"))
            appendLine(tableRow("Status", "stable"))
            appendLine(tableRow("Release flavor", releaseFlavor))
            appendLine(tableRow("Expected ABIs", releaseExpectedAbis.ifEmpty { listOf("none") }.joinToString(", ")))
            appendLine(tableRow("Supported ABIs", supportedAbis.joinToString(", ")))
            appendLine(tableRow("Unsupported ABIs", "x86"))
            appendLine(tableRow("Native libraries", "libnode.so, libautojs6-node.so, libc++_shared.so"))
            appendLine(tableRow("Service process", ":nodejs_embedded"))
            appendLine()
            appendLine("## Android")
            appendLine()
            appendLine(tableRow("Field", "Value"))
            appendLine(tableRow("---", "---"))
            appendLine(tableRow("minSdkVersion", versionProperties.getProperty("MIN_SDK_VERSION")))
            appendLine(tableRow("targetSdkVersion", versionProperties.getProperty("TARGET_SDK_VERSION")))
            appendLine(tableRow("targetSdkVersionInrt", versionProperties.getProperty("TARGET_SDK_VERSION_INRT")))
            appendLine(tableRow("compileSdkVersion", versionProperties.getProperty("COMPILE_SDK_VERSION")))
        }
        nodeCompatibilityMarkdownReport.get().asFile.writeText(markdown)
        logger.lifecycle("Generated Embedded Node compatibility profile JSON: ${nodeCompatibilityJsonReport.get().asFile.absolutePath}")
        logger.lifecycle("Generated Embedded Node compatibility profile Markdown: ${nodeCompatibilityMarkdownReport.get().asFile.absolutePath}")
    }
}

val verifyNodeCompatibilityProfile = tasks.register("verifyNodeCompatibilityProfile") {
    group = "verification"
    description = "Verifies Embedded Node compatibility documentation against the runtime capability registry."

    dependsOn(generateNodeCompatibilityProfile)

    doLast {
        val runtimeSource = projectDir.resolve("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt").readText()
        val bridgeSource = projectDir.resolve("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt").readText()
        val docsFile = rootProject.projectDir.resolve("docs/nodejs/COMPATIBILITY_PROFILE.md")
        val docsText = docsFile.readText()
        val requiredNeedles = buildList {
            add("generateNodeCompatibilityProfile")
            add("build/reports/nodejs/compatibility-profile.json")
            add("build/reports/nodejs/compatibility-profile.md")
            addAll(extractStringList(runtimeSource, "limitedBuiltinModuleNames"))
            addAll(extractStringList(runtimeSource, "disabledBuiltinModuleNames"))
            addAll(extractStringList(runtimeSource, "networkBuiltinModuleNames"))
            extractFeatureFlagRegistry(runtimeSource).forEach { flag ->
                add(flag["id"].toString())
                flag["gradleProperty"]?.toString()?.takeIf(String::isNotBlank)?.let(::add)
            }
            addAll(extractBridgeMethods(bridgeSource).keys)
        }.distinct().sorted()
        val missing = requiredNeedles.filterNot { docsText.contains(it) }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "docs/nodejs/COMPATIBILITY_PROFILE.md is missing compatibility registry entries: " +
                        missing.joinToString()
            )
        }
        logger.lifecycle("Embedded Node compatibility profile documentation agrees with the registry.")
    }
}

tasks.matching { it.name == "verifyEmbeddedNodeDebug" }.configureEach {
    dependsOn(verifyNodeCompatibilityProfile)
}
