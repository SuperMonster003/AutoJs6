package org.autojs.autojs.core.plugin.explorer.session

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

/**
 * Process-wide recovery coordinator for durable Explorer Action outputs.
 *
 * Active transaction IDs prevent a newly created session from recovering work still owned by
 * another session in this process. Durable records from a previous process are either completed,
 * rolled back, or marked as requiring explicit recovery.
 */
internal object ExplorerActionOutputRecovery {

    private const val OUTPUT_TREE_TERMINAL_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
    private const val OUTPUT_BATCH_TERMINAL_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
    private val outputStagingNamePattern = Regex(
        "\\.autojs6-explorer-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
            "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.part",
    )
    private val activeLock = Any()
    private val activeOutputTreeIds = HashSet<String>()
    private val activeOutputBatchIds = HashSet<String>()
    private val outputTreeRecoveryLock = Any()
    private val outputBatchRecoveryLock = Any()

    fun recoverOutputTrees(store: ExplorerActionOutputTreeStore, nowMillis: Long) {
        synchronized(outputTreeRecoveryLock) {
            store.list().forEach { record ->
                val age = if (nowMillis < record.updatedAtMillis) {
                    Long.MAX_VALUE
                } else {
                    nowMillis - record.updatedAtMillis
                }
                val terminal = record.state == ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED ||
                    record.state == ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED
                if (terminal) {
                    if (age >= OUTPUT_TREE_TERMINAL_RETENTION_MILLIS) store.remove(record.id)
                    return@forEach
                }
                if (isOutputTreeActive(record.id)) return@forEach

                val parent = File(record.parentPath)
                val target = File(record.targetPath)
                val parentMatches = runCatching {
                    val identity = TargetIdentity.capture(parent)
                    identity.device == record.parentDevice && identity.inode == record.parentInode
                }.getOrDefault(false)
                if (!parentMatches) {
                    store.write(record.abortedAt(nowMillis))
                    return@forEach
                }
                if (!target.exists()) {
                    store.write(record.abortedAt(nowMillis))
                    return@forEach
                }
                if (!outputTreeIdentityMatches(record, target)) {
                    store.write(record.abortedAt(nowMillis))
                    return@forEach
                }
                val marker = File(target, record.markerName)
                if (!marker.exists()) {
                    val terminalState = if (
                        record.state == ExplorerActionHostSessionValues.OUTPUT_STATE_VERIFYING
                    ) {
                        ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED
                    } else {
                        ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED
                    }
                    store.write(record.copy(state = terminalState, updatedAtMillis = nowMillis))
                } else if (safeDeleteTree(target)) {
                    store.write(record.abortedAt(nowMillis))
                }
            }
        }
    }

    fun recoverOutputBatches(store: ExplorerActionOutputBatchStore, nowMillis: Long) {
        synchronized(outputBatchRecoveryLock) {
            store.list().forEach { record ->
                val age = if (nowMillis < record.updatedAtMillis) {
                    0L
                } else {
                    nowMillis - record.updatedAtMillis
                }
                val terminal = record.state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED ||
                    record.state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED
                if (terminal) {
                    if (age >= OUTPUT_BATCH_TERMINAL_RETENTION_MILLIS) store.remove(record.id)
                    return@forEach
                }
                if (isOutputBatchActive(record.id)) return@forEach

                val failures = mutableListOf<Throwable>()
                val parent = File(record.parentPath)
                val parentMatches = runCatching {
                    val canonicalParent = parent.canonicalFile
                    val identity = TargetIdentity.capture(canonicalParent)
                    canonicalParent == parent &&
                        identity.device == record.parentDevice &&
                        identity.inode == record.parentInode
                }.getOrDefault(false)
                if (!parentMatches) {
                    failures += IOException("Explorer Action output batch recovery parent changed")
                } else {
                    record.members.asReversed().forEach { member ->
                        listOf(File(member.targetPath), File(member.stagingPath)).forEach { file ->
                            val failure = runCatching {
                                val canonical = file.canonicalFile
                                check(canonical == file && canonical.parentFile == parent) {
                                    "Explorer Action output batch recovery path is invalid"
                                }
                                if (file == File(member.stagingPath)) {
                                    check(outputStagingNamePattern.matches(file.name)) {
                                        "Explorer Action output batch staging name is invalid"
                                    }
                                }
                                val stat = try {
                                    Os.lstat(file.path)
                                } catch (error: ErrnoException) {
                                    if (error.errno == OsConstants.ENOENT) return@runCatching
                                    throw IOException(
                                        "Explorer Action output batch recovery path is unavailable",
                                        error,
                                    )
                                }
                                check(OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                                    "Explorer Action output batch recovery path is not a regular file"
                                }
                                check(
                                    stat.st_dev == member.stagingDevice &&
                                        stat.st_ino == member.stagingInode &&
                                        stat.st_size == member.stagingSize &&
                                        file.lastModified() == member.stagingLastModifiedMillis,
                                ) { "Explorer Action output batch recovery identity changed" }
                                if (!file.delete()) {
                                    throw IOException(
                                        "Explorer Action output batch recovery file could not be removed",
                                    )
                                }
                            }.exceptionOrNull()
                            if (failure != null) failures += failure
                        }
                    }
                    runCatching { syncDirectory(parent) }
                        .exceptionOrNull()
                        ?.let(failures::add)
                }
                val recovered = record.copy(
                    state = if (failures.isEmpty()) {
                        ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED
                    } else {
                        ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_RECOVERY_REQUIRED
                    },
                    updatedAtMillis = nowMillis.coerceAtLeast(record.updatedAtMillis),
                )
                runCatching { store.write(recovered) }
            }
        }
    }

    fun markOutputTreeActive(transactionId: String, active: Boolean) {
        synchronized(activeLock) {
            if (active) activeOutputTreeIds += transactionId else activeOutputTreeIds -= transactionId
        }
    }

    fun markOutputBatchActive(batchId: String, active: Boolean) {
        synchronized(activeLock) {
            if (active) activeOutputBatchIds += batchId else activeOutputBatchIds -= batchId
        }
    }

    fun outputTreeIdentityMatches(record: ExplorerActionOutputTreeRecord, target: File): Boolean {
        return runCatching {
            val identity = TargetIdentity.capture(target)
            identity.device == record.targetDevice && identity.inode == record.targetInode
        }.getOrDefault(false)
    }

    fun safeDeleteTree(root: File): Boolean {
        val rootStat = try {
            Os.lstat(root.path)
        } catch (error: ErrnoException) {
            return error.errno == OsConstants.ENOENT
        }
        if (!OsConstants.S_ISDIR(rootStat.st_mode) || OsConstants.S_ISLNK(rootStat.st_mode)) {
            return false
        }

        val pending = ArrayDeque<OutputTreeDeletion>()
        pending.addLast(OutputTreeDeletion(root))
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current.afterChildren) {
                if (!current.file.delete()) return false
                continue
            }
            val stat = runCatching { Os.lstat(current.file.path) }.getOrNull() ?: return false
            if (OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                val children = current.file.listFiles() ?: return false
                pending.addLast(current.copy(afterChildren = true))
                children.forEach { child -> pending.addLast(OutputTreeDeletion(child)) }
            } else if (!current.file.delete()) {
                return false
            }
        }
        return true
    }

    private fun isOutputTreeActive(transactionId: String): Boolean = synchronized(activeLock) {
        transactionId in activeOutputTreeIds
    }

    private fun isOutputBatchActive(batchId: String): Boolean = synchronized(activeLock) {
        batchId in activeOutputBatchIds
    }

    private fun syncDirectory(directory: File) {
        var descriptor: java.io.FileDescriptor? = null
        try {
            descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            Os.fsync(descriptor)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.EINVAL && error.errno != OsConstants.EOPNOTSUPP) {
                throw IOException(
                    "Explorer Action output batch recovery directory could not be synchronized",
                    error,
                )
            }
        } finally {
            descriptor?.let { runCatching { Os.close(it) } }
        }
    }

    private fun ExplorerActionOutputTreeRecord.abortedAt(nowMillis: Long): ExplorerActionOutputTreeRecord {
        return copy(
            state = ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
            updatedAtMillis = nowMillis,
        )
    }

    private data class OutputTreeDeletion(
        val file: File,
        val afterChildren: Boolean = false,
    )
}
