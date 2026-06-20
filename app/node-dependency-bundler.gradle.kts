import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

private val nodeDependencyBundlerProjectRootProperty = "autojs.nodejs.bundle.projectRoot"
private val nodeDependencyBundlerOutputDirProperty = "autojs.nodejs.bundle.outputDir"
private val nodeDependencyBundlerReportDirProperty = "autojs.nodejs.bundle.reportDir"
private val nodeDependencyBundlerCacheDirProperty = "autojs.nodejs.bundle.cacheDir"
private val nodeDependencyBundlerMaxFileBytesProperty = "autojs.nodejs.bundle.maxFileBytes"
private val nodeDependencyBundlerMaxPackageBytesProperty = "autojs.nodejs.bundle.maxPackageBytes"
private val nodeDependencyBundlerMaxTotalBytesProperty = "autojs.nodejs.bundle.maxTotalBytes"
private val nodeDependencyBundlerMaxPackageCountProperty = "autojs.nodejs.bundle.maxPackageCount"
private val nodeDependencyBundlerAllowSuspiciousExecutablesProperty = "autojs.nodejs.bundle.allowSuspiciousExecutables"
private val nodeDependencyNpmCiProjectRootProperty = "autojs.nodejs.npmCi.projectRoot"
private val nodeDependencyNpmCiWorkDirProperty = "autojs.nodejs.npmCi.workDir"
private val nodeDependencyNpmCiOutputDirProperty = "autojs.nodejs.npmCi.outputDir"
private val nodeDependencyNpmCiReportDirProperty = "autojs.nodejs.npmCi.reportDir"
private val nodeDependencyNpmCiCacheDirProperty = "autojs.nodejs.npmCi.cacheDir"
private val nodeDependencyNpmCiExecutableProperty = "autojs.nodejs.npmCi.executable"

private val nodeDependencyBundlerForbiddenScripts = setOf("preinstall", "install", "postinstall", "prepare")
private val nodeDependencyBundlerPathReferenceFields = setOf(
    "main",
    "module",
    "browser",
    "types",
    "typings",
    "bin",
    "man",
    "files",
    "directories",
    "exports",
    "imports",
    "typesVersions",
)
private val nodeDependencyBundlerSuspiciousExecutableExtensions = setOf(
    "bat",
    "cmd",
    "com",
    "dll",
    "dylib",
    "exe",
    "ps1",
    "sh",
    "so",
)
private val nodeDependencyBundlerProtocolOrWindowsDrivePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val nodeDependencyBundlerCapabilityBuiltins = linkedMapOf(
    "raw_network" to listOf("http", "https", "net", "tls", "dns", "dns/promises", "dgram"),
    "child_process" to listOf("child_process"),
    "worker_threads" to listOf("worker_threads"),
    "vm" to listOf("vm"),
    "inspector" to listOf("inspector"),
    "java_interop" to listOf("java"),
)
private val nodeDependencyCompatibilityStatusRank = linkedMapOf(
    "compatible" to 0,
    "compatible with capability" to 1,
    "experimental" to 2,
    "unsupported" to 3,
    "unsafe" to 4,
)
private val nodeDependencyCompatibilityKnownBuiltins = setOf(
    "assert",
    "assert/strict",
    "async_hooks",
    "buffer",
    "child_process",
    "cluster",
    "console",
    "constants",
    "crypto",
    "dgram",
    "diagnostics_channel",
    "dns",
    "dns/promises",
    "events",
    "fs",
    "fs/promises",
    "http",
    "https",
    "inspector",
    "module",
    "net",
    "os",
    "path",
    "perf_hooks",
    "process",
    "punycode",
    "querystring",
    "readline",
    "repl",
    "stream",
    "stream/consumers",
    "stream/promises",
    "stream/web",
    "string_decoder",
    "test",
    "timers",
    "timers/promises",
    "tls",
    "tty",
    "url",
    "util",
    "util/types",
    "v8",
    "vm",
    "wasi",
    "worker_threads",
    "zlib",
)
private val nodeDependencyCompatibilityBuiltinCapabilities =
    nodeDependencyBundlerCapabilityBuiltins.flatMap { (capability, modules) -> modules.map { it to capability } }.toMap()
private val nodeDependencyCompatibilityBridgeCapabilities = linkedMapOf(
    "toast" to "toast",
    "app" to "app",
    "dialogs" to "dialogs",
    "engines" to "engines",
    "accessibility" to "accessibility",
    "image" to "image",
    "images" to "image",
    "ocr" to "ocr",
    "storage" to "storage",
    "storages" to "storage",
    "notifications" to "notifications",
    "sensors" to "sensors",
    "ui" to "ui",
    "clipboard" to "clipboard",
    "device" to "device",
    "shell" to "shell",
    "work_manager" to "work_manager",
)
private val nodeDependencyCompatibilityCapabilityAliases = mapOf(
    "media_projection" to "screen_capture",
    "media.projection" to "screen_capture",
    "screen.capture" to "screen_capture",
    "screencapture" to "screen_capture",
    "engines:exec" to "engines.exec",
    "engine.exec" to "engines.exec",
    "shell:root" to "shell.root",
    "storages" to "storage",
    "java" to "java_interop",
)
private val nodeDependencyCompatibilitySourceExtensions = setOf("js", "cjs", "mjs")
private val nodeDependencyCompatibilityMaxSourceBytes = 256 * 1024

private data class NodeDependencyBundlePolicy(
    val maxFileBytes: Long,
    val maxPackageBytes: Long,
    val maxTotalBytes: Long,
    val maxPackageCount: Int,
    val allowSuspiciousExecutables: Boolean,
)

private data class NodeDependencyPackageInfo(
    val name: String,
    val version: String,
    val license: String,
    val relativePath: String,
    val packageJsonSha256: String,
    val contentSha256: String,
    val integrity: String,
    val fileCount: Int,
    val sizeBytes: Long,
    val unsupportedCapabilities: List<String>,
    val autoJs6CompatibilityScore: Int,
)

private data class NodeDependencyBundleIssue(
    val code: String,
    val path: String,
    val message: String,
)

private data class NodeDependencyBundleScanResult(
    val packages: List<NodeDependencyPackageInfo>,
    val totalFiles: Int,
    val totalBytes: Long,
    val bundleHash: String,
    val issues: List<NodeDependencyBundleIssue>,
)

private fun nodeDependencyBundlerJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun File.nodeDependencyBundlerSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun String.nodeDependencyBundlerSha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun nodeDependencyBundlerDigestTree(root: File, files: List<File>): String {
    val rootFile = root.canonicalFile
    val digest = MessageDigest.getInstance("SHA-256")
    files
        .map { file -> file.canonicalFile }
        .sortedBy { file -> file.nodeDependencyBundlerRelativeTo(rootFile) }
        .forEach { file ->
            val relativePath = file.nodeDependencyBundlerRelativeTo(rootFile)
            val fileHash = file.nodeDependencyBundlerSha256()
            digest.update("file\u0000$relativePath\u0000${file.length()}\u0000$fileHash\n".toByteArray(Charsets.UTF_8))
        }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun nodeDependencyBundlerPackageContentSha256(packageDir: File): String {
    if (!packageDir.isDirectory) {
        return ""
    }
    val files = Files.walk(packageDir.toPath()).use { stream ->
        stream
            .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .map { it.toFile() }
            .toList()
    }
    return nodeDependencyBundlerDigestTree(packageDir, files)
}

private fun nodeDependencyBundlerSanitizedProjectFiles(projectRoot: File): List<File> {
    val files = mutableListOf<File>()
    projectRoot.resolve("package.json").takeIf(File::isFile)?.let(files::add)
    projectRoot.resolve("package-lock.json").takeIf(File::isFile)?.let(files::add)
    val nodeModules = projectRoot.resolve("node_modules")
    if (nodeModules.isDirectory) {
        Files.walk(nodeModules.toPath()).use { stream ->
            stream
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { it.toFile() }
                .forEach(files::add)
        }
    }
    return files
}

private fun nodeDependencyBundlerSanitizedBundleHash(projectRoot: File): String =
    nodeDependencyBundlerDigestTree(projectRoot, nodeDependencyBundlerSanitizedProjectFiles(projectRoot))

private fun nodeDependencyBundlerHostExecutable(executable: String): String {
    val trimmed = executable.trim().ifBlank { "npm" }
    val fileName = File(trimmed).name
    val hasExtension = fileName.contains('.')
    val looksLikePath = trimmed.contains('/') || trimmed.contains('\\')
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    return if (isWindows && !looksLikePath && !hasExtension) {
        "$trimmed.cmd"
    } else {
        trimmed
    }
}

private fun runNodeDependencyBundlerCommand(workingDir: File, command: List<String>): String {
    val process = ProcessBuilder(command)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException(
            "Command failed with exit=$exitCode in ${workingDir.absolutePath}: ${command.joinToString(" ")}\n$output"
        )
    }
    return output
}

private fun String.nodeDependencyBundlerLastVersionLine(): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.matches(Regex("""\d+(?:\.\d+){1,3}.*""")) }
        .lastOrNull()
        ?: trim()

private fun File.nodeDependencyBundlerCanonicalInside(root: File): Boolean {
    val rootPath = root.canonicalFile.toPath()
    val candidatePath = canonicalFile.toPath()
    return candidatePath == rootPath || candidatePath.startsWith(rootPath)
}

private fun File.nodeDependencyBundlerRelativeTo(root: File): String =
    root.canonicalFile.toPath()
        .relativize(canonicalFile.toPath())
        .toString()
        .replace(File.separatorChar, '/')

private fun String.nodeDependencyBundlerIsNodeGypArtifact(): Boolean {
    val path = lowercase()
    return path.endsWith("/binding.gyp") ||
            path.contains("/build/release/") ||
            path.contains("/build/debug/")
}

private fun String.nodeDependencyBundlerIsDotBinEntry(): Boolean =
    this == "node_modules/.bin" ||
            startsWith("node_modules/.bin/") ||
            contains("/node_modules/.bin/")

private fun String.nodeDependencyBundlerIsPostinstallArtifact(): Boolean {
    val path = lowercase()
    return path.contains("/.node-gyp/") ||
            path.contains("/.npm/") ||
            path.contains("/.cache/node-gyp/") ||
            path.endsWith("/npm-debug.log") ||
            path.endsWith("/yarn-error.log") ||
            path.endsWith("/pnpm-debug.log")
}

private fun File.nodeDependencyBundlerIsSuspiciousExecutable(): Boolean {
    val extension = extension.lowercase()
    if (extension in nodeDependencyBundlerSuspiciousExecutableExtensions) {
        return true
    }
    if (!isFile || length() < 2L) {
        return false
    }
    return runCatching {
        inputStream().use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            read >= 2 && header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte() ||
                    read >= 4 && header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() ||
                    read >= 4 && header[0] == 0xFE.toByte() && header[1] == 0xED.toByte() &&
                    header[2] == 0xFA.toByte() && (header[3] == 0xCE.toByte() || header[3] == 0xCF.toByte()) ||
                    read >= 4 && header[0] == 0xCF.toByte() && header[1] == 0xFA.toByte() &&
                    header[2] == 0xED.toByte() && header[3] == 0xFE.toByte()
        }
    }.getOrDefault(false)
}

private fun String.nodeDependencyBundlerPathReferenceIssueCode(): String? {
    val target = trim().removePrefix("!").trim()
    if (target.isEmpty()) {
        return null
    }
    if (target.contains('\u0000')) {
        return "PACKAGE_NUL_PATH_REFERENCE"
    }
    if (target.startsWith("/") ||
        target.startsWith("\\") ||
        nodeDependencyBundlerProtocolOrWindowsDrivePattern.containsMatchIn(target)
    ) {
        return "PACKAGE_ABSOLUTE_PATH_REFERENCE"
    }
    val segments = target.replace('\\', '/')
        .split('/')
        .filter { it.isNotEmpty() && it != "." }
    return if (".." in segments) {
        "PACKAGE_PATH_ESCAPE_REFERENCE"
    } else {
        null
    }
}

private fun nodeDependencyBundlerAuditPackageJsonPathReferences(
    packageJson: File,
    json: Map<*, *>,
    issue: (String, File, String) -> Unit,
) {
    fun auditValue(fieldPath: String, value: Any?) {
        when (value) {
            is String -> {
                val code = value.nodeDependencyBundlerPathReferenceIssueCode() ?: return
                issue(code, packageJson, "package.json field '$fieldPath' contains unsafe path reference '$value'.")
            }
            is Map<*, *> -> value.forEach { (key, child) -> auditValue("$fieldPath.$key", child) }
            is Iterable<*> -> value.forEachIndexed { index, child -> auditValue("$fieldPath[$index]", child) }
        }
    }

    nodeDependencyBundlerPathReferenceFields.forEach { field ->
        if (json.containsKey(field)) {
            auditValue(field, json[field])
        }
    }
}

private fun nodeDependencyBundlerReadJsonObject(file: File): Map<*, *>? =
    runCatching { JsonSlurper().parse(file) as? Map<*, *> }.getOrNull()

private fun nodeDependencyBundlerLockPackageEntries(projectRoot: File): Map<String, Map<*, *>> {
    val lock = nodeDependencyBundlerReadJsonObject(projectRoot.resolve("package-lock.json")) ?: return emptyMap()
    val packages = lock["packages"] as? Map<*, *> ?: return emptyMap()
    return packages.mapNotNull { (key, value) ->
        val path = key?.toString() ?: return@mapNotNull null
        val entry = value as? Map<*, *> ?: return@mapNotNull null
        path to entry
    }.toMap()
}

private fun nodeDependencyBundlerUnsupportedCapabilities(packageDir: File, packageJson: Map<*, *>): List<String> {
    val capabilities = linkedSetOf<String>()
    val scripts = packageJson["scripts"] as? Map<*, *>
    if (nodeDependencyBundlerForbiddenScripts.any { scripts?.containsKey(it) == true }) {
        capabilities += "lifecycle_scripts"
    }
    if (packageJson["type"] == "module") {
        capabilities += "esm_only"
    }
    if (packageJson.containsKey("bin")) {
        capabilities += "bin_entries"
    }

    val requirePattern = Regex("""require\s*\(\s*["'](?:node:)?([^"']+)["']\s*\)""")
    val importPattern = Regex("""(?:from|import)\s*["'](?:node:)?([^"']+)["']""")
    val jsExtensions = setOf("js", "cjs", "mjs")
    if (packageDir.isDirectory) {
        Files.walk(packageDir.toPath()).use { stream ->
            stream
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { it.toFile() }
                .filter { it.extension.lowercase() in jsExtensions }
                .forEach { file ->
                    val text = runCatching { file.readText() }.getOrDefault("")
                    if (text.contains("process.binding") || text.contains("process.dlopen")) {
                        capabilities += "native_process_api"
                    }
                    (requirePattern.findAll(text).map { it.groupValues[1] } +
                            importPattern.findAll(text).map { it.groupValues[1] })
                        .forEach { moduleName ->
                            nodeDependencyBundlerCapabilityBuiltins.forEach { (capability, modules) ->
                                if (moduleName in modules) {
                                    capabilities += capability
                                }
                            }
                        }
                    if (text.contains(".node")) {
                        capabilities += "native_addon"
                    }
                }
        }
    }
    return capabilities.sorted()
}

private fun nodeDependencyBundlerCompatibilityScore(
    packageInfoIssues: Int,
    unsupportedCapabilities: List<String>,
    license: String,
): Int {
    var score = 100
    score -= unsupportedCapabilities.size * 15
    score -= packageInfoIssues * 20
    if (license.isBlank()) {
        score -= 5
    }
    return score.coerceIn(0, 100)
}

private fun nodeDependencyCompatibilityNormalizeCapability(value: Any?): String {
    val normalized = value?.toString().orEmpty().trim().lowercase().replace(':', '.')
    return nodeDependencyCompatibilityCapabilityAliases[normalized] ?: normalized
}

private fun nodeDependencyCompatibilityDeclaredCapabilities(projectRoot: File): Set<String> {
    val values = linkedSetOf<String>()
    fun addPermissions(value: Any?) {
        (value as? Iterable<*>)?.forEach { item ->
            nodeDependencyCompatibilityNormalizeCapability(item)
                .takeIf(String::isNotBlank)
                ?.let(values::add)
        }
    }

    val projectJson = nodeDependencyBundlerReadJsonObject(projectRoot.resolve("project.json"))
    val projectNode = projectJson?.get("node") as? Map<*, *>
    addPermissions(projectNode?.get("permissions"))
    addPermissions(projectNode?.get("bridgePermissions"))

    val packageJson = nodeDependencyBundlerReadJsonObject(projectRoot.resolve("package.json"))
    val autojs6 = packageJson?.get("autojs6") as? Map<*, *>
    addPermissions(autojs6?.get("permissions"))
    addPermissions(autojs6?.get("bridgePermissions"))
    val packageNode = autojs6?.get("node") as? Map<*, *>
    addPermissions(packageNode?.get("permissions"))
    addPermissions(packageNode?.get("bridgePermissions"))
    return values
}

private fun nodeDependencyCompatibilityHint(code: String, status: String): String = when {
    code == "CAPABILITY_DECLARATION_MISSING" ->
        "Declare the required Safe Node Profile capability in project.json or package.json."
    code == "ESM_ENTRY_PARTIAL" || code == "PACKAGE_EXPORTS_CONDITIONS_PARTIAL" ->
        "Keep this project on the v1.1 partial support gate and retain CommonJS fallback coverage where possible."
    status == "unsafe" ->
        "Remove the unsafe native/process/fs behavior before packaging."
    status == "unsupported" ->
        "Replace this dependency or API use with a Safe Node Profile supported surface."
    else ->
        "No action required unless the target gate disallows this compatibility level."
}

private fun nodeDependencyCompatibilitySignal(
    category: String,
    status: String,
    code: String,
    file: String,
    message: String,
    extra: Map<String, Any?> = emptyMap(),
): Map<String, Any?> =
    linkedMapOf<String, Any?>(
        "category" to category,
        "status" to status,
        "code" to code,
        "file" to file,
        "message" to message,
        "hint" to nodeDependencyCompatibilityHint(code, status),
    ).apply { putAll(extra) }

private fun nodeDependencyCompatibilityWorseStatus(left: String, right: String): String =
    if ((nodeDependencyCompatibilityStatusRank[right] ?: 0) > (nodeDependencyCompatibilityStatusRank[left] ?: 0)) {
        right
    } else {
        left
    }

private fun nodeDependencyCompatibilityStatusForIssue(code: String): String = when (code) {
    "LIFECYCLE_SCRIPT_DISABLED",
    "NATIVE_ADDON_DISABLED",
    "PACKAGE_ABSOLUTE_PATH_REFERENCE",
    "PACKAGE_NUL_PATH_REFERENCE",
    "PACKAGE_PATH_ESCAPE_REFERENCE",
    "PATH_ESCAPE",
    "POSTINSTALL_ARTIFACT_UNSUPPORTED",
    "SUSPICIOUS_EXECUTABLE_UNSUPPORTED",
    "SYMLINK_UNSUPPORTED",
    -> "unsafe"
    "ESM_ONLY_PACKAGE_UNSUPPORTED" -> "experimental"
    else -> "unsupported"
}

private fun nodeDependencyCompatibilityStatusForCapability(capability: String): String = when (capability) {
    "lifecycle_scripts",
    "native_addon",
    "native_process_api",
    "java_interop",
    -> "unsafe"
    "worker_threads",
    "esm_only",
    -> "experimental"
    "raw_network",
    "child_process",
    "vm",
    "inspector",
    "bin_entries",
    -> "unsupported"
    else -> "compatible with capability"
}

private fun nodeDependencyCompatibilityPackageExportConditions(value: Any?, output: MutableSet<String>) {
    when (value) {
        is Map<*, *> -> value.forEach { (key, child) ->
            val name = key?.toString().orEmpty()
            if (name.isNotBlank() && !name.startsWith(".") && !name.startsWith("#") && name != "types") {
                output += name
            }
            nodeDependencyCompatibilityPackageExportConditions(child, output)
        }
        is Iterable<*> -> value.forEach { nodeDependencyCompatibilityPackageExportConditions(it, output) }
    }
}

private fun nodeDependencyCompatibilitySourceFiles(projectRoot: File): List<File> {
    if (!projectRoot.isDirectory) {
        return emptyList()
    }
    return Files.walk(projectRoot.toPath()).use { stream ->
        stream
            .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .map { it.toFile() }
            .filter { it.extension.lowercase() in nodeDependencyCompatibilitySourceExtensions }
            .limit(5000)
            .toList()
            .sortedBy { file -> runCatching { file.nodeDependencyBundlerRelativeTo(projectRoot) }.getOrDefault(file.path) }
    }
}

private fun nodeDependencyCompatibilityReadSource(file: File): String =
    runCatching {
        file.inputStream().use { input ->
            input.readNBytes(nodeDependencyCompatibilityMaxSourceBytes).toString(Charsets.UTF_8)
        }
    }.getOrDefault("")

private fun nodeDependencyCompatibilityAddSet(
    map: MutableMap<String, MutableSet<String>>,
    key: String,
    value: String,
) {
    map.getOrPut(key) { linkedSetOf() } += value
}

private fun nodeDependencyCompatibilityAnalyzeSource(
    projectRoot: File,
    file: File,
    requiredBuiltins: MutableMap<String, MutableSet<String>>,
    capabilityRequirements: MutableMap<String, MutableSet<String>>,
    signals: MutableList<Map<String, Any?>>,
) {
    val text = nodeDependencyCompatibilityReadSource(file)
    val relativePath = runCatching { file.nodeDependencyBundlerRelativeTo(projectRoot) }.getOrDefault(file.path)
    val moduleNames = linkedSetOf<String>()
    val requirePattern = Regex("""require\s*\(\s*["'](?:node:)?([^"']+)["']\s*\)""")
    val importPattern = Regex("""(?:from|import)\s*["'](?:node:)?([^"']+)["']""")
    requirePattern.findAll(text).forEach { moduleNames += it.groupValues[1] }
    importPattern.findAll(text).forEach { moduleNames += it.groupValues[1] }

    moduleNames.forEach { moduleName ->
        if (moduleName in nodeDependencyCompatibilityKnownBuiltins ||
            nodeDependencyCompatibilityBuiltinCapabilities.containsKey(moduleName)
        ) {
            nodeDependencyCompatibilityAddSet(requiredBuiltins, moduleName, relativePath)
        }
        nodeDependencyCompatibilityBuiltinCapabilities[moduleName]?.let { capability ->
            nodeDependencyCompatibilityAddSet(capabilityRequirements, capability, moduleName)
        }
        nodeDependencyCompatibilityBridgeCapabilities[moduleName]?.let { capability ->
            nodeDependencyCompatibilityAddSet(capabilityRequirements, capability, moduleName)
        }
    }
    if (Regex("""\bfetch\s*\(""").containsMatchIn(text)) {
        nodeDependencyCompatibilityAddSet(capabilityRequirements, "network", "fetch")
    }
    if (Regex("""\bprocess\.(binding|dlopen)\s*\(""").containsMatchIn(text)) {
        signals += nodeDependencyCompatibilitySignal(
            "native-process-api",
            "unsafe",
            "PROCESS_NATIVE_API_DISABLED",
            relativePath,
            "process.binding/process.dlopen are disabled.",
        )
    }
    if (Regex("""\.node["']""").containsMatchIn(text)) {
        signals += nodeDependencyCompatibilitySignal(
            "native-addon",
            "unsafe",
            "NATIVE_ADDON_DISABLED",
            relativePath,
            "Source references a native .node addon.",
        )
    }
    if (Regex("""(?:\b(?:fs|fsp)\s*|require\s*\(\s*["'](?:node:)?fs(?:/promises)?["']\s*\))\.\s*(?:readFile|readFileSync|writeFile|writeFileSync|open|openSync|mkdir|mkdirSync|readdir|readdirSync|stat|statSync)\s*\(\s*["'](?:/|\\|[A-Za-z]:|\.\./|\.\.\\)""").containsMatchIn(text)) {
        signals += nodeDependencyCompatibilitySignal(
            "fs-scope",
            "unsafe",
            "FS_OUTSIDE_SCOPE",
            relativePath,
            "Source contains a direct fs call with an absolute or escaping literal path.",
        )
    }
}

private fun nodeDependencyCompatibilityEntry(
    projectRoot: File,
    signals: MutableList<Map<String, Any?>>,
): Map<String, Any?> {
    val projectJson = nodeDependencyBundlerReadJsonObject(projectRoot.resolve("project.json"))
    val packageJson = nodeDependencyBundlerReadJsonObject(projectRoot.resolve("package.json"))
    val entry = projectJson?.get("main")?.toString()?.takeIf(String::isNotBlank)
        ?: packageJson?.get("main")?.toString()?.takeIf(String::isNotBlank)
        ?: ""
    val extension = File(entry).extension.lowercase().let { if (it.isBlank()) "" else ".$it" }
    val packageType = if (packageJson?.get("type") == "module") "module" else "commonjs"
    val format = when {
        extension == ".mjs" || (extension == ".js" && packageType == "module") -> "esm"
        extension == ".cjs" || extension == ".js" -> "cjs"
        else -> "unknown"
    }
    if (format == "esm") {
        signals += nodeDependencyCompatibilitySignal(
            "module-format",
            "experimental",
            "ESM_ENTRY_PARTIAL",
            entry.ifBlank { projectRoot.path },
            "ESM entry support is partial in Node Profile v1.1.",
        )
    }
    return linkedMapOf(
        "path" to entry,
        "extension" to extension,
        "packageType" to packageType,
        "format" to format,
    )
}

private fun nodeDependencyCompatibilityReport(
    projectRoot: File,
    scan: NodeDependencyBundleScanResult,
    policy: NodeDependencyBundlePolicy,
): Map<String, Any?> {
    val signals = mutableListOf<Map<String, Any?>>()
    val declaredCapabilities = nodeDependencyCompatibilityDeclaredCapabilities(projectRoot)
    val requiredBuiltins = linkedMapOf<String, MutableSet<String>>()
    val capabilityRequirements = linkedMapOf<String, MutableSet<String>>()
    val entry = nodeDependencyCompatibilityEntry(projectRoot, signals)
    val sourceFiles = nodeDependencyCompatibilitySourceFiles(projectRoot)
    sourceFiles.forEach { file ->
        nodeDependencyCompatibilityAnalyzeSource(projectRoot, file, requiredBuiltins, capabilityRequirements, signals)
    }
    if (sourceFiles.size >= 5000) {
        signals += nodeDependencyCompatibilitySignal(
            "graph-size",
            "unsupported",
            "DEPENDENCY_BUNDLE_TOO_LARGE",
            projectRoot.path,
            "Project source scan reached the compatibility analyzer entry budget.",
        )
    }

    scan.issues.forEach { issue ->
        signals += nodeDependencyCompatibilitySignal(
            category = "sanitizer",
            status = nodeDependencyCompatibilityStatusForIssue(issue.code),
            code = issue.code,
            file = issue.path,
            message = issue.message,
        )
    }
    scan.packages.forEach { pkg ->
        pkg.unsupportedCapabilities.forEach { capability ->
            signals += nodeDependencyCompatibilitySignal(
                category = "package-capability",
                status = nodeDependencyCompatibilityStatusForCapability(capability),
                code = "PACKAGE_CAPABILITY_${capability.uppercase()}",
                file = pkg.relativePath,
                message = "Package ${pkg.name}@${pkg.version} requires unsupported capability '$capability'.",
                extra = mapOf("capability" to capability, "package" to pkg.name, "version" to pkg.version),
            )
        }
    }

    val packageFormats = linkedMapOf<String, Int>()
    var packageExportsCount = 0
    var packageImportsCount = 0
    val packageExportConditions = linkedSetOf<String>()
    scan.packages.forEach { pkg ->
        val packageJson = nodeDependencyBundlerReadJsonObject(projectRoot.resolve(pkg.relativePath).resolve("package.json"))
        val format = if (packageJson?.get("type") == "module") "esm" else "cjs"
        packageFormats[format] = (packageFormats[format] ?: 0) + 1
        if (packageJson?.containsKey("exports") == true) {
            packageExportsCount += 1
            nodeDependencyCompatibilityPackageExportConditions(packageJson["exports"], packageExportConditions)
        }
        if (packageJson?.containsKey("imports") == true) {
            packageImportsCount += 1
            nodeDependencyCompatibilityPackageExportConditions(packageJson["imports"], packageExportConditions)
        }
    }
    if (packageExportsCount > 0 || packageImportsCount > 0) {
        signals += nodeDependencyCompatibilitySignal(
            "package-exports",
            "experimental",
            "PACKAGE_EXPORTS_CONDITIONS_PARTIAL",
            "node_modules",
            "Dependency graph uses package exports/imports conditions covered by the v1.1 partial resolver gate.",
        )
    }
    if (scan.totalBytes > policy.maxTotalBytes || scan.packages.size > policy.maxPackageCount) {
        signals += nodeDependencyCompatibilitySignal(
            "graph-size",
            "unsupported",
            "DEPENDENCY_BUNDLE_TOO_LARGE",
            "node_modules",
            "Dependency graph exceeds the configured packaged graph budget.",
        )
    }

    capabilityRequirements.forEach { (capability, modules) ->
        val declared = capability in declaredCapabilities
        val baseStatus = nodeDependencyCompatibilityStatusForCapability(capability)
        val status = if (!declared && baseStatus == "compatible with capability") "unsupported" else baseStatus
        signals += nodeDependencyCompatibilitySignal(
            category = "capability",
            status = status,
            code = if (declared) "CAPABILITY_REQUIRED" else "CAPABILITY_DECLARATION_MISSING",
            file = projectRoot.path,
            message = "Project requires capability '$capability' from ${modules.sorted().joinToString(", ")}.",
            extra = mapOf(
                "capability" to capability,
                "declared" to declared,
                "modules" to modules.sorted(),
            ),
        )
    }

    val status = signals.fold("compatible") { current, signal ->
        nodeDependencyCompatibilityWorseStatus(current, signal["status"].toString())
    }
    val sortedSignals = signals.sortedWith(
        compareByDescending<Map<String, Any?>> { nodeDependencyCompatibilityStatusRank[it["status"].toString()] ?: 0 }
            .thenBy { it["code"].toString() }
            .thenBy { it["file"].toString() }
    )
    return linkedMapOf(
        "schema" to "autojs6-node-dependency-compatibility-v1",
        "status" to status,
        "entry" to entry,
        "declaredCapabilities" to declaredCapabilities.sorted(),
        "requiredBuiltins" to requiredBuiltins
            .map { (name, files) -> linkedMapOf("name" to name, "files" to files.sorted()) }
            .sortedBy { it["name"].toString() },
        "capabilityRequirements" to capabilityRequirements
            .map { (capability, modules) ->
                linkedMapOf(
                    "capability" to capability,
                    "declared" to (capability in declaredCapabilities),
                    "modules" to modules.sorted(),
                )
            }
            .sortedBy { it["capability"].toString() },
        "dependencyGraph" to linkedMapOf(
            "installed" to projectRoot.resolve("node_modules").isDirectory,
            "packageCount" to scan.packages.size,
            "fileCount" to scan.totalFiles,
            "totalBytes" to scan.totalBytes,
            "packageFormats" to packageFormats.toSortedMap(),
            "packageExportsCount" to packageExportsCount,
            "packageImportsCount" to packageImportsCount,
            "packageExportConditions" to packageExportConditions.sorted(),
            "expectedPackagedGraphSize" to linkedMapOf(
                "maxTotalBytes" to policy.maxTotalBytes,
                "maxPackageCount" to policy.maxPackageCount,
                "withinBudget" to (scan.totalBytes <= policy.maxTotalBytes && scan.packages.size <= policy.maxPackageCount),
            ),
        ),
        "apkBuilder" to linkedMapOf(
            "gate" to "validate-before-directory-packaging",
            "packagedAssetRoot" to "assets/project",
            "reportFile" to "dependency-compatibility.json",
        ),
        "signals" to sortedSignals,
    )
}

private fun nodeDependencyBundlerPackageSize(packageDir: File): Pair<Int, Long> {
    var count = 0
    var bytes = 0L
    Files.walk(packageDir.toPath()).use { stream ->
        stream
            .filter { Files.isRegularFile(it) }
            .forEach { path ->
                count += 1
                bytes += path.toFile().length()
            }
    }
    return count to bytes
}

private fun nodeDependencyBundlerPolicy(): NodeDependencyBundlePolicy =
    NodeDependencyBundlePolicy(
        maxFileBytes = providers.gradleProperty(nodeDependencyBundlerMaxFileBytesProperty)
            .orElse((2L * 1024L * 1024L).toString())
            .get()
            .toLong(),
        maxPackageBytes = providers.gradleProperty(nodeDependencyBundlerMaxPackageBytesProperty)
            .orElse((5L * 1024L * 1024L).toString())
            .get()
            .toLong(),
        maxTotalBytes = providers.gradleProperty(nodeDependencyBundlerMaxTotalBytesProperty)
            .orElse((25L * 1024L * 1024L).toString())
            .get()
            .toLong(),
        maxPackageCount = providers.gradleProperty(nodeDependencyBundlerMaxPackageCountProperty)
            .orElse("200")
            .get()
            .toInt(),
        allowSuspiciousExecutables = providers.gradleProperty(nodeDependencyBundlerAllowSuspiciousExecutablesProperty)
            .orElse("false")
            .get()
            .trim()
            .let { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) },
    )

private fun scanNodeDependencyBundle(projectRoot: File, policy: NodeDependencyBundlePolicy): NodeDependencyBundleScanResult {
    val root = projectRoot.canonicalFile
    val rootPackageJson = root.resolve("package.json")
    val nodeModules = root.resolve("node_modules")
    val packageLockEntries = nodeDependencyBundlerLockPackageEntries(root)
    val issues = mutableListOf<NodeDependencyBundleIssue>()
    val packages = mutableListOf<NodeDependencyPackageInfo>()

    fun issue(code: String, file: File, message: String) {
        val path = runCatching { file.nodeDependencyBundlerRelativeTo(root) }.getOrDefault(file.path)
        issues += NodeDependencyBundleIssue(code, path, message)
    }

    if (!rootPackageJson.isFile) {
        issue("PROJECT_PACKAGE_JSON_MISSING", rootPackageJson, "Project root must contain package.json.")
    }
    if (!nodeModules.isDirectory) {
        issue("NODE_MODULES_MISSING", nodeModules, "Project root must contain node_modules.")
    }
    val missingRequiredInputIssues = issues.size
    listOf(".pnp.cjs", ".pnp.loader.mjs", ".yarn/install-state.gz").forEach { relativePath ->
        val marker = root.resolve(relativePath)
        if (marker.exists()) {
            issue("UNSUPPORTED_PACKAGE_MANAGER_LAYOUT", marker, "Yarn Plug'n'Play/package-manager layout is not supported.")
        }
    }
    listOf(".pnpm", ".store").forEach { relativePath ->
        val marker = nodeModules.resolve(relativePath)
        if (marker.exists()) {
            issue("UNSUPPORTED_PACKAGE_MANAGER_LAYOUT", marker, "Non-flat package-manager node_modules layout is not supported.")
        }
    }
    if (missingRequiredInputIssues > 0) {
        return NodeDependencyBundleScanResult(
            emptyList(),
            0,
            0L,
            bundleHash = runCatching { nodeDependencyBundlerSanitizedBundleHash(root) }.getOrDefault(""),
            issues = issues,
        )
    }

    var totalFiles = 0
    var totalBytes = 0L
    fun auditPackageJson(packageJson: File, includePackageInfo: Boolean) {
        val packageIssueStart = issues.size
        val json = nodeDependencyBundlerReadJsonObject(packageJson)
        if (json == null) {
            issue("PACKAGE_JSON_INVALID", packageJson, "Unable to parse package.json.")
            return
        }
        val scripts = json["scripts"] as? Map<*, *>
        nodeDependencyBundlerForbiddenScripts.forEach { scriptName ->
            if (scripts?.containsKey(scriptName) == true) {
                issue("LIFECYCLE_SCRIPT_DISABLED", packageJson, "Lifecycle script '$scriptName' is not allowed.")
            }
        }
        if (json["type"] == "module") {
            issue("ESM_ONLY_PACKAGE_UNSUPPORTED", packageJson, "ESM-only package metadata is not supported by the default profile.")
        }
        nodeDependencyBundlerAuditPackageJsonPathReferences(packageJson, json, ::issue)

        if (!includePackageInfo) {
            return
        }

        val packageDir = packageJson.parentFile
        val (fileCount, sizeBytes) = nodeDependencyBundlerPackageSize(packageDir)
        if (sizeBytes > policy.maxPackageBytes) {
            issue("PACKAGE_SIZE_LIMIT", packageDir, "Package exceeds maxPackageBytes=${policy.maxPackageBytes}.")
        }
        val relativePath = packageDir.nodeDependencyBundlerRelativeTo(root)
        val contentSha256 = nodeDependencyBundlerPackageContentSha256(packageDir)
        val lockIntegrity = packageLockEntries[relativePath]?.get("integrity")?.toString()?.takeIf(String::isNotBlank)
        val unsupportedCapabilities = nodeDependencyBundlerUnsupportedCapabilities(packageDir, json)
        packages += NodeDependencyPackageInfo(
            name = json["name"]?.toString().orEmpty(),
            version = json["version"]?.toString().orEmpty(),
            license = json["license"]?.toString().orEmpty(),
            relativePath = relativePath,
            packageJsonSha256 = packageJson.nodeDependencyBundlerSha256(),
            contentSha256 = contentSha256,
            integrity = lockIntegrity ?: "sha256:$contentSha256",
            fileCount = fileCount,
            sizeBytes = sizeBytes,
            unsupportedCapabilities = unsupportedCapabilities,
            autoJs6CompatibilityScore = nodeDependencyBundlerCompatibilityScore(
                packageInfoIssues = issues.size - packageIssueStart,
                unsupportedCapabilities = unsupportedCapabilities,
                license = json["license"]?.toString().orEmpty(),
            ),
        )
    }

    auditPackageJson(rootPackageJson, includePackageInfo = false)

    Files.walk(nodeModules.toPath()).use { stream ->
        stream.forEach { path ->
            val file = path.toFile()
            if (!file.nodeDependencyBundlerCanonicalInside(root)) {
                issue("PATH_ESCAPE", file, "Dependency entry resolves outside the project root.")
                return@forEach
            }
            if (Files.isSymbolicLink(path)) {
                issue("SYMLINK_UNSUPPORTED", file, "Symlink entries are not copied into sanitized bundles.")
                return@forEach
            }
            if (!file.isFile) {
                return@forEach
            }
            totalFiles += 1
            totalBytes += file.length()
            if (file.length() > policy.maxFileBytes) {
                issue("FILE_SIZE_LIMIT", file, "File exceeds maxFileBytes=${policy.maxFileBytes}.")
            }
            val relativePath = file.nodeDependencyBundlerRelativeTo(root)
            if (file.name.endsWith(".node", ignoreCase = true)) {
                issue("NATIVE_ADDON_DISABLED", file, "Native addon files are not part of Safe Node Profile.")
            }
            if (relativePath.nodeDependencyBundlerIsNodeGypArtifact()) {
                issue("NODE_GYP_ARTIFACT_UNSUPPORTED", file, "node-gyp build artifacts are not bundled by Safe Node Profile.")
            }
            if (relativePath.nodeDependencyBundlerIsDotBinEntry()) {
                issue("BIN_ENTRY_UNSUPPORTED", file, "Unchecked node_modules/.bin entries are not bundled.")
            }
            if (relativePath.nodeDependencyBundlerIsPostinstallArtifact()) {
                issue("POSTINSTALL_ARTIFACT_UNSUPPORTED", file, "npm install/postinstall cache and log artifacts are not bundled.")
            }
            if (!policy.allowSuspiciousExecutables && file.nodeDependencyBundlerIsSuspiciousExecutable()) {
                issue("SUSPICIOUS_EXECUTABLE_UNSUPPORTED", file, "Executable or native binary files are not bundled by Safe Node Profile.")
            }
        }
    }

    nodeModules.walkTopDown()
        .filter { it.isFile && it.name == "package.json" }
        .forEach { auditPackageJson(it, includePackageInfo = true) }

    if (packages.size > policy.maxPackageCount) {
        issues += NodeDependencyBundleIssue(
            code = "PACKAGE_COUNT_LIMIT",
            path = "node_modules",
            message = "Package count ${packages.size} exceeds maxPackageCount=${policy.maxPackageCount}.",
        )
    }
    if (totalBytes > policy.maxTotalBytes) {
        issues += NodeDependencyBundleIssue(
            code = "TOTAL_SIZE_LIMIT",
            path = "node_modules",
            message = "Dependency tree size $totalBytes exceeds maxTotalBytes=${policy.maxTotalBytes}.",
        )
    }

    return NodeDependencyBundleScanResult(
        packages = packages.sortedBy { it.relativePath },
        totalFiles = totalFiles,
        totalBytes = totalBytes,
        bundleHash = nodeDependencyBundlerSanitizedBundleHash(root),
        issues = issues,
    )
}

private fun writeNodeDependencyBundleReports(
    projectRoot: File,
    bundleDir: File,
    reportDir: File,
    policy: NodeDependencyBundlePolicy,
    scan: NodeDependencyBundleScanResult,
    installMetadata: Map<String, Any?> = emptyMap(),
    cacheInfo: Map<String, Any?> = emptyMap(),
) {
    reportDir.mkdirs()
    val capabilitySummary = scan.packages
        .flatMap { pkg -> pkg.unsupportedCapabilities }
        .groupingBy { it }
        .eachCount()
        .toSortedMap()
    val compatibilityScore = scan.packages
        .map { it.autoJs6CompatibilityScore }
        .minOrNull()
        ?: 100
    val compatibilityReport = nodeDependencyCompatibilityReport(projectRoot, scan, policy)
    val manifest = linkedMapOf<String, Any?>(
        "schema" to "autojs6-node-dependency-bundle-v1",
        "projectRoot" to projectRoot.canonicalPath,
        "sanitizedBundle" to bundleDir.canonicalPath,
        "bundle" to linkedMapOf(
            "hashAlgorithm" to "sha256-normalized-sanitized-project-v1",
            "hash" to scan.bundleHash,
        ),
        "cache" to cacheInfo.takeIf { it.isNotEmpty() },
        "policy" to linkedMapOf(
            "maxFileBytes" to policy.maxFileBytes,
            "maxPackageBytes" to policy.maxPackageBytes,
            "maxTotalBytes" to policy.maxTotalBytes,
            "maxPackageCount" to policy.maxPackageCount,
            "allowSuspiciousExecutables" to policy.allowSuspiciousExecutables,
        ),
        "sbomLite" to "dependency-sbom-lite.json",
        "install" to installMetadata.takeIf { it.isNotEmpty() },
        "packagedLayout" to linkedMapOf(
            "sanitized" to scan.issues.isEmpty(),
            "assetRoot" to "assets/project",
            "packageJson" to "assets/project/package.json",
            "nodeModules" to "assets/project/node_modules",
        ),
        "totals" to linkedMapOf(
            "packages" to scan.packages.size,
            "files" to scan.totalFiles,
            "bytes" to scan.totalBytes,
        ),
        "unsupportedCapabilityUsage" to capabilitySummary,
        "autoJs6Compatibility" to linkedMapOf(
            "score" to compatibilityScore,
            "packageScoreMode" to "minimum",
        ),
        "dependencyCompatibility" to linkedMapOf(
            "schema" to compatibilityReport["schema"],
            "status" to compatibilityReport["status"],
            "report" to "dependency-compatibility.json",
            "apkBuilderGate" to (compatibilityReport["apkBuilder"] as? Map<*, *>)?.get("gate"),
        ),
        "packages" to scan.packages.map {
            linkedMapOf(
                "name" to it.name,
                "version" to it.version,
                "license" to it.license,
                "relativePath" to it.relativePath,
                "packageJsonSha256" to it.packageJsonSha256,
                "contentSha256" to it.contentSha256,
                "integrity" to it.integrity,
                "fileCount" to it.fileCount,
                "sizeBytes" to it.sizeBytes,
                "unsupportedCapabilities" to it.unsupportedCapabilities,
                "autoJs6CompatibilityScore" to it.autoJs6CompatibilityScore,
            )
        },
        "issues" to scan.issues.map {
            linkedMapOf(
                "code" to it.code,
                "path" to it.path,
                "message" to it.message,
            )
        },
    )
    reportDir.resolve("dependency-manifest.json").writeText(nodeDependencyBundlerJson(manifest) + "\n")
    val sbomLite = linkedMapOf<String, Any?>(
        "schema" to "autojs6-node-dependency-sbom-lite-v1",
        "project" to linkedMapOf(
            "name" to nodeDependencyBundlerReadJsonObject(projectRoot.resolve("package.json"))?.get("name")?.toString().orEmpty(),
            "version" to nodeDependencyBundlerReadJsonObject(projectRoot.resolve("package.json"))?.get("version")?.toString().orEmpty(),
        ),
        "totals" to linkedMapOf(
            "packages" to scan.packages.size,
            "files" to scan.totalFiles,
            "bytes" to scan.totalBytes,
        ),
        "bundleHash" to scan.bundleHash,
        "packages" to scan.packages.map {
            linkedMapOf(
                "name" to it.name,
                "version" to it.version,
                "integrity" to it.integrity,
                "license" to it.license.ifBlank { "UNKNOWN" },
                "fileCount" to it.fileCount,
                "totalSizeBytes" to it.sizeBytes,
                "relativePath" to it.relativePath,
                "unsupportedCapabilities" to it.unsupportedCapabilities,
                "autoJs6CompatibilityScore" to it.autoJs6CompatibilityScore,
            )
        },
    )
    reportDir.resolve("dependency-sbom-lite.json").writeText(nodeDependencyBundlerJson(sbomLite) + "\n")
    reportDir.resolve("dependency-compatibility.json").writeText(nodeDependencyBundlerJson(compatibilityReport) + "\n")
    reportDir.resolve("dependency-size-report.txt").writeText(
        buildString {
            appendLine("AutoJs6 Node Dependency Bundle Size Report")
            appendLine("projectRoot=${projectRoot.canonicalPath}")
            appendLine("sanitizedBundle=${bundleDir.canonicalPath}")
            appendLine("bundleHash=${scan.bundleHash}")
            appendLine("cacheKey=${cacheInfo["cacheKey"] ?: "unavailable"}")
            appendLine("packages=${scan.packages.size}")
            appendLine("files=${scan.totalFiles}")
            appendLine("bytes=${scan.totalBytes}")
            scan.packages.forEach { pkg ->
                appendLine(
                    "${pkg.relativePath} files=${pkg.fileCount} bytes=${pkg.sizeBytes} " +
                            "license=${pkg.license.ifBlank { "unknown" }} score=${pkg.autoJs6CompatibilityScore} " +
                            "integrity=${pkg.integrity}"
                )
            }
        }
    )
    reportDir.resolve("dependency-rejections.txt").writeText(
        buildString {
            if (scan.issues.isEmpty()) {
                appendLine("No dependency bundle policy issues.")
            } else {
                scan.issues.forEach { issue ->
                    appendLine("${issue.code}: ${issue.path}: ${issue.message}")
                }
            }
        }
    )
}

private fun verifyNodeDependencyBundlerPackagedLayout(
    bundleDir: File,
    reportDir: File,
    policy: NodeDependencyBundlePolicy,
) {
    val projectDir = bundleDir.resolve("project").canonicalFile
    val scan = scanNodeDependencyBundle(projectDir, policy)
    if (scan.issues.isNotEmpty()) {
        throw GradleException("Sanitized dependency bundle is not packaged-safe: ${scan.issues}")
    }
    val manifestFile = reportDir.resolve("dependency-manifest.json")
    val sbomFile = reportDir.resolve("dependency-sbom-lite.json")
    val compatibilityFile = reportDir.resolve("dependency-compatibility.json")
    if (!manifestFile.isFile || !sbomFile.isFile || !compatibilityFile.isFile) {
        throw GradleException("Dependency bundler did not write manifest/SBOM-lite/compatibility reports.")
    }
    val manifest = nodeDependencyBundlerReadJsonObject(manifestFile)
    val packagedLayout = manifest?.get("packagedLayout") as? Map<*, *>
    if (packagedLayout?.get("sanitized") != true ||
        packagedLayout["assetRoot"] != "assets/project" ||
        packagedLayout["nodeModules"] != "assets/project/node_modules"
    ) {
        throw GradleException("Dependency manifest does not record a sanitized packaged APK layout: $packagedLayout")
    }
    val bundle = manifest["bundle"] as? Map<*, *>
    val bundleHash = bundle?.get("hash")?.toString().orEmpty()
    if (bundleHash.isBlank()) {
        throw GradleException("Dependency manifest does not record a deterministic bundle hash: $bundle")
    }
    if (bundleHash != scan.bundleHash) {
        throw GradleException("Dependency bundle hash mismatch. manifest=$bundleHash actual=${scan.bundleHash}")
    }
    val cache = manifest["cache"] as? Map<*, *>
    if (cache?.get("cacheKey") != bundleHash) {
        throw GradleException("Dependency manifest cache key does not match bundle hash: $cache")
    }
    val cacheProjectDir = cache?.get("projectDir")?.toString()?.let(::File)
    if (cacheProjectDir?.isDirectory != true) {
        throw GradleException("Dependency cache project directory is missing: ${cacheProjectDir?.absolutePath}")
    }
    val sbom = nodeDependencyBundlerReadJsonObject(sbomFile)
    if (sbom?.get("schema") != "autojs6-node-dependency-sbom-lite-v1") {
        throw GradleException("Dependency SBOM-lite schema missing or invalid: ${sbom?.get("schema")}")
    }
    val compatibility = nodeDependencyBundlerReadJsonObject(compatibilityFile)
    if (compatibility?.get("schema") != "autojs6-node-dependency-compatibility-v1") {
        throw GradleException("Dependency compatibility analyzer schema missing or invalid: ${compatibility?.get("schema")}")
    }
    val compatibilityStatus = compatibility["status"]?.toString().orEmpty()
    if (compatibilityStatus in setOf("unsafe", "unsupported")) {
        throw GradleException("Dependency compatibility analyzer reported a blocking status: $compatibilityStatus")
    }
    val manifestCompatibility = manifest["dependencyCompatibility"] as? Map<*, *>
    if (manifestCompatibility?.get("status") != compatibilityStatus ||
        manifestCompatibility["report"] != "dependency-compatibility.json"
    ) {
        throw GradleException("Dependency manifest compatibility summary is stale: $manifestCompatibility")
    }
    val packages = sbom["packages"] as? List<*>
    if (packages.isNullOrEmpty()) {
        throw GradleException("Dependency SBOM-lite must list bundled packages.")
    }
    val manifestPackages = manifest["packages"] as? List<*>
    if (manifestPackages.isNullOrEmpty()) {
        throw GradleException("Dependency manifest must list bundled packages.")
    }
    manifestPackages.forEach { entry ->
        val pkg = entry as? Map<*, *> ?: throw GradleException("Dependency manifest package entry is invalid: $entry")
        if (pkg["integrity"]?.toString().isNullOrBlank() ||
            pkg["contentSha256"]?.toString().isNullOrBlank() ||
            pkg["autoJs6CompatibilityScore"] !is Number ||
            pkg["unsupportedCapabilities"] !is List<*>
        ) {
            throw GradleException("Dependency manifest package entry is missing S7-40 fields: $pkg")
        }
    }
}

private fun copyNodeDependencyBundlerSanitizedProject(sourceRoot: File, bundleDir: File) {
    bundleDir.deleteRecursively()
    bundleDir.mkdirs()
    project.copy {
        from(sourceRoot.resolve("node_modules"))
        into(bundleDir.resolve("node_modules"))
        includeEmptyDirs = false
    }
    sourceRoot.resolve("package.json").copyTo(bundleDir.resolve("package.json"), overwrite = true)
    sourceRoot.resolve("package-lock.json")
        .takeIf(File::isFile)
        ?.copyTo(bundleDir.resolve("package-lock.json"), overwrite = true)
}

private fun nodeDependencyBundlerCacheInfo(cacheRoot: File, scan: NodeDependencyBundleScanResult): Map<String, Any?> {
    val cacheProjectDir = cacheRoot.resolve(scan.bundleHash).resolve("project")
    return linkedMapOf(
        "enabled" to true,
        "cacheKey" to scan.bundleHash,
        "cacheRoot" to cacheRoot.absolutePath,
        "projectDir" to cacheProjectDir.absolutePath,
        "hit" to cacheProjectDir.isDirectory,
    )
}

private fun writeNodeDependencyBundlerCache(
    projectRoot: File,
    cacheRoot: File,
    scan: NodeDependencyBundleScanResult,
) {
    if (scan.bundleHash.isBlank()) {
        return
    }
    val cacheEntryDir = cacheRoot.resolve(scan.bundleHash)
    val cacheProjectDir = cacheEntryDir.resolve("project")
    if (!cacheProjectDir.isDirectory) {
        copyNodeDependencyBundlerSanitizedProject(projectRoot, cacheProjectDir)
        cacheEntryDir.resolve("cache-metadata.json").writeText(
            nodeDependencyBundlerJson(
                linkedMapOf(
                    "schema" to "autojs6-node-dependency-cache-entry-v1",
                    "cacheKey" to scan.bundleHash,
                    "bundleHash" to scan.bundleHash,
                    "hashAlgorithm" to "sha256-normalized-sanitized-project-v1",
                )
            ) + "\n"
        )
    }
}

val bundleNodeDependencies = tasks.register("bundleNodeDependencies") {
    group = "verification"
    description = "Validates and copies a sanitized Safe Node Profile node_modules dependency bundle."

    val defaultProjectRoot = rootProject.projectDir.resolve("sample/nodejs/npm-pure-js-cjs")
    val projectRootProvider = providers.gradleProperty(nodeDependencyBundlerProjectRootProperty)
        .map(::File)
        .orElse(defaultProjectRoot)
    val outputDirProvider = providers.gradleProperty(nodeDependencyBundlerOutputDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("nodejs/dependency-bundle").map { it.asFile })
    val reportDirProvider = providers.gradleProperty(nodeDependencyBundlerReportDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("reports/nodejs/dependencies").map { it.asFile })
    val cacheDirProvider = providers.gradleProperty(nodeDependencyBundlerCacheDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("nodejs/dependency-cache").map { it.asFile })

    inputs.dir(projectRootProvider)
    outputs.dir(outputDirProvider)
    outputs.dir(reportDirProvider)
    outputs.dir(cacheDirProvider)
    outputs.upToDateWhen { false }

    doLast {
        val projectRoot = projectRootProvider.get().canonicalFile
        val outputDir = outputDirProvider.get().canonicalFile
        val bundleDir = outputDir.resolve("project")
        val reportDir = reportDirProvider.get().canonicalFile
        val cacheDir = cacheDirProvider.get().canonicalFile
        val policy = nodeDependencyBundlerPolicy()
        val scan = scanNodeDependencyBundle(projectRoot, policy)
        val cacheInfo = nodeDependencyBundlerCacheInfo(cacheDir, scan)

        writeNodeDependencyBundleReports(projectRoot, bundleDir, reportDir, policy, scan, cacheInfo = cacheInfo)
        if (scan.issues.isNotEmpty()) {
            throw GradleException(
                "Node dependency bundle rejected ${scan.issues.size} issue(s). See ${reportDir.resolve("dependency-rejections.txt").absolutePath}"
            )
        }

        copyNodeDependencyBundlerSanitizedProject(projectRoot, bundleDir)
        writeNodeDependencyBundlerCache(projectRoot, cacheDir, scan)
        logger.lifecycle("Sanitized Node dependency bundle written to ${bundleDir.absolutePath}")
        logger.lifecycle("Node dependency manifest written to ${reportDir.resolve("dependency-manifest.json").absolutePath}")
    }
}

val prepareNodeNpmCiDependencies = tasks.register("prepareNodeNpmCiDependencies") {
    group = "verification"
    description = "Runs desktop npm ci with Safe Node Profile flags and writes a sanitized dependency bundle."

    val defaultProjectRoot = rootProject.projectDir.resolve("sample/nodejs/npm-ci-pure-js")
    val projectRootProvider = providers.gradleProperty(nodeDependencyNpmCiProjectRootProperty)
        .map(::File)
        .orElse(defaultProjectRoot)
    val workDirProvider = providers.gradleProperty(nodeDependencyNpmCiWorkDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("tmp/nodejs/npm-ci/work").map { it.asFile })
    val outputDirProvider = providers.gradleProperty(nodeDependencyNpmCiOutputDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("nodejs/npm-ci-bundle").map { it.asFile })
    val reportDirProvider = providers.gradleProperty(nodeDependencyNpmCiReportDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("reports/nodejs/npm-ci").map { it.asFile })
    val cacheDirProvider = providers.gradleProperty(nodeDependencyNpmCiCacheDirProperty)
        .map(::File)
        .orElse(layout.buildDirectory.dir("nodejs/dependency-cache").map { it.asFile })
    val npmExecutableProvider = providers.gradleProperty(nodeDependencyNpmCiExecutableProperty).orElse("npm")

    inputs.dir(projectRootProvider)
    outputs.dir(outputDirProvider)
    outputs.dir(reportDirProvider)
    outputs.dir(cacheDirProvider)
    outputs.upToDateWhen { false }

    doLast {
        val sourceRoot = projectRootProvider.get().canonicalFile
        val sourcePackageJson = sourceRoot.resolve("package.json")
        val sourceLockFile = sourceRoot.resolve("package-lock.json")
        if (!sourcePackageJson.isFile) {
            throw GradleException("Desktop npm ci project root must contain package.json: ${sourcePackageJson.absolutePath}")
        }
        if (!sourceLockFile.isFile) {
            throw GradleException("Desktop npm ci requires package-lock.json: ${sourceLockFile.absolutePath}")
        }

        val workDir = workDirProvider.get().canonicalFile
        workDir.deleteRecursively()
        workDir.mkdirs()
        project.copy {
            from(sourceRoot) {
                exclude("node_modules/**")
                exclude(".git/**")
            }
            into(workDir)
            includeEmptyDirs = false
        }

        val workPackageJson = workDir.resolve("package.json")
        val workLockFile = workDir.resolve("package-lock.json")
        val packageJsonHashBefore = workPackageJson.nodeDependencyBundlerSha256()
        val lockHashBefore = workLockFile.nodeDependencyBundlerSha256()
        val npmExecutable = nodeDependencyBundlerHostExecutable(npmExecutableProvider.get())
        val npmVersion = runNodeDependencyBundlerCommand(workDir, listOf(npmExecutable, "--version"))
            .nodeDependencyBundlerLastVersionLine()
        val npmArgs = listOf(
            "ci",
            "--ignore-scripts",
            "--no-bin-links",
            "--install-links",
            "--no-audit",
            "--fund=false",
        )
        runNodeDependencyBundlerCommand(workDir, listOf(npmExecutable) + npmArgs)
        if (workPackageJson.nodeDependencyBundlerSha256() != packageJsonHashBefore) {
            throw GradleException("npm ci mutated package.json in the working copy.")
        }
        if (workLockFile.nodeDependencyBundlerSha256() != lockHashBefore) {
            throw GradleException("npm ci mutated package-lock.json in the working copy.")
        }
        if (workDir.resolve("node_modules/.bin").exists()) {
            throw GradleException("npm ci produced node_modules/.bin despite --no-bin-links.")
        }

        val outputDir = outputDirProvider.get().canonicalFile
        val bundleDir = outputDir.resolve("project")
        val reportDir = reportDirProvider.get().canonicalFile
        val cacheDir = cacheDirProvider.get().canonicalFile
        val policy = nodeDependencyBundlerPolicy()
        val scan = scanNodeDependencyBundle(workDir, policy)
        val cacheInfo = nodeDependencyBundlerCacheInfo(cacheDir, scan)
        val installMetadata = linkedMapOf<String, Any?>(
            "manager" to "npm",
            "managerVersion" to npmVersion,
            "command" to "npm ${npmArgs.joinToString(" ")}",
            "flags" to npmArgs.drop(1),
            "installOptionsSha256" to nodeDependencyBundlerJson(
                linkedMapOf(
                    "packageJsonSha256" to packageJsonHashBefore,
                    "packageLockSha256" to lockHashBefore,
                    "flags" to npmArgs.drop(1),
                )
            ).nodeDependencyBundlerSha256(),
            "sourceProjectRoot" to sourceRoot.canonicalPath,
            "workDir" to workDir.canonicalPath,
            "packageJsonSha256" to packageJsonHashBefore,
            "packageLockSha256" to lockHashBefore,
            "lockfileRequired" to true,
            "scriptsIgnored" to true,
            "binLinksDisabled" to true,
            "fileDependenciesInstalledAsCopies" to true,
        )
        writeNodeDependencyBundleReports(
            workDir,
            bundleDir,
            reportDir,
            policy,
            scan,
            installMetadata,
            cacheInfo,
        )
        if (scan.issues.isNotEmpty()) {
            throw GradleException(
                "Desktop npm ci dependency bundle rejected ${scan.issues.size} issue(s). " +
                        "See ${reportDir.resolve("dependency-rejections.txt").absolutePath}"
            )
        }

        copyNodeDependencyBundlerSanitizedProject(workDir, bundleDir)
        writeNodeDependencyBundlerCache(workDir, cacheDir, scan)
        logger.lifecycle("Desktop npm ci sanitized bundle written to ${bundleDir.absolutePath}")
        logger.lifecycle("Desktop npm ci dependency manifest written to ${reportDir.resolve("dependency-manifest.json").absolutePath}")
    }
}

val verifyNodeNpmCiFixtures = tasks.register("verifyNodeNpmCiFixtures") {
    group = "verification"
    description = "Verifies desktop npm ci installs through the sanitizer without lifecycle scripts or native addons."

    dependsOn(prepareNodeNpmCiDependencies)

    doLast {
        val outputDir = providers.gradleProperty(nodeDependencyNpmCiOutputDirProperty)
            .map(::File)
            .orElse(layout.buildDirectory.dir("nodejs/npm-ci-bundle").map { it.asFile })
            .get()
            .canonicalFile
        val reportDir = providers.gradleProperty(nodeDependencyNpmCiReportDirProperty)
            .map(::File)
            .orElse(layout.buildDirectory.dir("reports/nodejs/npm-ci").map { it.asFile })
            .get()
            .canonicalFile
        val policy = nodeDependencyBundlerPolicy()
        verifyNodeDependencyBundlerPackagedLayout(outputDir, reportDir, policy)
        val projectDir = outputDir.resolve("project")
        if (!projectDir.resolve("package-lock.json").isFile) {
            throw GradleException("Desktop npm ci sanitized bundle must retain package-lock.json.")
        }
        if (projectDir.resolve("node_modules/.bin").exists()) {
            throw GradleException("Desktop npm ci sanitized bundle must not include node_modules/.bin.")
        }
        val manifest = nodeDependencyBundlerReadJsonObject(reportDir.resolve("dependency-manifest.json"))
        val install = manifest?.get("install") as? Map<*, *>
            ?: throw GradleException("Desktop npm ci manifest is missing install metadata.")
        val flags = (install["flags"] as? List<*>)?.map { it.toString() }.orEmpty()
        val requiredFlags = setOf("--ignore-scripts", "--no-bin-links", "--install-links")
        val missingFlags = requiredFlags - flags.toSet()
        if (missingFlags.isNotEmpty()) {
            throw GradleException("Desktop npm ci manifest is missing required flag(s): $missingFlags")
        }
        if (install["lockfileRequired"] != true ||
            install["scriptsIgnored"] != true ||
            install["binLinksDisabled"] != true
        ) {
            throw GradleException("Desktop npm ci manifest does not record required safety controls: $install")
        }
    }
}

val verifyNodeDependencyBundlerFixtures = tasks.register("verifyNodeDependencyBundlerFixtures") {
    group = "verification"
    description = "Verifies the Node dependency bundler accepts safe fixtures and rejects unsafe fixtures."

    dependsOn(bundleNodeDependencies, verifyNodeNpmCiFixtures)

    doLast {
        val policy = nodeDependencyBundlerPolicy()
        val safeRoot = rootProject.projectDir.resolve("sample/nodejs/npm-pure-js-cjs")
        val safeScan = scanNodeDependencyBundle(safeRoot, policy)
        if (safeScan.issues.isNotEmpty()) {
            throw GradleException("Expected sample npm-pure-js-cjs to pass bundler policy, got ${safeScan.issues}")
        }
        val safeCompatibility = nodeDependencyCompatibilityReport(safeRoot, safeScan, policy)
        if (safeCompatibility["status"] != "compatible") {
            throw GradleException("Expected sample npm-pure-js-cjs to be dependency-compatible, got $safeCompatibility")
        }
        val outputDir = providers.gradleProperty(nodeDependencyBundlerOutputDirProperty)
            .map(::File)
            .orElse(layout.buildDirectory.dir("nodejs/dependency-bundle").map { it.asFile })
            .get()
            .canonicalFile
        val reportDir = providers.gradleProperty(nodeDependencyBundlerReportDirProperty)
            .map(::File)
            .orElse(layout.buildDirectory.dir("reports/nodejs/dependencies").map { it.asFile })
            .get()
            .canonicalFile
        verifyNodeDependencyBundlerPackagedLayout(outputDir, reportDir, policy)

        val unsafeRoot = layout.buildDirectory.dir("tmp/nodejs/dependency-bundler/unsafe").get().asFile
        unsafeRoot.deleteRecursively()
        unsafeRoot.resolve("node_modules/unsafe-package").mkdirs()
        unsafeRoot.resolve("node_modules/.bin").mkdirs()
        unsafeRoot.resolve("node_modules/.pnpm").mkdirs()
        unsafeRoot.resolve("package.json").writeText("""{"name":"unsafe-root","version":"1.0.0"}""" + "\n")
        unsafeRoot.resolve(".pnp.cjs").writeText("// unsupported package manager layout\n")
        unsafeRoot.resolve("node_modules/unsafe-package/package.json").writeText(
            """
            {
              "name": "unsafe-package",
              "version": "1.0.0",
              "main": "C:/absolute/evil.js",
              "types": "bad\u0000types.d.ts",
              "files": ["dist", "../escape"],
              "directories": {
                "bin": "../bin"
              },
              "bin": {
                "unsafe-package": "/tmp/evil.js"
              },
              "exports": {
                ".": "./index.js",
                "./escape": "../outside.js"
              },
              "imports": {
                "#escape": "../outside.js"
              },
              "typesVersions": {
                "*": {
                  "*": ["../types/*"]
                }
              },
              "gypfile": true,
              "scripts": {
                "preinstall": "node preinstall.js",
                "install": "node install.js",
                "postinstall": "node postinstall.js",
                "prepare": "node prepare.js"
              }
            }
            """.trimIndent() + "\n"
        )
        unsafeRoot.resolve("node_modules/unsafe-package/binding.node").writeBytes(byteArrayOf(0, 1, 2, 3))
        unsafeRoot.resolve("node_modules/unsafe-package/binding.gyp").writeText("{}\n")
        unsafeRoot.resolve("node_modules/unsafe-package/build/Release").mkdirs()
        unsafeRoot.resolve("node_modules/unsafe-package/build/Release/addon.o").writeBytes(byteArrayOf(4, 5, 6, 7))
        unsafeRoot.resolve("node_modules/unsafe-package/.npm/_logs").mkdirs()
        unsafeRoot.resolve("node_modules/unsafe-package/.npm/_logs/postinstall.log").writeText("postinstall wrote an npm cache artifact\n")
        unsafeRoot.resolve("node_modules/unsafe-package/tool.exe").writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0))
        unsafeRoot.resolve("node_modules/.bin/unsafe").writeText("node ../unsafe-package/postinstall.js\n")
        val outsideTarget = layout.buildDirectory.file("tmp/nodejs/dependency-bundler/outside-target.txt").get().asFile
        outsideTarget.parentFile.mkdirs()
        outsideTarget.writeText("outside\n")
        val symlinkPath = unsafeRoot.resolve("node_modules/unsafe-package/outside-link")
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(symlinkPath.toPath(), outsideTarget.toPath())
        }.isSuccess

        val unsafeScan = scanNodeDependencyBundle(unsafeRoot, policy)
        val rejectionCodes = unsafeScan.issues.map { it.code }.toSet()
        val unsafeCompatibility = nodeDependencyCompatibilityReport(unsafeRoot, unsafeScan, policy)
        val unsafeCompatibilityStatus = unsafeCompatibility["status"]?.toString().orEmpty()
        if (unsafeCompatibilityStatus !in setOf("unsafe", "unsupported")) {
            throw GradleException("Unsafe dependency fixture should produce a blocking compatibility status: $unsafeCompatibility")
        }
        val requiredCodes = setOf(
            "LIFECYCLE_SCRIPT_DISABLED",
            "NATIVE_ADDON_DISABLED",
            "NODE_GYP_ARTIFACT_UNSUPPORTED",
            "BIN_ENTRY_UNSUPPORTED",
            "POSTINSTALL_ARTIFACT_UNSUPPORTED",
            "SUSPICIOUS_EXECUTABLE_UNSUPPORTED",
            "PACKAGE_ABSOLUTE_PATH_REFERENCE",
            "PACKAGE_PATH_ESCAPE_REFERENCE",
            "PACKAGE_NUL_PATH_REFERENCE",
            "UNSUPPORTED_PACKAGE_MANAGER_LAYOUT",
        )
        val missingCodes = requiredCodes - rejectionCodes
        if (missingCodes.isNotEmpty()) {
            throw GradleException("Unsafe dependency fixture did not produce expected rejection codes: $missingCodes; actual=$rejectionCodes")
        }
        val lifecycleMessages = unsafeScan.issues
            .filter { it.code == "LIFECYCLE_SCRIPT_DISABLED" }
            .joinToString(separator = "\n") { it.message }
        val missingLifecycleScripts = nodeDependencyBundlerForbiddenScripts.filter { scriptName ->
            "'$scriptName'" !in lifecycleMessages
        }
        if (missingLifecycleScripts.isNotEmpty()) {
            throw GradleException(
                "Unsafe dependency fixture did not reject every lifecycle script variant: " +
                        "$missingLifecycleScripts; messages=$lifecycleMessages"
            )
        }
        if (symlinkCreated && rejectionCodes.intersect(setOf("SYMLINK_UNSUPPORTED", "PATH_ESCAPE")).isEmpty()) {
            throw GradleException("Unsafe dependency symlink fixture did not produce a symlink/path escape rejection; actual=$rejectionCodes")
        }
        if (!symlinkCreated) {
            logger.lifecycle("Skipping dependency bundler symlink fixture because the host filesystem denied symlink creation.")
        }

        val oversizedRoot = layout.buildDirectory.dir("tmp/nodejs/dependency-bundler/oversized").get().asFile
        oversizedRoot.deleteRecursively()
        oversizedRoot.resolve("node_modules/oversized-package").mkdirs()
        oversizedRoot.resolve("package.json").writeText("""{"name":"oversized-root","version":"1.0.0"}""" + "\n")
        oversizedRoot.resolve("node_modules/oversized-package/package.json").writeText(
            """{"name":"oversized-package","version":"1.0.0","license":"MIT"}""" + "\n"
        )
        oversizedRoot.resolve("node_modules/oversized-package/blob.dat").writeBytes(ByteArray(64) { 7 })
        val oversizedScan = scanNodeDependencyBundle(
            oversizedRoot,
            policy.copy(maxFileBytes = 8L, maxPackageBytes = 32L, maxTotalBytes = 40L, maxPackageCount = 1),
        )
        val oversizedCodes = oversizedScan.issues.map { it.code }.toSet()
        val requiredOversizedCodes = setOf("FILE_SIZE_LIMIT", "PACKAGE_SIZE_LIMIT", "TOTAL_SIZE_LIMIT")
        val missingOversizedCodes = requiredOversizedCodes - oversizedCodes
        if (missingOversizedCodes.isNotEmpty()) {
            throw GradleException(
                "Oversized dependency fixture did not produce expected rejection codes: " +
                        "$missingOversizedCodes; actual=$oversizedCodes"
            )
        }
        logger.lifecycle("Node dependency bundler unsafe fixture rejected codes: ${rejectionCodes.sorted().joinToString(", ")}")
    }
}

tasks.matching { it.name == "verifyEmbeddedNodeDebug" }.configureEach {
    dependsOn(verifyNodeDependencyBundlerFixtures)
}

tasks.matching { it.name == "verifyEmbeddedNodePackagedDebug" }.configureEach {
    dependsOn(verifyNodeDependencyBundlerFixtures)
}

tasks.matching { it.name == "verifyEmbeddedNodeReleaseCandidate" }.configureEach {
    dependsOn(verifyNodeDependencyBundlerFixtures)
}
