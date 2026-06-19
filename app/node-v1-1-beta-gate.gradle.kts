import groovy.json.JsonOutput
import java.io.File
import java.time.ZonedDateTime

private val nodeV11BetaDeviceProfileProperty = "autojs.nodejs.test.deviceProfile"
private val nodeV11BetaDeviceProfile = "v1_1_beta"
private val nodeV11BetaBlockerRegisterPath = "docs/nodejs/releases/NODE_PROFILE_V1_1_BETA_BLOCKERS.md"
private val nodeV11BetaRequiredBlockers = listOf(
    "V11-BETA-PACKAGED-ESM-TEMPLATE",
    "V11-BETA-NETWORK-LIVE-PROVIDER",
    "V11-BETA-WEBSOCKET-LIVE-PROVIDER",
    "V11-BETA-UI-LIVE-PROVIDER",
    "V11-BETA-FLOATY-OVERLAY",
    "V11-BETA-LONG-RUNNING",
    "V11-BETA-WORKER-THREADS",
    "V11-BETA-JAVA-INTEROP",
    "V11-BETA-PLUGIN-RUNTIME",
    "V11-BETA-WORK-MANAGER-EXECUTION",
)

private fun nodeV11BetaJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun File.nodeV11BetaPathRelativeTo(root: File): String =
    runCatching {
        root.canonicalFile.toPath()
            .relativize(canonicalFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')
    }.getOrElse { invariantSeparatorsPath }

private fun nodeV11BetaReportReference(file: File, rootDir: File): Map<String, Any?> =
    linkedMapOf(
        "path" to file.nodeV11BetaPathRelativeTo(rootDir),
        "absolutePath" to file.absolutePath,
        "available" to file.isFile,
        "bytes" to file.takeIf(File::isFile)?.length(),
    )

private fun nodeV11BetaLatestFile(root: File, predicate: (File) -> Boolean): File? =
    root.takeIf(File::isDirectory)
        ?.walkTopDown()
        ?.filter(File::isFile)
        ?.filter(predicate)
        ?.maxByOrNull(File::lastModified)

private fun nodeV11BetaXmlAttribute(xmlText: String?, name: String): String? =
    xmlText
        ?.let { Regex("""\b${Regex.escape(name)}="([^"]*)"""").find(it) }
        ?.groupValues
        ?.getOrNull(1)

private fun nodeV11BetaCommandText(rootDir: File, vararg command: String): String? =
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

private fun nodeV11BetaProperties(text: String?, prefix: String): Map<String, String> =
    text
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter { it.startsWith(prefix) && it.contains('=') }
        ?.associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        .orEmpty()

private fun nodeV11BetaBooleanProperty(name: String, defaultValue: Boolean = false): Boolean =
    providers.gradleProperty(name).orNull
        ?.trim()
        ?.lowercase()
        ?.let { it == "true" || it == "yes" || it == "1" }
        ?: defaultValue

private fun nodeV11BetaBlockSection(text: String, id: String): String? =
    Regex(
        pattern = """^##\s+${Regex.escape(id)}\b.*?(?=^##\s+|\z)""",
        options = setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    ).find(text)?.value

val verifyNodeV11BetaBlockerRegister = tasks.register("verifyNodeV11BetaBlockerRegister") {
    group = "verification"
    description = "Verifies the Node Profile v1.1 beta blocker register contains the required RC decision entries."

    val blockerFile = rootProject.layout.projectDirectory.file(nodeV11BetaBlockerRegisterPath)
    val reportFile = layout.buildDirectory.file("reports/nodejs/v1.1-beta-blockers.json")
    inputs.file(blockerFile)
    outputs.file(reportFile)

    doLast {
        val file = blockerFile.asFile
        if (!file.isFile) {
            throw GradleException("Missing Node Profile v1.1 beta blocker register: ${file.absolutePath}")
        }
        val text = file.readText()
        val records = nodeV11BetaRequiredBlockers.map { id ->
            val section = nodeV11BetaBlockSection(text, id)
            val status = section
                ?.lineSequence()
                ?.firstOrNull { it.startsWith("Status:") }
                ?.substringAfter(':')
                ?.trim()
            val hasRcDecision = section?.contains(Regex("""(?m)^RC decision:\s+\S""")) == true
            val hasUnblockCondition = section?.contains(Regex("""(?m)^Unblock condition:\s*$""")) == true
            val hasValidation = section?.contains(Regex("""(?m)^Validation:\s*$""")) == true
            linkedMapOf(
                "id" to id,
                "present" to (section != null),
                "status" to status,
                "hasRcDecision" to hasRcDecision,
                "hasUnblockCondition" to hasUnblockCondition,
                "hasValidation" to hasValidation,
            )
        }
        val invalid = records.filter { record ->
            record["present"] != true ||
                    record["status"] !in setOf("open", "deferred", "closed") ||
                    record["hasRcDecision"] != true ||
                    record["hasUnblockCondition"] != true ||
                    record["hasValidation"] != true
        }
        val report = linkedMapOf(
            "schema" to "autojs6-node-profile-v1-1-beta-blockers-v1",
            "register" to nodeV11BetaBlockerRegisterPath,
            "requiredBlockers" to nodeV11BetaRequiredBlockers,
            "records" to records,
            "ready" to invalid.isEmpty(),
            "invalid" to invalid,
        )
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(nodeV11BetaJson(report) + "\n")
        }
        if (invalid.isNotEmpty()) {
            throw GradleException(
                "Node Profile v1.1 beta blocker register is incomplete: " +
                        invalid.joinToString { it["id"].toString() } +
                        ". See ${reportFile.get().asFile.absolutePath}"
            )
        }
        logger.lifecycle("Node Profile v1.1 beta blocker register covers ${records.size} required entries.")
        logger.lifecycle("Node Profile v1.1 beta blocker report written to ${reportFile.get().asFile.absolutePath}")
    }
}

val generateNodeV11BetaSummary = tasks.register("generateNodeV11BetaSummary") {
    group = "verification"
    description = "Writes the Node Profile v1.1 beta stabilization summary JSON from available gate reports."

    dependsOn(
        "embeddedNodeDoctorReport",
        "generateNodeCompatibilityProfile",
        "generateDesktopNodeDifferentialReport",
        "generateNodeModernCoreDifferentialReport",
        "verifyNodeErrorCatalog",
        "verifyNodeProjectWizard",
        "verifyAutoJs6NodeTypeDeclarations",
        verifyNodeV11BetaBlockerRegister,
    )
    mustRunAfter("connectedAppDebugAndroidTest", "embeddedNodePackagedSmokeHostVerify", "collectNodeFuzzerSeeds")

    val outputProvider = rootProject.layout.buildDirectory.file("reports/nodejs/v1.1-beta-summary.json")
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
        val latestAndroidXml = nodeV11BetaLatestFile(androidResultsRoot) {
            it.name.startsWith("TEST-") && it.extension == "xml"
        }
        val latestAndroidXmlText = latestAndroidXml?.readText()
        val packagedSmokeFile = appReportsRoot.resolve("embedded-node/packaged-smoke-aggregate.txt")
        val packagedSmokeSummary = nodeV11BetaProperties(
            packagedSmokeFile.takeIf(File::isFile)?.readText(),
            "embedded.node.packaged.smoke.",
        )
        val fuzzerSeedDir = rootReportsRoot.resolve("nodejs/fuzz-seeds")
        val fuzzerSeedFiles = fuzzerSeedDir.takeIf(File::isDirectory)
            ?.walkTopDown()
            ?.filter(File::isFile)
            ?.toList()
            .orEmpty()
        val gitCommit = nodeV11BetaCommandText(rootDir, "git", "rev-parse", "HEAD") ?: "unknown"
        val gitStatus = nodeV11BetaCommandText(rootDir, "git", "status", "--short").orEmpty()
        val deviceProfile = providers.gradleProperty(nodeV11BetaDeviceProfileProperty).orNull
        val requestedTasks = gradle.startParameter.taskNames
        val gateTaskRequested = requestedTasks.any { taskName ->
            taskName == "verifyNodeV11BetaGate" || taskName.endsWith(":verifyNodeV11BetaGate")
        }
        val workerExperimental = nodeV11BetaBooleanProperty("autojs.nodejs.worker_threads.experimental")
        val templateOverride =
            nodeV11BetaBooleanProperty("autojs.embeddedNode.packagedSmoke.templateOverride") ||
                    nodeV11BetaBooleanProperty("autojs.embeddedNode.packagedSmokeTemplateOverride")
        fun betaStressArgument(name: String, defaultValue: String): String =
            providers.gradleProperty(name).orElse(defaultValue).get()
        val betaStressArguments = linkedMapOf(
            "autojs.nodejs.leak.iterations" to betaStressArgument("autojs.nodejs.leak.iterations", "20"),
            "autojs.nodejs.leak.imageIterations" to betaStressArgument("autojs.nodejs.leak.imageIterations", "8"),
            "autojs.nodejs.leak.faultIterations" to betaStressArgument("autojs.nodejs.leak.faultIterations", "5"),
            "autojs.nodejs.bridgeStress.iterations" to betaStressArgument("autojs.nodejs.bridgeStress.iterations", "12"),
        )

        val requiredCoverage = listOf(
            linkedMapOf(
                "area" to "alpha capability coverage",
                "status" to "required",
                "tests" to listOf(
                    "NodeEsmExperimentalInstrumentationTest",
                    "NodeModernCoreDifferentialInstrumentationTest",
                    "generateNodeModernCoreDifferentialReport",
                    "NodeRealNpmCompatInstrumentationTest",
                    "NodeFetchBridgeInstrumentationTest",
                    "NodeWebSocketBridgeInstrumentationTest",
                    "NodeOcrBridgeInstrumentationTest",
                    "NodeImageBridgeExpansionInstrumentationTest",
                    "NodeStorageBridgeInstrumentationTest",
                    "NodeNotificationsBridgeInstrumentationTest",
                    "NodeSensorsBridgeInstrumentationTest",
                    "NodeUiBridgeInstrumentationTest",
                    "NodeWorkManagerBridgeInstrumentationTest",
                    "NodeShellBridgeInstrumentationTest",
                    "NodeAccessibilitySelectorBridgeInstrumentationTest",
                    "EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk",
                ),
            ),
            linkedMapOf(
                "area" to "beta baseline regressions",
                "status" to "required",
                "tests" to listOf(
                    "NodeEmbeddedLifecycleStressInstrumentationTest",
                    "NodeFsPromisesInstrumentationTest",
                    "NodeNativeAddonPolicyInstrumentationTest",
                    "NodeResolverFuzzerInstrumentationTest",
                    "NodeScopedFsFuzzerInstrumentationTest",
                ),
            ),
            linkedMapOf(
                "area" to "host/package tooling",
                "status" to "required",
                "tests" to listOf(
                    "verifyEmbeddedNodePackagedDebug",
                    "verifyNodeErrorCatalog",
                    "verifyNodeProjectWizard",
                    "verifyAutoJs6NodeTypeDeclarations",
                ),
            ),
            linkedMapOf(
                "area" to "packaged ESM template override",
                "status" to if (templateOverride) "explicit-subgate-enabled" else "explicit-subgate-required",
                "tests" to listOf(
                    "EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk",
                    "EmbeddedNodePackagedApkSmokeTest#installAndRunDynamicImportLocalEmbeddedNodePackagedApk",
                ),
            ),
            linkedMapOf(
                "area" to "worker threads",
                "status" to if (workerExperimental) "experimental-enabled" else "blocked-default",
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
            "schema" to "autojs6-node-profile-v1-1-beta-summary-v1",
            "generatedAt" to now,
            "git" to linkedMapOf(
                "commit" to gitCommit,
                "dirty" to gitStatus.isNotBlank(),
                "statusShort" to gitStatus.lineSequence().take(80).toList(),
            ),
            "gate" to linkedMapOf(
                "task" to "verifyNodeV11BetaGate",
                "deviceProfile" to (deviceProfile ?: "unset"),
                "requiredDeviceProfile" to nodeV11BetaDeviceProfile,
                "summaryOnly" to !gateTaskRequested,
                "requestedTasks" to requestedTasks,
                "stressArguments" to betaStressArguments,
                "packagedTemplateOverride" to templateOverride,
            ),
            "androidTest" to linkedMapOf(
                "latestXml" to latestAndroidXml?.let { nodeV11BetaReportReference(it, rootDir) },
                "tests" to nodeV11BetaXmlAttribute(latestAndroidXmlText, "tests")?.toIntOrNull(),
                "failures" to nodeV11BetaXmlAttribute(latestAndroidXmlText, "failures")?.toIntOrNull(),
                "errors" to nodeV11BetaXmlAttribute(latestAndroidXmlText, "errors")?.toIntOrNull(),
                "skipped" to nodeV11BetaXmlAttribute(latestAndroidXmlText, "skipped")?.toIntOrNull(),
                "device" to nodeV11BetaXmlAttribute(latestAndroidXmlText, "hostname"),
            ),
            "coverage" to requiredCoverage,
            "reports" to linkedMapOf(
                "compatibilityProfile" to nodeV11BetaReportReference(
                    appReportsRoot.resolve("nodejs/compatibility-profile.json"),
                    rootDir,
                ),
                "doctor" to nodeV11BetaReportReference(
                    appReportsRoot.resolve("nodejs/doctor/node-doctor-report.json"),
                    rootDir,
                ),
                "desktopDifferential" to nodeV11BetaReportReference(
                    rootReportsRoot.resolve("nodejs/differential/desktop-node-differential.json"),
                    rootDir,
                ),
                "modernCoreDifferential" to nodeV11BetaReportReference(
                    rootReportsRoot.resolve("nodejs/differential/modern-core-differential.json"),
                    rootDir,
                ),
                "npmCompat" to nodeV11BetaReportReference(
                    rootReportsRoot.resolve("nodejs/npm-compat-v1.1.md"),
                    rootDir,
                ),
                "v11Differential" to nodeV11BetaReportReference(
                    rootReportsRoot.resolve("nodejs/v1.1-differential.md"),
                    rootDir,
                ),
                "packagedSmokeAggregate" to nodeV11BetaReportReference(packagedSmokeFile, rootDir),
                "errorCatalog" to nodeV11BetaReportReference(
                    appReportsRoot.resolve("nodejs/error-catalog.json"),
                    rootDir,
                ),
                "projectWizard" to nodeV11BetaReportReference(
                    appReportsRoot.resolve("nodejs/project-wizard/project-wizard-report.json"),
                    rootDir,
                ),
                "betaBlockers" to nodeV11BetaReportReference(
                    appReportsRoot.resolve("nodejs/v1.1-beta-blockers.json"),
                    rootDir,
                ),
                "fuzzerSeeds" to linkedMapOf(
                    "dir" to fuzzerSeedDir.nodeV11BetaPathRelativeTo(rootDir),
                    "count" to fuzzerSeedFiles.size,
                    "seeds" to fuzzerSeedFiles.take(100).map { nodeV11BetaReportReference(it, rootDir) },
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
                "This is a v1.1 beta stabilization gate, not a release candidate or tag gate.",
                "Run with ANDROID_SERIAL targeting the Sony unattended device and -P$nodeV11BetaDeviceProfileProperty=$nodeV11BetaDeviceProfile.",
                "The beta device profile keeps the default normal fuzzer budget; run extended fuzzer budget as a separate stress subgate.",
                "Packaged .mjs and dynamic import install-and-run smokes remain explicit template-override subgates until the refreshed inrt template is default.",
                "Worker runtime stress is included only when autojs.nodejs.worker_threads.experimental=true.",
            ),
        )

        val output = outputProvider.get().asFile
        output.parentFile.mkdirs()
        output.writeText(nodeV11BetaJson(report) + "\n")
        logger.lifecycle("Node Profile v1.1 beta summary written to ${output.absolutePath}")
    }
}

tasks.register("verifyNodeV11BetaGate") {
    group = "verification"
    description = "Runs the Node Profile v1.1 beta stabilization gate; requires the v1_1_beta device profile."

    val configuredProfile = providers.gradleProperty(nodeV11BetaDeviceProfileProperty).orNull
        ?.trim()
        ?.lowercase()
    if (configuredProfile == nodeV11BetaDeviceProfile) {
        dependsOn(
            "verifyNodePrGate",
            "verifyEmbeddedNodeDebug",
            "verifyEmbeddedNodePackagedDebug",
            "generateDesktopNodeDifferentialReport",
            "generateNodeModernCoreDifferentialReport",
            "verifyNodeErrorCatalog",
            "verifyNodeProjectWizard",
            "verifyAutoJs6NodeTypeDeclarations",
            verifyNodeV11BetaBlockerRegister,
            "connectedAppDebugAndroidTest",
        )
    }
    finalizedBy(generateNodeV11BetaSummary)

    doFirst {
        if (configuredProfile != nodeV11BetaDeviceProfile) {
            throw GradleException(
                "verifyNodeV11BetaGate requires -P$nodeV11BetaDeviceProfileProperty=$nodeV11BetaDeviceProfile. " +
                        "Current profile: ${configuredProfile ?: "unset"}."
            )
        }
    }
}
