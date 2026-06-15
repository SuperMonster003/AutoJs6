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

tasks.matching { it.name == "verifyEmbeddedNodeReleaseCandidate" }.configureEach {
    dependsOn(embeddedNodeDoctorReport)
}
