package org.autojs.autojs.core.plugin.batchrename

import java.io.File
import java.util.Locale

/**
 * What the executor needs from the host beyond plain file renames; tests replace it.
 * zh-CN: 执行器在普通文件改名之外需要宿主提供的能力; 测试中替换为桩实现.
 */
interface BatchRenameEnvironment {
    /** Moves [file] into the host Trash and returns its record id, or null when that failed. */
    fun moveToTrash(file: File): String?

    /** Puts the Trash record [id] back at [target]; true when the entry is there again. */
    fun restoreFromTrash(id: String, target: File): Boolean

    /** Keeps version history attached to a renamed file or directory. */
    fun relocateHistory(from: File, to: File)

    /** Refreshes the file manager after renames in [directories]. */
    fun notifyChanged(directories: Collection<File>)
}

/**
 * One rename the caller asks for. [expectedLastModified] and [expectedLength] come from the moment the caller listed
 * the entry; a file whose values differ is stale and stays as it is.
 * zh-CN: 调用方请求的一次改名. [expectedLastModified] 与 [expectedLength] 取自调用方列出该条目的时刻; 数值不同的文件视为过期,
 * 保持不变.
 */
data class BatchRenameOperation(
    val source: File,
    val newName: String,
    val directory: Boolean,
    val expectedLastModified: Long? = null,
    val expectedLength: Long? = null,
    /** Replace an existing entry with the new name, which goes to the Trash first. zh-CN: 覆盖同名条目, 其先进回收站. */
    val overwrite: Boolean = false,
    /** Copied into the journal for Explorer Action answers. zh-CN: 复制到日志中, 供管理器应答使用. */
    val targetId: String? = null,
    val relativePath: String? = null,
)

/** Progress of a batch: entries handled so far, the total and the entry just handled. */
fun interface BatchRenameProgress {
    fun onProgress(done: Int, total: Int, entry: BatchRenameJournalEntry)
}

/**
 * Executes rename batches and undoes them (plugin roadmap D20 / D21), shared by `files.batchRename` and the Explorer
 * Action protocol v25 session.
 *
 * - Every entry is checked again first: it still exists with the same kind, and a file still has the modification
 *   time and size the caller listed; otherwise it is stale. With `allOrNothing` one failure rejects the whole batch.
 * - Deeper entries go first, so a directory is renamed after everything below it. Within one depth, an entry whose new
 *   name only changes case, or is the current name of another entry of the batch, is moved to a temporary name first;
 *   the final renames then run in passes until no entry can move, which covers case-insensitive volumes, chains, swaps
 *   and cycles without depending on the request order.
 * - A new name that stays taken fails the entry unless it asked to overwrite an entry outside the batch, which goes
 *   to the Trash first. A failed entry keeps its name; renamed entries stay renamed (undo reverts them).
 * - Every call is journaled; undo restores renamed entries shallowest first with the same rules, skips entries that
 *   changed since, never overwrites an occupied old name, and returns replaced entries from the Trash.
 *
 * zh-CN: 执行改名批次并撤销 (插件路线图 D20 / D21), 由 `files.batchRename` 与 Explorer Action 协议 v25 会话共用. 先逐项复核:
 * 条目仍存在且类型相同, 文件的修改时间与大小仍是调用方列出时的值, 否则为过期; `allOrNothing` 时任一失败即拒绝整批. 深层条目先执行,
 * 目录在其下全部条目之后改名. 同一深度中, 新名称只改变大小写或等于本批另一条目当前名称的条目先改为临时名, 最终改名按轮次进行直到没有
 * 条目可以移动, 从而覆盖不区分大小写的卷, 链, 交换与循环, 且不依赖请求顺序. 新名称始终被占用时该条目失败, 除非请求覆盖本批以外的条目,
 * 后者先进回收站. 失败条目保留原名; 已改名的条目保持改名 (可撤销). 每次调用都记录日志; 撤销以浅层先的顺序按相同规则恢复已改名条目,
 * 跳过此后发生变化的条目, 从不覆盖被占用的原名, 并从回收站取回被覆盖的条目.
 */
class BatchRenameExecutor(
    val journal: BatchRenameJournalStore,
    private val environment: BatchRenameEnvironment,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    /**
     * Runs one batch and journals it. [scope] is the Explorer Action session parent the batch belongs to.
     * zh-CN: 执行一个批次并记入日志. [scope] 为批次所属的 Explorer Action 会话父目录.
     */
    fun execute(
        operations: List<BatchRenameOperation>,
        source: BatchRenameSource,
        pluginUid: Int? = null,
        allOrNothing: Boolean = false,
        scope: File? = null,
        progress: BatchRenameProgress? = null,
    ): BatchRenameJournalRecord = synchronized(lock) {
        require(operations.isNotEmpty()) { "A rename batch needs at least one entry" }
        val batchId = journal.newBatchId()
        val entries = arrayOfNulls<BatchRenameJournalEntry>(operations.size)
        var done = 0
        fun settle(index: Int, entry: BatchRenameJournalEntry) {
            entries[index] = entry
            progress?.onProgress(++done, operations.size, entry)
        }
        val failures = operations.map(::check)
        val rejected = allOrNothing && failures.any { it != null }
        operations.forEachIndexed { index, operation ->
            val failure = failures[index]
            when {
                failure != null -> settle(index, unchanged(operation, failure.first, failure.second))
                rejected -> settle(index, unchanged(operation, BatchRenameEntryState.NOT_RENAMED, REASON_REJECTED))
            }
        }
        if (!rejected) {
            val pending = operations.indices.filter { failures[it] == null }
            val sources = pending.map { operations[it].source.absoluteFile }.toSet()
            pending.groupBy { depthOf(operations[it].source) }.toSortedMap(reverseOrder()).values.forEach { group ->
                val moves = group.map { Move(it, operations[it].source, operations[it].newName, operations[it].overwrite) }
                runGroup(moves, batchId, sources) { index, outcome ->
                    val operation = operations[index]
                    settle(index, when (outcome) {
                        is Outcome.Moved -> {
                            environment.relocateHistory(operation.source, outcome.target)
                            BatchRenameJournalEntry(
                                oldPath = operation.source.path,
                                newPath = outcome.target.path,
                                directory = operation.directory,
                                state = BatchRenameEntryState.RENAMED,
                                replacedTrashId = outcome.replacedTrashId,
                                targetId = operation.targetId,
                                relativePath = operation.relativePath,
                                renamed = true,
                            )
                        }
                        is Outcome.Failed -> unchanged(operation, BatchRenameEntryState.NOT_RENAMED, outcome.reason)
                    })
                }
            }
        }
        // Record what undo compares against once every entry is in place.
        // zh-CN: 全部条目就位后再记录撤销要比对的数值.
        val settled = BatchRenameJournalRecord(batchId, source, pluginUid, 0L, 0L, BatchRenameBatchState.COMMITTED, entries.map { it!! })
        val finished = settled.entries.map { entry ->
            if (entry.state != BatchRenameEntryState.RENAMED) entry else {
                val file = settled.locationOf(entry)
                entry.copy(lastModified = file.lastModified(), length = if (entry.directory) -1L else file.length())
            }
        }
        val renamed = finished.count { it.state == BatchRenameEntryState.RENAMED }
        val state = when {
            renamed == 0 -> BatchRenameBatchState.REJECTED
            renamed == finished.size -> BatchRenameBatchState.COMMITTED
            else -> BatchRenameBatchState.PARTIAL
        }
        val now = clock()
        val record = BatchRenameJournalRecord(batchId, source, pluginUid, now, now, state, finished, scope?.path)
        journal.save(record)
        if (renamed > 0) environment.notifyChanged(directoriesOf(finished.filter { it.state == BatchRenameEntryState.RENAMED }.map(record::locationOf)))
        record
    }

    /**
     * Undoes the batch [batchId]; null when it does not exist or [accept] turns the stored record down (checked under
     * the executor lock, so the record it sees is the one undo starts from). Entries left in place by an earlier undo
     * attempt (an occupied old name, an I/O failure) are tried again, so undo can be repeated once the obstacle is gone.
     * zh-CN: 撤销批次 [batchId]; 不存在或 [accept] 拒绝所存记录时为 null ([accept] 在执行器锁内检查, 它看到的记录就是撤销的起点).
     * 先前撤销中留在原处的条目 (原名被占用, I/O 失败) 会再次尝试, 因此障碍消除后可以重复撤销.
     */
    fun undo(
        batchId: String,
        progress: BatchRenameProgress? = null,
        accept: (BatchRenameJournalRecord) -> Boolean = { true },
    ): BatchRenameJournalRecord? = synchronized(lock) {
        val record = journal.load(batchId)?.takeIf(accept) ?: return null
        val entries = record.entries.toMutableList()
        val pending = entries.indices.filter { entries[it].undoable }
        var done = 0
        fun settle(index: Int, entry: BatchRenameJournalEntry) {
            entries[index] = entry
            progress?.onProgress(++done, pending.size, entry)
        }
        val sources = pending.map { File(entries[it].newPath).absoluteFile }.toSet()
        pending.groupBy { depthOf(File(entries[it].newPath)) }.toSortedMap().values.forEach { group ->
            val moves = mutableListOf<Move>()
            group.forEach { index ->
                val entry = entries[index]
                val current = File(entry.newPath)
                val stale = staleReason(current, entry.directory, entry.lastModified.takeUnless { entry.directory }, entry.length.takeUnless { entry.directory })
                if (stale != null) {
                    settle(index, entry.copy(state = BatchRenameEntryState.STALE, reason = stale))
                } else {
                    moves += Move(index, current, File(entry.oldPath).name, overwrite = false)
                }
            }
            runGroup(moves, journal.newBatchId(), sources) { index, outcome ->
                val entry = entries[index]
                settle(index, when (outcome) {
                    is Outcome.Moved -> {
                        environment.relocateHistory(File(entry.newPath), outcome.target)
                        val replacedRestored = entry.replacedTrashId?.let { environment.restoreFromTrash(it, File(entry.newPath)) } ?: false
                        if (entry.replacedTrashId != null && !replacedRestored) {
                            entry.copy(state = BatchRenameEntryState.RECOVERY_REQUIRED, reason = REASON_REPLACED_NOT_RESTORED)
                        } else {
                            entry.copy(state = BatchRenameEntryState.RESTORED, reason = null, replacedRestored = replacedRestored)
                        }
                    }
                    is Outcome.Failed -> entry.copy(state = BatchRenameEntryState.RECOVERY_REQUIRED, reason = outcome.reason)
                })
            }
        }
        val state = if (pending.all { entries[it].state == BatchRenameEntryState.RESTORED }) {
            BatchRenameBatchState.RESTORED
        } else {
            BatchRenameBatchState.RECOVERY_REQUIRED
        }
        val updated = record.copy(entries = entries, state = if (pending.isEmpty()) record.state else state, updatedAt = clock())
        journal.save(updated)
        val restored = pending.filter { entries[it].state == BatchRenameEntryState.RESTORED }
        if (restored.isNotEmpty()) environment.notifyChanged(directoriesOf(restored.map { File(entries[it].oldPath) }))
        updated
    }

    private data class Move(val index: Int, val from: File, val newName: String, val overwrite: Boolean)

    private sealed interface Outcome {
        data class Moved(val target: File, val replacedTrashId: String?) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /**
     * Moves one depth group. [batchSources] are the current paths of every entry of the batch, which an overwrite never
     * replaces.
     * zh-CN: 处理同一深度的一组移动. [batchSources] 是本批全部条目的当前路径, 覆盖从不替换它们.
     */
    private fun runGroup(moves: List<Move>, batchId: String, batchSources: Set<File>, onOutcome: (Int, Outcome) -> Unit) {
        if (moves.isEmpty()) return
        val sourceKeys = moves.groupBy { keyOf(it.from) }
        val located = HashMap<Int, File>()
        val queue = ArrayDeque<Move>()
        moves.forEachIndexed { position, move ->
            val target = File(move.from.parentFile, move.newName)
            val caseOnly = move.newName.equals(move.from.name, ignoreCase = true)
            val heldByAnother = sourceKeys[keyOf(target)]?.any { it.index != move.index } == true
            if (caseOnly || heldByAnother) {
                val temporary = File(move.from.parentFile, ".batch-rename-${batchId.takeLast(12)}-$position.tmp")
                if (temporary.exists() || !move.from.renameTo(temporary)) {
                    onOutcome(move.index, Outcome.Failed(REASON_IO_FAILED))
                    return@forEachIndexed
                }
                located[move.index] = temporary
            } else {
                located[move.index] = move.from
            }
            queue += move
        }
        // Renames whose target is free go first; a pass without progress settles the rest.
        // zh-CN: 目标已空出的改名先执行; 某一轮没有进展时处理剩余条目.
        while (queue.isNotEmpty()) {
            var progressed = false
            repeat(queue.size) {
                val move = queue.removeFirst()
                val from = located.getValue(move.index)
                val target = File(move.from.parentFile, move.newName)
                if (target.exists() && !isSameEntry(from, target)) {
                    queue += move
                } else {
                    progressed = true
                    finish(move, from, target, null, onOutcome)
                }
            }
            if (!progressed) {
                queue.forEach { move ->
                    val from = located.getValue(move.index)
                    val target = File(move.from.parentFile, move.newName)
                    if (!move.overwrite || target.absoluteFile in batchSources) {
                        restoreTemporary(from, move.from)
                        onOutcome(move.index, Outcome.Failed(REASON_OCCUPIED))
                        return@forEach
                    }
                    val trashId = environment.moveToTrash(target)
                    if (trashId == null) {
                        restoreTemporary(from, move.from)
                        onOutcome(move.index, Outcome.Failed(REASON_TRASH_FAILED))
                    } else {
                        finish(move, from, target, trashId, onOutcome)
                    }
                }
                queue.clear()
            }
        }
    }

    private fun finish(move: Move, from: File, target: File, trashId: String?, onOutcome: (Int, Outcome) -> Unit) {
        if (from.renameTo(target)) {
            onOutcome(move.index, Outcome.Moved(target, trashId))
        } else {
            restoreTemporary(from, move.from)
            trashId?.let { environment.restoreFromTrash(it, target) }
            onOutcome(move.index, Outcome.Failed(REASON_IO_FAILED))
        }
    }

    private fun restoreTemporary(from: File, original: File) {
        if (from != original) from.renameTo(original)
    }

    /** Case-insensitive volumes report a different-case name of the same entry as existing. */
    private fun isSameEntry(a: File, b: File): Boolean =
        a.name.equals(b.name, ignoreCase = true) && a.parentFile == b.parentFile &&
            runCatching { a.canonicalFile == b.canonicalFile }.getOrDefault(false)

    /** A failure as state and reason, or null when the operation can run. */
    private fun check(operation: BatchRenameOperation): Pair<BatchRenameEntryState, String>? {
        if (!BatchRenameNames.isValid(operation.newName)) return BatchRenameEntryState.NOT_RENAMED to REASON_INVALID_NAME
        if (operation.newName == operation.source.name) return BatchRenameEntryState.NOT_RENAMED to REASON_SAME_NAME
        val stale = staleReason(operation.source, operation.directory, operation.expectedLastModified, operation.expectedLength)
        return stale?.let { BatchRenameEntryState.STALE to it }
    }

    private fun staleReason(file: File, directory: Boolean, lastModified: Long?, length: Long?): String? = when {
        !file.exists() -> REASON_MISSING
        file.isDirectory != directory -> REASON_KIND_CHANGED
        !directory && lastModified != null && file.lastModified() != lastModified -> REASON_CHANGED
        !directory && length != null && length >= 0 && file.length() != length -> REASON_CHANGED
        else -> null
    }

    private fun unchanged(operation: BatchRenameOperation, state: BatchRenameEntryState, reason: String) = BatchRenameJournalEntry(
        oldPath = operation.source.path,
        newPath = File(operation.source.parentFile, operation.newName.takeIf(BatchRenameNames::isValid) ?: operation.source.name).path,
        directory = operation.directory,
        state = state,
        reason = reason,
        targetId = operation.targetId,
        relativePath = operation.relativePath,
    )

    private fun directoriesOf(files: List<File>): Set<File> = files.mapTo(linkedSetOf()) { it.absoluteFile.parentFile ?: it.absoluteFile }

    private fun depthOf(file: File): Int = file.absolutePath.count { it == File.separatorChar }

    private fun keyOf(file: File): String = file.absolutePath.lowercase(Locale.ROOT)

    companion object {
        const val REASON_REJECTED = "rejected"
        const val REASON_INVALID_NAME = "invalidName"
        const val REASON_SAME_NAME = "sameName"
        const val REASON_MISSING = "missing"
        const val REASON_KIND_CHANGED = "kindChanged"
        const val REASON_CHANGED = "changed"
        const val REASON_OCCUPIED = "occupied"
        const val REASON_TRASH_FAILED = "trashFailed"
        const val REASON_IO_FAILED = "ioFailed"
        const val REASON_REPLACED_NOT_RESTORED = "replacedNotRestored"
    }
}

/** Rules of one resulting name (Explorer Action `MAX_RENAME_NAME_BYTES`). zh-CN: 单个结果名称的规则. */
object BatchRenameNames {
    const val MAX_NAME_BYTES = 255

    fun isValid(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name &&
            name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES
}
