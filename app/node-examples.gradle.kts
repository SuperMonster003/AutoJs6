import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.Exec
import java.io.File

private val nodeExampleKnownStatuses = setOf("stable", "partial", "experimental")
private val nodeExampleIgnoredDirectories = setOf("npm-ci-pure-js")
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
    "typescript",
    "phase7",
    "esm",
    "dynamic-import",
    "network",
    "websocket",
    "ocr",
    "long-running",
    "scheduled-task",
    "worker",
    "node-test",
    "database",
    "plugin",
    "ui",
    "phase8",
    "tsx",
    "require-esm",
    "http",
    "filehandle",
    "fs-watch",
    "compile-cache",
    "inspector",
    "profiler",
    "hardened-sandbox",
    "design-gated",
)

private val nodeExampleSupportedEntryExtensions = setOf("js", "cjs", "mjs", "ts", "cts", "mts")

private val nodeTypeDeclarationModules = listOf(
    "toast",
    "app",
    "dialogs",
    "clipboard",
    "device",
    "shell",
    "engines",
    "accessibility",
    "media_projection",
    "image",
    "images",
    "ocr",
    "storage",
    "storages",
    "notifications",
    "sensors",
    "fetch",
    "axios",
    "websocket",
    "work_manager",
    "plugins",
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

private fun nodeExampleEntryFile(dir: File, name: String, projectJson: Map<*, *>): File {
    val entry = projectJson["main"]?.toString().orEmpty()
    if (entry.isBlank()) {
        throw GradleException("Node example '$name' project.json must declare a main entry.")
    }
    if (entry.contains('\u0000') || entry.startsWith("/") || entry.startsWith("\\") || Regex("""^[A-Za-z]:""").containsMatchIn(entry)) {
        throw GradleException("Node example '$name' project.json main must be a safe relative path: $entry")
    }
    val file = dir.resolve(entry).canonicalFile
    val root = dir.canonicalFile
    if (file != root && !file.toPath().startsWith(root.toPath())) {
        throw GradleException("Node example '$name' project.json main escapes the example directory: $entry")
    }
    val extension = file.extension.lowercase()
    if (extension !in nodeExampleSupportedEntryExtensions) {
        throw GradleException("Node example '$name' project.json main uses unsupported extension '.$extension'.")
    }
    return file
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
        val directoryNames = examplesRoot.listFiles(File::isDirectory).orEmpty()
            .map(File::getName)
            .filterNot(nodeExampleIgnoredDirectories::contains)
            .sorted()
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
            val requiredFiles = listOf("README.md", "project.json", "package.json", "expected-output.txt")
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
            val mainFile = nodeExampleEntryFile(dir, name, projectJson)
            if (!mainFile.isFile) {
                throw GradleException("Node example '$name' is missing declared main entry: ${mainFile.relativeTo(dir)}")
            }
            if ("phase7" in tags) {
                val exampleMetadata = projectJson["example"] as? Map<*, *>
                    ?: throw GradleException("Phase 7 Node example '$name' must declare project.json example metadata.")
                val capabilities = nodeExampleList(exampleMetadata["capabilities"])
                val limitations = nodeExampleList(exampleMetadata["securityLimitations"])
                val expectedProvider = exampleMetadata["expectedProvider"]?.toString().orEmpty()
                val packagedSupport = exampleMetadata["packagedSupport"]?.toString().orEmpty()
                if (capabilities.isEmpty()) {
                    throw GradleException("Phase 7 Node example '$name' must declare example.capabilities.")
                }
                if (expectedProvider.isBlank()) {
                    throw GradleException("Phase 7 Node example '$name' must declare example.expectedProvider.")
                }
                if (packagedSupport.isBlank()) {
                    throw GradleException("Phase 7 Node example '$name' must declare example.packagedSupport.")
                }
                if (limitations.isEmpty()) {
                    throw GradleException("Phase 7 Node example '$name' must declare example.securityLimitations.")
                }
            }
            val packageJson = nodeExampleReadJsonObject(dir.resolve("package.json"))
            val packageName = packageJson["name"]?.toString().orEmpty()
            if (!packageName.startsWith("@autojs6-sample/")) {
                throw GradleException("Node example '$name' package.json name must use @autojs6-sample/ scope.")
            }
            if (mainFile.extension.lowercase() !in setOf("mjs", "mts") && !nodeExampleMainHasNodeDirective(mainFile)) {
                throw GradleException("Node example '$name' ${mainFile.name} must start with a node directive.")
            }
            val expectedOutput = dir.resolve("expected-output.txt").readText()
            val passMarker = "sample.$name=PASS"
            if (!expectedOutput.contains(passMarker)) {
                throw GradleException("Node example '$name' expected-output.txt must contain '$passMarker'.")
            }
            if (!mainFile.readText().contains(passMarker)) {
                throw GradleException("Node example '$name' ${mainFile.name} must print '$passMarker'.")
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

val verifyAutoJs6NodeTypeDeclarations = tasks.register("verifyAutoJs6NodeTypeDeclarations") {
    group = "verification"
    description = "Verifies AutoJs6 Node TypeScript declarations and the checked-in TypeScript smoke sample."

    val jsonReport = layout.buildDirectory.file("reports/nodejs/typescript-declarations.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/typescript-declarations.md")

    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val typesRoot = rootProject.projectDir.resolve("docs/nodejs/types/autojs6-node")
        val sampleRoot = rootProject.projectDir.resolve("sample/nodejs/typescript-smoke")
        val indexFile = typesRoot.resolve("index.d.ts")
        val commonFile = typesRoot.resolve("common.d.ts")
        val sampleTsConfig = sampleRoot.resolve("tsconfig.json")
        val sampleSource = sampleRoot.resolve("src/main.ts")

        val missingRequired = listOf(indexFile, commonFile, sampleTsConfig, sampleSource)
            .filterNot(File::isFile)
            .map { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
        if (missingRequired.isNotEmpty()) {
            throw GradleException("Missing AutoJs6 Node TypeScript required file(s): ${missingRequired.joinToString()}")
        }

        val indexText = indexFile.readText()
        val sampleText = sampleSource.readText()
        val tsConfigText = sampleTsConfig.readText()
        val rows = nodeTypeDeclarationModules.map { moduleName ->
            val declaration = typesRoot.resolve("$moduleName.d.ts")
            if (!declaration.isFile) {
                throw GradleException("Missing TypeScript declaration for Node bridge module '$moduleName'.")
            }
            val declarationText = declaration.readText()
            if (!declarationText.contains("""declare module "$moduleName"""")) {
                throw GradleException("${declaration.relativeTo(rootProject.projectDir)} must declare module \"$moduleName\".")
            }
            if (!indexText.contains("$moduleName.d.ts")) {
                throw GradleException("index.d.ts must reference $moduleName.d.ts.")
            }
            if (!sampleText.contains("""require("$moduleName")""")) {
                throw GradleException("typescript-smoke sample must import '$moduleName'.")
            }
            linkedMapOf(
                "module" to moduleName,
                "declaration" to declaration.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
                "bytes" to declaration.length(),
            )
        }

        if (!tsConfigText.contains("../../../docs/nodejs/types/autojs6-node/**/*.d.ts")) {
            throw GradleException("typescript-smoke tsconfig.json must include the checked-in AutoJs6 Node declarations.")
        }

        val examplesManifest = nodeExampleReadJsonObject(rootProject.projectDir.resolve("sample/nodejs/examples.json"))
        val examples = (examplesManifest["examples"] as? List<*>).orEmpty().map {
            it as? Map<*, *> ?: throw GradleException("Each Node example manifest entry must be an object.")
        }
        val typeSmoke = examples.singleOrNull { it["name"] == "typescript-smoke" }
            ?: throw GradleException("sample/nodejs/examples.json must include the typescript-smoke example.")
        if ("typescript" !in nodeExampleList(typeSmoke["tags"])) {
            throw GradleException("typescript-smoke example must carry the 'typescript' tag.")
        }

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-types-report-v1",
            "typesRoot" to typesRoot.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "sample" to sampleRoot.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "moduleCount" to rows.size,
            "modules" to rows,
        )
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# AutoJs6 Node TypeScript Declaration Validation")
                    appendLine()
                    appendLine("| Module | Declaration | Bytes |")
                    appendLine("| --- | --- | --- |")
                    rows.forEach { row ->
                        appendLine("| ${row["module"]} | ${row["declaration"]} | ${row["bytes"]} |")
                    }
                }
            )
        }
        logger.lifecycle("Verified ${rows.size} AutoJs6 Node TypeScript declaration modules.")
        logger.lifecycle("AutoJs6 Node TypeScript declaration report written to ${jsonReport.get().asFile.absolutePath}")
    }
}

tasks.register<Exec>("typeCheckNodeTypescriptSmoke") {
    group = "verification"
    description = "Runs tsc against sample/nodejs/typescript-smoke using the checked-in AutoJs6 Node declarations."

    dependsOn(verifyAutoJs6NodeTypeDeclarations)

    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val tsconfig = rootProject.projectDir.resolve("sample/nodejs/typescript-smoke/tsconfig.json")
    if (isWindows) {
        commandLine("cmd", "/c", "tsc", "-p", tsconfig.absolutePath, "--pretty", "false")
    } else {
        commandLine("tsc", "-p", tsconfig.absolutePath, "--pretty", "false")
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
    dependsOn(verifyAutoJs6NodeTypeDeclarations)
}
