import groovy.json.JsonOutput
import java.io.File
import java.time.ZonedDateTime

private val nodeV11AlphaDeviceProfileProperty = "autojs.nodejs.test.deviceProfile"
private val nodeV11AlphaDeviceProfile = "v1_1_alpha"

private fun nodeV11AlphaJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun File.nodeV11AlphaPathRelativeTo(root: File): String =
    runCatching {
        root.canonicalFile.toPath()
            .relativize(canonicalFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')
    }.getOrElse { invariantSeparatorsPath }

private fun nodeV11AlphaReportReference(file: File, rootDir: File): Map<String, Any?> =
    linkedMapOf(
        "path" to file.nodeV11AlphaPathRelativeTo(rootDir),
        "absolutePath" to file.absolutePath,
        "available" to file.isFile,
        "bytes" to file.takeIf(File::isFile)?.length(),
    )

private fun nodeV11AlphaLatestFile(root: File, predicate: (File) -> Boolean): File? =
    root.takeIf(File::isDirectory)
        ?.walkTopDown()
        ?.filter(File::isFile)
        ?.filter(predicate)
        ?.maxByOrNull(File::lastModified)

private fun nodeV11AlphaXmlAttribute(xmlText: String?, name: String): String? =
    xmlText
        ?.let { Regex("""\b${Regex.escape(name)}="([^"]*)"""").find(it) }
        ?.groupValues
        ?.getOrNull(1)

private fun nodeV11AlphaCommandText(rootDir: File, vararg command: String): String? =
    runCatching {
        ProcessBuilder(*command)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
            .also { process -> process.waitFor() }
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
            .takeIf(String::isNotEmpty)
    }.getOrNull()

private fun nodeV11AlphaProperties(text: String?, prefix: String): Map<String, String> =
    text
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter { it.startsWith(prefix) && it.contains('=') }
        ?.associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        .orEmpty()

private fun nodeV11AlphaBooleanProperty(name: String, defaultValue: Boolean = false): Boolean =
    providers.gradleProperty(name).orNull
        ?.trim()
        ?.lowercase()
        ?.let { it == "true" || it == "yes" || it == "1" }
        ?: defaultValue

val generateNodeV11AlphaSummary = tasks.register("generateNodeV11AlphaSummary") {
    group = "verification"
    description = "Writes the Node Profile v1.1 alpha milestone summary JSON from available gate reports."

    dependsOn(
        "embeddedNodeDoctorReport",
        "generateNodeCompatibilityProfile",
        "generateDesktopNodeDifferentialReport",
    )
    mustRunAfter("connectedAppDebugAndroidTest", "collectNodeFuzzerSeeds")

    val outputProvider = rootProject.layout.buildDirectory.file("reports/nodejs/v1.1-alpha-summary.json")
    outputs.file(outputProvider)
    outputs.upToDateWhen { false }

    doLast {
        val now = ZonedDateTime.now().toString()
        val rootDir = rootProject.projectDir
        val appReportsRoot = layout.buildDirectory.dir("reports").get().asFile
        val rootReportsRoot = rootProject.layout.buildDirectory.dir("reports").get().asFile
        val androidResultsRoot = layout.buildDirectory
            .dir("outputs/androidTest-results/connected")
            .get()
            .asFile
        val latestAndroidXml = nodeV11AlphaLatestFile(androidResultsRoot) {
            it.name.startsWith("TEST-") && it.extension == "xml"
        }
        val latestAndroidXmlText = latestAndroidXml?.readText()
        val packagedSmokeFile = appReportsRoot.resolve("embedded-node/packaged-smoke-aggregate.txt")
        val packagedSmokeSummary = nodeV11AlphaProperties(
            packagedSmokeFile.takeIf(File::isFile)?.readText(),
            "embedded.node.packaged.smoke.",
        )
        val fuzzerSeedDir = rootReportsRoot.resolve("nodejs/fuzz-seeds")
        val fuzzerSeedFiles = fuzzerSeedDir.takeIf(File::isDirectory)
            ?.walkTopDown()
            ?.filter(File::isFile)
            ?.toList()
            .orEmpty()
        val gitCommit = nodeV11AlphaCommandText(rootDir, "git", "rev-parse", "HEAD") ?: "unknown"
        val gitStatus = nodeV11AlphaCommandText(rootDir, "git", "status", "--short").orEmpty()
        val deviceProfile = providers.gradleProperty(nodeV11AlphaDeviceProfileProperty).orNull
        val requestedTasks = gradle.startParameter.taskNames
        val gateTaskRequested = requestedTasks.any { taskName ->
            taskName == "verifyNodeV11AlphaGate" || taskName.endsWith(":verifyNodeV11AlphaGate")
        }
        val workerExperimental = nodeV11AlphaBooleanProperty("autojs.nodejs.worker_threads.experimental")
        fun alphaStressArgument(name: String, defaultValue: String): String =
            providers.gradleProperty(name).orElse(defaultValue).get()
        val alphaStressArguments = linkedMapOf(
            "autojs.nodejs.leak.iterations" to alphaStressArgument("autojs.nodejs.leak.iterations", "12"),
            "autojs.nodejs.leak.imageIterations" to alphaStressArgument("autojs.nodejs.leak.imageIterations", "6"),
            "autojs.nodejs.leak.faultIterations" to alphaStressArgument("autojs.nodejs.leak.faultIterations", "3"),
            "autojs.nodejs.bridgeStress.iterations" to alphaStressArgument("autojs.nodejs.bridgeStress.iterations", "8"),
        )

        val requiredCoverage = listOf(
            linkedMapOf(
                "area" to "ESM partial",
                "status" to "required",
                "tests" to listOf(
                    "NodeEsmExperimentalInstrumentationTest",
                    "NodeSourceMapStackTraceInstrumentationTest",
                    "NodeCompatCorpusInstrumentationTest",
                ),
            ),
            linkedMapOf(
                "area" to "npm corpus v1.1",
                "status" to "required",
                "tests" to listOf(
                    "NodeRealNpmCompatInstrumentationTest",
                    "generateDesktopNodeDifferentialReport",
                ),
            ),
            linkedMapOf(
                "area" to "controlled network",
                "status" to "required",
                "tests" to listOf(
                    "NodeFetchBridgeInstrumentationTest",
                    "NodeWebSocketBridgeInstrumentationTest",
                ),
            ),
            linkedMapOf(
                "area" to "AutoJs bridge expansion",
                "status" to "required",
                "tests" to listOf(
                    "NodeBridgeConformanceInstrumentationTest",
                    "NodeOcrBridgeInstrumentationTest",
                    "NodeImageBridgeExpansionInstrumentationTest",
                    "NodeStorageBridgeInstrumentationTest",
                    "NodeNotificationsBridgeInstrumentationTest",
                    "NodeSensorsBridgeInstrumentationTest",
                    "NodeUiBridgeInstrumentationTest",
                    "NodeWorkManagerBridgeInstrumentationTest",
                    "NodeShellBridgeInstrumentationTest",
                    "NodeAccessibilitySelectorBridgeInstrumentationTest",
                ),
            ),
            linkedMapOf(
                "area" to "packaged APK v1.1 smoke",
                "status" to "required",
                "tests" to listOf(
                    "EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk",
                ),
            ),
            linkedMapOf(
                "area" to "security corpus",
                "status" to "required",
                "tests" to listOf("NodeSecurityRegressionInstrumentationTest"),
            ),
            linkedMapOf(
                "area" to "memory/resource stress subset",
                "status" to "required",
                "tests" to listOf(
                    "NodeBridgeResourceLeakStressInstrumentationTest",
                    "NodeEmbeddedMemoryLeakStressInstrumentationTest",
                ),
            ),
            linkedMapOf(
                "area" to "worker threads",
                "status" to if (workerExperimental) "experimental-enabled" else "design-gate-only",
                "tests" to if (workerExperimental) {
                    listOf(
                        "NodeWorkerThreadsDesignGateInstrumentationTest",
                        "NodeWorkerThreadsExperimentalInstrumentationTest",
                        "NodeWorkerThreadsResourceStressInstrumentationTest",
                    )
                } else {
                    listOf("NodeWorkerThreadsDesignGateInstrumentationTest")
                },
            ),
        )

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-profile-v1-1-alpha-summary-v1",
            "generatedAt" to now,
            "git" to linkedMapOf(
                "commit" to gitCommit,
                "dirty" to gitStatus.isNotBlank(),
                "statusShort" to gitStatus.lineSequence().take(80).toList(),
            ),
            "gate" to linkedMapOf(
                "task" to "verifyNodeV11AlphaGate",
                "deviceProfile" to (deviceProfile ?: "unset"),
                "requiredDeviceProfile" to nodeV11AlphaDeviceProfile,
                "summaryOnly" to !gateTaskRequested,
                "requestedTasks" to requestedTasks,
                "stressArguments" to alphaStressArguments,
            ),
            "androidTest" to linkedMapOf(
                "latestXml" to latestAndroidXml?.let { nodeV11AlphaReportReference(it, rootDir) },
                "tests" to nodeV11AlphaXmlAttribute(latestAndroidXmlText, "tests")?.toIntOrNull(),
                "failures" to nodeV11AlphaXmlAttribute(latestAndroidXmlText, "failures")?.toIntOrNull(),
                "errors" to nodeV11AlphaXmlAttribute(latestAndroidXmlText, "errors")?.toIntOrNull(),
                "skipped" to nodeV11AlphaXmlAttribute(latestAndroidXmlText, "skipped")?.toIntOrNull(),
                "device" to nodeV11AlphaXmlAttribute(latestAndroidXmlText, "hostname"),
            ),
            "coverage" to requiredCoverage,
            "reports" to linkedMapOf(
                "compatibilityProfile" to nodeV11AlphaReportReference(
                    appReportsRoot.resolve("nodejs/compatibility-profile.json"),
                    rootDir,
                ),
                "doctor" to nodeV11AlphaReportReference(
                    appReportsRoot.resolve("nodejs/doctor/node-doctor-report.json"),
                    rootDir,
                ),
                "desktopDifferential" to nodeV11AlphaReportReference(
                    rootReportsRoot.resolve("nodejs/differential/desktop-node-differential.json"),
                    rootDir,
                ),
                "npmCompat" to nodeV11AlphaReportReference(
                    rootReportsRoot.resolve("nodejs/npm-compat-v1.1.md"),
                    rootDir,
                ),
                "v11Differential" to nodeV11AlphaReportReference(
                    rootReportsRoot.resolve("nodejs/v1.1-differential.md"),
                    rootDir,
                ),
                "packagedSmokeAggregate" to nodeV11AlphaReportReference(packagedSmokeFile, rootDir),
                "fuzzerSeeds" to linkedMapOf(
                    "dir" to fuzzerSeedDir.nodeV11AlphaPathRelativeTo(rootDir),
                    "count" to fuzzerSeedFiles.size,
                    "seeds" to fuzzerSeedFiles.take(100).map { nodeV11AlphaReportReference(it, rootDir) },
                ),
            ),
            "packagedSmoke" to linkedMapOf(
                "aggregateReady" to packagedSmokeSummary["embedded.node.packaged.smoke.aggregate.ready"],
                "pullReady" to packagedSmokeSummary["embedded.node.packaged.smoke.pull.ready"],
                "successOutputReady" to packagedSmokeSummary["embedded.node.packaged.smoke.success.output.ready"],
                "diagnosticsOutputReady" to packagedSmokeSummary["embedded.node.packaged.smoke.diagnostics.output.ready"],
                "templateReady" to packagedSmokeSummary["embedded.node.packaged.smoke.template.ready"],
                "moduleSourcesReady" to packagedSmokeSummary["embedded.node.packaged.smoke.module_sources.ready"],
            ),
            "notes" to listOf(
                "This is a functional v1.1 milestone gate, not a release candidate or tag gate.",
                "Run with ANDROID_SERIAL targeting the Sony unattended device and -P$nodeV11AlphaDeviceProfileProperty=$nodeV11AlphaDeviceProfile.",
                "Worker runtime stress is included only when autojs.nodejs.worker_threads.experimental=true.",
            ),
        )

        val output = outputProvider.get().asFile
        output.parentFile.mkdirs()
        output.writeText(nodeV11AlphaJson(report) + "\n")
        logger.lifecycle("Node Profile v1.1 alpha summary written to ${output.absolutePath}")
    }
}

tasks.register("verifyNodeV11AlphaGate") {
    group = "verification"
    description = "Runs the Node Profile v1.1 alpha functional milestone gate; requires the v1_1_alpha device profile."

    val configuredProfile = providers.gradleProperty(nodeV11AlphaDeviceProfileProperty).orNull
        ?.trim()
        ?.lowercase()
    if (configuredProfile == nodeV11AlphaDeviceProfile) {
        dependsOn(
            "verifyNodePrGate",
            "verifyEmbeddedNodeDebug",
            "generateDesktopNodeDifferentialReport",
            "connectedAppDebugAndroidTest",
        )
    }
    finalizedBy(generateNodeV11AlphaSummary)

    doFirst {
        if (configuredProfile != nodeV11AlphaDeviceProfile) {
            throw GradleException(
                "verifyNodeV11AlphaGate requires -P$nodeV11AlphaDeviceProfileProperty=$nodeV11AlphaDeviceProfile. " +
                        "Current profile: ${configuredProfile ?: "unset"}."
            )
        }
    }
}
