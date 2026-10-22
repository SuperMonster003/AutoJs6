package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.model.ModelBinaryMetadata
import org.autojs.autojs.core.accessibility.codegen.model.ModelDatasetMetadata
import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetricSummary
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetrics
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ModelTrainingMetadata
import org.autojs.autojs.core.accessibility.codegen.model.Rejected
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.ui.edit.editor.ace.font.FontIo
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Collections
import java.util.UUID

enum class ModelSource { OFFICIAL, CUSTOM }

data class InstalledModel(
    val manifest: ModelManifest,
    val sha256: String,
    val sizeBytes: Long,
    val source: ModelSource,
    val origin: String,
    val installedAtMillis: Long,
) {
    val modelId: String get() = manifest.modelId
    val versionCode: Int get() = manifest.versionCode
    val key: String get() = "$modelId@$versionCode-${sha256.take(8)}"
}

/** Android supplies a Pref adapter; the store itself has no platform dependency. */
interface CurrentModelSelection {
    var currentKey: String?
}

open class ModelStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)
class ModelInstallException(message: String, val rejection: Rejected? = null, cause: Throwable? = null) :
    ModelStoreException(message, cause)

/** A live lease protects the file from cleanup by another store instance or process. */
class ModelStaging internal constructor(
    val file: File,
    internal val owner: ModelStore,
    internal val lease: ModelStagingLease,
) : Closeable {
    internal var closed = false
    override fun close() = owner.discard(this)
}

internal class ModelStagingLease(
    val file: File,
    private val channel: FileChannel,
    val lock: FileLock,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            try {
                if (lock.isValid) lock.release()
            } finally {
                channel.close()
            }
        } finally {
            onClose()
        }
    }
}

/**
 * Durable, versioned model packages under noBackupFilesDir/codegen_models. Registry records are
 * authoritative: a completed package rename without a registry commit is an orphan, while an
 * unreadable registry stops reconciliation without deleting installed packages. Catalog providers
 * run before acquiring the store lock, preserving the catalog -> store lock order.
 * zh-CN: 包版本存放于不参与备份的目录. 登记表是提交依据; 登记前中断的包按孤立目录清理,
 * 登记表损坏时停止对账并保留包. 获取目录快照先于存储锁, 保持目录锁到存储锁的顺序.
 */
class ModelStore(
    rootDirectory: File,
    private val reader: ModelPackageReader,
    private val verifiedCatalogs: () -> List<ModelCatalog> = { emptyList() },
    private val selection: CurrentModelSelection,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val rootDirectory: File = rootDirectory.canonicalFile
    private val registryFile = File(this.rootDirectory, REGISTRY_FILE)
    private val stagingDirectory = File(this.rootDirectory, STAGING_DIRECTORY)

    /** Fault injection at the durable write boundary, before replacing the previous registry. */
    internal var beforeRegistryCommit: () -> Unit = {}
    internal var beforeCleanupLeaseOpen: (File) -> Unit = {}

    init {
        reconcile()
    }

    /** Retries startup reconciliation after an interrupted operation, retaining every live lease. */
    fun reconcile() {
        withStoreLock {
            val registry = loadAndReconcileLocked()
            cleanupOrphansLocked(registry.models)
            cleanupStagingLocked()
        }
    }

    fun listInstalled(): List<InstalledModel> = withStoreLock {
        loadAndReconcileLocked().models.sortedWith(compareBy<InstalledModel> { it.modelId }
            .thenByDescending { it.versionCode }.thenBy { it.sha256 })
    }

    fun current(): InstalledModel? = withStoreLock {
        val models = loadAndReconcileLocked().models
        models.firstOrNull { it.key == selection.currentKey }
    }

    /** Returns false for a missing key; passing null explicitly clears the selection. */
    fun select(key: String?): Boolean = withStoreLock {
        val registry = loadAndReconcileLocked()
        if (key != null && registry.models.none { it.key == key }) return@withStoreLock false
        val updated = Registry(registry.models, SelectionChange(selection.currentKey, key))
        writeRegistryLocked(updated)
        applySelectionChange(updated)
        true
    }

    /** Update completion must not overwrite a choice made while the package was downloading. */
    fun selectIfCurrent(expectedCurrentKey: String, newKey: String): Boolean = withStoreLock {
        val registry = loadAndReconcileLocked()
        if (selection.currentKey != expectedCurrentKey || registry.models.none { it.key == expectedCurrentKey } ||
            registry.models.none { it.key == newKey }) return@withStoreLock false
        applySelectionChange(Registry(registry.models, SelectionChange(expectedCurrentKey, newKey)).also(::writeRegistryLocked))
        true
    }

    /** Returns bounded plain text from a package whose exact registered bytes are still valid. */
    fun readModelCard(key: String): String = withStoreLock {
        val file = installedFile(key) ?: throw java.io.FileNotFoundException("Installed model is missing or changed")
        java.util.zip.ZipFile(file).use { archive ->
            val entry = archive.getEntry("MODEL_CARD.md") ?: throw ModelStoreException("Model card is missing")
            if (entry.size !in 1..MAX_MODEL_CARD_BYTES.toLong()) throw ModelStoreException("Model card exceeds its size limit")
            val bytes = archive.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count >= 0) {
                    if (count > 0) {
                        if (output.size() + count > MAX_MODEL_CARD_BYTES) throw ModelStoreException("Model card exceeds its size limit")
                        output.write(buffer, 0, count)
                    }
                    count = input.read(buffer)
                }
                output.toByteArray()
            }
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }
    }

    /** Resolves a registry key, never an arbitrary path, and verifies the bytes on every access. */
    fun installedFile(key: String): File? = withStoreLock {
        val record = loadAndReconcileLocked().models.firstOrNull { it.key == key } ?: return@withStoreLock null
        val file = packageFile(record)
        if (file.length() != record.sizeBytes) return@withStoreLock null
        val digest = file.inputStream().use { digest(it).first }
        file.takeIf { digest == record.sha256 }
    }

    fun delete(key: String): Boolean = withStoreLock {
        val registry = loadAndReconcileLocked()
        val record = registry.models.firstOrNull { it.key == key } ?: return@withStoreLock false
        val change = if (selection.currentKey == key) SelectionChange(key, null) else null
        val updated = Registry(registry.models.filterNot { it.key == key }, change)
        writeRegistryLocked(updated)
        applySelectionChange(updated)
        // A failed physical cleanup is retried at startup; the durable deletion already succeeded.
        runCatching { deleteManagedTree(versionDirectory(record)); removeEmptyModelDirectory(record.modelId) }
        true
    }

    fun beginStaging(): ModelStaging = withStoreLock {
        ensureDirectory(stagingDirectory)
        val file = File.createTempFile("model-", ".part", stagingDirectory)
        val leaseFile = File(stagingDirectory, "${file.name}.lease")
        var channel: FileChannel? = null
        try {
            val opened = RandomAccessFile(leaseFile, "rw").channel
            channel = opened
            val lock = opened.lock()
            synchronized(ACTIVE_LEASES_LOCK) { ACTIVE_LEASES.add(leaseFile.absolutePath) }
            val lease = ModelStagingLease(leaseFile, opened, lock) {
                synchronized(ACTIVE_LEASES_LOCK) { ACTIVE_LEASES.remove(leaseFile.absolutePath) }
            }
            ModelStaging(file, this, lease)
        } catch (failure: Throwable) {
            runCatching { channel?.close() }
            file.delete()
            leaseFile.delete()
            throw failure
        }
    }

    /** The input remains owned by the caller. No unbounded byte array is created. */
    fun install(
        input: InputStream,
        origin: String,
        replaceKey: String? = null,
        expectedSha256: String? = null,
        expectedSizeBytes: Long? = null,
        validateManifest: (ModelManifest) -> Unit = {},
    ): InstalledModel = beginStaging().use { staging ->
        FileOutputStream(staging.file).use { output -> copyBounded(input, output) }
        installStaged(staging, origin, replaceKey, expectedSha256, expectedSizeBytes, validateManifest)
    }

    /**
     * Consumes the lease on success or failure. The private snapshot binds the parsed manifest,
     * SHA-256 and final file to exactly the same bytes even if the public staging file changes.
     * replaceKey=null retains every previous version and preserves the current selection.
     */
    fun installStaged(
        staging: ModelStaging,
        origin: String,
        replaceKey: String? = null,
        expectedSha256: String? = null,
        expectedSizeBytes: Long? = null,
        validateManifest: (ModelManifest) -> Unit = {},
    ): InstalledModel = staging.use {
        require(origin.isNotBlank() && origin.length <= 1024 && origin.none(Char::isISOControl)) { "Invalid installation origin" }
        require(expectedSha256 == null || SHA256.matches(expectedSha256)) { "Invalid expected package SHA-256" }
        require(expectedSizeBytes == null || expectedSizeBytes in 1..MAX_PACKAGE_BYTES) { "Invalid expected package size" }
        // The provider can take the catalog lock and must never run under the store lock.
        val officialDigests = verifiedCatalogs().flatMap { catalog ->
            catalog.models.flatMap { model -> model.versions.map { version -> version.artifact.sha256 } }
        }.toHashSet()
        withStoreLock {
            requireStaging(staging)
            val registry = loadAndReconcileLocked()
            val transaction = File(stagingDirectory, "install-${UUID.randomUUID()}")
            ensureDirectory(transaction)
            var renamedTarget: File? = null
            var committed = false
            try {
                val snapshot = File(transaction, PACKAGE_FILE)
                val (sha256, size) = FileOutputStream(snapshot).use { output ->
                    val result = staging.file.inputStream().use { input -> copyBounded(input, output) }
                    output.flush()
                    output.fd.sync()
                    result
                }
                if (expectedSha256 != null && sha256 != expectedSha256) throw ModelInstallException("Package SHA-256 differs from catalog")
                if (expectedSizeBytes != null && size != expectedSizeBytes) throw ModelInstallException("Package size differs from catalog")
                val model = when (val result = reader.read(snapshot)) {
                    is ModelPackage -> result
                    is Rejected -> throw ModelInstallException("Model package rejected: ${result.detail}", result)
                }
                checkInterrupted()
                validateModelId(model.manifest.modelId)
                validateManifest(model.manifest)
                val now = clock()
                require(now >= 0) { "Invalid installation time" }
                val installed = InstalledModel(model.manifest, sha256, size,
                    if (sha256 in officialDigests) ModelSource.OFFICIAL else ModelSource.CUSTOM, origin, now)
                registry.models.firstOrNull { it.sha256 == sha256 }?.let { return@withStoreLock it }
                if (registry.models.any { it.key == installed.key }) throw ModelInstallException("Package SHA-256 prefix collides with an installed version")
                val replaced = replaceKey?.let { key ->
                    registry.models.firstOrNull { it.key == key }
                        ?: throw ModelInstallException("The version to replace is no longer installed")
                }
                if (replaced != null && replaced.modelId != installed.modelId) throw ModelInstallException("Only a version of the same model can be replaced")
                val target = versionDirectory(installed)
                ensureDirectory(checkNotNull(target.parentFile))
                requireSafePath(target)
                if (target.exists()) throw ModelInstallException("Installation directory already exists")
                checkInterrupted()
                if (!transaction.renameTo(target)) throw ModelInstallException("Unable to atomically install model package")
                renamedTarget = target
                val change = if (replaced != null && selection.currentKey == replaced.key) SelectionChange(replaced.key, installed.key) else null
                val updated = Registry(registry.models.filterNot { it.key == replaced?.key } + installed, change)
                writeRegistryLocked(updated)
                committed = true
                applySelectionChange(updated)
                if (replaced != null) runCatching { deleteManagedTree(versionDirectory(replaced)) }
                installed
            } finally {
                // Once registered, never roll back the package because a preference write failed.
                // The journal in registry.json replays the selection change on the next access.
                if (!committed) renamedTarget?.let { runCatching { deleteManagedTree(it); removeEmptyModelDirectory(checkNotNull(it.parentFile).name) } }
                if (transaction.exists()) runCatching { deleteManagedTree(transaction) }
            }
        }
    }

    internal fun discard(staging: ModelStaging) {
        // Cleanup must still acquire the file lock after an interrupted read/cancelled download.
        val interrupted = Thread.interrupted()
        try {
            withStoreLock {
                if (staging.closed) return@withStoreLock
                require(staging.owner.rootDirectory == rootDirectory) { "Staging lease belongs to another store" }
                staging.closed = true
                try {
                    staging.lease.close()
                } finally {
                    requireSafePath(stagingDirectory)
                    // Delete the entry itself, including a substituted symlink; never traverse it.
                    if (staging.file.parentFile == stagingDirectory) staging.file.delete()
                    if (staging.lease.file.parentFile == stagingDirectory) staging.lease.file.delete()
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun requireStaging(staging: ModelStaging) {
        require(staging.owner.rootDirectory == rootDirectory && !staging.closed && staging.lease.lock.isValid) { "Staging lease is no longer active" }
        require(staging.file.parentFile == stagingDirectory && STAGE_FILE.matches(staging.file.name)) { "Invalid staging path" }
        requireSafePath(staging.file)
        require(staging.file.isFile) { "Staging package is missing" }
    }

    internal data class SelectionChange(val previousKey: String?, val nextKey: String?)
    internal data class Registry(val models: List<InstalledModel>, val selectionChange: SelectionChange? = null)

    private fun loadAndReconcileLocked(): Registry {
        val registry = readRegistryLocked()
        val retained = registry.models.filter { record ->
            val file = packageFile(record)
            requireSafePath(file)
            file.isFile
        }
        val replayed = applySelectionChange(registry)
        val updated = if (retained.size != registry.models.size) Registry(retained) else replayed
        if (updated !== replayed) writeRegistryLocked(updated)
        if (selection.currentKey != null && retained.none { it.key == selection.currentKey }) selection.currentKey = null
        return updated
    }

    private fun applySelectionChange(registry: Registry): Registry {
        val change = registry.selectionChange ?: return registry
        // SharedPreferences.commit can update memory before failing to persist to disk.
        // Rewriting an already visible nextKey is necessary before a later registry commit
        // may discard the journal. The adapter must signal a failed durable write.
        if (selection.currentKey == change.previousKey || selection.currentKey == change.nextKey) {
            selection.currentKey = change.nextKey?.takeIf { key -> registry.models.any { it.key == key } }
        }
        // Clearing the journal is a separate durable commit after the preference write.
        // A failure at either boundary leaves a replayable transition, while a completed
        // transition never causes unrelated future operations to rewrite preferences.
        return registry.copy(selectionChange = null).also(::writeRegistryLocked)
    }

    private fun readRegistryLocked(): Registry {
        requireSafePath(registryFile)
        val backup = File(rootDirectory, "$REGISTRY_FILE.bak")
        requireSafePath(backup)
        if (!registryFile.exists() && backup.exists() && (!backup.isFile || !FontIo.recoverBackup(registryFile))) {
            throw ModelStoreException("Unable to recover model registry; installed packages were preserved")
        }
        if (!registryFile.exists()) return Registry(emptyList())
        try {
            require(registryFile.isFile && registryFile.length() in 1..MAX_REGISTRY_BYTES.toLong()) { "Invalid registry size" }
            val bytes = registryFile.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_REGISTRY_BYTES) { "Registry exceeds its size limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return decodeRegistry(bytes)
        } catch (failure: Exception) {
            throw ModelStoreException("Model registry is unreadable; installed packages were preserved", failure)
        }
    }

    private fun writeRegistryLocked(registry: Registry) {
        val bytes = encodeRegistry(registry)
        if (bytes.size > MAX_REGISTRY_BYTES) throw ModelStoreException("Model registry exceeds its size limit")
        requireSafePath(registryFile)
        requireSafePath(File(rootDirectory, "$REGISTRY_FILE.bak"))
        FontIo.writeAndReplace(registryFile) { output ->
            output.write(bytes)
            beforeRegistryCommit()
        }
    }

    private fun cleanupOrphansLocked(records: List<InstalledModel>) {
        val retained = records.map { versionDirectory(it).absolutePath }.toHashSet()
        rootDirectory.listFiles()?.filter { MODEL_ID.matches(it.name) && !isReservedRootName(it.name) && it.isDirectory }
            ?.forEach { modelDirectory ->
                requireSafePath(modelDirectory)
                modelDirectory.listFiles()?.filterNot { it.absolutePath in retained }?.forEach(::deleteManagedTree)
                if (modelDirectory.list()?.isEmpty() == true) modelDirectory.delete()
            }
        rootDirectory.listFiles()?.filter { it.name.startsWith(".$REGISTRY_FILE.tmp-") && it.name.endsWith(".part") }
            ?.forEach(::deleteManagedTree)
    }

    private fun cleanupStagingLocked() {
        requireSafePath(stagingDirectory)
        if (!stagingDirectory.exists()) return
        stagingDirectory.listFiles()?.filterNot { it.name.endsWith(".lease") }?.forEach { file ->
            val leaseFile = File(stagingDirectory, "${file.name}.lease")
            requireSafePath(leaseFile)
            if (!leaseFile.exists()) {
                deleteManagedTree(file)
            } else {
                val lease = tryCleanupLease(leaseFile) ?: return@forEach
                lease.close()
                deleteManagedTree(file)
                leaseFile.delete()
            }
        }
        stagingDirectory.listFiles()?.filter { it.name.endsWith(".lease") && !File(stagingDirectory, it.name.removeSuffix(".lease")).exists() }
            ?.forEach { leaseFile ->
                val lease = tryCleanupLease(leaseFile) ?: return@forEach
                lease.close()
                leaseFile.delete()
            }
    }

    private fun tryCleanupLease(file: File): ModelStagingLease? {
        requireSafePath(file)
        // POSIX fcntl locks belong to a process. Opening and then closing a second channel for
        // this file can drop the original lock even when tryLock reports a JVM overlap. Skip
        // every in-process live lease before opening any descriptor, as well as using OS locks.
        if (synchronized(ACTIVE_LEASES_LOCK) { file.absolutePath in ACTIVE_LEASES }) return null
        beforeCleanupLeaseOpen(file)
        val channel = RandomAccessFile(file, "rw").channel
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (failure: Throwable) {
            channel.close()
            throw failure
        }
        if (lock == null) { channel.close(); return null }
        return ModelStagingLease(file, channel, lock)
    }

    private fun versionDirectory(record: InstalledModel): File = File(File(rootDirectory, record.modelId), "${record.versionCode}-${record.sha256.take(8)}")
    private fun packageFile(record: InstalledModel): File = File(versionDirectory(record), PACKAGE_FILE)

    private fun removeEmptyModelDirectory(modelId: String) {
        val directory = File(rootDirectory, modelId)
        requireSafePath(directory)
        if (directory.list()?.isEmpty() == true) directory.delete()
    }

    private fun ensureDirectory(directory: File) {
        requireSafePath(directory)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw ModelStoreException("Unable to create model directory")
        requireSafePath(directory)
    }

    private fun requireSafePath(file: File) {
        if (rootDirectory.canonicalFile != rootDirectory ||
            file != rootDirectory && !file.absolutePath.startsWith(rootDirectory.absolutePath + File.separator) ||
            file.canonicalFile != file.absoluteFile ||
            !file.exists() && file.parentFile?.list()?.contains(file.name) == true) {
            throw ModelStoreException("Model storage path contains a link or escapes its root")
        }
    }

    /** Check each parent before descending; deleting a link itself never visits its target. */
    private fun deleteManagedTree(file: File) {
        val parent = checkNotNull(file.parentFile)
        requireSafePath(parent)
        require(file != rootDirectory) { "Cannot delete the model store root" }
        if (file.canonicalFile == file.absoluteFile && file.isDirectory) file.listFiles()?.forEach(::deleteManagedTree)
        if (!file.delete() && file.exists()) throw ModelStoreException("Unable to remove model storage entry")
    }

    private fun <T> withStoreLock(action: () -> T): T {
        ensureDirectory(rootDirectory)
        val lockFile = FontIo.storeLockFile(rootDirectory)
        ensureDirectory(checkNotNull(lockFile.parentFile))
        requireSafePath(lockFile)
        return FontIo.withFileLock(lockFile) {
            requireSafePath(lockFile)
            action()
        }
    }

    private fun digest(input: InputStream): Pair<String, Long> = copyBounded(input, null)

    private fun copyBounded(input: InputStream, output: FileOutputStream?): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(32 * 1024)
        while (true) {
            checkInterrupted()
            var count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) break
                buffer[0] = byte.toByte()
                count = 1
            }
            total += count
            if (total > MAX_PACKAGE_BYTES) throw ModelInstallException("Compressed model package exceeds 100 MiB")
            output?.write(buffer, 0, count)
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } to total
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Model installation interrupted")
    }

    companion object {
        const val DIRECTORY_NAME = "codegen_models"
        const val MAX_PACKAGE_BYTES = 100L * 1024 * 1024
        private const val MAX_MODEL_CARD_BYTES = 1024 * 1024
        private const val REGISTRY_FILE = "registry.json"
        private const val STAGING_DIRECTORY = "staging"
        private const val PACKAGE_FILE = "model.ajcgm"
        private const val MAX_REGISTRY_BYTES = 4 * 1024 * 1024
        private const val MAX_MODELS = 1024
        private val RESERVED_ROOT_NAMES = setOf("catalog", STAGING_DIRECTORY, REGISTRY_FILE, "$REGISTRY_FILE.bak")
        private val MODEL_ID = Regex("[a-z0-9][a-z0-9._-]{1,63}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val KEY = Regex("([a-z0-9][a-z0-9._-]{1,63})@([1-9][0-9]{0,9})-([0-9a-f]{8})")
        private val STAGE_FILE = Regex("model-[A-Za-z0-9-]+\\.part")
        private val WINDOWS_DEVICE = Regex("con|prn|aux|nul|com[1-9]|lpt[1-9]")
        private val ACTIVE_LEASES_LOCK = Any()
        private val ACTIVE_LEASES = hashSetOf<String>()
        private const val MAX_JSON_DEPTH = 32

        /**
         * Encodes the registry (`registry.json`) with [SettingsJson] (docs/settings/ROADMAP.md P5.3), byte for byte as the
         * former `GsonBuilder().serializeNulls()` code wrote it on Android: the record members in the order the code added
         * them, models sorted by key, JSON nulls kept, strings escaped HTML-safe like Gson's default, and the members of
         * every manifest object in name order, because Gson took them from `Class.getDeclaredFields`, which ART returns
         * sorted by name (the dex format sorts the fields of a class), not in declaration order.
         * zh-CN: 以 SettingsJson 编码登记表 (ROADMAP P5.3), 与 Android 上原 `GsonBuilder().serializeNulls()` 代码写出的结果逐字节相同:
         * 记录成员按原代码添加的顺序, 模型按键排序, 保留 JSON null, 字符串按 Gson 默认的 HTML 安全方式转义; 清单各对象的成员按名称
         * 排序, 因为 Gson 按 `Class.getDeclaredFields` 取成员, ART 按名称排序返回 (dex 格式对类的字段排序), 而不是声明顺序.
         */
        internal fun encodeRegistry(registry: Registry): ByteArray {
            require(registry.models.size <= MAX_MODELS) { "Too many installed models" }
            val raw = linkedMapOf<String, Any?>(
                "format" to 1,
                "models" to registry.models.sortedBy { it.key }.map { model ->
                    linkedMapOf<String, Any?>(
                        "modelId" to model.modelId,
                        "versionCode" to model.versionCode,
                        "versionName" to model.manifest.versionName,
                        "featureSchemaVersion" to model.manifest.featureSchemaVersion,
                        "sha256" to model.sha256,
                        "size" to model.sizeBytes,
                        "source" to model.source.name,
                        "origin" to model.origin,
                        "installedAt" to model.installedAtMillis,
                        "manifest" to encodeManifest(model.manifest),
                    )
                },
            )
            registry.selectionChange?.let { change ->
                raw["selectionChange"] = linkedMapOf<String, Any?>("previousKey" to change.previousKey, "nextKey" to change.nextKey)
            }
            return SettingsJson.stringify(raw, htmlSafe = true).toByteArray(Charsets.UTF_8)
        }

        /**
         * Decodes the registry bytes ([encodeRegistry], and every file that Gson wrote) with the rules of the former
         * `ModelCatalogJson.parse` reader: at most [MAX_REGISTRY_BYTES], strict UTF-8, one strict JSON object without
         * duplicate keys or non-finite numbers, values nested less than 32 deep, then the same record validation. Both
         * readers skip one leading byte order mark. The strict scan rejects what [SettingsJson] alone would accept beyond
         * the strict Gson reader (`\'` and raw control characters in strings, control characters between tokens, and
         * comments between tokens on Android) and bounds the nesting before the recursive parser runs. Throws for any
         * failure; the caller reports the registry as unreadable.
         * zh-CN: 按原 `ModelCatalogJson.parse` 的规则解码登记表字节 ([encodeRegistry] 以及 Gson 写出的全部文件): 不超过
         * [MAX_REGISTRY_BYTES], 严格 UTF-8, 恰好一个严格 JSON 对象 (无重复键, 无非有限数字), 嵌套深度小于 32, 然后做相同的记录校验.
         * 两者都跳过开头的一个字节顺序标记. 严格扫描先拒绝 SettingsJson 单独会接受而严格 Gson 读取器不接受的内容 (字符串中的 `\'`
         * 与未转义控制字符, 记号之间的控制字符, 以及 Android 上记号之间的注释), 并在递归解析之前限制嵌套深度. 任何失败都抛异常, 由
         * 调用方报告登记表不可读.
         */
        internal fun decodeRegistry(bytes: ByteArray): Registry {
            require(bytes.size <= MAX_REGISTRY_BYTES) { "Registry exceeds its size limit" }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            requireStrictJson(text)
            val raw = SettingsJson.parseMap(text).also { requireDepth(it, 0) }
            require(integer(raw["format"]) == 1L) { "Unsupported registry format" }
            val models = array(raw["models"], maximum = MAX_MODELS).map { element ->
                val record = obj(element)
                val manifest = readManifest(obj(record["manifest"]))
                val digest = string(record["sha256"], 64).also { require(SHA256.matches(it)) { "Invalid model digest" } }
                require(string(record["modelId"], 64) == manifest.modelId && integer(record["versionCode"]).toInt() == manifest.versionCode &&
                    string(record["versionName"], 256) == manifest.versionName && integer(record["featureSchemaVersion"]).toInt() == manifest.featureSchemaVersion) { "Registry manifest identity mismatch" }
                InstalledModel(manifest, digest, integer(record["size"], 1, MAX_PACKAGE_BYTES),
                    ModelSource.valueOf(string(record["source"], 16)), string(record["origin"]), integer(record["installedAt"], 0, Long.MAX_VALUE))
            }
            require(models.map { it.key }.toSet().size == models.size && models.map { it.sha256 }.toSet().size == models.size) { "Duplicate registry model" }
            val change = raw["selectionChange"]?.let { element ->
                val change = obj(element)
                require(change.containsKey("previousKey") && change.containsKey("nextKey")) { "Incomplete selection change" }
                val previous = change["previousKey"]?.let { string(it, 90).also(::validateKey) }
                val next = change["nextKey"]?.let { string(it, 90).also(::validateKey) }
                require(next == null || models.any { it.key == next }) { "Invalid selection change" }
                SelectionChange(previous, next)
            }
            return Registry(models, change)
        }

        // Every member of each manifest object in name order (see encodeRegistry), JSON nulls included.
        private fun encodeManifest(manifest: ModelManifest): Map<String, Any?> = linkedMapOf<String, Any?>(
            "algorithm" to manifest.algorithm,
            "dataset" to manifest.dataset.let {
                linkedMapOf<String, Any?>("createdAt" to it.createdAt, "datasetId" to it.datasetId, "groups" to it.groups)
            },
            "displayName" to LinkedHashMap(manifest.displayName),
            "featureNamesHash" to manifest.featureNamesHash,
            "featureSchemaVersion" to manifest.featureSchemaVersion,
            "format" to manifest.format,
            "kind" to manifest.kind,
            "license" to manifest.license,
            "metrics" to linkedMapOf<String, Any?>("realTest" to encodeMetrics(manifest.metrics.realTest), "test" to encodeMetrics(manifest.metrics.test)),
            "minHostVersionCode" to manifest.minHostVersionCode,
            "model" to manifest.model.let {
                linkedMapOf<String, Any?>("depth" to it.depth, "features" to it.features, "file" to it.file, "sha256" to it.sha256, "size" to it.size, "trees" to it.trees)
            },
            "modelId" to manifest.modelId,
            "publisher" to manifest.publisher,
            "training" to manifest.training.let {
                linkedMapOf<String, Any?>("catboostVersion" to it.catboostVersion, "hostCommit" to it.hostCommit, "pythonVersion" to it.pythonVersion, "trainedAt" to it.trainedAt)
            },
            "versionCode" to manifest.versionCode,
            "versionName" to manifest.versionName,
        )

        private fun encodeMetrics(metrics: ModelMetricSummary): Map<String, Any?> = linkedMapOf<String, Any?>(
            "groups" to metrics.groups,
            "regret" to metrics.regret,
            "ruleTop1Survival" to metrics.ruleTop1Survival,
            "top1Survival" to metrics.top1Survival,
        )

        /** Decode durable display metadata without applying today's host/schema compatibility. */
        private fun readManifest(raw: Map<String, Any?>): ModelManifest {
            val modelId = string(raw["modelId"], 64).also(::validateModelId)
            require(integer(raw["format"]) == 1L) { "Unsupported manifest format" }
            val names = obj(raw["displayName"])
            require(names.size in 1..128 && names.containsKey("en")) { "Missing display name" }
            val displayNames = names.keys.associateWith { language ->
                require(Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*").matches(language)) { "Invalid language" }
                string(names[language], 256)
            }
            require(string(raw["kind"]) == "ranker" && string(raw["algorithm"]) == "catboost-oblivious") { "Unsupported model kind" }
            val featureHash = string(raw["featureNamesHash"], 16).also { require(Regex("[0-9a-f]{16}").matches(it)) }
            val model = obj(raw["model"])
            require(string(model["file"]) == "model.bin") { "Invalid model file" }
            val binary = ModelBinaryMetadata("model.bin", integer(model["size"], 32, ModelPackageReader.MAX_MODEL_BYTES.toLong()),
                string(model["sha256"], 64).also { require(SHA256.matches(it)) }, integer(model["trees"], 1, 4096).toInt(),
                integer(model["depth"], 1, 8).toInt(), integer(model["features"], 1, 65535).toInt())
            val dataset = obj(raw["dataset"])
            val metrics = obj(raw["metrics"])
            val training = obj(raw["training"])
            fun timestamp(value: Any?) = string(value, 64).also { OffsetDateTime.parse(it) }
            return ModelManifest(1, modelId, Collections.unmodifiableMap(displayNames), integer(raw["versionCode"]).toInt(),
                string(raw["versionName"], 256), "ranker", "catboost-oblivious", integer(raw["featureSchemaVersion"]).toInt(), featureHash, binary,
                ModelDatasetMetadata(string(dataset["datasetId"]), integer(dataset["groups"]).toInt(), timestamp(dataset["createdAt"])),
                ModelMetrics(readMetrics(obj(metrics["test"])), readMetrics(obj(metrics["realTest"]))),
                ModelTrainingMetadata(string(training["catboostVersion"]), string(training["pythonVersion"]),
                    string(training["hostCommit"], 40).also { require(Regex("[0-9a-f]{40}").matches(it)) }, timestamp(training["trainedAt"])),
                integer(raw["minHostVersionCode"]).toInt(), string(raw["license"], 256), string(raw["publisher"], 256))
        }

        private fun readMetrics(raw: Map<String, Any?>): ModelMetricSummary {
            val groups = integer(raw["groups"], 0).toInt()
            fun metric(name: String): Double? {
                require(raw.containsKey(name)) { "Missing metric" }
                val value = raw[name]
                if (groups == 0) { require(value == null); return null }
                require(value is Number)
                return value.toDouble().also { require(it.isFinite() && it in 0.0..1.0) }
            }
            return ModelMetricSummary(metric("top1Survival"), metric("ruleTop1Survival"), metric("regret"), groups)
        }

        private fun validateModelId(id: String) {
            require(MODEL_ID.matches(id) && !isReservedRootName(id) && !id.endsWith('.') &&
                !WINDOWS_DEVICE.matches(id.substringBefore('.'))) { "Invalid or reserved model id" }
        }

        private fun isReservedRootName(name: String): Boolean = name in RESERVED_ROOT_NAMES || name.startsWith("catalog-debug-")

        private fun validateKey(key: String) {
            val match = requireNotNull(KEY.matchEntire(key)) { "Invalid model key" }
            validateModelId(match.groupValues[1])
            require(match.groupValues[2].toIntOrNull()?.let { it > 0 } == true) { "Invalid model version" }
        }

        // The bounded plain-value checks of ModelCatalogJson (obj / string / integer / array) for SettingsJson values.
        private fun obj(value: Any?): Map<String, Any?> {
            require(value is Map<*, *>) { "Expected an object" }
            @Suppress("UNCHECKED_CAST")
            return value as Map<String, Any?>
        }

        private fun string(value: Any?, maxLength: Int = 1024): String {
            require(value is String) { "Expected a string" }
            return value.also { require(it.isNotBlank() && it.length <= maxLength && it.none(Char::isISOControl)) { "Invalid bounded string" } }
        }

        // SettingsJson reads a lexical integer within Long as Int or Long; a fraction, an exponent or a larger value is a Double.
        private fun integer(value: Any?, minimum: Long = 1, maximum: Long = Int.MAX_VALUE.toLong()): Long {
            require(value is Number) { "Expected an integer" }
            require(value is Int || value is Long) { "Expected a lexical integer" }
            return value.toLong().also { require(it in minimum..maximum) { "Integer is out of range" } }
        }

        private fun array(value: Any?, minimum: Int = 0, maximum: Int = 4096): List<*> {
            require(value is List<*>) { "Expected an array" }
            return value.also { require(it.size in minimum..maximum) { "Array length is out of range" } }
        }

        // Values nested 32 or more deep (the root is at depth 0) were rejected by ModelCatalogJson.
        private fun requireDepth(value: Any?, depth: Int) {
            require(depth < MAX_JSON_DEPTH) { "Registry JSON is nested too deeply" }
            when (value) {
                is Map<*, *> -> value.values.forEach { requireDepth(it, depth + 1) }
                is List<*> -> value.forEach { requireDepth(it, depth + 1) }
            }
        }

        // Lexical rules of the strict Gson reader that SettingsJson does not enforce, plus a nesting bound for its parser.
        // A leading byte order mark passes the scan (it is above the control characters); both readers skip it.
        private fun requireStrictJson(text: String) {
            var nesting = 0
            var index = 0
            while (index < text.length) {
                val c = text[index++]
                when {
                    c == '"' -> index = skipString(text, index)
                    c == '{' || c == '[' -> require(++nesting <= MAX_JSON_DEPTH) { "Registry JSON is nested too deeply" }
                    c == '}' || c == ']' -> nesting--
                    c == '/' || c == '#' -> throw IllegalArgumentException("Comments are not allowed")
                    c < ' ' -> require(c == '\t' || c == '\n' || c == '\r') { "Unexpected control character" }
                }
            }
        }

        // Returns the index after the closing quote of the string whose content starts at [start].
        private fun skipString(text: String, start: Int): Int {
            var index = start
            while (index < text.length) {
                val c = text[index++]
                when {
                    c == '"' -> return index
                    c < ' ' -> throw IllegalArgumentException("Unescaped control character in a string")
                    c == '\\' -> {
                        require(index < text.length) { "Unterminated string" }
                        when (text[index++]) {
                            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                            'u' -> {
                                require(index + 4 <= text.length && (index until index + 4).all { text[it] in HEX_DIGITS }) { "Malformed unicode escape" }
                                index += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape sequence")
                        }
                    }
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private const val HEX_DIGITS = "0123456789abcdefABCDEF"
    }
}
