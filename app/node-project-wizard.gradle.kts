import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.PathSensitivity
import java.io.File

private val nodeProjectWizardTemplates = listOf(
    "commonjs-app",
    "esm-app",
    "automation-script",
    "screenshot-ocr-script",
    "network-fetch-script",
)

private fun nodeProjectWizardReadJsonObject(text: String): Map<*, *> =
    JsonSlurper().parseText(text) as? Map<*, *>
        ?: throw GradleException("Expected JSON object from Node project wizard output.")

private fun nodeProjectWizardReadJsonFile(file: File): Map<*, *> =
    JsonSlurper().parse(file) as? Map<*, *>
        ?: throw GradleException("Expected JSON object in ${file.absolutePath}")

private fun nodeProjectWizardRun(
    tool: File,
    args: List<String>,
    expectSuccess: Boolean = true,
): String {
    val process = ProcessBuilder(listOf("node", tool.absolutePath) + args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val text = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    val exitCode = process.waitFor()
    if (expectSuccess && exitCode != 0) {
        throw GradleException("Node project wizard command failed: ${args.joinToString(" ")}\n$text")
    }
    if (!expectSuccess && exitCode == 0) {
        throw GradleException("Node project wizard command unexpectedly succeeded: ${args.joinToString(" ")}\n$text")
    }
    return text
}

private fun nodeProjectWizardJson(
    tool: File,
    args: List<String>,
    expectSuccess: Boolean = true,
): Map<*, *> =
    nodeProjectWizardReadJsonObject(nodeProjectWizardRun(tool, args + "--json", expectSuccess))

private fun Map<*, *>.nodeProjectWizardIssueCodes(): Set<String> =
    ((this["issues"] as? List<*>).orEmpty())
        .mapNotNull { issue -> (issue as? Map<*, *>)?.get("code")?.toString() }
        .toSet()

private fun assertNodeProjectWizardIssueCodes(
    report: Map<*, *>,
    vararg expectedCodes: String,
) {
    val actual = report.nodeProjectWizardIssueCodes()
    val missing = expectedCodes.filterNot(actual::contains)
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Node project wizard validation report missing issue code(s): ${missing.joinToString()}. " +
                    "Actual=${actual.joinToString()} report=${JsonOutput.toJson(report)}"
        )
    }
}

val verifyNodeProjectWizard = tasks.register("verifyNodeProjectWizard") {
    group = "verification"
    description = "Verifies AutoJs6 Node project wizard templates and validator diagnostics."

    val toolFile = rootProject.layout.projectDirectory.file("tools/nodejs/project/autojs6-node-project.js")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/project-wizard.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/project-wizard.md")

    inputs.file(toolFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("templates", nodeProjectWizardTemplates)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val tool = toolFile.asFile
        if (!tool.isFile) {
            throw GradleException("Missing Node project wizard tool: ${tool.relativeTo(rootProject.projectDir)}")
        }
        val workRoot = layout.buildDirectory.dir("node-project-wizard").get().asFile
        workRoot.deleteRecursively()
        check(workRoot.mkdirs()) { "Failed to create ${workRoot.absolutePath}" }

        val rows = mutableListOf<Map<String, Any?>>()
        nodeProjectWizardTemplates.forEach { template ->
            val projectDir = workRoot.resolve("valid/$template")
            val createReport = nodeProjectWizardJson(
                tool,
                listOf(
                    "create",
                    "--template", template,
                    "--out", projectDir.absolutePath,
                    "--name", "fixture-$template",
                    "--force",
                ),
            )
            val validateReport = nodeProjectWizardJson(
                tool,
                listOf("validate", projectDir.absolutePath),
            )
            if (validateReport["ok"] != true) {
                throw GradleException("Generated Node project template '$template' failed validation: $validateReport")
            }
            val compatibility = validateReport["compatibility"] as? Map<*, *>
                ?: throw GradleException("Generated Node project template '$template' did not include compatibility analysis.")
            val compatibilityStatus = compatibility["status"]?.toString().orEmpty()
            if (compatibilityStatus in setOf("unsafe", "unsupported")) {
                throw GradleException("Generated Node project template '$template' has blocking compatibility status: $compatibility")
            }
            rows += linkedMapOf(
                "template" to template,
                "project" to projectDir.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
                "files" to createReport["files"],
                "issueCount" to validateReport["issueCount"],
                "compatibilityStatus" to compatibilityStatus,
            )
        }

        val missingEntry = workRoot.resolve("invalid/missing-entry")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", missingEntry.absolutePath,
                "--name", "missing-entry",
                "--force",
            ),
        )
        missingEntry.resolve("main.cjs").delete()
        val missingEntryReport = nodeProjectWizardJson(
            tool,
            listOf("validate", missingEntry.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(missingEntryReport, "ENTRY_FILE_MISSING")

        val unknownPermission = workRoot.resolve("invalid/unknown-permission")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", unknownPermission.absolutePath,
                "--name", "unknown-permission",
                "--force",
            ),
        )
        val projectJsonFile = unknownPermission.resolve("project.json")
        val projectJson = nodeProjectWizardReadJsonFile(projectJsonFile).toMutableMap()
        val node = (projectJson["node"] as? Map<*, *>).orEmpty().toMutableMap()
        node["permissions"] = listOf("android.context")
        projectJson["node"] = node
        projectJsonFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(projectJson)) + "\n")
        val unknownPermissionReport = nodeProjectWizardJson(
            tool,
            listOf("validate", unknownPermission.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(unknownPermissionReport, "UNKNOWN_PERMISSION")

        val invalidPackageName = workRoot.resolve("invalid/invalid-package-name")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", invalidPackageName.absolutePath,
                "--name", "invalid-package-name",
                "--force",
            ),
        )
        val packageJsonFile = invalidPackageName.resolve("package.json")
        val packageJson = nodeProjectWizardReadJsonFile(packageJsonFile).toMutableMap()
        packageJson["name"] = "Bad Package Name"
        packageJsonFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(packageJson)) + "\n")
        val invalidPackageNameReport = nodeProjectWizardJson(
            tool,
            listOf("validate", invalidPackageName.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(invalidPackageNameReport, "PACKAGE_NAME_INVALID")

        val unsafeDependency = workRoot.resolve("invalid/unsafe-dependency")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", unsafeDependency.absolutePath,
                "--name", "unsafe-dependency",
                "--force",
            ),
        )
        unsafeDependency.resolve("node_modules/native-dep").mkdirs()
        unsafeDependency.resolve("node_modules/native-dep/package.json").writeText(
            """
            {
              "name": "native-dep",
              "version": "1.0.0",
              "scripts": {
                "install": "node-gyp rebuild"
              }
            }
            """.trimIndent() + "\n",
        )
        unsafeDependency.resolve("node_modules/native-dep/addon.node").writeText("native-placeholder\n")
        val unsafeDependencyReport = nodeProjectWizardJson(
            tool,
            listOf("validate", unsafeDependency.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(
            unsafeDependencyReport,
            "DEPENDENCY_LIFECYCLE_SCRIPT_UNSUPPORTED",
            "NATIVE_ADDON_DISABLED",
        )

        val unsupportedSource = workRoot.resolve("invalid/unsupported-source")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", unsupportedSource.absolutePath,
                "--name", "unsupported-source",
                "--force",
            ),
        )
        unsupportedSource.resolve("main.cjs").appendText(
            """
            const net = require("net");
            const childProcess = require("child_process");
            void net;
            void childProcess;
            """.trimIndent() + "\n",
        )
        val unsupportedSourceReport = nodeProjectWizardJson(
            tool,
            listOf("validate", unsupportedSource.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(unsupportedSourceReport, "CAPABILITY_DECLARATION_MISSING")
        val unsupportedCompatibility = unsupportedSourceReport["compatibility"] as? Map<*, *>
            ?: throw GradleException("Unsupported source fixture did not include compatibility analysis.")
        if (unsupportedCompatibility["status"] != "unsupported") {
            throw GradleException("Unsupported source fixture should report unsupported compatibility: $unsupportedCompatibility")
        }

        val unsafeFsSource = workRoot.resolve("invalid/unsafe-fs-source")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", unsafeFsSource.absolutePath,
                "--name", "unsafe-fs-source",
                "--force",
            ),
        )
        unsafeFsSource.resolve("main.cjs").appendText(
            """
            const fs = require("fs");
            fs.readFileSync("../outside.txt", "utf8");
            """.trimIndent() + "\n",
        )
        val unsafeFsSourceReport = nodeProjectWizardJson(
            tool,
            listOf("validate", unsafeFsSource.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(unsafeFsSourceReport, "FS_OUTSIDE_SCOPE")
        val unsafeFsCompatibility = unsafeFsSourceReport["compatibility"] as? Map<*, *>
            ?: throw GradleException("Unsafe fs fixture did not include compatibility analysis.")
        if (unsafeFsCompatibility["status"] != "unsafe") {
            throw GradleException("Unsafe fs fixture should report unsafe compatibility: $unsafeFsCompatibility")
        }

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-project-wizard-report-v1",
            "tool" to tool.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "templateCount" to rows.size,
            "templates" to rows,
            "invalidFixtures" to listOf(
                linkedMapOf("name" to "missing-entry", "issueCodes" to missingEntryReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unknown-permission", "issueCodes" to unknownPermissionReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "invalid-package-name", "issueCodes" to invalidPackageNameReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsafe-dependency", "issueCodes" to unsafeDependencyReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsupported-source", "issueCodes" to unsupportedSourceReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsafe-fs-source", "issueCodes" to unsafeFsSourceReport.nodeProjectWizardIssueCodes().sorted()),
            ),
        )
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# AutoJs6 Node Project Wizard Validation")
                    appendLine()
                    appendLine("| Template | Project | Issue count |")
                    appendLine("| --- | --- | ---: |")
                    rows.forEach { row ->
                        appendLine("| ${row["template"]} | ${row["project"]} | ${row["issueCount"]} (${row["compatibilityStatus"]}) |")
                    }
                    appendLine()
                    appendLine("Invalid fixture issue codes:")
                    appendLine()
                    (report["invalidFixtures"] as List<*>).forEach { item ->
                        val fixture = item as Map<*, *>
                        appendLine("- ${fixture["name"]}: ${(fixture["issueCodes"] as List<*>).joinToString(", ")}")
                    }
                },
            )
        }
        logger.lifecycle("Verified ${rows.size} AutoJs6 Node project wizard templates.")
        logger.lifecycle("Node project wizard report written to ${jsonReport.get().asFile.absolutePath}")
    }
}

tasks.matching {
    it.name in setOf(
        "verifyNodePrGate",
        "verifyNodeNightlyGate",
        "verifyNodeReleaseGate",
        "verifyEmbeddedNodeReleaseCandidate",
    )
}.configureEach {
    dependsOn(verifyNodeProjectWizard)
}
