package org.autojs.autojs.core.plugin.explorer.session

import android.annotation.SuppressLint
import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

internal enum class ExplorerActionReplacementHistoryState {
    PREPARED,
    AVAILABLE,
    RESTORING,
    RESTORED,
    STALE,
    RECOVERY_REQUIRED,
}

internal data class ExplorerActionReplacementHistoryRecord(
    val id: String,
    val transactionId: String,
    val pluginUid: Int,
    val parentPath: String,
    val parentDevice: Long,
    val parentInode: Long,
    val targetPath: String,
    val displayName: String,
    val backupRelativePath: String,
    val previousSha256: String,
    val previousIdentity: ExplorerActionFileIdentity,
    val replacementIdentity: ExplorerActionFileIdentity,
    val state: ExplorerActionReplacementHistoryState,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val restoreTempPath: String? = null,
    val restoredIdentity: ExplorerActionFileIdentity? = null,
) {
    init {
        require(isCanonicalReplacementHistoryUuid(id))
        require(isCanonicalReplacementHistoryUuid(transactionId))
        require(pluginUid >= 0)
        require(parentPath.isNotBlank() && targetPath.isNotBlank())
        require(displayName.length in 1..ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH)
        require(backupRelativePath.isNotBlank())
        require(previousSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
        require((restoreTempPath == null) == (restoredIdentity == null))
    }

    val protocolState: Int
        get() = when (state) {
            ExplorerActionReplacementHistoryState.AVAILABLE ->
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_AVAILABLE
            ExplorerActionReplacementHistoryState.RESTORED ->
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_RESTORED
            ExplorerActionReplacementHistoryState.STALE ->
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_STALE
            ExplorerActionReplacementHistoryState.RECOVERY_REQUIRED ->
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_RECOVERY_REQUIRED
            ExplorerActionReplacementHistoryState.PREPARED,
            ExplorerActionReplacementHistoryState.RESTORING,
            -> ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_UNKNOWN
        }

    val canBeAutomaticallyRemoved: Boolean
        get() = when (state) {
            ExplorerActionReplacementHistoryState.AVAILABLE,
            ExplorerActionReplacementHistoryState.RESTORED,
            ExplorerActionReplacementHistoryState.STALE,
            -> true
            ExplorerActionReplacementHistoryState.PREPARED,
            ExplorerActionReplacementHistoryState.RESTORING,
            ExplorerActionReplacementHistoryState.RECOVERY_REQUIRED,
            -> false
        }
}

internal data class ExplorerActionReplacementUndoResult(
    val record: ExplorerActionReplacementHistoryRecord,
    val restoredIdentity: ExplorerActionFileIdentity,
)

/** Durable, host-private previous-version store for Explorer Action v18 target replacement. */
internal class ExplorerActionReplacementHistoryStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterRestorePublished: () -> Unit = {},
) {

    private val root = File(context.noBackupFilesDir, ROOT_DIRECTORY_NAME)
    private val recordsDirectory = File(root, RECORDS_DIRECTORY_NAME)
    private val blobsDirectory = File(root, BLOBS_DIRECTORY_NAME)

    fun recover() = synchronized(LOCK) {
        recoverLocked(clock())
    }

    fun prepare(
        transactionId: String,
        pluginUid: Int,
        parent: File,
        target: File,
        previousIdentity: ExplorerActionFileIdentity,
        replacementIdentity: ExplorerActionFileIdentity,
    ): ExplorerActionReplacementHistoryRecord = synchronized(LOCK) {
        require(isCanonicalReplacementHistoryUuid(transactionId)) {
            "Explorer Action replacement transaction ID is invalid"
        }
        require(pluginUid >= 0)
        val canonicalParent = parent.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(canonicalTarget.parentFile == canonicalParent) {
            "Explorer Action replacement history target escapes its parent"
        }
        require(
            ExplorerActionFileIdentity.captureRegularFile(canonicalTarget) == previousIdentity,
        ) { "Explorer Action replacement target changed before history capture" }
        require(previousIdentity.size <= ExplorerActionProtocol.MAX_OUTPUT_BYTES) {
            "Explorer Action replacement target is too large for recoverable history"
        }

        ensureDirectoriesLocked()
        recoverLocked(clock())
        check(
            listRecordsLocked().none { record ->
                record.pluginUid == pluginUid &&
                    record.targetPath == canonicalTarget.path &&
                    !record.canBeAutomaticallyRemoved
            },
        ) { "Explorer Action replacement history requires recovery before another replacement" }
        makeRoomForLocked(previousIdentity.size)
        requireUsableSpace(root, previousIdentity.size)

        val id = UUID.randomUUID().toString()
        val partial = File(blobsDirectory, "$id$PARTIAL_SUFFIX")
        val backup = File(blobsDirectory, "$id$BACKUP_SUFFIX")
        check(partial.createNewFile()) {
            "Explorer Action replacement history staging file already exists"
        }
        var recordWritten = false
        try {
            val sha256 = copyRegularFile(
                source = canonicalTarget,
                destination = partial,
                expectedSourceIdentity = previousIdentity,
            )
            check(!backup.exists()) {
                "Explorer Action replacement history backup already exists"
            }
            try {
                Os.rename(partial.path, backup.path)
            } catch (error: ErrnoException) {
                throw IOException("Explorer Action replacement history could not be published", error)
            }
            syncDirectory(blobsDirectory)

            val parentIdentity = ExplorerActionFileIdentity.capture(canonicalParent)
            val now = clock().coerceAtLeast(0L)
            val record = ExplorerActionReplacementHistoryRecord(
                id = id,
                transactionId = transactionId,
                pluginUid = pluginUid,
                parentPath = canonicalParent.path,
                parentDevice = parentIdentity.device,
                parentInode = parentIdentity.inode,
                targetPath = canonicalTarget.path,
                displayName = canonicalTarget.name,
                backupRelativePath = "$BLOBS_DIRECTORY_NAME/${backup.name}",
                previousSha256 = sha256,
                previousIdentity = previousIdentity,
                replacementIdentity = replacementIdentity,
                state = ExplorerActionReplacementHistoryState.PREPARED,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            writeRecordLocked(record)
            recordWritten = true
            record
        } finally {
            partial.delete()
            if (!recordWritten) backup.delete()
        }
    }

    fun markAvailable(historyId: String): ExplorerActionReplacementHistoryRecord = synchronized(LOCK) {
        val record = requireNotNull(readRecordLocked(historyId)) {
            "Explorer Action replacement history is unknown"
        }
        require(record.state == ExplorerActionReplacementHistoryState.PREPARED) {
            "Explorer Action replacement history is not awaiting commit"
        }
        check(hasValidBackupLocked(record)) {
            "Explorer Action replacement history backup is unavailable"
        }
        val target = File(record.targetPath)
        check(captureRegularFileOrNull(target) == record.replacementIdentity) {
            "Explorer Action replacement target does not match its committed version"
        }
        val available = record.copy(
            state = ExplorerActionReplacementHistoryState.AVAILABLE,
            updatedAtMillis = record.nextUpdatedAt(),
        )
        writeRecordLocked(available)
        retireOtherTargetRecordsLocked(available)
        cleanupLocked(clock(), protectedId = available.id)
        available
    }

    fun discardPrepared(historyId: String) = synchronized(LOCK) {
        val record = readRecordLocked(historyId) ?: return@synchronized
        if (record.state == ExplorerActionReplacementHistoryState.PREPARED) {
            removeRecordLocked(record)
        }
    }

    fun queryLatest(
        pluginUid: Int,
        target: File,
    ): ExplorerActionReplacementHistoryRecord? = synchronized(LOCK) {
        recoverLocked(clock())
        val canonicalTarget = target.canonicalFile
        listRecordsLocked()
            .filter { record ->
                record.pluginUid == pluginUid && record.targetPath == canonicalTarget.path
            }
            .maxByOrNull(ExplorerActionReplacementHistoryRecord::createdAtMillis)
    }

    fun undo(
        pluginUid: Int,
        target: File,
        historyId: String,
    ): ExplorerActionReplacementUndoResult = synchronized(LOCK) {
        require(isCanonicalReplacementHistoryUuid(historyId)) {
            "Explorer Action replacement history ID is invalid"
        }
        recoverLocked(clock())
        val record = requireNotNull(readRecordLocked(historyId)) {
            "Explorer Action replacement history is unknown"
        }
        val canonicalTarget = target.canonicalFile
        require(record.pluginUid == pluginUid && record.targetPath == canonicalTarget.path) {
            "Explorer Action replacement history does not belong to this target"
        }
        check(record.state == ExplorerActionReplacementHistoryState.AVAILABLE) {
            "Explorer Action replacement history is not available"
        }
        if (!hasMatchingParentIdentity(record, canonicalTarget)) {
            markStaleLocked(record)
            error("Explorer Action replacement history parent changed")
        }
        if (captureRegularFileOrNull(canonicalTarget) != record.replacementIdentity) {
            markStaleLocked(record)
            error("Explorer Action replacement target changed after commit")
        }

        val backup = backupFileLocked(record)
        if (!hasValidBackupLocked(record)) {
            markRecoveryRequiredLocked(record)
            error("Explorer Action replacement history backup is unavailable")
        }
        val parent = requireNotNull(canonicalTarget.parentFile)
        requireUsableSpace(parent, record.previousIdentity.size)
        val restoreTemp = File(parent, ".autojs6-explorer-${UUID.randomUUID()}.part")
        check(restoreTemp.createNewFile()) {
            "Explorer Action replacement restore staging file already exists"
        }

        var restoring: ExplorerActionReplacementHistoryRecord? = null
        var backupIntegrityFailed = false
        var renamed = false
        try {
            val restoredSha256 = copyRegularFile(
                source = backup,
                destination = restoreTemp,
                expectedSourceIdentity = ExplorerActionFileIdentity.captureRegularFile(backup),
            )
            if (restoredSha256 != record.previousSha256) {
                backupIntegrityFailed = true
                markRecoveryRequiredLocked(record)
                error("Explorer Action replacement history backup failed verification")
            }
            val restoredIdentity = ExplorerActionFileIdentity.captureRegularFile(restoreTemp)
            restoring = record.copy(
                state = ExplorerActionReplacementHistoryState.RESTORING,
                updatedAtMillis = record.nextUpdatedAt(),
                restoreTempPath = restoreTemp.canonicalPath,
                restoredIdentity = restoredIdentity,
            )
            writeRecordLocked(restoring)
            check(captureRegularFileOrNull(canonicalTarget) == record.replacementIdentity) {
                "Explorer Action replacement target changed before undo"
            }
            try {
                Os.rename(restoreTemp.path, canonicalTarget.path)
            } catch (error: ErrnoException) {
                throw IOException("Explorer Action previous archive version could not be restored", error)
            }
            renamed = true
            runCatching { syncDirectory(parent) }
            afterRestorePublished()
            val restored = restoring.copy(
                state = ExplorerActionReplacementHistoryState.RESTORED,
                updatedAtMillis = restoring.nextUpdatedAt(),
                restoreTempPath = null,
                restoredIdentity = null,
            )
            runCatching { writeRecordLocked(restored) }
            backup.delete()
            ExplorerActionReplacementUndoResult(restored, restoredIdentity)
        } catch (error: Throwable) {
            if (!renamed) {
                val current = restoring
                val currentTargetIdentity = captureRegularFileOrNull(canonicalTarget)
                if (
                    !backupIntegrityFailed &&
                    currentTargetIdentity == record.replacementIdentity
                ) {
                    runCatching {
                        writeRecordLocked(
                            record.copy(
                                state = ExplorerActionReplacementHistoryState.AVAILABLE,
                                updatedAtMillis = record.nextUpdatedAt(),
                            ),
                        )
                    }
                } else if (!backupIntegrityFailed) {
                    runCatching { markStaleLocked(current ?: record) }
                }
                deleteExactRestoreTemp(
                    restoreTemp,
                    restoring?.restoredIdentity ?: captureRegularFileOrNull(restoreTemp),
                )
                throw error
            }
            val restoredIdentity = restoring?.restoredIdentity
            if (restoredIdentity != null && captureRegularFileOrNull(canonicalTarget) == restoredIdentity) {
                val restored = record.copy(
                    state = ExplorerActionReplacementHistoryState.RESTORED,
                    updatedAtMillis = record.nextUpdatedAt(),
                )
                runCatching { writeRecordLocked(restored) }
                backup.delete()
                ExplorerActionReplacementUndoResult(restored, restoredIdentity)
            } else {
                markRecoveryRequiredLocked(restoring ?: record)
                throw IOException("Explorer Action replacement undo requires host recovery", error)
            }
        }
    }

    internal fun removeAllForTarget(pluginUid: Int, target: File) = synchronized(LOCK) {
        val targetPath = target.canonicalPath
        listRecordsLocked()
            .filter { it.pluginUid == pluginUid && it.targetPath == targetPath }
            .forEach(::removeRecordLocked)
    }

    private fun recoverLocked(now: Long) {
        if (!root.exists()) return
        val effectiveNow = now.coerceAtLeast(0L)
        ensureDirectoriesLocked()
        listRecordsLocked().forEach { record ->
            val currentIdentity = captureRegularFileOrNull(File(record.targetPath))
            when (record.state) {
                ExplorerActionReplacementHistoryState.PREPARED -> when (currentIdentity) {
                    record.replacementIdentity -> {
                        if (hasValidBackupLocked(record)) {
                            val available = record.copy(
                                state = ExplorerActionReplacementHistoryState.AVAILABLE,
                                updatedAtMillis = record.nextUpdatedAt(effectiveNow),
                            )
                            writeRecordLocked(available)
                            retireOtherTargetRecordsLocked(available)
                        } else {
                            markRecoveryRequiredLocked(record)
                        }
                    }
                    record.previousIdentity -> removeRecordLocked(record)
                    else -> markRecoveryRequiredLocked(record)
                }
                ExplorerActionReplacementHistoryState.RESTORING -> {
                    val restoredIdentity = record.restoredIdentity
                    when {
                        restoredIdentity != null && currentIdentity == restoredIdentity -> {
                            writeRecordLocked(
                                record.copy(
                                    state = ExplorerActionReplacementHistoryState.RESTORED,
                                    updatedAtMillis = record.nextUpdatedAt(effectiveNow),
                                    restoreTempPath = null,
                                    restoredIdentity = null,
                                ),
                            )
                            backupFileLocked(record).delete()
                        }
                        currentIdentity == record.replacementIdentity -> {
                            record.restoreTempPath?.let { path ->
                                deleteExactRestoreTemp(File(path), restoredIdentity)
                            }
                            writeRecordLocked(
                                record.copy(
                                    state = ExplorerActionReplacementHistoryState.AVAILABLE,
                                    updatedAtMillis = record.nextUpdatedAt(effectiveNow),
                                    restoreTempPath = null,
                                    restoredIdentity = null,
                                ),
                            )
                        }
                        else -> markRecoveryRequiredLocked(record)
                    }
                }
                ExplorerActionReplacementHistoryState.AVAILABLE -> {
                    when {
                        !hasMatchingParentIdentity(record, File(record.targetPath)) ->
                            markStaleLocked(record)
                        currentIdentity != record.replacementIdentity -> markStaleLocked(record)
                        !hasValidBackupLocked(record) -> markRecoveryRequiredLocked(record)
                    }
                }
                ExplorerActionReplacementHistoryState.RESTORED,
                ExplorerActionReplacementHistoryState.STALE,
                ExplorerActionReplacementHistoryState.RECOVERY_REQUIRED,
                -> Unit
            }
        }
        cleanupLocked(effectiveNow)
        cleanupOrphanBlobsLocked(effectiveNow)
    }

    private fun cleanupLocked(now: Long, protectedId: String? = null) {
        val records = listRecordsLocked().sortedBy(ExplorerActionReplacementHistoryRecord::createdAtMillis)
        records.filter { record ->
            record.id != protectedId &&
                record.canBeAutomaticallyRemoved &&
                now - record.updatedAtMillis > HISTORY_RETENTION_MILLIS
        }.forEach(::removeRecordLocked)

        val remaining = listRecordsLocked()
            .sortedBy(ExplorerActionReplacementHistoryRecord::createdAtMillis)
            .toMutableList()
        while (remaining.size > MAX_HISTORY_RECORDS) {
            val index = remaining.indexOfFirst { record ->
                record.id != protectedId && record.canBeAutomaticallyRemoved
            }
            if (index < 0) break
            removeRecordLocked(remaining.removeAt(index))
        }
        var totalBytes = listRecordsLocked().sumOf { record ->
            backupFileLocked(record).takeIf { it.isFile }?.length() ?: 0L
        }
        for (record in remaining) {
            if (totalBytes <= MAX_TOTAL_HISTORY_BYTES) break
            if (record.id == protectedId || !record.canBeAutomaticallyRemoved) continue
            val bytes = backupFileLocked(record).takeIf { it.isFile }?.length() ?: 0L
            removeRecordLocked(record)
            totalBytes = (totalBytes - bytes).coerceAtLeast(0L)
        }
    }

    private fun makeRoomForLocked(payloadBytes: Long) {
        require(payloadBytes <= MAX_TOTAL_HISTORY_BYTES) {
            "Explorer Action replacement history exceeds its storage limit"
        }
        val records = listRecordsLocked()
            .sortedBy(ExplorerActionReplacementHistoryRecord::createdAtMillis)
            .toMutableList()
        var totalBytes = records.sumOf { record ->
            backupFileLocked(record).takeIf { it.isFile }?.length() ?: 0L
        }
        while (
            records.size >= MAX_HISTORY_RECORDS ||
            totalBytes > MAX_TOTAL_HISTORY_BYTES - payloadBytes
        ) {
            val index = records.indexOfFirst(
                ExplorerActionReplacementHistoryRecord::canBeAutomaticallyRemoved,
            )
            check(index >= 0) {
                "Explorer Action replacement history storage contains unresolved recovery data"
            }
            val record = records.removeAt(index)
            val bytes = backupFileLocked(record).takeIf { it.isFile }?.length() ?: 0L
            removeRecordLocked(record)
            totalBytes = (totalBytes - bytes).coerceAtLeast(0L)
        }
        check(records.size < MAX_HISTORY_RECORDS) {
            "Explorer Action replacement history record limit could not be reclaimed"
        }
        check(totalBytes <= MAX_TOTAL_HISTORY_BYTES - payloadBytes) {
            "Explorer Action replacement history storage could not be reclaimed"
        }
    }

    private fun cleanupOrphanBlobsLocked(now: Long) {
        val referenced = listRecordsLocked().mapTo(HashSet()) { backupFileLocked(it).canonicalPath }
        blobsDirectory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile || file.canonicalPath in referenced) return@forEach
            if (now - file.lastModified() >= ORPHAN_RETENTION_MILLIS) file.delete()
        }
    }

    private fun retireOtherTargetRecordsLocked(available: ExplorerActionReplacementHistoryRecord) {
        listRecordsLocked()
            .filter { record ->
                record.id != available.id &&
                    record.pluginUid == available.pluginUid &&
                    record.targetPath == available.targetPath &&
                    record.canBeAutomaticallyRemoved
            }
            .forEach(::removeRecordLocked)
    }

    private fun markStaleLocked(
        record: ExplorerActionReplacementHistoryRecord,
    ): ExplorerActionReplacementHistoryRecord {
        val stale = record.copy(
            state = ExplorerActionReplacementHistoryState.STALE,
            updatedAtMillis = record.nextUpdatedAt(),
            restoreTempPath = null,
            restoredIdentity = null,
        )
        writeRecordLocked(stale)
        backupFileLocked(record).delete()
        return stale
    }

    private fun markRecoveryRequiredLocked(
        record: ExplorerActionReplacementHistoryRecord,
    ): ExplorerActionReplacementHistoryRecord {
        val failed = record.copy(
            state = ExplorerActionReplacementHistoryState.RECOVERY_REQUIRED,
            updatedAtMillis = record.nextUpdatedAt(),
        )
        writeRecordLocked(failed)
        return failed
    }

    private fun hasMatchingParentIdentity(
        record: ExplorerActionReplacementHistoryRecord,
        target: File,
    ): Boolean = runCatching {
        val parent = requireNotNull(target.parentFile).canonicalFile
        if (parent.path != record.parentPath) return@runCatching false
        val identity = ExplorerActionFileIdentity.capture(parent)
        identity.device == record.parentDevice && identity.inode == record.parentInode
    }.getOrDefault(false)

    private fun copyRegularFile(
        source: File,
        destination: File,
        expectedSourceIdentity: ExplorerActionFileIdentity,
    ): String {
        check(ExplorerActionFileIdentity.captureRegularFile(source) == expectedSourceIdentity) {
            "Explorer Action replacement history source changed before copying"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    copied = Math.addExact(copied, read.toLong())
                    check(copied <= ExplorerActionProtocol.MAX_OUTPUT_BYTES) {
                        "Explorer Action replacement history exceeds its size limit"
                    }
                }
                output.fd.sync()
                check(copied == expectedSourceIdentity.size) {
                    "Explorer Action replacement history source size changed while copying"
                }
            }
        }
        check(ExplorerActionFileIdentity.captureRegularFile(source) == expectedSourceIdentity) {
            "Explorer Action replacement history source changed while copying"
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
        }
    }

    // History creation must not evict unrelated cache data; require immediately writable space.
    @SuppressLint("UsableSpace")
    private fun requireUsableSpace(directory: File, payloadBytes: Long) {
        val required = try {
            Math.addExact(payloadBytes, FREE_SPACE_RESERVE_BYTES)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        if (directory.usableSpace < required) {
            throw IOException("Explorer Action does not have enough space to preserve the previous archive version")
        }
    }

    private fun deleteExactRestoreTemp(file: File, expectedIdentity: ExplorerActionFileIdentity?) {
        if (expectedIdentity == null) return
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (captureRegularFileOrNull(canonical) == expectedIdentity) canonical.delete()
    }

    private fun captureRegularFileOrNull(file: File): ExplorerActionFileIdentity? =
        runCatching { ExplorerActionFileIdentity.captureRegularFile(file) }.getOrNull()

    private fun ExplorerActionReplacementHistoryRecord.nextUpdatedAt(
        now: Long = clock(),
    ): Long = maxOf(createdAtMillis, updatedAtMillis, now)

    private fun ensureDirectoriesLocked() {
        listOf(root, recordsDirectory, blobsDirectory).forEach { directory ->
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Explorer Action replacement history directory is unavailable")
            }
        }
    }

    private fun writeRecordLocked(record: ExplorerActionReplacementHistoryRecord) {
        ensureDirectoriesLocked()
        val atomic = AtomicFile(recordFile(record.id))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toJson().toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw IOException("Explorer Action replacement history journal could not be written", error)
        }
    }

    private fun readRecordLocked(historyId: String): ExplorerActionReplacementHistoryRecord? {
        if (!isCanonicalReplacementHistoryUuid(historyId)) return null
        val file = recordFile(historyId)
        if (
            !file.exists() &&
            !File(file.path + ATOMIC_BACKUP_SUFFIX).exists() &&
            !File(file.path + ATOMIC_NEW_SUFFIX).exists()
        ) {
            return null
        }
        return runCatching {
            AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().toReplacementHistoryRecord()
            }
        }.getOrNull()
    }

    private fun listRecordsLocked(): List<ExplorerActionReplacementHistoryRecord> {
        if (!recordsDirectory.isDirectory) return emptyList()
        return recordsDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isFile)
            .mapNotNull { file -> historyIdFromJournalFileName(file.name) }
            .distinct()
            .mapNotNull(::readRecordLocked)
            .toList()
    }

    private fun historyIdFromJournalFileName(fileName: String): String? {
        val baseSuffix = ".$RECORD_EXTENSION"
        val historyId = when {
            fileName.endsWith(baseSuffix) -> fileName.removeSuffix(baseSuffix)
            fileName.endsWith(baseSuffix + ATOMIC_BACKUP_SUFFIX) ->
                fileName.removeSuffix(baseSuffix + ATOMIC_BACKUP_SUFFIX)
            fileName.endsWith(baseSuffix + ATOMIC_NEW_SUFFIX) ->
                fileName.removeSuffix(baseSuffix + ATOMIC_NEW_SUFFIX)
            else -> return null
        }
        return historyId.takeIf(::isCanonicalReplacementHistoryUuid)
    }

    private fun removeRecordLocked(record: ExplorerActionReplacementHistoryRecord) {
        record.restoreTempPath?.let { path ->
            deleteExactRestoreTemp(File(path), record.restoredIdentity)
        }
        backupFileLocked(record).delete()
        val file = recordFile(record.id)
        file.delete()
        File(file.path + ATOMIC_BACKUP_SUFFIX).delete()
        File(file.path + ATOMIC_NEW_SUFFIX).delete()
    }

    private fun recordFile(historyId: String): File = File(
        recordsDirectory,
        "$historyId.$RECORD_EXTENSION",
    )

    private fun backupFileLocked(record: ExplorerActionReplacementHistoryRecord): File {
        val file = File(root, record.backupRelativePath).canonicalFile
        require(file.parentFile == blobsDirectory.canonicalFile) {
            "Explorer Action replacement history backup path is invalid"
        }
        return file
    }

    private fun hasValidBackupLocked(record: ExplorerActionReplacementHistoryRecord): Boolean {
        val backup = runCatching { backupFileLocked(record) }.getOrNull() ?: return false
        return backup.isFile && backup.length() == record.previousIdentity.size
    }

    private fun syncDirectory(directory: File) {
        var descriptor: java.io.FileDescriptor? = null
        try {
            descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            Os.fsync(descriptor)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.EINVAL && error.errno != OsConstants.EOPNOTSUPP) {
                throw IOException("Explorer Action replacement history directory could not be synchronized", error)
            }
        } finally {
            descriptor?.let { runCatching { Os.close(it) } }
        }
    }

    private fun ExplorerActionReplacementHistoryRecord.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_TRANSACTION_ID, transactionId)
        put(KEY_PLUGIN_UID, pluginUid)
        put(KEY_PARENT_PATH, parentPath)
        put(KEY_PARENT_DEVICE, parentDevice)
        put(KEY_PARENT_INODE, parentInode)
        put(KEY_TARGET_PATH, targetPath)
        put(KEY_DISPLAY_NAME, displayName)
        put(KEY_BACKUP_RELATIVE_PATH, backupRelativePath)
        put(KEY_PREVIOUS_SHA256, previousSha256)
        putIdentity(KEY_PREVIOUS_IDENTITY, previousIdentity)
        putIdentity(KEY_REPLACEMENT_IDENTITY, replacementIdentity)
        put(KEY_STATE, state.name)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_UPDATED_AT, updatedAtMillis)
        restoreTempPath?.let { put(KEY_RESTORE_TEMP_PATH, it) }
        restoredIdentity?.let { putIdentity(KEY_RESTORED_IDENTITY, it) }
    }

    private fun JSONObject.putIdentity(prefix: String, identity: ExplorerActionFileIdentity) {
        put("${prefix}Device", identity.device)
        put("${prefix}Inode", identity.inode)
        put("${prefix}Size", identity.size)
        put("${prefix}LastModified", identity.lastModifiedMillis)
    }

    private fun String.toReplacementHistoryRecord(): ExplorerActionReplacementHistoryRecord {
        val json = JSONObject(this)
        val restoreTempPath = json.optString(KEY_RESTORE_TEMP_PATH).takeIf { it.isNotBlank() }
        val restoredIdentity = if (restoreTempPath == null) null else json.identity(KEY_RESTORED_IDENTITY)
        return ExplorerActionReplacementHistoryRecord(
            id = json.getString(KEY_ID),
            transactionId = json.getString(KEY_TRANSACTION_ID),
            pluginUid = json.getInt(KEY_PLUGIN_UID),
            parentPath = json.getString(KEY_PARENT_PATH),
            parentDevice = json.getLong(KEY_PARENT_DEVICE),
            parentInode = json.getLong(KEY_PARENT_INODE),
            targetPath = json.getString(KEY_TARGET_PATH),
            displayName = json.getString(KEY_DISPLAY_NAME),
            backupRelativePath = json.getString(KEY_BACKUP_RELATIVE_PATH),
            previousSha256 = json.getString(KEY_PREVIOUS_SHA256),
            previousIdentity = json.identity(KEY_PREVIOUS_IDENTITY),
            replacementIdentity = json.identity(KEY_REPLACEMENT_IDENTITY),
            state = ExplorerActionReplacementHistoryState.valueOf(json.getString(KEY_STATE)),
            createdAtMillis = json.getLong(KEY_CREATED_AT),
            updatedAtMillis = json.getLong(KEY_UPDATED_AT),
            restoreTempPath = restoreTempPath,
            restoredIdentity = restoredIdentity,
        )
    }

    private fun JSONObject.identity(prefix: String): ExplorerActionFileIdentity =
        ExplorerActionFileIdentity(
            device = getLong("${prefix}Device"),
            inode = getLong("${prefix}Inode"),
            size = getLong("${prefix}Size"),
            lastModifiedMillis = getLong("${prefix}LastModified"),
        )

    private companion object {
        val LOCK = Any()
        const val ROOT_DIRECTORY_NAME = "explorer-action-replacement-history"
        const val RECORDS_DIRECTORY_NAME = "records"
        const val BLOBS_DIRECTORY_NAME = "blobs"
        const val RECORD_EXTENSION = "json"
        const val ATOMIC_BACKUP_SUFFIX = ".bak"
        const val ATOMIC_NEW_SUFFIX = ".new"
        const val PARTIAL_SUFFIX = ".partial"
        const val BACKUP_SUFFIX = ".bak"
        const val COPY_BUFFER_BYTES = 256 * 1024
        const val MAX_HISTORY_RECORDS = 32
        const val FREE_SPACE_RESERVE_BYTES = 128L * 1024L * 1024L
        const val MAX_TOTAL_HISTORY_BYTES = 4L * 1024L * 1024L * 1024L
        const val HISTORY_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
        const val ORPHAN_RETENTION_MILLIS = 60L * 60L * 1000L
        const val KEY_ID = "id"
        const val KEY_TRANSACTION_ID = "transactionId"
        const val KEY_PLUGIN_UID = "pluginUid"
        const val KEY_PARENT_PATH = "parentPath"
        const val KEY_PARENT_DEVICE = "parentDevice"
        const val KEY_PARENT_INODE = "parentInode"
        const val KEY_TARGET_PATH = "targetPath"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_BACKUP_RELATIVE_PATH = "backupRelativePath"
        const val KEY_PREVIOUS_SHA256 = "previousSha256"
        const val KEY_PREVIOUS_IDENTITY = "previous"
        const val KEY_REPLACEMENT_IDENTITY = "replacement"
        const val KEY_RESTORED_IDENTITY = "restored"
        const val KEY_STATE = "state"
        const val KEY_CREATED_AT = "createdAtMillis"
        const val KEY_UPDATED_AT = "updatedAtMillis"
        const val KEY_RESTORE_TEMP_PATH = "restoreTempPath"
    }
}

private fun isCanonicalReplacementHistoryUuid(value: String): Boolean {
    if (value.length > ExplorerActionProtocol.MAX_TARGET_REPLACEMENT_HISTORY_ID_LENGTH) return false
    val parsed = runCatching { UUID.fromString(value) }.getOrNull() ?: return false
    return parsed.toString().equals(value, ignoreCase = true)
}
