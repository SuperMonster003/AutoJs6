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
    "typescript-cjs",
    "typescript-esm",
    "typescript-automation",
    "typescript-ocr",
    "typescript-long-running-task",
    "compiled-tsx",
)

private val nodeProjectWizardExpectedEntryFormats = mapOf(
    "commonjs-app" to "cjs",
    "esm-app" to "esm",
    "automation-script" to "cjs",
    "screenshot-ocr-script" to "cjs",
    "network-fetch-script" to "cjs",
    "typescript-cjs" to "cjs",
    "typescript-esm" to "esm",
    "typescript-automation" to "cjs",
    "typescript-ocr" to "cjs",
    "typescript-long-running-task" to "cjs",
    "compiled-tsx" to "cjs",
)

private fun nodeProjectWizardDisplayPath(file: File): String =
    runCatching { file.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
        .getOrElse { file.absolutePath }

private fun nodeProjectWizardPluginProjectRoot(overridePath: String?): File {
    val candidate = overridePath
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(rootProject::file)
        ?: rootProject.projectDir.parentFile.resolve("autojs6-plugin-nodejs")
    return candidate.canonicalFile
}

private fun nodeProjectWizardToolsRoot(
    source: String?,
    explicitRoot: String?,
    pluginRoot: File,
): File {
    explicitRoot
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(rootProject::file)
        ?.let { return it.canonicalFile }
    return when (val normalized = source.orEmpty().trim().lowercase()) {
        "", "host", "main", "app" -> rootProject.projectDir.resolve("tools/nodejs").canonicalFile
        "plugin", "node-plugin", "nodejs-plugin" -> pluginRoot.resolve("tools/nodejs").canonicalFile
        else -> throw GradleException(
            "Unsupported Node tools source '$normalized'. Use host or plugin, " +
                    "or set -Pautojs.nodejs.toolsRoot=<dir>."
        )
    }
}

private fun nodeProjectWizardConfiguredToolsRoot(): File {
    val pluginRootOverride = providers.gradleProperty("autojs.nodejs.pluginProjectDir")
        .orElse(providers.gradleProperty("autojs.nodejs.plugin.root"))
    return nodeProjectWizardToolsRoot(
        providers.gradleProperty("autojs.nodejs.toolsSource").orElse("host").get(),
        providers.gradleProperty("autojs.nodejs.toolsRoot").orNull,
        nodeProjectWizardPluginProjectRoot(pluginRootOverride.orNull),
    )
}

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

    val toolsRoot = nodeProjectWizardConfiguredToolsRoot()
    val toolFile = toolsRoot.resolve("project/autojs6-node-project.js")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/project-wizard.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/project-wizard.md")

    inputs.file(toolFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("toolsRoot", toolsRoot.absolutePath)
    inputs.property("templates", nodeProjectWizardTemplates)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val tool = toolFile
        if (!tool.isFile) {
            throw GradleException(
                "Missing Node project wizard tool: ${nodeProjectWizardDisplayPath(tool)}. " +
                        "Use -Pautojs.nodejs.toolsRoot=<dir> or -Pautojs.nodejs.toolsSource=plugin."
            )
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
            val entry = compatibility["entry"] as? Map<*, *>
                ?: throw GradleException("Generated Node project template '$template' did not include entry analysis.")
            val expectedFormat = nodeProjectWizardExpectedEntryFormats[template]
                ?: throw GradleException("Missing expected entry format for Node project wizard template '$template'.")
            if (entry["format"] != expectedFormat) {
                throw GradleException("Generated Node project template '$template' should be '$expectedFormat': $entry")
            }
            rows += linkedMapOf(
                "template" to template,
                "project" to projectDir.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
                "files" to createReport["files"],
                "issueCount" to validateReport["issueCount"],
                "compatibilityStatus" to compatibilityStatus,
                "entryFormat" to entry["format"],
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
            const inspector = require("inspector");
            void inspector;
            """.trimIndent() + "\n",
        )
        val unsupportedSourceReport = nodeProjectWizardJson(
            tool,
            listOf("validate", unsupportedSource.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(unsupportedSourceReport, "CAPABILITY_UNSUPPORTED")
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

        val unsupportedTsxEntry = workRoot.resolve("invalid/unsupported-tsx-entry")
        nodeProjectWizardJson(
            tool,
            listOf(
                "create",
                "--template", "commonjs-app",
                "--out", unsupportedTsxEntry.absolutePath,
                "--name", "unsupported-tsx-entry",
                "--force",
            ),
        )
        val unsupportedTsxProjectJsonFile = unsupportedTsxEntry.resolve("project.json")
        val unsupportedTsxProjectJson = nodeProjectWizardReadJsonFile(unsupportedTsxProjectJsonFile).toMutableMap()
        unsupportedTsxProjectJson["main"] = "main.tsx"
        unsupportedTsxProjectJsonFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(unsupportedTsxProjectJson)) + "\n")
        unsupportedTsxEntry.resolve("main.tsx").writeText("""export default <View />;""" + "\n")
        val unsupportedTsxReport = nodeProjectWizardJson(
            tool,
            listOf("validate", unsupportedTsxEntry.absolutePath),
            expectSuccess = false,
        )
        assertNodeProjectWizardIssueCodes(unsupportedTsxReport, "ENTRY_TYPESCRIPT_TSX_UNSUPPORTED")

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-project-wizard-report-v1",
            "tool" to nodeProjectWizardDisplayPath(tool),
            "toolsRoot" to nodeProjectWizardDisplayPath(toolsRoot),
            "templateCount" to rows.size,
            "templates" to rows,
            "invalidFixtures" to listOf(
                linkedMapOf("name" to "missing-entry", "issueCodes" to missingEntryReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unknown-permission", "issueCodes" to unknownPermissionReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "invalid-package-name", "issueCodes" to invalidPackageNameReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsafe-dependency", "issueCodes" to unsafeDependencyReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsupported-source", "issueCodes" to unsupportedSourceReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsafe-fs-source", "issueCodes" to unsafeFsSourceReport.nodeProjectWizardIssueCodes().sorted()),
                linkedMapOf("name" to "unsupported-tsx-entry", "issueCodes" to unsupportedTsxReport.nodeProjectWizardIssueCodes().sorted()),
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
                    appendLine("| Template | Project | Entry format | Issue count |")
                    appendLine("| --- | --- | --- | ---: |")
                    rows.forEach { row ->
                        appendLine("| ${row["template"]} | ${row["project"]} | ${row["entryFormat"]} | ${row["issueCount"]} (${row["compatibilityStatus"]}) |")
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
