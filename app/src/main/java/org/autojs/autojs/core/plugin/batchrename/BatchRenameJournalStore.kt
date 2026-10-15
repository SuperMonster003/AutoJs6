package org.autojs.autojs.core.plugin.batchrename

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.SecureRandom

/**
 * Where a rename batch came from. zh-CN: 改名批次的来源.
 */
enum class BatchRenameSource(val value: String) {
    /** `files.batchRename` in a script. zh-CN: 脚本中的 `files.batchRename`. */
    SCRIPT("script"),

    /** An Explorer Action session (protocol v25 `renameEntries`). zh-CN: Explorer Action 会话 (协议 v25 `renameEntries`). */
    EXPLORER("explorer"),
}

/** State of a whole batch. zh-CN: 整个批次的状态. */
enum class BatchRenameBatchState(val value: String) {
    COMMITTED("committed"),
    PARTIAL("partial"),
    REJECTED("rejected"),
    RESTORED("restored"),
    RECOVERY_REQUIRED("recoveryRequired"),
}

/** State of one entry. zh-CN: 单个条目的状态. */
enum class BatchRenameEntryState(val value: String) {
    RENAMED("renamed"),
    NOT_RENAMED("notRenamed"),
    STALE("stale"),
    RESTORED("restored"),
    RECOVERY_REQUIRED("recoveryRequired"),
}

/**
 * One journaled entry. [oldPath] and [newPath] are the paths at the moment the entry was renamed: an item renamed before
 * its parent directory keeps the parent's old path, which is where it is again once undo has restored the parent.
 * zh-CN: 一条日志记录. [oldPath] 与 [newPath] 是该条目改名那一刻的路径: 先于父目录改名的条目保留父目录的旧路径, 撤销恢复父目录之后
 * 它正好回到该路径.
 */
data class BatchRenameJournalEntry(
    val oldPath: String,
    val newPath: String,
    val directory: Boolean,
    val state: BatchRenameEntryState,
    /** Why the entry was not renamed or not restored; null otherwise. zh-CN: 未改名或未恢复的原因. */
    val reason: String? = null,
    /** Modification time and size after the rename, checked before undo (files only). zh-CN: 改名后的修改时间与大小. */
    val lastModified: Long = 0L,
    val length: Long = -1L,
    /** Trash record of the entry the rename replaced, restored by undo. zh-CN: 被覆盖条目的回收站记录. */
    val replacedTrashId: String? = null,
    /** Whether undo returned the replaced entry from the Trash. zh-CN: 撤销是否已从回收站取回被覆盖条目. */
    val replacedRestored: Boolean = false,
    /** Explorer Action request target and relative path of the entry; null for scripts. zh-CN: 管理器请求目标与相对路径. */
    val targetId: String? = null,
    val relativePath: String? = null,
    /** The batch renamed this entry, whatever undo did later. zh-CN: 本批曾为该条目改名, 与之后的撤销无关. */
    val renamed: Boolean = false,
) {
    /**
     * The entry carries the new name now: renamed, and undo has not given it its old name back.
     * zh-CN: 条目当前持有新名称: 已改名, 且撤销尚未将其恢复为原名.
     */
    val applied: Boolean
        get() = renamed && state != BatchRenameEntryState.RESTORED && reason != BatchRenameExecutor.REASON_REPLACED_NOT_RESTORED

    /** Undo can still act on this entry. zh-CN: 撤销仍可作用于该条目. */
    val undoable: Boolean
        get() = state == BatchRenameEntryState.RENAMED ||
            state == BatchRenameEntryState.RECOVERY_REQUIRED && reason != BatchRenameExecutor.REASON_REPLACED_NOT_RESTORED
}

data class BatchRenameJournalRecord(
    val batchId: String,
    val source: BatchRenameSource,
    /** UID of the plugin that ran an Explorer batch; null for scripts. zh-CN: 执行管理器批次的插件 UID; 脚本为 null. */
    val pluginUid: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val state: BatchRenameBatchState,
    val entries: List<BatchRenameJournalEntry>,
    /** The Explorer Action session parent a batch belongs to; null for scripts. zh-CN: 管理器批次所属的会话父目录. */
    val scope: String? = null,
) {
    val renamedCount: Int get() = entries.count { it.renamed }
    val restoredCount: Int get() = entries.count { it.state == BatchRenameEntryState.RESTORED }

    /** Undo can still act on this batch. zh-CN: 撤销仍可作用于该批次. */
    val undoable: Boolean get() = entries.any { it.undoable }

    private val appliedPaths by lazy {
        BatchRenamePathMap(entries.filter { it.applied }.associate { it.oldPath to it.newPath })
    }

    /**
     * Where [path], as it was before the batch, is now: moved by every rename of the batch that is in effect (the path
     * itself and its ancestors).
     * zh-CN: 批次执行前的 [path] 当前所在位置: 经本批所有仍生效的改名 (路径本身及其祖先) 移动后的结果.
     */
    fun relocate(path: String): File = File(appliedPaths.apply(path))

    /** Where [entry] is now. zh-CN: [entry] 当前所在位置. */
    fun locationOf(entry: BatchRenameJournalEntry): File = relocate(entry.oldPath)
}

/**
 * Renames of single path segments taken together: every rule gives one path a new last segment, and the rules are in
 * the coordinates of the original tree (an entry renamed below a renamed directory keeps the directory's old path).
 * [apply] follows a path deepest segment first and uses at most one rule per segment, so swaps and cycles of
 * directories map correctly.
 * zh-CN: 一组同时生效的单段改名: 每条规则为一个路径更换最后一段, 规则以原始目录树为坐标 (已改名目录下改名的条目保留目录的旧路径).
 * [apply] 从最深的一段开始沿路径向上, 每段至多使用一条规则, 因此目录的交换与循环也能正确映射.
 */
class BatchRenamePathMap(rules: Map<String, String>) {
    private val rules = HashMap(rules)

    fun apply(path: String): String {
        if (rules.isEmpty()) return path
        var current = path
        var end = current.length
        while (end > 0) {
            rules[current.substring(0, end)]?.let { target ->
                current = target + current.substring(end)
                end = target.length
            }
            end = current.lastIndexOf(File.separatorChar, end - 1)
        }
        return current
    }
}

/**
 * Persists rename batches as one JSON file each (plugin roadmap D21): `<directory>/<batchId>.json`. Scripts and every
 * plugin keep at most [maxBatches] each, the oldest evicted first; unreadable files are skipped and logged. Batch ids
 * are random and not guessable, so a plugin cannot address batches it did not create without listing them through its
 * session.
 *
 * zh-CN: 把改名批次各存为一个 JSON 文件 (插件路线图 D21). 脚本与每个插件各至多保留 [maxBatches] 批, 最旧的先淘汰; 无法读取的
 * 文件被跳过并记录日志. 批次 id 随机且不可猜测, 插件不经会话列出就无法定位不是它创建的批次.
 */
class BatchRenameJournalStore(
    private val directory: File,
    private val maxBatches: Int = MAX_BATCHES,
    private val log: (String) -> Unit = { message -> runCatching { Log.w(TAG, message) } },
) {
    private val lock = Any()
    private val random = SecureRandom()

    fun newBatchId(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return "b_" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun save(record: BatchRenameJournalRecord) = synchronized(lock) {
        require(isValidBatchId(record.batchId)) { "Invalid batch id" }
        directory.mkdirs()
        val file = fileOf(record.batchId)
        val temporary = File(directory, "${record.batchId}.json.tmp")
        temporary.writeText(encode(record).toString(), Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not write the rename journal ${file.name}" }
        }
        evict()
    }

    fun load(batchId: String): BatchRenameJournalRecord? = synchronized(lock) {
        if (!isValidBatchId(batchId)) return null
        read(fileOf(batchId))
    }

    /** Every readable batch, newest first. zh-CN: 全部可读取的批次, 最新在前. */
    fun list(): List<BatchRenameJournalRecord> = synchronized(lock) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty()
            .mapNotNull(::read)
            .sortedWith(compareByDescending<BatchRenameJournalRecord> { it.createdAt }.thenByDescending { it.batchId })
    }

    /** Keeps the newest [maxBatches] of scripts and of every plugin. zh-CN: 脚本与每个插件各保留最新的 [maxBatches] 批. */
    private fun evict() {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty()
            .mapNotNull { file -> read(file)?.let { it to file } }
            .groupBy { (record, _) -> record.source to record.pluginUid }
            .values.forEach { owned ->
                owned.sortedWith(compareByDescending<Pair<BatchRenameJournalRecord, File>> { it.first.createdAt }.thenByDescending { it.first.batchId })
                    .drop(maxBatches)
                    .forEach { (_, file) -> file.delete() }
            }
    }

    private fun fileOf(batchId: String) = File(directory, "$batchId.json")

    private fun read(file: File): BatchRenameJournalRecord? = try {
        decode(JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject)
    } catch (error: Exception) {
        log("Skipped the unreadable rename journal ${file.name}: $error")
        null
    }

    companion object {
        private const val TAG = "BatchRenameJournal"
        private const val FORMAT = 1

        /** Batches kept for scripts and for each plugin (Explorer Action `MAX_RENAME_BATCH_RESULTS`). */
        const val MAX_BATCHES = 20

        private val BATCH_ID = Regex("b_[0-9a-f]{32}")

        fun isValidBatchId(batchId: String?): Boolean = batchId != null && BATCH_ID.matches(batchId)

        internal fun encode(record: BatchRenameJournalRecord): JsonObject = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("batchId", record.batchId)
            addProperty("source", record.source.value)
            record.pluginUid?.let { addProperty("pluginUid", it) }
            addProperty("createdAt", record.createdAt)
            addProperty("updatedAt", record.updatedAt)
            addProperty("state", record.state.value)
            add("entries", JsonArray().apply {
                record.entries.forEach { entry ->
                    add(JsonObject().apply {
                        addProperty("oldPath", entry.oldPath)
                        addProperty("newPath", entry.newPath)
                        addProperty("directory", entry.directory)
                        addProperty("state", entry.state.value)
                        entry.reason?.let { addProperty("reason", it) }
                        addProperty("lastModified", entry.lastModified)
                        addProperty("length", entry.length)
                        entry.replacedTrashId?.let { addProperty("replacedTrashId", it) }
                        addProperty("replacedRestored", entry.replacedRestored)
                        entry.targetId?.let { addProperty("targetId", it) }
                        entry.relativePath?.let { addProperty("relativePath", it) }
                        addProperty("renamed", entry.renamed)
                    })
                }
            })
            record.scope?.let { addProperty("scope", it) }
        }

        internal fun decode(json: JsonObject): BatchRenameJournalRecord {
            require(json.get("format").asInt == FORMAT) { "Unsupported journal format" }
            val batchId = json.get("batchId").asString
            require(isValidBatchId(batchId)) { "Invalid batch id" }
            return BatchRenameJournalRecord(
                batchId = batchId,
                source = BatchRenameSource.entries.first { it.value == json.get("source").asString },
                pluginUid = json.get("pluginUid")?.takeUnless { it.isJsonNull }?.asInt,
                createdAt = json.get("createdAt").asLong,
                updatedAt = json.get("updatedAt").asLong,
                state = BatchRenameBatchState.entries.first { it.value == json.get("state").asString },
                entries = json.getAsJsonArray("entries").map { element ->
                    val entry = element.asJsonObject
                    val state = BatchRenameEntryState.entries.first { it.value == entry.get("state").asString }
                    BatchRenameJournalEntry(
                        oldPath = entry.get("oldPath").asString,
                        newPath = entry.get("newPath").asString,
                        directory = entry.get("directory").asBoolean,
                        state = state,
                        reason = entry.get("reason")?.takeUnless { it.isJsonNull }?.asString,
                        lastModified = entry.get("lastModified").asLong,
                        length = entry.get("length").asLong,
                        replacedTrashId = entry.get("replacedTrashId")?.takeUnless { it.isJsonNull }?.asString,
                        replacedRestored = entry.get("replacedRestored")?.asBoolean ?: false,
                        targetId = entry.get("targetId")?.takeUnless { it.isJsonNull }?.asString,
                        relativePath = entry.get("relativePath")?.takeUnless { it.isJsonNull }?.asString,
                        renamed = entry.get("renamed")?.asBoolean ?: (state == BatchRenameEntryState.RENAMED),
                    )
                },
                scope = json.get("scope")?.takeUnless { it.isJsonNull }?.asString,
            )
        }
    }
}
