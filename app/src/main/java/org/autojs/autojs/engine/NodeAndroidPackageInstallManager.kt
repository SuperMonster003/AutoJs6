package org.autojs.autojs.engine

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NodeAndroidPackageInstallManager(
    private val storeRoot: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    data class PackageRequest(
        val name: String,
        val version: String,
        val sourceDir: File,
        val expectedIntegrity: String? = null,
        val kind: String = KIND_DEPENDENCY,
    )

    data class PackageRecord(
        val name: String,
        val version: String,
        val kind: String,
        val integrity: String,
        val installedAtEpochMs: Long,
        val fileCount: Int,
        val totalBytes: Long,
        val packageDir: File,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("schema", RECORD_SCHEMA)
            .put("name", name)
            .put("version", version)
            .put("kind", kind)
            .put("integrity", integrity)
            .put("installedAtEpochMs", installedAtEpochMs)
            .put("fileCount", fileCount)
            .put("totalBytes", totalBytes)
            .put("packageDir", packageDir.absolutePath)
    }

    data class OperationResult(
        val operation: String,
        val status: String,
        val record: PackageRecord? = null,
        val transactionId: String = "",
        val message: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("operation", operation)
            .put("status", status)
            .put("transactionId", transactionId)
            .put("message", message)
            .put("record", record?.toJson() ?: JSONObject.NULL)
    }

    class CancellationToken {
        private val cancelled = AtomicBoolean(false)

        fun cancel() {
            cancelled.set(true)
        }

        fun throwIfCancellationRequested() {
            if (cancelled.get()) {
                throw PackageInstallException(ERROR_CANCELLED, "Android package operation was cancelled.")
            }
        }

    }

    class PackageInstallException(
        val code: String,
        override val message: String,
    ) : IllegalStateException(message)

    fun install(request: PackageRequest, token: CancellationToken = CancellationToken()): OperationResult {
        return installOrUpdate(OP_INSTALL, request, replace = false, token = token)
    }

    fun update(request: PackageRequest, token: CancellationToken = CancellationToken()): OperationResult {
        return installOrUpdate(OP_UPDATE, request, replace = true, token = token)
    }

    fun remove(name: String, version: String, token: CancellationToken = CancellationToken()): OperationResult {
        return withStoreLock {
            token.throwIfCancellationRequested()
            val target = packageDir(name, version)
            if (!target.exists()) {
                return@withStoreLock OperationResult(
                    operation = OP_REMOVE,
                    status = STATUS_MISSING,
                    message = "Package is not installed.",
                )
            }
            val transactionId = newTransactionId()
            val transactionDir = transactionDir(transactionId)
            val trash = File(transactionDir, "trash")
            transactionDir.mkdirs()
            try {
                token.throwIfCancellationRequested()
                moveDirectory(target, trash)
                trash.deleteRecursively()
                OperationResult(
                    operation = OP_REMOVE,
                    status = STATUS_REMOVED,
                    transactionId = transactionId,
                )
            } finally {
                transactionDir.deleteRecursively()
            }
        }
    }

    fun list(): List<PackageRecord> = withStoreLock {
        packagesDir()
            .listFiles()
            .orEmpty()
            .flatMap { nameDir -> nameDir.listFiles().orEmpty().asIterable() }
            .mapNotNull(::readRecord)
            .sortedWith(compareBy<PackageRecord> { it.name }.thenBy { it.version })
    }

    fun verify(name: String, version: String): OperationResult = withStoreLock {
        val target = packageDir(name, version)
        val record = readRecord(target)
            ?: return@withStoreLock OperationResult(
                operation = OP_VERIFY,
                status = STATUS_MISSING,
                message = "Package is not installed.",
            )
        val actualIntegrity = calculateSourceIntegrity(target, includeRecord = false)
        if (actualIntegrity != record.integrity) {
            throw PackageInstallException(
                ERROR_INTEGRITY_MISMATCH,
                "Package integrity mismatch for $name@$version.",
            )
        }
        OperationResult(
            operation = OP_VERIFY,
            status = STATUS_VERIFIED,
            record = record,
            message = "Package integrity verified.",
        )
    }

    fun prune(maxTransactionAgeMs: Long = 0L): OperationResult = withStoreLock {
        val now = clock()
        val transactionRoot = transactionsDir()
        var removed = 0
        transactionRoot.listFiles().orEmpty().forEach { child ->
            val oldEnough = maxTransactionAgeMs <= 0L || now - child.lastModified() >= maxTransactionAgeMs
            if (oldEnough && child.deleteRecursively()) {
                removed += 1
            }
        }
        OperationResult(
            operation = OP_PRUNE,
            status = STATUS_PRUNED,
            message = "Removed $removed stale transaction(s).",
        )
    }

    fun calculateSourceIntegrity(sourceDir: File, includeRecord: Boolean = true): String {
        val digest = MessageDigest.getInstance("SHA-512")
        collectPackageFiles(sourceDir, CancellationToken())
            .filter { includeRecord || it.name != RECORD_FILE }
            .forEach { file ->
                val relative = sourceDir.canonicalFile.toPath().relativize(file.canonicalFile.toPath())
                    .joinToString("/") { it.toString() }
                digest.update(relative.toByteArray(StandardCharsets.UTF_8))
                digest.update(0)
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.update(0)
            }
        return "sha512-" + Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    fun nativeValues(): Map<String, String> = mapOf(
        "android_package_manager.status" to "partial",
        "android_package_manager.transactional" to "true",
        "android_package_manager.cancellable" to "true",
        "android_package_manager.locked" to "true",
        "android_package_manager.integrity" to "sha512",
        "android_package_manager.lifecycle_scripts" to "disabled",
        "android_package_manager.native_payload_execution" to "disabled",
        "android_package_manager.network_install" to "disabled",
    )

    private fun installOrUpdate(
        operation: String,
        request: PackageRequest,
        replace: Boolean,
        token: CancellationToken,
    ): OperationResult = withStoreLock {
        validateRequest(request)
        token.throwIfCancellationRequested()
        validatePackageSource(request)
        val target = packageDir(request.name, request.version)
        if (!replace && target.exists()) {
            throw PackageInstallException(ERROR_ALREADY_INSTALLED, "Package is already installed: ${request.name}@${request.version}.")
        }
        if (replace && !target.exists()) {
            throw PackageInstallException(ERROR_NOT_INSTALLED, "Package is not installed: ${request.name}@${request.version}.")
        }

        val transactionId = newTransactionId()
        val transactionDir = transactionDir(transactionId)
        val staged = File(transactionDir, "staged")
        val backup = File(transactionDir, "backup")
        transactionDir.mkdirs()
        try {
            copyPackageTree(request.sourceDir, staged, token)
            val integrity = calculateSourceIntegrity(staged)
            request.expectedIntegrity?.let { expected ->
                if (expected != integrity) {
                    throw PackageInstallException(ERROR_INTEGRITY_MISMATCH, "Package integrity mismatch for ${request.name}@${request.version}.")
                }
            }
            val files = collectPackageFiles(staged, token)
            val record = PackageRecord(
                name = request.name,
                version = request.version,
                kind = request.kind,
                integrity = integrity,
                installedAtEpochMs = clock(),
                fileCount = files.size,
                totalBytes = files.sumOf { it.length() },
                packageDir = target,
            )
            File(staged, RECORD_FILE).writeText(record.toJson().toString(2))
            token.throwIfCancellationRequested()
            if (target.exists()) {
                moveDirectory(target, backup)
            }
            try {
                moveDirectory(staged, target)
            } catch (error: Throwable) {
                if (backup.exists() && !target.exists()) {
                    moveDirectory(backup, target)
                }
                throw error
            }
            backup.deleteRecursively()
            OperationResult(
                operation = operation,
                status = if (operation == OP_INSTALL) STATUS_INSTALLED else STATUS_UPDATED,
                record = record.copy(packageDir = target),
                transactionId = transactionId,
            )
        } finally {
            transactionDir.deleteRecursively()
        }
    }

    private fun validateRequest(request: PackageRequest) {
        if (request.name.isBlank()) {
            throw PackageInstallException(ERROR_INVALID_PACKAGE, "Package name must not be blank.")
        }
        if (request.version.isBlank()) {
            throw PackageInstallException(ERROR_INVALID_PACKAGE, "Package version must not be blank.")
        }
        if (!request.sourceDir.isDirectory) {
            throw PackageInstallException(ERROR_INVALID_PACKAGE, "Package source directory does not exist: ${request.sourceDir}.")
        }
    }

    private fun validatePackageSource(request: PackageRequest) {
        val packageJson = File(request.sourceDir, "package.json")
        if (!packageJson.isFile) {
            throw PackageInstallException(ERROR_INVALID_PACKAGE, "Android package install requires package.json.")
        }
        val json = JSONObject(packageJson.readText())
        val metadataName = json.optString("name").takeIf { it.isNotBlank() }
        val metadataVersion = json.optString("version").takeIf { it.isNotBlank() }
        if (metadataName != null && metadataName != request.name) {
            throw PackageInstallException(ERROR_INVALID_PACKAGE, "package.json name does not match request name.")
        }
        if (metadataVersion != null && metadataVersion != request.version) {
            throw PackageInstallException(ERROR_INVALID_PACKAGE, "package.json version does not match request version.")
        }
        val scripts = json.optJSONObject("scripts")
        DISABLED_LIFECYCLE_SCRIPTS.forEach { script ->
            if (scripts?.has(script) == true) {
                throw PackageInstallException(ERROR_LIFECYCLE_SCRIPT_DISABLED, "Package lifecycle script is disabled on Android: $script.")
            }
        }
        if (json.has("bin")) {
            throw PackageInstallException(ERROR_BIN_DISABLED, "Package bin execution is disabled on Android.")
        }
        collectPackageFiles(request.sourceDir, CancellationToken()).forEach { file ->
            val relative = request.sourceDir.canonicalFile.toPath().relativize(file.canonicalFile.toPath())
                .joinToString("/") { it.toString() }
            val normalized = relative.replace('\\', '/')
            if (normalized.endsWith(".node") || normalized == "binding.gyp" ||
                normalized.startsWith("build/Release/") || normalized.startsWith("build/Debug/")
            ) {
                throw PackageInstallException(ERROR_NATIVE_PAYLOAD_DISABLED, "Native package payload is disabled on Android: $normalized.")
            }
        }
    }

    private fun copyPackageTree(sourceDir: File, targetDir: File, token: CancellationToken) {
        val sourceRoot = sourceDir.canonicalFile
        collectPackageFiles(sourceRoot, token).forEach { file ->
            token.throwIfCancellationRequested()
            val relative = sourceRoot.toPath().relativize(file.canonicalFile.toPath()).joinToString("/") { it.toString() }
            val target = File(targetDir, relative)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = false)
        }
    }

    private fun collectPackageFiles(sourceDir: File, token: CancellationToken): List<File> {
        val sourceRoot = sourceDir.canonicalFile
        val result = mutableListOf<File>()
        fun visit(file: File) {
            token.throwIfCancellationRequested()
            if (Files.isSymbolicLink(file.toPath())) {
                throw PackageInstallException(ERROR_PATH_ESCAPE, "Package symlinks are disabled on Android: ${file.path}.")
            }
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(sourceRoot.path + File.separator) && canonical != sourceRoot) {
                throw PackageInstallException(ERROR_PATH_ESCAPE, "Package file escapes source root: ${file.path}.")
            }
            when {
                canonical.isDirectory -> canonical.listFiles().orEmpty().sortedBy { it.name }.forEach(::visit)
                canonical.isFile -> result += canonical
            }
        }
        visit(sourceRoot)
        return result.sortedBy { sourceRoot.toPath().relativize(it.toPath()).joinToString("/") { part -> part.toString() } }
    }

    private fun readRecord(packageDir: File): PackageRecord? {
        val recordFile = File(packageDir, RECORD_FILE)
        if (!recordFile.isFile) return null
        val json = JSONObject(recordFile.readText())
        return PackageRecord(
            name = json.getString("name"),
            version = json.getString("version"),
            kind = json.optString("kind", KIND_DEPENDENCY),
            integrity = json.getString("integrity"),
            installedAtEpochMs = json.optLong("installedAtEpochMs"),
            fileCount = json.optInt("fileCount"),
            totalBytes = json.optLong("totalBytes"),
            packageDir = packageDir,
        )
    }

    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
    }

    private fun <T> withStoreLock(block: () -> T): T {
        storeRoot.mkdirs()
        packagesDir().mkdirs()
        transactionsDir().mkdirs()
        return processLock.withLock {
            RandomAccessFile(File(storeRoot, LOCK_FILE), "rw").use { file ->
                file.channel.use { channel ->
                    channel.lock().use {
                        block()
                    }
                }
            }
        }
    }

    private fun packagesDir(): File = File(storeRoot, "packages")

    private fun transactionsDir(): File = File(storeRoot, ".transactions")

    private fun transactionDir(id: String): File = File(transactionsDir(), id)

    private fun packageDir(name: String, version: String): File {
        return File(File(packagesDir(), encodeSegment(name)), encodeSegment(version))
    }

    private fun newTransactionId(): String = clock().toString() + "-" + UUID.randomUUID().toString()

    private fun encodeSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val KIND_DEPENDENCY = "dependency"
        const val KIND_PLUGIN = "plugin"
        const val KIND_PROJECT = "project"

        const val OP_INSTALL = "install"
        const val OP_UPDATE = "update"
        const val OP_REMOVE = "remove"
        const val OP_LIST = "list"
        const val OP_VERIFY = "verify"
        const val OP_PRUNE = "prune"

        const val STATUS_INSTALLED = "installed"
        const val STATUS_UPDATED = "updated"
        const val STATUS_REMOVED = "removed"
        const val STATUS_MISSING = "missing"
        const val STATUS_VERIFIED = "verified"
        const val STATUS_PRUNED = "pruned"

        const val ERROR_INVALID_PACKAGE = "ERR_AUTOJS6_ANDROID_PACKAGE_INVALID"
        const val ERROR_ALREADY_INSTALLED = "ERR_AUTOJS6_ANDROID_PACKAGE_ALREADY_INSTALLED"
        const val ERROR_NOT_INSTALLED = "ERR_AUTOJS6_ANDROID_PACKAGE_NOT_INSTALLED"
        const val ERROR_CANCELLED = "ERR_AUTOJS6_ANDROID_PACKAGE_CANCELLED"
        const val ERROR_INTEGRITY_MISMATCH = "ERR_AUTOJS6_ANDROID_PACKAGE_INTEGRITY"
        const val ERROR_LIFECYCLE_SCRIPT_DISABLED = "ERR_AUTOJS6_ANDROID_PACKAGE_LIFECYCLE_DISABLED"
        const val ERROR_NATIVE_PAYLOAD_DISABLED = "ERR_AUTOJS6_ANDROID_PACKAGE_NATIVE_DISABLED"
        const val ERROR_BIN_DISABLED = "ERR_AUTOJS6_ANDROID_PACKAGE_BIN_DISABLED"
        const val ERROR_PATH_ESCAPE = "ERR_AUTOJS6_ANDROID_PACKAGE_PATH_ESCAPE"

        val DISABLED_LIFECYCLE_SCRIPTS = setOf("preinstall", "install", "postinstall", "prepare")

        private const val DEFAULT_BUFFER_SIZE = 8192
        private const val LOCK_FILE = ".autojs6-package-manager.lock"
        private const val RECORD_FILE = ".autojs6-package.json"
        private const val RECORD_SCHEMA = "autojs6-node-android-package-record-v1"

        private val processLock = ReentrantLock()

        fun recordsToJson(records: List<PackageRecord>): JSONArray {
            return JSONArray().also { array ->
                records.forEach { record -> array.put(record.toJson()) }
            }
        }
    }
}
