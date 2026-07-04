import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.Exec
import java.io.File
import java.security.MessageDigest

private val nodeExampleKnownStatuses = setOf("stable", "partial", "experimental")
private val nodeExampleIgnoredDirectories = setOf("npm-ci-pure-js", "autojs6-pm-fixture")
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
    "raw-network",
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
    "phase9",
    "runtime-info",
    "snapshot",
    "heap-snapshot",
    "multi-instance",
    "sandbox",
    "wasm",
    "wasi",
    "wasm-worker",
    "wasm-plugin",
    "phase10",
    "storage",
    "notifications",
    "sensors",
    "package-install",
    "provider",
    "capability-truth",
    "crash",
    "adapter",
    "debug",
    "queue",
    "phase13",
    "pro-parity",
    "desktop-parity",
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
    "database",
    "sqlite",
    "console",
    "timers",
    "files",
    "base64",
    "colors",
    "formatter",
    "converter",
    "s13n",
    "mime",
    "nanoid",
    "util",
    "opencc",
    "pinyin",
    "pinyin4j",
    "rhino",
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

private fun nodeExampleDisplayPath(file: File): String =
    runCatching { file.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
        .getOrElse { file.absolutePath }

private fun nodeExampleList(value: Any?): List<String> =
    (value as? List<*>).orEmpty().map { it.toString() }

private fun nodeExamplePluginProjectRoot(overridePath: String?): File {
    val candidate = overridePath
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(rootProject::file)
        ?: rootProject.projectDir.parentFile.resolve("autojs6-plugin-nodejs")
    return candidate.canonicalFile
}

private fun nodeExampleRoot(
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
        "", "host", "main", "app" -> rootProject.projectDir.resolve("sample/nodejs").canonicalFile
        "plugin", "node-plugin", "nodejs-plugin" -> pluginRoot.resolve("sample/nodejs").canonicalFile
        else -> throw GradleException(
            "Unsupported Node example source '$normalized'. Use host or plugin, " +
                    "or set -Pautojs.nodejs.examplesRoot=<dir>."
        )
    }
}

private fun nodeExampleConfiguredRoot(): File {
    val pluginRootOverride = providers.gradleProperty("autojs.nodejs.pluginProjectDir")
        .orElse(providers.gradleProperty("autojs.nodejs.plugin.root"))
    return nodeExampleRoot(
        providers.gradleProperty("autojs.nodejs.examplesSource").orElse("host").get(),
        providers.gradleProperty("autojs.nodejs.examplesRoot").orNull,
        nodeExamplePluginProjectRoot(pluginRootOverride.orNull),
    )
}

private fun nodeExampleSha256(file: File): String {
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

private fun nodeExampleFileInventory(root: File): Map<String, String> {
    if (!root.isDirectory) {
        throw GradleException("Missing Node example directory: ${root.absolutePath}")
    }
    return root.walkTopDown()
        .filter(File::isFile)
        .associate { file -> file.relativeTo(root).invariantSeparatorsPath to nodeExampleSha256(file) }
        .toSortedMap()
}

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
    description = "Verifies Node sample examples, metadata, expected outputs, and smoke classifications."

    val examplesRoot = nodeExampleConfiguredRoot()
    val jsonReport = layout.buildDirectory.file("reports/nodejs/examples.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/examples.md")

    inputs.dir(examplesRoot)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val manifestFile = examplesRoot.resolve("examples.json")
        if (!manifestFile.isFile) {
            throw GradleException("Missing Node example manifest: ${nodeExampleDisplayPath(manifestFile)}")
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
            "manifest" to nodeExampleDisplayPath(manifestFile),
            "examplesRoot" to nodeExampleDisplayPath(examplesRoot),
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
        logger.lifecycle("Verified ${rows.size} Node examples from ${nodeExampleDisplayPath(manifestFile)}.")
        logger.lifecycle("Node examples report written to ${jsonReport.get().asFile.absolutePath}")
    }
}

tasks.register("verifyNodePluginExampleMirror") {
    group = "verification"
    description = "Verifies the Node.js plugin owns a synchronized mirror of sample/nodejs before the host copy is removed."

    dependsOn(verifyNodeExamples)

    val pluginRootOverride = providers.gradleProperty("autojs.nodejs.pluginProjectDir")
        .orElse(providers.gradleProperty("autojs.nodejs.plugin.root"))
    val pluginRoot = nodeExamplePluginProjectRoot(pluginRootOverride.orNull)
    val pluginExamplesRoot = pluginRoot.resolve("sample/nodejs")
    val pluginVerifier = pluginRoot.resolve("node-plugin-examples.gradle.kts")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/plugin-example-mirror.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/plugin-example-mirror.md")

    inputs.dir(rootProject.layout.projectDirectory.dir("sample/nodejs"))
    inputs.dir(pluginExamplesRoot)
    inputs.file(pluginVerifier)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val hostExamplesRoot = rootProject.projectDir.resolve("sample/nodejs").canonicalFile
        val hostInventory = nodeExampleFileInventory(hostExamplesRoot)

        if (!pluginExamplesRoot.resolve("examples.json").isFile) {
            throw GradleException(
                "Missing Node plugin example catalog: ${pluginExamplesRoot.resolve("examples.json").absolutePath}. " +
                        "Use -Pautojs.nodejs.plugin.root=<path> if the plugin repo is not a sibling of this repo."
            )
        }
        if (!pluginVerifier.isFile) {
            throw GradleException("Missing Node plugin example verifier: ${pluginVerifier.absolutePath}")
        }

        val pluginInventory = nodeExampleFileInventory(pluginExamplesRoot.canonicalFile)
        val hostFiles = hostInventory.keys
        val pluginFiles = pluginInventory.keys
        val missingFromPlugin = (hostFiles - pluginFiles).sorted()
        val extraInPlugin = (pluginFiles - hostFiles).sorted()
        val contentMismatches = hostFiles.intersect(pluginFiles)
            .filter { relativePath -> hostInventory[relativePath] != pluginInventory[relativePath] }
            .sorted()
        val matches = missingFromPlugin.isEmpty() && extraInPlugin.isEmpty() && contentMismatches.isEmpty()

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-plugin-example-mirror-report-v1",
            "hostExamplesRoot" to nodeExampleDisplayPath(hostExamplesRoot),
            "pluginRoot" to pluginRoot.absolutePath,
            "pluginExamplesRoot" to pluginExamplesRoot.absolutePath,
            "pluginVerifier" to pluginVerifier.absolutePath,
            "hostFileCount" to hostInventory.size,
            "pluginFileCount" to pluginInventory.size,
            "matches" to matches,
            "missingFromPlugin" to missingFromPlugin,
            "extraInPlugin" to extraInPlugin,
            "contentMismatches" to contentMismatches,
        )
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# AutoJs6 Node Plugin Example Mirror")
                    appendLine()
                    appendLine("- Host examples: `${nodeExampleDisplayPath(hostExamplesRoot)}`")
                    appendLine("- Plugin examples: `${pluginExamplesRoot.absolutePath}`")
                    appendLine("- Host files: `${hostInventory.size}`")
                    appendLine("- Plugin files: `${pluginInventory.size}`")
                    appendLine("- Matches: `$matches`")
                    appendLine()
                    if (!matches) {
                        appendLine("## Differences")
                        appendLine()
                        appendLine("- Missing from plugin: `${missingFromPlugin.size}`")
                        appendLine("- Extra in plugin: `${extraInPlugin.size}`")
                        appendLine("- Content mismatches: `${contentMismatches.size}`")
                    }
                }
            )
        }

        if (!matches) {
            throw GradleException(
                "Node plugin sample mirror is out of sync: " +
                        "missing=${missingFromPlugin.size}, extra=${extraInPlugin.size}, mismatched=${contentMismatches.size}. " +
                        "See ${nodeExampleDisplayPath(jsonReport.get().asFile)}."
            )
        }
        logger.lifecycle("Verified Node plugin example mirror with ${pluginInventory.size} files from ${pluginExamplesRoot.absolutePath}.")
    }
}

val verifyAutoJs6NodeTypeDeclarations = tasks.register("verifyAutoJs6NodeTypeDeclarations") {
    group = "verification"
    description = "Verifies AutoJs6 Node TypeScript declarations and the checked-in TypeScript smoke sample."

    val examplesRoot = nodeExampleConfiguredRoot()
    val typesRoot = rootProject.projectDir.resolve("docs/nodejs/types/autojs6-node")
    val sampleRoot = examplesRoot.resolve("typescript-smoke")
    val examplesManifestFile = examplesRoot.resolve("examples.json")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/typescript-declarations.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/typescript-declarations.md")

    inputs.dir(typesRoot)
    inputs.dir(sampleRoot)
    inputs.file(examplesManifestFile)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val indexFile = typesRoot.resolve("index.d.ts")
        val commonFile = typesRoot.resolve("common.d.ts")
        val profileCapabilitiesFile = typesRoot.resolve("profile_capabilities.d.ts")
        val sampleTsConfig = sampleRoot.resolve("tsconfig.json")
        val sampleSource = sampleRoot.resolve("src/main.ts")

        val missingRequired = listOf(indexFile, commonFile, profileCapabilitiesFile, sampleTsConfig, sampleSource)
            .filterNot(File::isFile)
            .map(::nodeExampleDisplayPath)
        if (missingRequired.isNotEmpty()) {
            throw GradleException("Missing AutoJs6 Node TypeScript required file(s): ${missingRequired.joinToString()}")
        }

        val indexText = indexFile.readText()
        val profileCapabilitiesText = profileCapabilitiesFile.readText()
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
        if (!indexText.contains("profile_capabilities.d.ts")) {
            throw GradleException("index.d.ts must reference profile_capabilities.d.ts.")
        }
        if (!profileCapabilitiesText.contains("ProfileModuleCatalog") ||
            !profileCapabilitiesText.contains("ProfileModuleModeFor") ||
            !profileCapabilitiesText.contains("desktop_compat_opt_in") ||
            !profileCapabilitiesText.contains("NodeProfileV12CapabilityCatalog") ||
            !profileCapabilitiesText.contains("NodeProfileV12DeclarationMatrix")
        ) {
            throw GradleException("profile_capabilities.d.ts must declare the profile-aware module catalog and conditional helpers.")
        }
        if (!sampleText.contains("""ProfileModuleModeFor<"java", "safe_default">""") ||
            !sampleText.contains("""ProfileModuleModeFor<"worker_threads", "desktop_compat_opt_in">""") ||
            !sampleText.contains("ProfileDeclarationMatrix") ||
            !sampleText.contains("""NodeProfileV12ModeFor<"ui.overlay", "pro_compat_opt_in">""") ||
            !sampleText.contains("NodeProfileV12DeclarationMatrix")
        ) {
            throw GradleException("typescript-smoke sample must cover Safe/Pro/Desktop profile declaration differences.")
        }

        val examplesManifest = nodeExampleReadJsonObject(examplesManifestFile)
        val examples = (examplesManifest["examples"] as? List<*>).orEmpty().map {
            it as? Map<*, *> ?: throw GradleException("Each Node example manifest entry must be an object.")
        }
        val typeSmoke = examples.singleOrNull { it["name"] == "typescript-smoke" }
            ?: throw GradleException("${nodeExampleDisplayPath(examplesManifestFile)} must include the typescript-smoke example.")
        if ("typescript" !in nodeExampleList(typeSmoke["tags"])) {
            throw GradleException("typescript-smoke example must carry the 'typescript' tag.")
        }

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-types-report-v1",
            "typesRoot" to typesRoot.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "sample" to nodeExampleDisplayPath(sampleRoot),
            "examplesManifest" to nodeExampleDisplayPath(examplesManifestFile),
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

val generateNodeTypescriptSmokeTsconfig = tasks.register("generateNodeTypescriptSmokeTsconfig") {
    group = "verification"
    description = "Generates a host-owned tsconfig for the selected Node TypeScript smoke sample source."

    val examplesRoot = nodeExampleConfiguredRoot()
    val sampleRoot = examplesRoot.resolve("typescript-smoke")
    val typesRoot = rootProject.projectDir.resolve("docs/nodejs/types/autojs6-node")
    val generatedTsconfig = layout.buildDirectory.file("generated/nodejs/typescript-smoke/tsconfig.json")

    inputs.file(sampleRoot.resolve("tsconfig.json"))
    inputs.dir(sampleRoot.resolve("src"))
    inputs.dir(typesRoot)
    outputs.file(generatedTsconfig)

    doLast {
        val sourceInclude = sampleRoot.resolve("src").absolutePath.replace('\\', '/') + "/**/*.ts"
        val typesInclude = typesRoot.absolutePath.replace('\\', '/') + "/**/*.d.ts"
        val tsconfig = linkedMapOf<String, Any?>(
            "compilerOptions" to linkedMapOf(
                "target" to "ES2020",
                "module" to "CommonJS",
                "moduleResolution" to "node",
                "strict" to true,
                "noEmit" to true,
                "skipLibCheck" to false,
                "lib" to listOf("ES2020"),
                "types" to emptyList<String>(),
            ),
            "include" to listOf(sourceInclude, typesInclude),
        )
        generatedTsconfig.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(tsconfig)) + "\n")
        }
    }
}

tasks.register<Exec>("typeCheckNodeTypescriptSmoke") {
    group = "verification"
    description = "Runs tsc against the selected Node TypeScript smoke sample using the checked-in AutoJs6 Node declarations."

    dependsOn(verifyAutoJs6NodeTypeDeclarations)
    dependsOn(generateNodeTypescriptSmokeTsconfig)

    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val tsconfig = layout.buildDirectory.file("generated/nodejs/typescript-smoke/tsconfig.json")
    if (isWindows) {
        commandLine("cmd", "/c", "tsc", "-p", tsconfig.get().asFile.absolutePath, "--pretty", "false")
    } else {
        commandLine("tsc", "-p", tsconfig.get().asFile.absolutePath, "--pretty", "false")
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
