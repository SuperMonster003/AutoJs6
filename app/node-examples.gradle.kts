import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File

private val nodeExampleKnownStatuses = setOf("stable", "partial", "experimental")
private val nodeExampleKnownTags = setOf(
    "main-app",
    "packaged",
    "smoke",
    "commonjs",
    "node_modules",
    "scoped-fs",
    "fs-promises",
    "pure-js-npm",
    "bridge",
    "toast",
    "app",
    "requires-accessibility",
    "requires-screen-capture",
    "requires-image",
    "permission-optional",
    "security",
    "disabled-features",
)

private fun nodeExampleReadJsonObject(file: File): Map<*, *> =
    JsonSlurper().parse(file) as? Map<*, *>
        ?: throw GradleException("Expected JSON object in ${file.absolutePath}")

private fun nodeExampleList(value: Any?): List<String> =
    (value as? List<*>).orEmpty().map { it.toString() }

private fun nodeExampleMainHasNodeDirective(file: File): Boolean {
    val firstMeaningful = file.readLines()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() && !it.startsWith("//") }
        .orEmpty()
    return firstMeaningful == "\"nodejs\";" ||
            firstMeaningful == "'nodejs';" ||
            firstMeaningful == "\"node\";" ||
            firstMeaningful == "'node';"
}

val verifyNodeExamples = tasks.register("verifyNodeExamples") {
    group = "verification"
    description = "Verifies checked-in sample/nodejs examples, metadata, expected outputs, and smoke classifications."

    val jsonReport = layout.buildDirectory.file("reports/nodejs/examples.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/examples.md")

    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val examplesRoot = rootProject.projectDir.resolve("sample/nodejs")
        val manifestFile = examplesRoot.resolve("examples.json")
        if (!manifestFile.isFile) {
            throw GradleException("Missing Node example manifest: ${manifestFile.relativeTo(rootProject.projectDir)}")
        }
        val manifest = nodeExampleReadJsonObject(manifestFile)
        if (manifest["schema"] != "autojs6-node-examples-v1") {
            throw GradleException("Unsupported Node example manifest schema: ${manifest["schema"]}")
        }
        val examples = (manifest["examples"] as? List<*>).orEmpty().map {
            it as? Map<*, *> ?: throw GradleException("Each Node example manifest entry must be an object.")
        }
        val manifestNames = examples.map { it["name"].toString() }
        val duplicateNames = manifestNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            throw GradleException("Duplicate Node example manifest entries: ${duplicateNames.joinToString()}")
        }
        val directoryNames = examplesRoot.listFiles(File::isDirectory).orEmpty().map(File::getName).sorted()
        val missingManifest = directoryNames - manifestNames.toSet()
        val missingDirectory = manifestNames.toSet() - directoryNames.toSet()
        if (missingManifest.isNotEmpty() || missingDirectory.isNotEmpty()) {
            throw GradleException(
                "Node example manifest mismatch; directories missing from manifest=${missingManifest.joinToString()}, " +
                        "manifest entries missing directories=${missingDirectory.joinToString()}"
            )
        }

        val rows = mutableListOf<Map<String, Any?>>()
        examples.forEach { entry ->
            val name = entry["name"].toString()
            val status = entry["status"].toString()
            val tags = nodeExampleList(entry["tags"])
            val mainAppSmoke = entry["mainAppSmoke"] as? Boolean ?: false
            val packagedCompatible = entry["packagedCompatible"] as? Boolean ?: false
            if (status !in nodeExampleKnownStatuses) {
                throw GradleException("Node example '$name' has unknown status '$status'.")
            }
            val unknownTags = tags.filterNot(nodeExampleKnownTags::contains)
            if (unknownTags.isNotEmpty()) {
                throw GradleException("Node example '$name' has unknown tags: ${unknownTags.joinToString()}")
            }
            val dir = examplesRoot.resolve(name)
            val requiredFiles = listOf("README.md", "project.json", "package.json", "main.cjs", "expected-output.txt")
            val missingFiles = requiredFiles.filterNot { dir.resolve(it).isFile }
            if (missingFiles.isNotEmpty()) {
                throw GradleException("Node example '$name' is missing files: ${missingFiles.joinToString()}")
            }
            val readme = dir.resolve("README.md").readText()
            if (!readme.lineSequence().firstOrNull().orEmpty().contains(name)) {
                throw GradleException("Node example '$name' README should start with a title containing the example name.")
            }
            val projectJson = nodeExampleReadJsonObject(dir.resolve("project.json"))
            if (projectJson["name"] != name || projectJson["type"] != "node") {
                throw GradleException("Node example '$name' project.json must declare name='$name' and type='node'.")
            }
            if (projectJson["main"] != "main.cjs") {
                throw GradleException("Node example '$name' project.json must use main.cjs as main.")
            }
            val packageJson = nodeExampleReadJsonObject(dir.resolve("package.json"))
            val packageName = packageJson["name"]?.toString().orEmpty()
            if (!packageName.startsWith("@autojs6-sample/")) {
                throw GradleException("Node example '$name' package.json name must use @autojs6-sample/ scope.")
            }
            val mainFile = dir.resolve("main.cjs")
            if (!nodeExampleMainHasNodeDirective(mainFile)) {
                throw GradleException("Node example '$name' main.cjs must start with a node directive.")
            }
            val expectedOutput = dir.resolve("expected-output.txt").readText()
            val passMarker = "sample.$name=PASS"
            if (!expectedOutput.contains(passMarker)) {
                throw GradleException("Node example '$name' expected-output.txt must contain '$passMarker'.")
            }
            if (!mainFile.readText().contains(passMarker)) {
                throw GradleException("Node example '$name' main.cjs must print '$passMarker'.")
            }
            if (packagedCompatible && "packaged" !in tags) {
                throw GradleException("Node example '$name' is packagedCompatible but is missing the packaged tag.")
            }
            if (mainAppSmoke && "main-app" !in tags) {
                throw GradleException("Node example '$name' is mainAppSmoke but is missing the main-app tag.")
            }
            rows += linkedMapOf(
                "name" to name,
                "status" to status,
                "tags" to tags,
                "mainAppSmoke" to mainAppSmoke,
                "packagedCompatible" to packagedCompatible,
                "packageName" to packageName,
            )
        }

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-examples-report-v1",
            "manifest" to manifestFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "exampleCount" to rows.size,
            "mainAppSmoke" to rows.filter { it["mainAppSmoke"] == true }.map { it["name"] },
            "packagedCompatible" to rows.filter { it["packagedCompatible"] == true }.map { it["name"] },
            "examples" to rows,
        )
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# AutoJs6 Node Example Validation")
                    appendLine()
                    appendLine("| Example | Status | Main app smoke | Packaged | Tags |")
                    appendLine("| --- | --- | --- | --- | --- |")
                    rows.forEach { row ->
                        appendLine(
                            "| ${row["name"]} | ${row["status"]} | ${row["mainAppSmoke"]} | ${row["packagedCompatible"]} | " +
                                    "${(row["tags"] as List<*>).joinToString(", ")} |"
                        )
                    }
                }
            )
        }
        logger.lifecycle("Verified ${rows.size} Node examples from ${manifestFile.relativeTo(rootProject.projectDir)}.")
        logger.lifecycle("Node examples report written to ${jsonReport.get().asFile.absolutePath}")
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
    dependsOn(verifyNodeExamples)
}
