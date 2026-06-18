import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.time.ZonedDateTime

private val nodeV11RcDeviceProfileProperty = "autojs.nodejs.test.deviceProfile"
private val nodeV11RcRequiredBetaProfile = "v1_1_beta"
private val nodeV11RcPlanPath = "docs/nodejs/releases/NODE_PROFILE_V1_1_RC_PLAN.md"
private val nodeV11RcReportReadmePath = "docs/nodejs/reports/v1.1-rc/README.md"
private val nodeV11RcBetaSummaryPath = "build/reports/nodejs/v1.1-beta-summary.json"
private val nodeV11RcBetaBlockersPath = "app/build/reports/nodejs/v1.1-beta-blockers.json"

private fun nodeV11RcJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun File.nodeV11RcPathRelativeTo(root: File): String =
    runCatching {
        root.canonicalFile.toPath()
            .relativize(canonicalFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')
    }.getOrElse { invariantSeparatorsPath }

private fun nodeV11RcReportReference(file: File, rootDir: File): Map<String, Any?> =
    linkedMapOf(
        "path" to file.nodeV11RcPathRelativeTo(rootDir),
        "absolutePath" to file.absolutePath,
        "available" to file.isFile,
        "bytes" to file.takeIf(File::isFile)?.length(),
    )

private fun nodeV11RcReadJson(file: File): Any? =
    JsonSlurper().parse(file)

private fun nodeV11RcValue(root: Any?, vararg keys: String): Any? {
    var current = root
    for (key in keys) {
        current = (current as? Map<*, *>)?.get(key) ?: return null
    }
    return current
}

private fun nodeV11RcBool(root: Any?, vararg keys: String): Boolean? =
    when (val value = nodeV11RcValue(root, *keys)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> null
    }

private fun nodeV11RcString(root: Any?, vararg keys: String): String? =
    nodeV11RcValue(root, *keys)?.toString()

private fun nodeV11RcInt(root: Any?, vararg keys: String): Int? =
    when (val value = nodeV11RcValue(root, *keys)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

private fun nodeV11RcList(root: Any?, vararg keys: String): List<*> =
    nodeV11RcValue(root, *keys) as? List<*> ?: emptyList<Any?>()

tasks.register("verifyNodeV11RcReadiness") {
    group = "verification"
    description = "Verifies Node Profile v1.1 beta evidence is ready to enter release-candidate planning."

    val configuredProfile = providers.gradleProperty(nodeV11RcDeviceProfileProperty).orNull
        ?.trim()
        ?.lowercase()
    if (configuredProfile == nodeV11RcRequiredBetaProfile) {
        dependsOn(
            "verifyNodeV11BetaBlockerRegister",
            "generateNodeV11BetaSummary",
        )
    }

    val rcPlanFile = rootProject.layout.projectDirectory.file(nodeV11RcPlanPath)
    val rcReportReadmeFile = rootProject.layout.projectDirectory.file(nodeV11RcReportReadmePath)
    val betaSummaryFile = rootProject.layout.projectDirectory.file(nodeV11RcBetaSummaryPath)
    val betaBlockersFile = rootProject.layout.projectDirectory.file(nodeV11RcBetaBlockersPath)
    val outputProvider = layout.buildDirectory.file("reports/nodejs/v1.1-rc-readiness.json")
    inputs.file(rcPlanFile)
    inputs.file(rcReportReadmeFile)
    inputs.file(betaSummaryFile)
    inputs.file(betaBlockersFile)
    outputs.file(outputProvider)
    outputs.upToDateWhen { false }

    doFirst {
        if (configuredProfile != nodeV11RcRequiredBetaProfile) {
            throw GradleException(
                "verifyNodeV11RcReadiness requires -P$nodeV11RcDeviceProfileProperty=$nodeV11RcRequiredBetaProfile. " +
                        "Current profile: ${configuredProfile ?: "unset"}."
            )
        }
    }

    doLast {
        val rootDir = rootProject.projectDir
        val rcPlan = rcPlanFile.asFile
        val rcReportReadme = rcReportReadmeFile.asFile
        val betaSummary = betaSummaryFile.asFile
        val betaBlockers = betaBlockersFile.asFile
        val summaryJson = betaSummary.takeIf(File::isFile)?.let(::nodeV11RcReadJson)
        val blockersJson = betaBlockers.takeIf(File::isFile)?.let(::nodeV11RcReadJson)
        val checks = mutableListOf<Map<String, Any?>>()

        fun check(id: String, passed: Boolean, detail: String, evidence: Any? = null) {
            checks += linkedMapOf(
                "id" to id,
                "passed" to passed,
                "detail" to detail,
                "evidence" to evidence,
            )
        }

        check("rc-plan-doc", rcPlan.isFile, "v1.1 RC plan document exists", nodeV11RcReportReference(rcPlan, rootDir))
        check(
            "rc-report-readme",
            rcReportReadme.isFile,
            "v1.1 RC report README exists",
            nodeV11RcReportReference(rcReportReadme, rootDir),
        )
        check(
            "beta-summary-present",
            betaSummary.isFile,
            "v1.1 beta summary JSON exists",
            nodeV11RcReportReference(betaSummary, rootDir),
        )
        check(
            "beta-blocker-report-present",
            betaBlockers.isFile,
            "v1.1 beta blocker report JSON exists",
            nodeV11RcReportReference(betaBlockers, rootDir),
        )
        check(
            "beta-summary-schema",
            nodeV11RcString(summaryJson, "schema") == "autojs6-node-profile-v1-1-beta-summary-v1",
            "beta summary uses the expected schema",
            nodeV11RcString(summaryJson, "schema"),
        )
        check(
            "beta-summary-clean-git",
            nodeV11RcBool(summaryJson, "git", "dirty") == false,
            "beta summary records a clean git state",
            nodeV11RcValue(summaryJson, "git"),
        )
        check(
            "beta-summary-device-profile",
            nodeV11RcString(summaryJson, "gate", "deviceProfile") == nodeV11RcRequiredBetaProfile &&
                    nodeV11RcString(summaryJson, "gate", "requiredDeviceProfile") == nodeV11RcRequiredBetaProfile,
            "beta summary was generated with the v1_1_beta device profile",
            nodeV11RcValue(summaryJson, "gate"),
        )
        val tests = nodeV11RcInt(summaryJson, "androidTest", "tests")
        check(
            "beta-instrumentation-green",
            tests != null && tests > 0 &&
                    nodeV11RcInt(summaryJson, "androidTest", "failures") == 0 &&
                    nodeV11RcInt(summaryJson, "androidTest", "errors") == 0 &&
                    nodeV11RcInt(summaryJson, "androidTest", "skipped") == 0,
            "latest beta connected-test XML has tests and no failures/errors/skips",
            nodeV11RcValue(summaryJson, "androidTest"),
        )
        val requiredReports = listOf(
            "compatibilityProfile",
            "doctor",
            "desktopDifferential",
            "npmCompat",
            "v11Differential",
            "packagedSmokeAggregate",
            "errorCatalog",
            "betaBlockers",
        )
        val missingReports = requiredReports.filter { id ->
            nodeV11RcBool(summaryJson, "reports", id, "available") != true
        }
        check(
            "beta-required-reports",
            missingReports.isEmpty(),
            "required beta reports are available",
            linkedMapOf("missing" to missingReports),
        )
        val packagedReady = listOf(
            "aggregateReady",
            "pullReady",
            "successOutputReady",
            "diagnosticsOutputReady",
            "templateReady",
            "moduleSourcesReady",
        )
        val missingPackagedReady = packagedReady.filter { key ->
            nodeV11RcBool(summaryJson, "packagedSmoke", key) != true
        }
        check(
            "beta-packaged-smoke-ready",
            missingPackagedReady.isEmpty(),
            "packaged smoke aggregate is ready for RC planning",
            linkedMapOf("missing" to missingPackagedReady),
        )
        check(
            "beta-blocker-report-ready",
            nodeV11RcBool(blockersJson, "ready") == true,
            "beta blocker report is structurally ready",
            nodeV11RcValue(blockersJson, "invalid"),
        )
        val unresolvedBlockers = nodeV11RcList(blockersJson, "records").mapNotNull { record ->
            val map = record as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"]?.toString() ?: return@mapNotNull null
            val status = map["status"]?.toString()
            if (status in setOf("deferred", "closed")) {
                null
            } else {
                linkedMapOf("id" to id, "status" to status)
            }
        }
        check(
            "beta-blockers-rc-decisioned",
            unresolvedBlockers.isEmpty(),
            "all beta blockers are deferred or closed before RC planning",
            unresolvedBlockers,
        )

        val invalid = checks.filter { it["passed"] != true }
        val report = linkedMapOf(
            "schema" to "autojs6-node-profile-v1-1-rc-readiness-v1",
            "generatedAt" to ZonedDateTime.now().toString(),
            "ready" to invalid.isEmpty(),
            "requiredDeviceProfile" to nodeV11RcRequiredBetaProfile,
            "inputs" to linkedMapOf(
                "rcPlan" to nodeV11RcReportReference(rcPlan, rootDir),
                "rcReportReadme" to nodeV11RcReportReference(rcReportReadme, rootDir),
                "betaSummary" to nodeV11RcReportReference(betaSummary, rootDir),
                "betaBlockers" to nodeV11RcReportReference(betaBlockers, rootDir),
            ),
            "checks" to checks,
            "invalid" to invalid,
            "notes" to listOf(
                "This task verifies readiness to enter v1.1 RC planning; it is not a release tag or publication approval.",
                "Run the full v1.1 beta gate on Sony before relying on this report.",
                "Run the production release gate separately before any RC tag or publication decision.",
            ),
        )
        val output = outputProvider.get().asFile
        output.parentFile.mkdirs()
        output.writeText(nodeV11RcJson(report) + "\n")
        if (invalid.isNotEmpty()) {
            throw GradleException(
                "Node Profile v1.1 RC readiness failed: " +
                        invalid.joinToString { it["id"].toString() } +
                        ". See ${output.absolutePath}"
            )
        }
        logger.lifecycle("Node Profile v1.1 RC readiness report written to ${output.absolutePath}")
    }
}
