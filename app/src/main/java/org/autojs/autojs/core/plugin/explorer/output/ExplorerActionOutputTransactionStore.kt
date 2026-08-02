package org.autojs.autojs.core.plugin.explorer.output

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.UUID

internal class ExplorerActionOutputTransactionStore(
    private val journalRoot: File,
    private val stagingRoot: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {

    private val lock = Any()

    fun prepare(request: ExplorerActionOutputPrepareRequest): ExplorerActionOutputTransaction = synchronized(lock) {
        val source = File(request.sourcePath).canonicalFile
        require(source.isFile && source.canRead()) { "Explorer Action output source is not a readable file" }
        val sourceParent = source.parentFile?.canonicalFile
            ?: throw IllegalArgumentException("Explorer Action output source has no parent directory")
        require(sourceParent.isDirectory && sourceParent.canWrite()) {
            "Explorer Action output source directory is not writable"
        }
        require(request.pluginPackageName.isNotBlank()) { "Plugin package name is empty" }
        require(request.pluginActivityClassName.isNotBlank()) { "Plugin activity class name is empty" }
        require(request.pluginUid >= 0) { "Plugin UID is invalid" }
        require(request.pluginVersionCode >= 0L) { "Plugin version code is invalid" }
        require(request.pluginLastUpdateTime > 0L) { "Plugin last update time is invalid" }
        require(request.pluginSigningDigests.isNotEmpty()) { "Plugin signing digests are empty" }
        require(request.pluginSigningDigests.all(SHA256_PATTERN::matches)) { "Plugin signing digest is invalid" }
        require(request.sourceParentDevice >= 0L && request.sourceParentInode > 0L) {
            "Explorer Action output source parent identity is invalid"
        }
        require(request.actionId.isNotBlank()) { "Explorer Action ID is empty" }
        require(request.allowedOutputMimeTypes.isNotEmpty()) { "Output MIME types are empty" }
        require(request.outputNameSuffix.isNotBlank()) { "Output name suffix is empty" }
        require(request.maxOutputBytes > 0L) { "Maximum output size must be positive" }

        ensureDirectory(journalRoot)
        ensureDirectory(stagingRoot)
        check(list().none { transaction ->
            transaction.state != ExplorerActionOutputTransactionState.COMMITTED &&
                transaction.state != ExplorerActionOutputTransactionState.ROLLED_BACK
        }) { "Another Explorer Action output transaction is already active" }
        val id = idFactory().also(::requireValidId)
        val journalDirectory = journalDirectory(id)
        val stagingDirectory = stagingDirectory(id)
        check(!journalDirectory.exists() && !stagingDirectory.exists()) {
            "Explorer Action output transaction already exists: $id"
        }
        if (!journalDirectory.mkdir()) throw IOException("Unable to create output transaction journal directory")
        try {
            if (!stagingDirectory.mkdir()) throw IOException("Unable to create output transaction staging directory")
            val staging = stagingFile(id)
            if (!staging.createNewFile()) throw IOException("Unable to create output transaction staging file")
            val now = clock()
            val transaction = ExplorerActionOutputTransaction(
                id = id,
                state = ExplorerActionOutputTransactionState.PREPARED,
                pluginPackageName = request.pluginPackageName,
                pluginActivityClassName = request.pluginActivityClassName,
                pluginUid = request.pluginUid,
                pluginVersionCode = request.pluginVersionCode,
                pluginLastUpdateTime = request.pluginLastUpdateTime,
                pluginSigningDigests = request.pluginSigningDigests.toSortedSet(),
                actionId = request.actionId,
                protocolVersion = request.protocolVersion,
                sourcePath = source.path,
                sourceParentPath = sourceParent.path,
                sourceParentDevice = request.sourceParentDevice,
                sourceParentInode = request.sourceParentInode,
                sourceDisplayName = request.sourceDisplayName,
                sourceMimeType = request.sourceMimeType,
                sourceSize = source.length(),
                sourceLastModified = source.lastModified(),
                sourceSurface = request.sourceSurface,
                allowedOutputMimeTypes = request.allowedOutputMimeTypes.toSortedSet(),
                outputNameSuffix = request.outputNameSuffix,
                maxOutputBytes = request.maxOutputBytes,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            write(transaction)
            transaction
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            journalDirectory.deleteRecursively()
            throw error
        }
    }

    fun load(id: String): ExplorerActionOutputTransaction? = synchronized(lock) {
        requireValidId(id)
        val journal = journalFile(id)
        val backup = journalBackupFile(id)
        when {
            journal.isFile -> runCatching { read(journal) }
                .recoverCatching { restoreBackup(journal, backup) }
                .getOrThrow()
            backup.isFile -> restoreBackup(journal, backup)
            else -> null
        }
    }

    fun list(): List<ExplorerActionOutputTransaction> = synchronized(lock) {
        if (!journalRoot.isDirectory) return@synchronized emptyList()
        journalRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { directory ->
                runCatching {
                    requireValidId(directory.name)
                    load(directory.name)
                }.getOrNull()
            }
            .sortedBy(ExplorerActionOutputTransaction::createdAtMillis)
            .toList()
    }

    fun update(
        id: String,
        transform: (ExplorerActionOutputTransaction) -> ExplorerActionOutputTransaction,
    ): ExplorerActionOutputTransaction = synchronized(lock) {
        val current = load(id) ?: throw IOException("Explorer Action output transaction does not exist: $id")
        val updated = transform(current)
        require(updated.id == current.id) { "Transaction ID cannot be changed" }
        write(updated)
        updated
    }

    fun transition(
        id: String,
        next: ExplorerActionOutputTransactionState,
    ): ExplorerActionOutputTransaction = update(id) { current ->
        current.transitionTo(next, clock())
    }

    fun createSnapshot(id: String): File = synchronized(lock) {
        val transaction = load(id) ?: throw IOException("Explorer Action output transaction does not exist: $id")
        require(transaction.state == ExplorerActionOutputTransactionState.RESULT_RECEIVED) {
            "Output can only be snapshotted after a successful result"
        }
        val staging = stagingFile(id)
        if (!staging.isFile) throw IOException("Explorer Action output staging file is missing")
        val stagingSize = staging.length()
        if (stagingSize !in 1L..transaction.maxOutputBytes) {
            throw IOException("Explorer Action output staging file has an invalid size")
        }
        val snapshot = snapshotFile(id)
        val temporary = File(snapshot.parentFile, "$SNAPSHOT_FILE_NAME.tmp")
        temporary.delete()
        try {
            FileInputStream(staging).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > transaction.maxOutputBytes) {
                            throw IOException("Explorer Action output exceeded its size limit while snapshotting")
                        }
                        output.write(buffer, 0, count)
                    }
                    if (copied != stagingSize) {
                        throw IOException("Explorer Action output changed while it was being snapshotted")
                    }
                    output.fd.sync()
                }
            }
            if (snapshot.exists() && !snapshot.delete()) throw IOException("Unable to replace output snapshot")
            if (!temporary.renameTo(snapshot)) throw IOException("Unable to publish output snapshot")
            transition(id, ExplorerActionOutputTransactionState.SNAPSHOTTED)
            snapshot
        } finally {
            temporary.delete()
        }
    }

    fun stagingFile(id: String): File {
        requireValidId(id)
        return descendant(stagingRoot, "$id/$STAGING_FILE_NAME")
    }

    fun snapshotFile(id: String): File {
        requireValidId(id)
        return descendant(journalRoot, "$id/$SNAPSHOT_FILE_NAME")
    }

    fun cleanupArtifacts(id: String, keepJournal: Boolean = false) = synchronized(lock) {
        requireValidId(id)
        stagingDirectory(id).deleteRecursively()
        val journalDirectory = journalDirectory(id)
        snapshotFile(id).delete()
        File(journalDirectory, "$SNAPSHOT_FILE_NAME.tmp").delete()
        if (!keepJournal) journalDirectory.deleteRecursively()
    }

    private fun write(transaction: ExplorerActionOutputTransaction) {
        val directory = journalDirectory(transaction.id)
        ensureDirectory(directory)
        val journal = journalFile(transaction.id)
        val backup = journalBackupFile(transaction.id)
        val temporary = File(directory, "$JOURNAL_FILE_NAME.tmp")
        val properties = transaction.toProperties()
        try {
            FileOutputStream(temporary).use { output ->
                properties.store(output, null)
                output.fd.sync()
            }
            if (backup.exists() && !backup.delete()) {
                throw IOException("Unable to replace output transaction journal backup")
            }
            if (journal.exists() && !journal.renameTo(backup)) {
                throw IOException("Unable to back up output transaction journal")
            }
            if (!temporary.renameTo(journal)) {
                if (backup.isFile) backup.renameTo(journal)
                throw IOException("Unable to publish output transaction journal")
            }
            backup.delete()
        } finally {
            temporary.delete()
        }
    }

    private fun read(file: File): ExplorerActionOutputTransaction {
        val properties = Properties().apply {
            FileInputStream(file).use(::load)
        }
        return properties.toTransaction().also { transaction ->
            require(transaction.id == file.parentFile?.name) { "Transaction journal ID does not match its directory" }
        }
    }

    private fun restoreBackup(
        journal: File,
        backup: File,
    ): ExplorerActionOutputTransaction {
        val transaction = read(backup)
        if (journal.exists() && !journal.delete()) {
            throw IOException("Unable to remove damaged output transaction journal")
        }
        if (!backup.renameTo(journal)) {
            throw IOException("Unable to restore output transaction journal backup")
        }
        return transaction
    }

    private fun ExplorerActionOutputTransaction.toProperties() = Properties().apply {
        setProperty(KEY_ID, id)
        setProperty(KEY_STATE, state.name)
        setProperty(KEY_PLUGIN_PACKAGE, pluginPackageName)
        setProperty(KEY_PLUGIN_ACTIVITY, pluginActivityClassName)
        setProperty(KEY_PLUGIN_UID, pluginUid.toString())
        setProperty(KEY_PLUGIN_VERSION_CODE, pluginVersionCode.toString())
        setProperty(KEY_PLUGIN_LAST_UPDATE_TIME, pluginLastUpdateTime.toString())
        setProperty(KEY_PLUGIN_SIGNING_COUNT, pluginSigningDigests.size.toString())
        pluginSigningDigests.sorted().forEachIndexed { index, digest ->
            setProperty("$KEY_PLUGIN_SIGNING_PREFIX$index", digest)
        }
        setProperty(KEY_ACTION_ID, actionId)
        setProperty(KEY_PROTOCOL_VERSION, protocolVersion.toString())
        setProperty(KEY_SOURCE_PATH, sourcePath)
        setProperty(KEY_SOURCE_PARENT_PATH, sourceParentPath)
        setProperty(KEY_SOURCE_PARENT_DEVICE, sourceParentDevice.toString())
        setProperty(KEY_SOURCE_PARENT_INODE, sourceParentInode.toString())
        setProperty(KEY_SOURCE_DISPLAY_NAME, sourceDisplayName)
        setProperty(KEY_SOURCE_MIME_TYPE, sourceMimeType)
        setProperty(KEY_SOURCE_SIZE, sourceSize.toString())
        setProperty(KEY_SOURCE_LAST_MODIFIED, sourceLastModified.toString())
        setProperty(KEY_SOURCE_SURFACE, sourceSurface)
        setProperty(KEY_OUTPUT_MIME_COUNT, allowedOutputMimeTypes.size.toString())
        allowedOutputMimeTypes.sorted().forEachIndexed { index, mimeType ->
            setProperty("$KEY_OUTPUT_MIME_PREFIX$index", mimeType)
        }
        setProperty(KEY_OUTPUT_SUFFIX, outputNameSuffix)
        setProperty(KEY_MAX_OUTPUT_BYTES, maxOutputBytes.toString())
        setProperty(KEY_CREATED_AT, createdAtMillis.toString())
        setProperty(KEY_UPDATED_AT, updatedAtMillis.toString())
        detectedOutputMimeType?.let { setProperty(KEY_DETECTED_OUTPUT_MIME, it) }
        snapshotSha256?.let { setProperty(KEY_SNAPSHOT_SHA256, it) }
        finalPath?.let { setProperty(KEY_FINAL_PATH, it) }
        publishTemporaryPath?.let { setProperty(KEY_PUBLISH_TEMPORARY_PATH, it) }
        finalSha256?.let { setProperty(KEY_FINAL_SHA256, it) }
    }

    private fun Properties.toTransaction(): ExplorerActionOutputTransaction {
        val signingCount = requireLong(KEY_PLUGIN_SIGNING_COUNT).toInt()
        require(signingCount in 1..MAX_SIGNING_DIGESTS) { "Invalid plugin signing digest count" }
        val signingDigests = (0 until signingCount).mapTo(LinkedHashSet()) { index ->
            requireProperty("$KEY_PLUGIN_SIGNING_PREFIX$index").also { digest ->
                require(SHA256_PATTERN.matches(digest)) { "Invalid plugin signing digest" }
            }
        }
        val mimeCount = requireLong(KEY_OUTPUT_MIME_COUNT).toInt()
        require(mimeCount in 1..MAX_MIME_TYPES) { "Invalid output MIME type count" }
        val mimeTypes = (0 until mimeCount).mapTo(LinkedHashSet()) { index ->
            requireProperty("$KEY_OUTPUT_MIME_PREFIX$index")
        }
        return ExplorerActionOutputTransaction(
            id = requireProperty(KEY_ID).also(::requireValidId),
            state = ExplorerActionOutputTransactionState.valueOf(requireProperty(KEY_STATE)),
            pluginPackageName = requireProperty(KEY_PLUGIN_PACKAGE),
            pluginActivityClassName = requireProperty(KEY_PLUGIN_ACTIVITY),
            pluginUid = requireLong(KEY_PLUGIN_UID).toInt(),
            pluginVersionCode = requireLong(KEY_PLUGIN_VERSION_CODE),
            pluginLastUpdateTime = requireLong(KEY_PLUGIN_LAST_UPDATE_TIME),
            pluginSigningDigests = signingDigests,
            actionId = requireProperty(KEY_ACTION_ID),
            protocolVersion = requireLong(KEY_PROTOCOL_VERSION).toInt(),
            sourcePath = requireProperty(KEY_SOURCE_PATH),
            sourceParentPath = requireProperty(KEY_SOURCE_PARENT_PATH),
            sourceParentDevice = requireLong(KEY_SOURCE_PARENT_DEVICE),
            sourceParentInode = requireLong(KEY_SOURCE_PARENT_INODE),
            sourceDisplayName = requireProperty(KEY_SOURCE_DISPLAY_NAME),
            sourceMimeType = requireProperty(KEY_SOURCE_MIME_TYPE),
            sourceSize = requireLong(KEY_SOURCE_SIZE),
            sourceLastModified = requireLong(KEY_SOURCE_LAST_MODIFIED),
            sourceSurface = requireProperty(KEY_SOURCE_SURFACE),
            allowedOutputMimeTypes = mimeTypes,
            outputNameSuffix = requireProperty(KEY_OUTPUT_SUFFIX),
            maxOutputBytes = requireLong(KEY_MAX_OUTPUT_BYTES),
            createdAtMillis = requireLong(KEY_CREATED_AT),
            updatedAtMillis = requireLong(KEY_UPDATED_AT),
            detectedOutputMimeType = getProperty(KEY_DETECTED_OUTPUT_MIME),
            snapshotSha256 = getProperty(KEY_SNAPSHOT_SHA256),
            finalPath = getProperty(KEY_FINAL_PATH),
            publishTemporaryPath = getProperty(KEY_PUBLISH_TEMPORARY_PATH),
            finalSha256 = getProperty(KEY_FINAL_SHA256),
        )
    }

    private fun Properties.requireProperty(key: String): String =
        getProperty(key)?.takeIf(String::isNotEmpty)
            ?: throw IOException("Missing output transaction property: $key")

    private fun Properties.requireLong(key: String): Long =
        requireProperty(key).toLongOrNull()
            ?: throw IOException("Invalid output transaction number: $key")

    private fun journalDirectory(id: String): File {
        requireValidId(id)
        return descendant(journalRoot, id)
    }

    private fun stagingDirectory(id: String): File {
        requireValidId(id)
        return descendant(stagingRoot, id)
    }

    private fun journalFile(id: String) = File(journalDirectory(id), JOURNAL_FILE_NAME)

    private fun journalBackupFile(id: String) = File(journalDirectory(id), "$JOURNAL_FILE_NAME.bak")

    private fun descendant(root: File, relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        require(candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "Explorer Action output path escapes its storage root"
        }
        return candidate
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create Explorer Action output directory: ${directory.path}")
        }
    }

    private fun requireValidId(id: String) {
        require(TRANSACTION_ID_PATTERN.matches(id)) { "Invalid Explorer Action output transaction ID" }
    }

    companion object {
        const val JOURNAL_ROOT_DIRECTORY_NAME = "explorer-action-output-journal"
        const val STAGING_ROOT_DIRECTORY_NAME = "explorer-action-output"

        private const val JOURNAL_FILE_NAME = "transaction.properties"
        private const val STAGING_FILE_NAME = "output.part"
        private const val SNAPSHOT_FILE_NAME = "output.snapshot"
        private const val MAX_MIME_TYPES = 128
        private const val MAX_SIGNING_DIGESTS = 32

        private const val KEY_ID = "id"
        private const val KEY_STATE = "state"
        private const val KEY_PLUGIN_PACKAGE = "plugin.package"
        private const val KEY_PLUGIN_ACTIVITY = "plugin.activity"
        private const val KEY_PLUGIN_UID = "plugin.uid"
        private const val KEY_PLUGIN_VERSION_CODE = "plugin.version.code"
        private const val KEY_PLUGIN_LAST_UPDATE_TIME = "plugin.last.update.time"
        private const val KEY_PLUGIN_SIGNING_COUNT = "plugin.signing.count"
        private const val KEY_PLUGIN_SIGNING_PREFIX = "plugin.signing."
        private const val KEY_ACTION_ID = "action.id"
        private const val KEY_PROTOCOL_VERSION = "protocol.version"
        private const val KEY_SOURCE_PATH = "source.path"
        private const val KEY_SOURCE_PARENT_PATH = "source.parent.path"
        private const val KEY_SOURCE_PARENT_DEVICE = "source.parent.device"
        private const val KEY_SOURCE_PARENT_INODE = "source.parent.inode"
        private const val KEY_SOURCE_DISPLAY_NAME = "source.display.name"
        private const val KEY_SOURCE_MIME_TYPE = "source.mime.type"
        private const val KEY_SOURCE_SIZE = "source.size"
        private const val KEY_SOURCE_LAST_MODIFIED = "source.last.modified"
        private const val KEY_SOURCE_SURFACE = "source.surface"
        private const val KEY_OUTPUT_MIME_COUNT = "output.mime.count"
        private const val KEY_OUTPUT_MIME_PREFIX = "output.mime."
        private const val KEY_OUTPUT_SUFFIX = "output.suffix"
        private const val KEY_MAX_OUTPUT_BYTES = "output.max.bytes"
        private const val KEY_CREATED_AT = "created.at"
        private const val KEY_UPDATED_AT = "updated.at"
        private const val KEY_DETECTED_OUTPUT_MIME = "output.detected.mime"
        private const val KEY_SNAPSHOT_SHA256 = "output.snapshot.sha256"
        private const val KEY_FINAL_PATH = "output.final.path"
        private const val KEY_PUBLISH_TEMPORARY_PATH = "output.publish.temporary.path"
        private const val KEY_FINAL_SHA256 = "output.final.sha256"

        private val TRANSACTION_ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
