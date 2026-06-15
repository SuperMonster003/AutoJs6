import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

private val nodeDependencyBundlerProjectRootProperty = "autojs.nodejs.bundle.projectRoot"
private val nodeDependencyBundlerOutputDirProperty = "autojs.nodejs.bundle.outputDir"
private val nodeDependencyBundlerReportDirProperty = "autojs.nodejs.bundle.reportDir"
private val nodeDependencyBundlerMaxFileBytesProperty = "autojs.nodejs.bundle.maxFileBytes"
private val nodeDependencyBundlerMaxPackageBytesProperty = "autojs.nodejs.bundle.maxPackageBytes"
private val nodeDependencyBundlerMaxTotalBytesProperty = "autojs.nodejs.bundle.maxTotalBytes"
private val nodeDependencyBundlerMaxPackageCountProperty = "autojs.nodejs.bundle.maxPackageCount"
private val nodeDependencyBundlerAllowSuspiciousExecutablesProperty = "autojs.nodejs.bundle.allowSuspiciousExecutables"

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
    val fileCount: Int,
    val sizeBytes: Long,
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
    if (issues.isNotEmpty()) {
        return NodeDependencyBundleScanResult(emptyList(), 0, 0L, issues)
    }

    var totalFiles = 0
    var totalBytes = 0L
    fun auditPackageJson(packageJson: File, includePackageInfo: Boolean) {
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
        packages += NodeDependencyPackageInfo(
            name = json["name"]?.toString().orEmpty(),
            version = json["version"]?.toString().orEmpty(),
            license = json["license"]?.toString().orEmpty(),
            relativePath = packageDir.nodeDependencyBundlerRelativeTo(root),
            packageJsonSha256 = packageJson.nodeDependencyBundlerSha256(),
            fileCount = fileCount,
            sizeBytes = sizeBytes,
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
        issues = issues,
    )
}

private fun writeNodeDependencyBundleReports(
    projectRoot: File,
    bundleDir: File,
    reportDir: File,
    policy: NodeDependencyBundlePolicy,
    scan: NodeDependencyBundleScanResult,
) {
    reportDir.mkdirs()
    val manifest = linkedMapOf<String, Any?>(
        "schema" to "autojs6-node-dependency-bundle-v1",
        "projectRoot" to projectRoot.canonicalPath,
        "sanitizedBundle" to bundleDir.canonicalPath,
        "policy" to linkedMapOf(
            "maxFileBytes" to policy.maxFileBytes,
            "maxPackageBytes" to policy.maxPackageBytes,
            "maxTotalBytes" to policy.maxTotalBytes,
            "maxPackageCount" to policy.maxPackageCount,
            "allowSuspiciousExecutables" to policy.allowSuspiciousExecutables,
        ),
        "sbomLite" to "dependency-sbom-lite.json",
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
        "packages" to scan.packages.map {
            linkedMapOf(
                "name" to it.name,
                "version" to it.version,
                "license" to it.license,
                "relativePath" to it.relativePath,
                "packageJsonSha256" to it.packageJsonSha256,
                "fileCount" to it.fileCount,
                "sizeBytes" to it.sizeBytes,
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
        "packages" to scan.packages.map {
            linkedMapOf(
                "name" to it.name,
                "version" to it.version,
                "license" to it.license.ifBlank { "UNKNOWN" },
                "fileCount" to it.fileCount,
                "totalSizeBytes" to it.sizeBytes,
                "relativePath" to it.relativePath,
            )
        },
    )
    reportDir.resolve("dependency-sbom-lite.json").writeText(nodeDependencyBundlerJson(sbomLite) + "\n")
    reportDir.resolve("dependency-size-report.txt").writeText(
        buildString {
            appendLine("AutoJs6 Node Dependency Bundle Size Report")
            appendLine("projectRoot=${projectRoot.canonicalPath}")
            appendLine("sanitizedBundle=${bundleDir.canonicalPath}")
            appendLine("packages=${scan.packages.size}")
            appendLine("files=${scan.totalFiles}")
            appendLine("bytes=${scan.totalBytes}")
            scan.packages.forEach { pkg ->
                appendLine("${pkg.relativePath} files=${pkg.fileCount} bytes=${pkg.sizeBytes} license=${pkg.license.ifBlank { "unknown" }}")
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
    if (!manifestFile.isFile || !sbomFile.isFile) {
        throw GradleException("Dependency bundler did not write manifest/SBOM-lite reports.")
    }
    val manifest = nodeDependencyBundlerReadJsonObject(manifestFile)
    val packagedLayout = manifest?.get("packagedLayout") as? Map<*, *>
    if (packagedLayout?.get("sanitized") != true ||
        packagedLayout["assetRoot"] != "assets/project" ||
        packagedLayout["nodeModules"] != "assets/project/node_modules"
    ) {
        throw GradleException("Dependency manifest does not record a sanitized packaged APK layout: $packagedLayout")
    }
    val sbom = nodeDependencyBundlerReadJsonObject(sbomFile)
    if (sbom?.get("schema") != "autojs6-node-dependency-sbom-lite-v1") {
        throw GradleException("Dependency SBOM-lite schema missing or invalid: ${sbom?.get("schema")}")
    }
    val packages = sbom["packages"] as? List<*>
    if (packages.isNullOrEmpty()) {
        throw GradleException("Dependency SBOM-lite must list bundled packages.")
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

    inputs.dir(projectRootProvider)
    outputs.dir(outputDirProvider)
    outputs.dir(reportDirProvider)
    outputs.upToDateWhen { false }

    doLast {
        val projectRoot = projectRootProvider.get().canonicalFile
        val outputDir = outputDirProvider.get().canonicalFile
        val bundleDir = outputDir.resolve("project")
        val reportDir = reportDirProvider.get().canonicalFile
        val policy = nodeDependencyBundlerPolicy()
        val scan = scanNodeDependencyBundle(projectRoot, policy)

        writeNodeDependencyBundleReports(projectRoot, bundleDir, reportDir, policy, scan)
        if (scan.issues.isNotEmpty()) {
            throw GradleException(
                "Node dependency bundle rejected ${scan.issues.size} issue(s). See ${reportDir.resolve("dependency-rejections.txt").absolutePath}"
            )
        }

        bundleDir.deleteRecursively()
        bundleDir.mkdirs()
        project.copy {
            from(projectRoot.resolve("node_modules"))
            into(bundleDir.resolve("node_modules"))
            includeEmptyDirs = false
        }
        projectRoot.resolve("package.json").copyTo(bundleDir.resolve("package.json"), overwrite = true)
        logger.lifecycle("Sanitized Node dependency bundle written to ${bundleDir.absolutePath}")
        logger.lifecycle("Node dependency manifest written to ${reportDir.resolve("dependency-manifest.json").absolutePath}")
    }
}

val verifyNodeDependencyBundlerFixtures = tasks.register("verifyNodeDependencyBundlerFixtures") {
    group = "verification"
    description = "Verifies the Node dependency bundler accepts safe fixtures and rejects unsafe fixtures."

    dependsOn(bundleNodeDependencies)

    doLast {
        val policy = nodeDependencyBundlerPolicy()
        val safeRoot = rootProject.projectDir.resolve("sample/nodejs/npm-pure-js-cjs")
        val safeScan = scanNodeDependencyBundle(safeRoot, policy)
        if (safeScan.issues.isNotEmpty()) {
            throw GradleException("Expected sample npm-pure-js-cjs to pass bundler policy, got ${safeScan.issues}")
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
        unsafeRoot.resolve("package.json").writeText("""{"name":"unsafe-root","version":"1.0.0"}""" + "\n")
        unsafeRoot.resolve("node_modules/unsafe-package/package.json").writeText(
            """
            {
              "name": "unsafe-package",
              "version": "1.0.0",
              "main": "C:/absolute/evil.js",
              "bin": {
                "unsafe-package": "/tmp/evil.js"
              },
              "exports": {
                ".": "./index.js",
                "./escape": "../outside.js"
              },
              "scripts": {
                "postinstall": "node postinstall.js"
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
        val requiredCodes = setOf(
            "LIFECYCLE_SCRIPT_DISABLED",
            "NATIVE_ADDON_DISABLED",
            "NODE_GYP_ARTIFACT_UNSUPPORTED",
            "BIN_ENTRY_UNSUPPORTED",
            "POSTINSTALL_ARTIFACT_UNSUPPORTED",
            "SUSPICIOUS_EXECUTABLE_UNSUPPORTED",
            "PACKAGE_ABSOLUTE_PATH_REFERENCE",
            "PACKAGE_PATH_ESCAPE_REFERENCE",
        )
        val missingCodes = requiredCodes - rejectionCodes
        if (missingCodes.isNotEmpty()) {
            throw GradleException("Unsafe dependency fixture did not produce expected rejection codes: $missingCodes; actual=$rejectionCodes")
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
