import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.time.ZonedDateTime

private fun nodeDoctorJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun nodeDoctorReadJsonObject(file: File): Map<*, *>? =
    runCatching { JsonSlurper().parse(file) as? Map<*, *> }.getOrNull()

private fun Any?.nodeDoctorMap(): Map<*, *> =
    this as? Map<*, *> ?: emptyMap<String, Any?>()

private fun Any?.nodeDoctorListOfMaps(): List<Map<*, *>> =
    (this as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()

private fun Map<*, *>.nodeDoctorString(key: String): String =
    this[key]?.toString().orEmpty()

private fun Map<*, *>.nodeDoctorBoolean(key: String): Boolean? =
    when (val value = this[key]) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }

private fun File.nodeDoctorPathRelativeTo(root: File): String =
    runCatching {
        root.canonicalFile.toPath()
            .relativize(canonicalFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')
    }.getOrElse { invariantSeparatorsPath }

private fun nodeDoctorReportReference(file: File, rootDir: File): Map<String, Any?> =
    linkedMapOf(
        "path" to file.nodeDoctorPathRelativeTo(rootDir),
        "absolutePath" to file.absolutePath,
        "available" to file.isFile,
        "bytes" to file.takeIf(File::isFile)?.length(),
    )

private fun nodeDoctorFirstValueLine(text: String?, prefix: String): String? =
    text?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith(prefix) }
        ?.substringAfter(':')
        ?.trim()

private fun nodeDoctorSummaryProperties(text: String?, keyPrefix: String): Map<String, String> =
    text?.lineSequence()
        ?.map(String::trim)
        ?.filter { it.startsWith(keyPrefix) && it.contains('=') }
        ?.map { line -> line.substringBefore('=') to line.substringAfter('=') }
        ?.toMap()
        .orEmpty()

private fun nodeDoctorNativeHashEntries(text: String?): List<Map<String, Any?>> {
    if (text == null) {
        return emptyList()
    }
    val entries = mutableListOf<MutableMap<String, Any?>>()
    var abi = ""
    var library = ""
    var source = ""
    var path = ""
    var size: Long? = null

    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.startsWith("ABI: ") -> {
                abi = line.substringAfter("ABI: ").trim()
                library = ""
                source = ""
                path = ""
                size = null
            }
            Regex("""^\s{2}lib[^:]+\.so:$""").matches(line) -> {
                library = line.trim().removeSuffix(":")
                source = ""
                path = ""
                size = null
            }
            Regex("""^\s{4}.+:$""").matches(line) -> {
                source = line.trim().removeSuffix(":")
                path = ""
                size = null
            }
            line.trim().startsWith("path: ") -> {
                path = line.trim().substringAfter("path: ").trim()
            }
            line.trim().startsWith("size: ") -> {
                size = line.trim().substringAfter("size: ").trim().toLongOrNull()
            }
            line.trim().startsWith("sha256: ") -> {
                entries += linkedMapOf(
                    "abi" to abi,
                    "library" to library,
                    "source" to source,
                    "path" to path,
                    "size" to size,
                    "sha256" to line.trim().substringAfter("sha256: ").trim(),
                )
            }
            line.trim().startsWith("build_id: ") -> {
                entries.lastOrNull()
                    ?.takeIf { it["abi"] == abi && it["library"] == library && it["source"] == source }
                    ?.put("buildId", line.trim().substringAfter("build_id: ").trim())
            }
        }
    }
    return entries
}

private fun nodeDoctorHashLines(text: String?): List<String> =
    text?.lineSequence()
        ?.map(String::trim)
        ?.filter { it.contains(" hash:", ignoreCase = true) || it.contains("sha256:", ignoreCase = true) }
        ?.take(160)
        ?.toList()
        .orEmpty()

private fun nodeDoctorPackageSummary(text: String?, reportFile: File): Map<String, Any?> {
    val summary = nodeDoctorSummaryProperties(text, "embedded.node.packaged.output.")
    return linkedMapOf(
        "available" to reportFile.isFile,
        "report" to reportFile.absolutePath,
        "ready" to summary["embedded.node.packaged.output.ready"],
        "apkExists" to summary["embedded.node.packaged.output.apk.exists"],
        "manifestServiceReady" to summary["embedded.node.packaged.output.manifest.service.ready"],
        "manifestForegroundServiceReady" to summary["embedded.node.packaged.output.manifest.fgs_permission.ready"],
        "nativeLibsReady" to summary["embedded.node.packaged.output.native_libs.ready"],
        "expectedAbis" to summary["embedded.node.packaged.output.release.expected_abis"],
        "releaseFlavor" to summary["embedded.node.packaged.output.release.flavor"],
        "summary" to summary,
    )
}

private fun nodeDoctorTextList(values: List<Any?>, emptyText: String = "none"): String =
    if (values.isEmpty()) emptyText else values.joinToString()

private fun nodeNightlyLatestFile(root: File, predicate: (File) -> Boolean): File? =
    root.takeIf(File::isDirectory)
        ?.walkTopDown()
        ?.filter(File::isFile)
        ?.filter(predicate)
        ?.maxByOrNull(File::lastModified)

private fun nodeNightlyXmlAttribute(text: String?, attribute: String): String? =
    text?.let { Regex("""\b$attribute="([^"]*)"""").find(it)?.groupValues?.get(1) }

private fun nodeNightlyXmlDevice(text: String?): String? =
    text?.let { Regex("""<property\s+name="device"\s+value="([^"]*)"""").find(it)?.groupValues?.get(1) }

private fun nodeNightlyTagAttribute(tag: String, attribute: String): String? =
    Regex("""\b$attribute="([^"]*)"""").find(tag)?.groupValues?.get(1)

private fun nodeNightlyFailedTests(text: String?): List<Map<String, String>> {
    if (text.isNullOrBlank()) {
        return emptyList()
    }
    val testCaseRegex = Regex("""(<testcase\b[^>]*>)(.*?)</testcase>""", setOf(RegexOption.DOT_MATCHES_ALL))
    val failureRegex = Regex("""<(failure|error)\b[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL))
    return testCaseRegex.findAll(text).mapNotNull { match ->
        val failure = failureRegex.find(match.groupValues[2]) ?: return@mapNotNull null
        val tag = match.groupValues[1]
        linkedMapOf(
            "className" to (nodeNightlyTagAttribute(tag, "classname") ?: "unknown"),
            "name" to (nodeNightlyTagAttribute(tag, "name") ?: "unknown"),
            "type" to failure.groupValues[1],
            "message" to failure.groupValues[2].lineSequence().firstOrNull().orEmpty().take(240),
        )
    }.toList()
}

private fun nodeNightlyCommandText(workingDir: File, vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(command.toList())
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        output.trim().takeIf { exitCode == 0 && it.isNotEmpty() }
    }.getOrNull()

val embeddedNodeDoctorReport = tasks.register("embeddedNodeDoctorReport") {
    group = "verification"
    description = "Writes an attachable Embedded Node doctor report with runtime, native, packaging, and dependency diagnostics."

    dependsOn(
        "generateNodeCompatibilityProfile",
        "nativeNodeDependencyReportAppDebug",
        "nativeNodeSymbolArchiveReport",
        "bundleNodeDependencies",
    )

    val doctorDirProvider = layout.buildDirectory.dir("reports/nodejs/doctor")
    val doctorJsonProvider = doctorDirProvider.map { it.file("node-doctor-report.json") }
    val doctorTextProvider = doctorDirProvider.map { it.file("node-doctor-report.txt") }

    outputs.file(doctorJsonProvider)
    outputs.file(doctorTextProvider)
    outputs.upToDateWhen { false }

    doLast {
        val reportsRoot = layout.buildDirectory.dir("reports").get().asFile
        val compatibilityFile = reportsRoot.resolve("nodejs/compatibility-profile.json")
        val nativeDepsFile = reportsRoot.resolve("native-node/native-deps-appDebug.txt")
        val nativeSymbolsFile = reportsRoot.resolve("native-node/native-symbol-archive-appDebug.txt")
        val packagedDiagnosticFile = reportsRoot.resolve("embedded-node/packaged-diagnostic-report.txt")
        val dependencyManifestFile = reportsRoot.resolve("nodejs/dependencies/dependency-manifest.json")
        val differentialFile = reportsRoot.resolve("nodejs/differential/desktop-node-differential.json")

        val compatibility = nodeDoctorReadJsonObject(compatibilityFile).orEmpty()
        val runtime = compatibility["runtime"].nodeDoctorMap()
        val builtins = compatibility["builtins"].nodeDoctorMap()
        val builtinStatuses = builtins["statuses"].nodeDoctorListOfMaps()
        val bridgeModules = compatibility["autoJsBridgeModules"].nodeDoctorListOfMaps()
        val dangerousApis = compatibility["dangerousApis"].nodeDoctorListOfMaps()
        val featureFlags = compatibility["featureFlags"].nodeDoctorListOfMaps()
        val packagedProfile = compatibility["packagedApkSupport"].nodeDoctorMap()

        val stableBuiltins = builtinStatuses
            .filter { it.nodeDoctorString("status") == "stable" }
            .map { it.nodeDoctorString("name") }
            .sorted()
        val partialBuiltins = builtinStatuses
            .filter { it.nodeDoctorString("status") == "partial" }
            .map { it.nodeDoctorString("name") }
            .sorted()
        val disabledBuiltins = builtinStatuses
            .filter { it.nodeDoctorString("status") in setOf("disabled", "unsupported") }
            .map { "${it.nodeDoctorString("name")}=${it.nodeDoctorString("status")}" }
            .sorted()
        val enabledBridgeModules = bridgeModules
            .map { "${it.nodeDoctorString("name")}=${it.nodeDoctorString("status")}" }
            .sorted()

        val dependencyManifest = nodeDoctorReadJsonObject(dependencyManifestFile)
        val dependencyTotals = dependencyManifest?.get("totals").nodeDoctorMap()
        val dependencyIssues = dependencyManifest?.get("issues").nodeDoctorListOfMaps()
        val differential = nodeDoctorReadJsonObject(differentialFile)
        val differentialSummary = differential?.get("summary").nodeDoctorMap()

        val nativeDepsText = nativeDepsFile.takeIf(File::isFile)?.readText()
        val nativeSymbolsText = nativeSymbolsFile.takeIf(File::isFile)?.readText()
        val packagedText = packagedDiagnosticFile.takeIf(File::isFile)?.readText()
        val nativeHashEntries = nodeDoctorNativeHashEntries(nativeSymbolsText)
        val nativeDependencyHashLines = nodeDoctorHashLines(nativeDepsText)
        val packagedSummary = nodeDoctorPackageSummary(packagedText, packagedDiagnosticFile)
        val backend = providers.gradleProperty("autojs.nodejs.backend").orElse(runtime.nodeDoctorString("defaultBackend").ifBlank { "embedded" }).get()
            .trim()
            .lowercase()
        val releaseFlavor = providers.gradleProperty("autojs.nodejs.releaseFlavor")
            .orElse(packagedProfile.nodeDoctorString("releaseFlavor").ifBlank { "nodeFull" })
            .get()
        val now = ZonedDateTime.now().toString()

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-doctor-v1",
            "generatedAt" to now,
            "host" to linkedMapOf(
                "osName" to System.getProperty("os.name"),
                "osVersion" to System.getProperty("os.version"),
                "osArch" to System.getProperty("os.arch"),
                "javaVersion" to System.getProperty("java.version"),
                "gradleVersion" to gradle.gradleVersion,
                "timezone" to System.getProperty("user.timezone"),
            ),
            "workspace" to linkedMapOf(
                "root" to rootProject.projectDir.absolutePath,
                "appProject" to projectDir.absolutePath,
                "buildDir" to layout.buildDirectory.get().asFile.absolutePath,
            ),
            "runtime" to linkedMapOf(
                "profileName" to runtime.nodeDoctorString("profileName"),
                "engineVersion" to runtime.nodeDoctorString("engineVersion"),
                "backend" to backend,
                "nodeExpectedPrefix" to runtime.nodeDoctorString("nodeVersionExpectedPrefix"),
                "nodeHeaderVersion" to runtime.nodeDoctorString("nodeHeaderVersion"),
                "backendSwitches" to runtime["backendSwitches"],
                "routeSuffixes" to runtime["routeSuffixes"],
                "workingDirectory" to linkedMapOf(
                    "status" to "host-unavailable",
                    "message" to "Run a script/device diagnostic to capture the per-execution scoped workingDirectory.",
                ),
                "lastError" to linkedMapOf(
                    "status" to "host-unavailable",
                    "message" to "No live Node execution is attached to this host report.",
                ),
                "lifecycleState" to linkedMapOf(
                    "status" to "host-unavailable",
                    "message" to "Lifecycle state is process-local and must be captured from service diagnostics/logcat.",
                ),
                "resourceCounts" to linkedMapOf(
                    "status" to "host-unavailable",
                    "message" to "Resource counts are runtime counters and are unavailable from a host-only Gradle report.",
                ),
            ),
            "featureFlags" to featureFlags.map {
                linkedMapOf(
                    "id" to it.nodeDoctorString("id"),
                    "diagnosticName" to it.nodeDoctorString("diagnosticName"),
                    "gradleProperty" to it["gradleProperty"],
                    "status" to it.nodeDoctorString("status"),
                    "securityLevel" to it.nodeDoctorString("securityLevel"),
                    "effectiveEnabled" to it.nodeDoctorBoolean("effectiveEnabled"),
                )
            },
            "modules" to linkedMapOf(
                "stableBuiltins" to stableBuiltins,
                "partialBuiltins" to partialBuiltins,
                "disabledBuiltins" to disabledBuiltins,
                "bridgeModules" to enabledBridgeModules,
                "dangerousApis" to dangerousApis.map {
                    "${it.nodeDoctorString("name")}=${it.nodeDoctorString("status")}"
                },
            ),
            "packagedApk" to linkedMapOf(
                "releaseFlavor" to releaseFlavor,
                "profile" to packagedProfile,
                "diagnostic" to packagedSummary,
            ),
            "nativeLibraries" to linkedMapOf(
                "dependencyReport" to nodeDoctorReportReference(nativeDepsFile, rootProject.projectDir),
                "symbolArchiveReport" to nodeDoctorReportReference(nativeSymbolsFile, rootProject.projectDir),
                "hashEntries" to nativeHashEntries,
                "dependencyHashLines" to nativeDependencyHashLines,
                "symbolArchiveReady" to nodeDoctorSummaryProperties(nativeSymbolsText, "embedded.node.native.symbol_archive.")["embedded.node.native.symbol_archive.ready"],
                "dependencyGeneratedAt" to nodeDoctorFirstValueLine(nativeDepsText, "Generated at:"),
                "symbolArchiveGeneratedAt" to nodeDoctorFirstValueLine(nativeSymbolsText, "Generated at:"),
            ),
            "dependencyBundle" to linkedMapOf(
                "manifest" to nodeDoctorReportReference(dependencyManifestFile, rootProject.projectDir),
                "projectRoot" to dependencyManifest?.get("projectRoot"),
                "sanitizedBundle" to dependencyManifest?.get("sanitizedBundle"),
                "totals" to dependencyTotals,
                "issues" to dependencyIssues,
                "ready" to (dependencyManifestFile.isFile && dependencyIssues.isEmpty()),
            ),
            "differential" to linkedMapOf(
                "report" to nodeDoctorReportReference(differentialFile, rootProject.projectDir),
                "summary" to differentialSummary,
            ),
            "inputs" to listOf(
                compatibilityFile,
                nativeDepsFile,
                nativeSymbolsFile,
                packagedDiagnosticFile,
                dependencyManifestFile,
                differentialFile,
            ).map { nodeDoctorReportReference(it, rootProject.projectDir) },
        )

        val textReport = buildString {
            appendLine("AutoJs6 Embedded Node Doctor Report")
            appendLine("Generated at: $now")
            appendLine("Schema: autojs6-node-doctor-v1")
            appendLine()
            appendLine("Host:")
            appendLine("  OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            appendLine("  Java: ${System.getProperty("java.version")}")
            appendLine("  Gradle: ${gradle.gradleVersion}")
            appendLine("  Workspace: ${rootProject.projectDir.absolutePath}")
            appendLine()
            appendLine("Runtime:")
            appendLine("  profile: ${runtime.nodeDoctorString("profileName")}")
            appendLine("  engine: ${runtime.nodeDoctorString("engineVersion")}")
            appendLine("  backend: $backend")
            appendLine("  expected Node: ${runtime.nodeDoctorString("nodeVersionExpectedPrefix")}")
            appendLine("  ABI release flavor: $releaseFlavor")
            appendLine("  expected ABIs: ${packagedProfile["expectedAbis"]}")
            appendLine("  unsupported ABIs: ${packagedProfile["unsupportedAbis"]}")
            appendLine("  workingDirectory: host-unavailable")
            appendLine("  lastError: host-unavailable")
            appendLine("  lifecycleState: host-unavailable")
            appendLine("  resourceCounts: host-unavailable")
            appendLine()
            appendLine("Feature Flags:")
            featureFlags.forEach {
                appendLine(
                    "  ${it.nodeDoctorString("id")}: enabled=${it.nodeDoctorBoolean("effectiveEnabled")} " +
                            "status=${it.nodeDoctorString("status")} security=${it.nodeDoctorString("securityLevel")}"
                )
            }
            appendLine()
            appendLine("Modules:")
            appendLine("  stable builtins: ${nodeDoctorTextList(stableBuiltins)}")
            appendLine("  partial builtins: ${nodeDoctorTextList(partialBuiltins)}")
            appendLine("  disabled builtins: ${nodeDoctorTextList(disabledBuiltins)}")
            appendLine("  bridge modules: ${nodeDoctorTextList(enabledBridgeModules)}")
            appendLine()
            appendLine("Native Libraries:")
            appendLine("  dependency report: ${nativeDepsFile.nodeDoctorPathRelativeTo(rootProject.projectDir)} available=${nativeDepsFile.isFile}")
            appendLine("  symbol archive: ${nativeSymbolsFile.nodeDoctorPathRelativeTo(rootProject.projectDir)} available=${nativeSymbolsFile.isFile}")
            appendLine("  symbol archive ready: ${nodeDoctorSummaryProperties(nativeSymbolsText, "embedded.node.native.symbol_archive.")["embedded.node.native.symbol_archive.ready"]}")
            appendLine("  native hash entries: ${nativeHashEntries.size}")
            nativeHashEntries.take(24).forEach {
                appendLine(
                    "    ${it["abi"]}/${it["library"]}/${it["source"]}: " +
                            "sha256=${it["sha256"]} build_id=${it["buildId"] ?: "n/a"}"
                )
            }
            if (nativeHashEntries.size > 24) {
                appendLine("    ... (${nativeHashEntries.size - 24} more; see JSON report)")
            }
            appendLine()
            appendLine("Packaged APK:")
            appendLine("  diagnostic report: ${packagedDiagnosticFile.nodeDoctorPathRelativeTo(rootProject.projectDir)} available=${packagedDiagnosticFile.isFile}")
            appendLine("  ready: ${packagedSummary["ready"]}")
            appendLine("  apk exists: ${packagedSummary["apkExists"]}")
            appendLine("  manifest service ready: ${packagedSummary["manifestServiceReady"]}")
            appendLine("  native libs ready: ${packagedSummary["nativeLibsReady"]}")
            appendLine()
            appendLine("Dependency Bundle:")
            appendLine("  manifest: ${dependencyManifestFile.nodeDoctorPathRelativeTo(rootProject.projectDir)} available=${dependencyManifestFile.isFile}")
            appendLine("  projectRoot: ${dependencyManifest?.get("projectRoot") ?: "unavailable"}")
            appendLine("  totals: packages=${dependencyTotals["packages"] ?: "unavailable"} files=${dependencyTotals["files"] ?: "unavailable"} bytes=${dependencyTotals["bytes"] ?: "unavailable"}")
            appendLine("  issues: ${dependencyIssues.size}")
            appendLine()
            appendLine("Differential:")
            appendLine("  report: ${differentialFile.nodeDoctorPathRelativeTo(rootProject.projectDir)} available=${differentialFile.isFile}")
            appendLine("  summary: ${if (differentialSummary.isEmpty()) "unavailable" else differentialSummary}")
            appendLine()
            appendLine("Attach both files to bug reports:")
            appendLine("  ${doctorTextProvider.get().asFile.nodeDoctorPathRelativeTo(rootProject.projectDir)}")
            appendLine("  ${doctorJsonProvider.get().asFile.nodeDoctorPathRelativeTo(rootProject.projectDir)}")
        }

        val doctorDir = doctorDirProvider.get().asFile
        doctorDir.mkdirs()
        doctorJsonProvider.get().asFile.writeText(nodeDoctorJson(report) + "\n")
        doctorTextProvider.get().asFile.writeText(textReport)
        logger.lifecycle("Embedded Node doctor JSON report written to ${doctorJsonProvider.get().asFile.absolutePath}")
        logger.lifecycle("Embedded Node doctor text report written to ${doctorTextProvider.get().asFile.absolutePath}")
    }
}

tasks.matching { it.name == "verifyEmbeddedNodeDebug" }.configureEach {
    dependsOn(embeddedNodeDoctorReport)
}

val generateNodeNightlyReport = tasks.register("generateNodeNightlyReport") {
    group = "verification"
    description = "Writes a single Embedded Node nightly JSON/Markdown report from available build and device reports."

    dependsOn(
        embeddedNodeDoctorReport,
        "generateNodeProfileReleaseManifest",
    )

    val nightlyDirProvider = rootProject.layout.buildDirectory.dir("reports/nodejs")
    val nightlyJsonProvider = nightlyDirProvider.map { it.file("nightly-report.json") }
    val nightlyMarkdownProvider = nightlyDirProvider.map { it.file("nightly-report.md") }

    outputs.file(nightlyJsonProvider)
    outputs.file(nightlyMarkdownProvider)
    outputs.upToDateWhen { false }

    doLast {
        val now = ZonedDateTime.now().toString()
        val reportsRoot = layout.buildDirectory.dir("reports").get().asFile
        val rootReportsRoot = rootProject.layout.buildDirectory.dir("reports").get().asFile
        val androidResultsRoot = layout.buildDirectory
            .dir("outputs/androidTest-results/connected")
            .get()
            .asFile
        val doctorJsonFile = reportsRoot.resolve("nodejs/doctor/node-doctor-report.json")
        val releaseManifestFile = reportsRoot.resolve("nodejs/release-manifest.json")
        val packagedSmokeFile = reportsRoot.resolve("embedded-node/packaged-smoke-aggregate.txt")
        val packagedDiagnosticFile = reportsRoot.resolve("embedded-node/packaged-diagnostic-report.txt")
        val perfDir = reportsRoot.resolve("nodejs/perf")
        val latestPerformanceFile = nodeNightlyLatestFile(perfDir) {
            it.name.startsWith("embedded-node-performance-") && it.extension == "json"
        }
        val latestMemoryFile = nodeNightlyLatestFile(perfDir) {
            it.name.startsWith("embedded-node-memory-leak-stress-") && it.extension == "json"
        }
        val latestAndroidXml = nodeNightlyLatestFile(androidResultsRoot) {
            it.name.startsWith("TEST-") && it.extension == "xml"
        }
        val fuzzSeedDir = rootReportsRoot.resolve("nodejs/fuzz-seeds")
        val fuzzSeedFiles = fuzzSeedDir.takeIf(File::isDirectory)
            ?.walkTopDown()
            ?.filter(File::isFile)
            ?.toList()
            .orEmpty()

        val doctor = nodeDoctorReadJsonObject(doctorJsonFile).orEmpty()
        val releaseManifest = nodeDoctorReadJsonObject(releaseManifestFile)
        val performance = latestPerformanceFile?.let(::nodeDoctorReadJsonObject)
        val memory = latestMemoryFile?.let(::nodeDoctorReadJsonObject)
        val androidXmlText = latestAndroidXml?.readText()
        val packagedSmokeText = packagedSmokeFile.takeIf(File::isFile)?.readText()
        val packagedSmokeSummary = nodeDoctorSummaryProperties(packagedSmokeText, "embedded.node.packaged.smoke.")
        val nativeLibraries = doctor["nativeLibraries"].nodeDoctorMap()
        val featureFlags = doctor["featureFlags"] as? List<*> ?: emptyList<Any?>()
        val memoryScenarios = (memory?.get("scenarios") as? List<*>).orEmpty()
        val memoryFailures = (memory?.get("failures") as? List<*>).orEmpty()
        val testFailures = nodeNightlyFailedTests(androidXmlText)
        val gitCommit = nodeNightlyCommandText(rootProject.projectDir, "git", "rev-parse", "HEAD") ?: "unknown"
        val gitStatus = nodeNightlyCommandText(rootProject.projectDir, "git", "status", "--short").orEmpty()
        val deviceProfile = providers.gradleProperty("autojs.nodejs.test.deviceProfile").orNull
        val adbSerial = providers.gradleProperty("autojs.embeddedNode.adbSerial").orNull
            ?: System.getenv("ANDROID_SERIAL")

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-nightly-report-v1",
            "generatedAt" to now,
            "git" to linkedMapOf(
                "commit" to gitCommit,
                "dirty" to gitStatus.isNotBlank(),
                "statusShort" to gitStatus.lineSequence().take(80).toList(),
            ),
            "device" to linkedMapOf(
                "profile" to (deviceProfile ?: "unset"),
                "adbSerial" to (adbSerial ?: "unset"),
                "latestAndroidTestDevice" to (nodeNightlyXmlDevice(androidXmlText) ?: "unavailable"),
                "performanceDevice" to (performance?.get("device") ?: "unavailable"),
                "memoryDevice" to (memory?.get("device") ?: "unavailable"),
            ),
            "abi" to linkedMapOf(
                "releaseFlavor" to providers.gradleProperty("autojs.nodejs.releaseFlavor").orNull,
                "releaseManifest" to (releaseManifest ?: "unavailable"),
            ),
            "tests" to linkedMapOf(
                "latestXml" to latestAndroidXml?.let { nodeDoctorReportReference(it, rootProject.projectDir) },
                "tests" to nodeNightlyXmlAttribute(androidXmlText, "tests")?.toIntOrNull(),
                "failures" to nodeNightlyXmlAttribute(androidXmlText, "failures")?.toIntOrNull(),
                "errors" to nodeNightlyXmlAttribute(androidXmlText, "errors")?.toIntOrNull(),
                "skipped" to nodeNightlyXmlAttribute(androidXmlText, "skipped")?.toIntOrNull(),
                "failedTests" to testFailures,
            ),
            "performance" to linkedMapOf(
                "report" to latestPerformanceFile?.let { nodeDoctorReportReference(it, rootProject.projectDir) },
                "metrics" to (performance?.get("metrics") ?: "unavailable"),
            ),
            "memory" to linkedMapOf(
                "report" to latestMemoryFile?.let { nodeDoctorReportReference(it, rootProject.projectDir) },
                "configuration" to (memory?.get("configuration") ?: "unavailable"),
                "scenarioCount" to memoryScenarios.size,
                "failureCount" to memoryFailures.size,
                "failures" to memoryFailures,
            ),
            "fuzzers" to linkedMapOf(
                "seedDir" to nodeDoctorReportReference(fuzzSeedDir, rootProject.projectDir),
                "seedCount" to fuzzSeedFiles.size,
                "seeds" to fuzzSeedFiles.take(100).map { nodeDoctorReportReference(it, rootProject.projectDir) },
            ),
            "packagedApkSmoke" to linkedMapOf(
                "aggregateReport" to nodeDoctorReportReference(packagedSmokeFile, rootProject.projectDir),
                "diagnosticReport" to nodeDoctorReportReference(packagedDiagnosticFile, rootProject.projectDir),
                "ready" to packagedSmokeSummary["embedded.node.packaged.smoke.aggregate.ready"],
                "pullReady" to packagedSmokeSummary["embedded.node.packaged.smoke.pull.ready"],
                "successOutputReady" to packagedSmokeSummary["embedded.node.packaged.smoke.success.output.ready"],
                "diagnosticsOutputReady" to packagedSmokeSummary["embedded.node.packaged.smoke.diagnostics.output.ready"],
                "templateReady" to packagedSmokeSummary["embedded.node.packaged.smoke.template.ready"],
                "moduleSourcesReady" to packagedSmokeSummary["embedded.node.packaged.smoke.module_sources.ready"],
            ),
            "nativeLibraries" to nativeLibraries,
            "experimentalFeatures" to featureFlags,
            "inputs" to listOfNotNull(
                doctorJsonFile,
                releaseManifestFile,
                packagedSmokeFile,
                packagedDiagnosticFile,
                latestPerformanceFile,
                latestMemoryFile,
                latestAndroidXml,
            ).map { nodeDoctorReportReference(it, rootProject.projectDir) },
        )

        val markdown = buildString {
            appendLine("# Embedded Node Nightly Report")
            appendLine()
            appendLine("- generated: `$now`")
            appendLine("- commit: `$gitCommit`${if (gitStatus.isBlank()) "" else " (dirty)"}")
            appendLine("- device profile: `${deviceProfile ?: "unset"}`")
            appendLine("- adb serial: `${adbSerial ?: "unset"}`")
            appendLine("- latest Android test device: `${nodeNightlyXmlDevice(androidXmlText) ?: "unavailable"}`")
            appendLine()
            appendLine("## Tests")
            appendLine()
            appendLine("- report: `${latestAndroidXml?.nodeDoctorPathRelativeTo(rootProject.projectDir) ?: "unavailable"}`")
            appendLine("- tests: `${nodeNightlyXmlAttribute(androidXmlText, "tests") ?: "unavailable"}`")
            appendLine("- failures: `${nodeNightlyXmlAttribute(androidXmlText, "failures") ?: "unavailable"}`")
            appendLine("- errors: `${nodeNightlyXmlAttribute(androidXmlText, "errors") ?: "unavailable"}`")
            appendLine("- skipped: `${nodeNightlyXmlAttribute(androidXmlText, "skipped") ?: "unavailable"}`")
            if (testFailures.isNotEmpty()) {
                appendLine()
                appendLine("Failed tests:")
                testFailures.take(20).forEach {
                    appendLine("- `${it["className"]}#${it["name"]}`: ${it["message"]}")
                }
            }
            appendLine()
            appendLine("## Packaged Smoke")
            appendLine()
            appendLine("- aggregate ready: `${packagedSmokeSummary["embedded.node.packaged.smoke.aggregate.ready"] ?: "unavailable"}`")
            appendLine("- pull ready: `${packagedSmokeSummary["embedded.node.packaged.smoke.pull.ready"] ?: "unavailable"}`")
            appendLine("- success output ready: `${packagedSmokeSummary["embedded.node.packaged.smoke.success.output.ready"] ?: "unavailable"}`")
            appendLine("- diagnostics output ready: `${packagedSmokeSummary["embedded.node.packaged.smoke.diagnostics.output.ready"] ?: "unavailable"}`")
            appendLine("- template ready: `${packagedSmokeSummary["embedded.node.packaged.smoke.template.ready"] ?: "unavailable"}`")
            appendLine()
            appendLine("## Performance")
            appendLine()
            appendLine("- report: `${latestPerformanceFile?.nodeDoctorPathRelativeTo(rootProject.projectDir) ?: "unavailable"}`")
            appendLine("- metrics: `${performance?.get("metrics") ?: "unavailable"}`")
            appendLine()
            appendLine("## Memory")
            appendLine()
            appendLine("- report: `${latestMemoryFile?.nodeDoctorPathRelativeTo(rootProject.projectDir) ?: "unavailable"}`")
            appendLine("- scenarios: `${memoryScenarios.size}`")
            appendLine("- failures: `${memoryFailures.size}`")
            appendLine()
            appendLine("## Fuzzers")
            appendLine()
            appendLine("- seed directory: `${fuzzSeedDir.nodeDoctorPathRelativeTo(rootProject.projectDir)}`")
            appendLine("- saved seeds: `${fuzzSeedFiles.size}`")
            appendLine()
            appendLine("## Native And Feature Flags")
            appendLine()
            appendLine("- native hash entries: `${(nativeLibraries["hashEntries"] as? List<*>)?.size ?: "unavailable"}`")
            appendLine("- experimental feature entries: `${featureFlags.size}`")
            appendLine()
            appendLine("JSON report: `${nightlyJsonProvider.get().asFile.nodeDoctorPathRelativeTo(rootProject.projectDir)}`")
        }

        val nightlyDir = nightlyDirProvider.get().asFile
        nightlyDir.mkdirs()
        nightlyJsonProvider.get().asFile.writeText(nodeDoctorJson(report) + "\n")
        nightlyMarkdownProvider.get().asFile.writeText(markdown)
        logger.lifecycle("Embedded Node nightly JSON report written to ${nightlyJsonProvider.get().asFile.absolutePath}")
        logger.lifecycle("Embedded Node nightly Markdown report written to ${nightlyMarkdownProvider.get().asFile.absolutePath}")
    }
}

tasks.matching { it.name in setOf("verifyNodeNightlyGate", "verifyNodeReleaseGate") }.configureEach {
    finalizedBy(generateNodeNightlyReport)
}

tasks.matching { it.name == "verifyEmbeddedNodeReleaseCandidate" }.configureEach {
    dependsOn(embeddedNodeDoctorReport)
}
