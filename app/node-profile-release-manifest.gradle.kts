import groovy.json.JsonOutput
import java.io.File
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.Properties

private fun nodeProfileReleaseManifestJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun nodeProfileReleaseManifestSha256(file: File): String? {
    if (!file.isFile) return null
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun nodeProfileReleaseManifestGit(vararg args: String): String {
    return try {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else "unavailable"
    } catch (_: Exception) {
        "unavailable"
    }
}

private fun nodeProfileReleaseManifestLoadVersionProperties(root: File): Properties {
    val properties = Properties()
    root.resolve("version.properties").inputStream().use(properties::load)
    return properties
}

private fun nodeProfileReleaseManifestRegexValue(text: String, regex: Regex): String? =
    regex.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

private fun nodeProfileReleaseManifestXmlStringValue(stringsXml: File, name: String): String? {
    if (!stringsXml.isFile) return null
    return nodeProfileReleaseManifestRegexValue(
        stringsXml.readText(),
        Regex("""<string\s+name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL),
    )?.trim()
        ?.replace("&amp;", "&")
        ?.replace("&lt;", "<")
        ?.replace("&gt;", ">")
}

private fun nodeProfileReleaseManifestBooleanProperty(name: String, defaultValue: Boolean): Boolean =
    providers.gradleProperty(name)
        .orElse(defaultValue.toString())
        .get()
        .trim()
        .let { value ->
            value.equals("true", ignoreCase = true) ||
                    value.equals("yes", ignoreCase = true) ||
                    value == "1"
        }

private fun nodeProfileReleaseManifestNormalizeFlavor(value: String): String =
    when (value.trim().lowercase()) {
        "nodefull", "node-full", "full" -> "nodeFull"
        "nodemini", "node-mini", "mini" -> "nodeMini"
        "nodeplugin", "node-plugin", "plugin" -> "nodePlugin"
        "rhinoonly", "rhino-only", "rhino" -> "rhinoOnly"
        else -> throw GradleException(
            "Unsupported Embedded Node release flavor '$value'. Use nodeFull, nodeMini, nodePlugin, or rhinoOnly."
        )
    }

private fun nodeProfileReleaseManifestNormalizeIdentity(value: String): String =
    when (value.trim().lowercase()) {
        "production", "prod", "release" -> "production"
        "nodejstest", "nodejs-test", "nodejs_test", "test" -> "nodejsTest"
        else -> "production"
    }

private fun nodeProfileReleaseManifestNativeLibHashes(
    mergedLibRoot: File,
    expectedAbis: List<String>,
): Map<String, Map<String, Any?>> {
    val libNames = listOf("libnode.so", "libautojs6-node.so", "libc++_shared.so")
    return expectedAbis.associateWith { abi ->
        libNames.associateWith { libName ->
            val file = mergedLibRoot.resolve("$abi/$libName")
            linkedMapOf(
                "path" to file.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                "exists" to file.isFile,
                "sizeBytes" to file.takeIf { it.isFile }?.length(),
                "sha256" to nodeProfileReleaseManifestSha256(file),
            )
        }
    }
}

val generateNodeProfileReleaseManifest = tasks.register("generateNodeProfileReleaseManifest") {
    group = "verification"
    description = "Generates the AutoJs6 Node Profile v1 release artifact manifest."

    dependsOn(
        "generateNodeCompatibilityProfile",
        "nativeNodeDependencyReportAppDebug",
        "nativeNodeSymbolArchiveReport",
        "embeddedNodePackagingPreflightAppDebug",
        "mergeAppDebugNativeLibs",
    )

    val reportDirProvider = layout.buildDirectory.dir("reports/nodejs")
    val jsonReportProvider = reportDirProvider.map { it.file("release-manifest.json") }
    val markdownReportProvider = reportDirProvider.map { it.file("release-manifest.md") }

    outputs.file(jsonReportProvider)
    outputs.file(markdownReportProvider)
    outputs.upToDateWhen { false }

    doLast {
        val versionProperties = nodeProfileReleaseManifestLoadVersionProperties(rootProject.projectDir)
        val buildGradle = projectDir.resolve("build.gradle.kts").readText()
        val applicationId = nodeProfileReleaseManifestRegexValue(
            buildGradle,
            Regex("""val\s+globalApplicationId\s*=\s*"([^"]+)""""),
        )
        val namespace = nodeProfileReleaseManifestRegexValue(
            buildGradle,
            Regex("""val\s+globalNamespace\s*=\s*"([^"]+)""""),
        )
        val releaseTag = providers.gradleProperty("autojs.nodejs.releaseTag")
            .orElse("TBD")
            .get()
            .trim()
            .ifBlank { "TBD" }
        val identityProfile = nodeProfileReleaseManifestNormalizeIdentity(
            providers.gradleProperty("autojs.nodejs.identity").orElse("production").get()
        )
        val applicationIdSuffix = when (identityProfile) {
            "nodejsTest" -> ".nodejs"
            else -> ""
        }
        val effectiveApplicationId = applicationId?.let { "$it$applicationIdSuffix" }
        val appLabel = when (identityProfile) {
            "nodejsTest" -> "AutoJs6 Node"
            else -> nodeProfileReleaseManifestXmlStringValue(
                projectDir.resolve("src/main/res/values/strings.xml"),
                "app_name",
            ) ?: "AutoJs6"
        }
        val defaultIdentityStatus = when (identityProfile) {
            "nodejsTest" -> "test-identity-ga-candidate"
            else -> "production-identity-pending-final-gate"
        }
        val releaseIdentityStatus = providers.gradleProperty("autojs.nodejs.releaseIdentityStatus")
            .orElse(defaultIdentityStatus)
            .get()
            .trim()
            .ifBlank { defaultIdentityStatus }
        val releaseFlavor = nodeProfileReleaseManifestNormalizeFlavor(
            project.extensions.extraProperties.get("nodeJsRuntimeConfig.nodeJsReleaseFlavor") as String
        )
        val expectedAbis = (project.extensions.extraProperties
            .get("nodeJsRuntimeConfig.nodeJsReleaseExpectedAbis") as List<*>)
            .map(Any?::toString)
        val effectiveBackend = project.extensions.extraProperties
            .get("nodeJsRuntimeConfig.effectiveNodeJsRuntimeBackend") as String
        val mergedLibRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs/appDebug/mergeAppDebugNativeLibs/out/lib")
            .get()
            .asFile
        val templateApk = projectDir.resolve("src/main/assets-app/template.apk")
        val compatibilityJson = layout.buildDirectory
            .file("reports/nodejs/compatibility-profile.json")
            .get()
            .asFile
        val compatibilityMarkdown = layout.buildDirectory
            .file("reports/nodejs/compatibility-profile.md")
            .get()
            .asFile
        val stableSurfaceDoc = rootProject.projectDir
            .resolve("docs/nodejs/releases/NODE_PROFILE_V1_STABLE_SURFACE.md")
        val nativeDependencyReport = layout.buildDirectory
            .file("reports/native-node/native-deps-appDebug.txt")
            .get()
            .asFile
        val nativeSymbolReport = layout.buildDirectory
            .file("reports/native-node/native-symbol-archive-appDebug.txt")
            .get()
            .asFile
        val packagingPreflightReport = layout.buildDirectory
            .file("reports/embedded-node/packaging-preflight-appDebug.txt")
            .get()
            .asFile

        val runtimeCapabilityDefaults = linkedMapOf(
            "autojs.nodejs.backend" to effectiveBackend,
            "autojs.nodejs.releaseFlavor" to releaseFlavor,
            "autojs.nodejs.releaseAbis" to expectedAbis,
            "autojs.nodejs.embedded.script.enabled" to nodeProfileReleaseManifestBooleanProperty(
                "autojs.nodejs.embedded.script.enabled",
                true,
            ),
            "esm" to true,
            "dynamicImport" to true,
            "network" to true,
            "rawNodeNetworkModules" to true,
            "webSocket" to true,
            "workerThreads" to true,
            "childProcess" to true,
            "javaInterop" to true,
            "autojs.nodejs.timelineTrace" to nodeProfileReleaseManifestBooleanProperty(
                "autojs.nodejs.timelineTrace",
                false,
            ),
        )

        val manifest = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-profile-release-manifest-v1",
            "generatedAt" to ZonedDateTime.now().toString(),
            "git" to linkedMapOf(
                "commit" to nodeProfileReleaseManifestGit("rev-parse", "HEAD"),
                "shortCommit" to nodeProfileReleaseManifestGit("rev-parse", "--short", "HEAD"),
                "statusPorcelain" to nodeProfileReleaseManifestGit("status", "--short"),
            ),
            "app" to linkedMapOf(
                "applicationId" to effectiveApplicationId,
                "baseApplicationId" to applicationId,
                "identityProfile" to identityProfile,
                "applicationIdSuffix" to applicationIdSuffix,
                "namespace" to namespace,
                "label" to appLabel,
                "testApplicationId" to effectiveApplicationId?.let { "$it.test" },
                "inrtApplicationId" to effectiveApplicationId?.let { "$it.inrt" },
                "inrtApplicationIdSuffix" to "$applicationIdSuffix.inrt",
                "inrtLabel" to "AutoJs6.inrt",
                "versionName" to versionProperties.getProperty("VERSION_NAME"),
                "versionCode" to versionProperties.getProperty("VERSION_BUILD")?.toIntOrNull(),
                "buildTime" to versionProperties.getProperty("BUILD_TIME")?.toLongOrNull(),
                "compileSdk" to versionProperties.getProperty("COMPILE_SDK_VERSION")?.toIntOrNull(),
                "targetSdk" to versionProperties.getProperty("TARGET_SDK_VERSION")?.toIntOrNull(),
                "minSdk" to versionProperties.getProperty("MIN_SDK_VERSION")?.toIntOrNull(),
            ),
            "release" to linkedMapOf(
                "tag" to releaseTag,
                "identityStatus" to releaseIdentityStatus,
                "identityDoc" to "docs/nodejs/releases/NODE_PROFILE_V1_RELEASE_IDENTITY.md",
            ),
            "nodeProfile" to linkedMapOf(
                "name" to "AutoJs6 Safe Node Profile",
                "engine" to "autojs6-node-profile-v1.2",
                "expectedNode" to "v24.5.0",
                "releaseFlavor" to releaseFlavor,
                "backend" to effectiveBackend,
                "expectedAbis" to expectedAbis,
                "unsupportedAbis" to listOf("x86"),
            ),
            "nativeLibraries" to nodeProfileReleaseManifestNativeLibHashes(mergedLibRoot, expectedAbis),
            "artifacts" to linkedMapOf(
                "packagedTemplate" to linkedMapOf(
                    "path" to templateApk.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                    "exists" to templateApk.isFile,
                    "sizeBytes" to templateApk.takeIf { it.isFile }?.length(),
                    "sha256" to nodeProfileReleaseManifestSha256(templateApk),
                ),
                "compatibilityProfileJson" to linkedMapOf(
                    "path" to compatibilityJson.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                    "exists" to compatibilityJson.isFile,
                    "sha256" to nodeProfileReleaseManifestSha256(compatibilityJson),
                ),
                "compatibilityProfileMarkdown" to linkedMapOf(
                    "path" to compatibilityMarkdown.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                    "exists" to compatibilityMarkdown.isFile,
                    "sha256" to nodeProfileReleaseManifestSha256(compatibilityMarkdown),
                ),
                "stableSurfaceDoc" to linkedMapOf(
                    "path" to stableSurfaceDoc.relativeToOrSelf(rootProject.projectDir).invariantSeparatorsPath,
                    "exists" to stableSurfaceDoc.isFile,
                    "sha256" to nodeProfileReleaseManifestSha256(stableSurfaceDoc),
                ),
                "nativeDependencyReport" to linkedMapOf(
                    "path" to nativeDependencyReport.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                    "exists" to nativeDependencyReport.isFile,
                    "sha256" to nodeProfileReleaseManifestSha256(nativeDependencyReport),
                ),
                "nativeSymbolReport" to linkedMapOf(
                    "path" to nativeSymbolReport.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                    "exists" to nativeSymbolReport.isFile,
                    "sha256" to nodeProfileReleaseManifestSha256(nativeSymbolReport),
                ),
                "packagingPreflightReport" to linkedMapOf(
                    "path" to packagingPreflightReport.relativeToOrSelf(projectDir).invariantSeparatorsPath,
                    "exists" to packagingPreflightReport.isFile,
                    "sha256" to nodeProfileReleaseManifestSha256(packagingPreflightReport),
                ),
            ),
            "runtimeCapabilityDefaults" to runtimeCapabilityDefaults,
        )

        val reportDir = reportDirProvider.get().asFile
        reportDir.mkdirs()
        val jsonReport = jsonReportProvider.get().asFile
        val markdownReport = markdownReportProvider.get().asFile
        jsonReport.writeText(nodeProfileReleaseManifestJson(manifest))
        markdownReport.writeText(
            buildString {
                appendLine("# AutoJs6 Node Profile Release Manifest")
                appendLine()
                appendLine("- schema: `${manifest["schema"]}`")
                appendLine("- generatedAt: `${manifest["generatedAt"]}`")
                appendLine("- git commit: `${(manifest["git"] as Map<*, *>)["shortCommit"]}`")
                appendLine("- release tag: `$releaseTag`")
                appendLine("- release identity status: `$releaseIdentityStatus`")
                appendLine("- identity profile: `$identityProfile`")
                appendLine("- application id: `${effectiveApplicationId ?: "unknown"}`")
                appendLine("- app label: `${appLabel ?: "unknown"}`")
                appendLine("- app version: `${versionProperties.getProperty("VERSION_NAME")}`")
                appendLine("- version code: `${versionProperties.getProperty("VERSION_BUILD")}`")
                appendLine("- build time: `${versionProperties.getProperty("BUILD_TIME")}`")
                appendLine("- release flavor: `$releaseFlavor`")
                appendLine("- backend: `$effectiveBackend`")
                appendLine("- expected ABIs: `${expectedAbis.joinToString()}`")
                appendLine()
                appendLine("## Native Libraries")
                appendLine()
                appendLine("| ABI | Library | Exists | Size | SHA-256 |")
                appendLine("| --- | --- | --- | ---: | --- |")
                val nativeLibraries = manifest["nativeLibraries"] as Map<*, *>
                nativeLibraries.forEach { (abi, libs) ->
                    @Suppress("UNCHECKED_CAST")
                    (libs as Map<String, Map<String, Any?>>).forEach { (name, info) ->
                        appendLine(
                            "| $abi | `$name` | ${info["exists"]} | ${info["sizeBytes"] ?: "n/a"} | `${info["sha256"] ?: "unavailable"}` |"
                        )
                    }
                }
                appendLine()
                appendLine("## Artifact Hashes")
                appendLine()
                appendLine("| Artifact | Exists | SHA-256 |")
                appendLine("| --- | --- | --- |")
                val artifacts = manifest["artifacts"] as Map<*, *>
                artifacts.forEach { (name, infoAny) ->
                    @Suppress("UNCHECKED_CAST")
                    val info = infoAny as Map<String, Any?>
                    appendLine("| `$name` | ${info["exists"]} | `${info["sha256"] ?: "unavailable"}` |")
                }
                appendLine()
                appendLine("## Runtime Capabilities")
                appendLine()
                appendLine("| Property | Effective value |")
                appendLine("| --- | --- |")
                runtimeCapabilityDefaults.forEach { (name, value) ->
                    appendLine("| `$name` | `$value` |")
                }
            }
        )

        logger.lifecycle("Node Profile release manifest written to ${jsonReport.absolutePath}")
        logger.lifecycle("Node Profile release manifest markdown written to ${markdownReport.absolutePath}")
    }
}
