package org.autojs.autojs.core.plugin.batchrename

import org.autojs.plugin.batchrename.api.BatchRenameOptionKeys
import org.autojs.plugin.batchrename.api.BatchRenameValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone

/** What the host sends the plugin for one plan (contract v1 request). zh-CN: 宿主为一个方案发给插件的内容. */
data class BatchRenamePlanRequest(
    val options: Map<String, Any>,
    val parents: List<BatchRenameParent>,
    val items: List<BatchRenameTarget>,
    /** Items the script excluded, one flag per item. zh-CN: 脚本排除的条目, 每项一个标志. */
    val excluded: List<Boolean>,
    val locale: String,
    val timeZone: String,
    val now: Long,
)

data class BatchRenamePlanAnswerItem(
    val newName: String?,
    val status: String,
    val reason: String?,
    val overwrites: Boolean,
    /** A metadata variable had no value for the item (plugin roadmap D34). zh-CN: 条目缺少某个元数据变量的值. */
    val metadataMissing: Boolean = false,
)

/**
 * The plugin's answer to a [BatchRenamePlanRequest]; [options] are the complete options the plan used, answered by
 * plugins with the presets feature (plugin roadmap D44).
 * zh-CN: 插件对 [BatchRenamePlanRequest] 的应答; [options] 为方案所用的完整选项, 由具备预设功能的插件回传 (插件路线图 D44).
 */
data class BatchRenamePlanAnswer(
    val rejected: Boolean,
    val items: List<BatchRenamePlanAnswerItem>,
    val counts: Map<String, Int>,
    val options: Map<String, Any>? = null,
)

/** A preset the plugin keeps: its name and complete options. zh-CN: 插件保存的预设: 名称与完整选项. */
data class BatchRenamePreset(val name: String, val options: Map<String, Any>)

/** The outcome of `importPresets`. zh-CN: `importPresets` 的结果. */
data class BatchRenamePresetImport(val imported: List<String>, val skipped: List<String>, val rejected: List<Map<String, Any?>>)

/** Progress of `apply` as the script sees it: renames done, renames in total and the item just handled. */
fun interface BatchRenameScriptProgress {
    fun onProgress(done: Int, total: Int, item: Map<String, Any?>)
}

/**
 * The `files.batchRename` script API without Rhino (plugin roadmap P3.2, appendix B): `plan` enumerates the targets
 * and asks the plugin, `apply` executes a plan through the shared host executor, `undo` and `history` work on the shared
 * journal. Values come back as maps and lists the Rhino layer turns into script objects. A script keeps its last
 * [MAX_PLANS] plans; `apply` finds a plan by its ID, plans again when the script changed `excluded` flags or the conflict
 * policy, and runs each plan at most once.
 *
 * zh-CN: 不含 Rhino 的 `files.batchRename` 脚本 API (插件路线图 P3.2, 附录 B): `plan` 枚举目标并询问插件, `apply` 经共用的宿主执行器
 * 执行方案, `undo` 与 `history` 作用于共用的日志. 结果以 Map 与 List 返回, 由 Rhino 层转为脚本对象. 每个脚本保留最近 [MAX_PLANS]
 * 个方案; `apply` 按 ID 找到方案, 脚本修改了 `excluded` 标志或冲突策略时重新询问插件, 每个方案至多执行一次.
 */
class BatchRenameScriptService(
    private val executor: () -> BatchRenameExecutor,
    private val planner: suspend (BatchRenamePlanRequest) -> BatchRenamePlanAnswer,
    /** The plugin's presets, asked only for a plan that names one (plugin roadmap D36). zh-CN: 插件的预设, 仅在方案指定预设时询问. */
    private val presets: suspend () -> List<BatchRenamePreset> = { emptyList() },
    /** Photo metadata of image files, null for the others (plugin roadmap D34). zh-CN: 图片文件的照片元数据, 其他文件为 null. */
    private val metadata: suspend (List<File>) -> List<Map<String, Any>?> = ::readMetadata,
    private val collector: BatchRenameTargetCollector = BatchRenameTargetCollector(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val locale: () -> String = { Locale.getDefault().toLanguageTag() },
    private val timeZone: () -> String = { TimeZone.getDefault().id },
) {
    private val random = SecureRandom()

    private val plans = object : LinkedHashMap<String, PlanState>(MAX_PLANS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlanState>?) = size > MAX_PLANS
    }

    private class PlanState(val id: String, val request: BatchRenamePlanRequest, val answer: BatchRenamePlanAnswer) {
        var used = false
    }

    /**
     * `plan(paths, options)`: [options] is the map of [BatchRenameScriptArguments.options]. With `preset` the host reads
     * the preset to enumerate the targets with its `includeSubfolders`, the plugin resolves the preset itself, and the
     * plan keeps the complete options the plugin answers (plugin roadmap D36 / D44).
     * zh-CN: `plan(paths, options)`: [options] 为 [BatchRenameScriptArguments.options] 的结果. 带 `preset` 时, 宿主读取该预设以按其
     * `includeSubfolders` 枚举目标, 由插件自行解析预设, 方案保存插件回传的完整选项 (插件路线图 D36 / D44).
     */
    suspend fun plan(paths: List<File>, options: Map<String, Any>): Map<String, Any?> {
        val starting = (options[BatchRenameOptionKeys.PRESET] as? String)?.let { name ->
            val preset = presets().firstOrNull { it.name == name }
                ?: batchRenameFailure(BatchRenameScriptCodes.INVALID_OPTIONS, "the preset \"$name\" does not exist")
            BatchRenameScriptArguments.withPreset(options, preset.options)
        } ?: options
        val collected = collector.collect(paths, starting[BatchRenameOptionKeys.INCLUDE_SUBFOLDERS] as Boolean)
        // Metadata is read only when the replacement uses it (plugin roadmap D34). zh-CN: 仅当替换文本用到时读取元数据.
        val targets = if (starting[BatchRenameOptionKeys.METADATA] == true &&
            BatchRenameScriptArguments.usesMetadataVariables(starting[BatchRenameOptionKeys.REPLACE] as? String ?: "")
        ) {
            val files = collected.items.filterNot { it.directory }.map { it.file }
            val read = files.zip(metadata(files)).toMap()
            collected.copy(items = collected.items.map { item -> if (item.directory) item else item.copy(metadata = read[item.file]) })
        } else {
            collected
        }
        val request = BatchRenamePlanRequest(
            options = LinkedHashMap(options),
            parents = targets.parents,
            items = targets.items,
            excluded = List(targets.items.size) { false },
            locale = locale(),
            timeZone = timeZone(),
            now = clock(),
        )
        val answer = ask(request)
        // Later requests of this plan (apply with new flags or policy) send the complete options without the preset.
        // zh-CN: 该方案之后的请求 (以新的标志或策略执行) 发送不带预设的完整选项.
        val kept = if (options.containsKey(BatchRenameOptionKeys.PRESET)) request.copy(options = LinkedHashMap(answer.options ?: starting)) else request
        val state = PlanState(newPlanId(), kept, answer)
        synchronized(plans) { plans[state.id] = state }
        return planView(state.id, kept, state.answer)
    }

    /**
     * `preview(nameOrItems, options)` (plugin roadmap D37): the plugin plans [items] as if they were the only entries of
     * one directory, so conflicts come only from the items themselves; nothing on disk is read or renamed. Counters
     * advance in the order of [items]. Each answer is `{ newName, status, reason }`.
     * zh-CN: `preview(nameOrItems, options)` (插件路线图 D37): 插件把 [items] 视为某个目录中仅有的条目来计算方案, 因此冲突只来自这些
     * 条目本身; 不读取也不改名磁盘上的任何内容. 计数器按 [items] 的顺序递增. 每个结果为 `{ newName, status, reason }`.
     */
    suspend fun preview(items: List<BatchRenamePreviewItem>, options: Map<String, Any>): List<Map<String, Any?>> {
        if (options[BatchRenameOptionKeys.METADATA] == true) {
            batchRenameFailure(BatchRenameScriptCodes.INVALID_OPTIONS, "preview reads no files, so it cannot expand metadata variables")
        }
        if (items.isEmpty()) return emptyList()
        val now = clock()
        val parent = BatchRenameParent(PREVIEW_PARENT, File(""), items.map { it.name }, BatchRenameVolumeTraits(caseInsensitive = false, restrictedCharacters = false))
        val targets = items.map { item ->
            BatchRenameTarget(File(item.name), PREVIEW_PARENT, 0, item.directory, item.lastModified ?: now, if (item.directory) -1L else 0L)
        }
        val request = BatchRenamePlanRequest(LinkedHashMap(options), listOf(parent), targets, List(items.size) { false }, locale(), timeZone(), now)
        return ask(request).items.map { linkedMapOf("newName" to it.newName, "status" to it.status, "reason" to it.reason) }
    }

    /**
     * `apply(plan, applyOptions)`. [excluded] holds the `excluded` flags of the script's plan items (null keeps those of
     * the plan) and [onConflict] overrides the plan's policy.
     * zh-CN: `apply(plan, applyOptions)`. [excluded] 为脚本方案条目的 `excluded` 标志 (null 沿用方案的), [onConflict] 覆盖方案的策略.
     */
    suspend fun apply(planId: String, excluded: List<Boolean>?, onConflict: String?, progress: BatchRenameScriptProgress?): Map<String, Any?> {
        val state = synchronized(plans) { plans[planId] }
            ?: batchRenameFailure(BatchRenameScriptCodes.PLAN_STALE, "the plan is unknown to this script or has expired; plan again")
        synchronized(state) {
            if (state.used) batchRenameFailure(BatchRenameScriptCodes.PLAN_STALE, "the plan was already applied; plan again")
            state.used = true
        }
        var executed = false
        try {
            val flags = excluded ?: state.request.excluded
            if (flags.size != state.request.items.size) {
                batchRenameFailure(BatchRenameScriptCodes.INVALID_OPTIONS, "plan items must not be added or removed")
            }
            val policy = onConflict ?: state.request.options.getValue(BatchRenameOptionKeys.ON_CONFLICT)
            val unchanged = flags == state.request.excluded && policy == state.request.options[BatchRenameOptionKeys.ON_CONFLICT]
            val request = if (unchanged) state.request else state.request.copy(
                options = LinkedHashMap(state.request.options).apply { put(BatchRenameOptionKeys.ON_CONFLICT, policy) },
                excluded = flags,
            )
            val answer = if (unchanged) state.answer else ask(request)
            if (answer.rejected) rejected(request, answer)
            val renames = answer.items.indices.filter { answer.items[it].status == BatchRenameValues.STATUS_RENAME }
            executed = true
            val record = if (renames.isEmpty()) null else execute(request, answer, renames, progress)
            return resultView(request, answer, renames, record)
        } finally {
            // A plan that did not reach the executor may be applied again. zh-CN: 未到达执行器的方案可以再次执行.
            if (!executed) synchronized(state) { state.used = false }
        }
    }

    /** `undo(result | batchId)`. */
    fun undo(batchId: String): Map<String, Any?> {
        val executor = executor()
        val stored = executor.journal.load(batchId)
            ?: batchRenameFailure(BatchRenameScriptCodes.UNDO_UNAVAILABLE, "no rename batch $batchId is kept (the journal keeps the newest ${BatchRenameJournalStore.MAX_BATCHES})")
        if (!stored.undoable) batchRenameFailure(BatchRenameScriptCodes.UNDO_UNAVAILABLE, "rename batch $batchId has nothing left to undo")
        var before: BatchRenameJournalRecord? = null
        val after = try {
            executor.undo(batchId) { record -> record.undoable.also { if (it) before = record } }
        } catch (e: IOException) {
            throw BatchRenameScriptException(BatchRenameScriptCodes.IO_FAILED, "could not update the rename journal: ${e.message}", e)
        } ?: batchRenameFailure(BatchRenameScriptCodes.UNDO_UNAVAILABLE, "rename batch $batchId has nothing left to undo")
        val start = requireNotNull(before)
        val items = start.entries.zip(after.entries)
            .filter { (old, _) -> old.undoable }
            .map { (old, new) ->
                linkedMapOf<String, Any?>(
                    "path" to start.locationOf(old).path,
                    "newPath" to after.locationOf(new).path,
                    "status" to when (new.state) {
                        BatchRenameEntryState.RESTORED -> STATUS_RESTORED
                        BatchRenameEntryState.STALE -> STATUS_STALE
                        else -> STATUS_FAILED
                    },
                    "reason" to new.reason,
                )
            }
        return linkedMapOf(
            "batchId" to batchId,
            "state" to after.state.value,
            "counts" to countsOf(items, listOf(STATUS_RESTORED, STATUS_STALE, STATUS_FAILED)),
            "items" to items,
            "undoable" to after.undoable,
        )
    }

    /** `history(limit)`: the newest batches of scripts and of the file manager. zh-CN: 脚本与文件管理器最近的批次. */
    fun history(limit: Int): List<Map<String, Any?>> = executor().journal.list().take(limit).map { record ->
        linkedMapOf(
            "batchId" to record.batchId,
            "source" to record.source.value,
            "state" to record.state.value,
            "createdAt" to record.createdAt,
            "updatedAt" to record.updatedAt,
            "counts" to linkedMapOf(
                "renamed" to record.renamedCount,
                "notRenamed" to record.entries.size - record.renamedCount,
                "restored" to record.restoredCount,
            ),
            "undoable" to record.undoable,
        )
    }

    private suspend fun ask(request: BatchRenamePlanRequest): BatchRenamePlanAnswer {
        val answer = planner(request)
        if (answer.items.size != request.items.size ||
            answer.items.any { it.status == BatchRenameValues.STATUS_RENAME && !BatchRenameNames.isValid(it.newName.orEmpty()) }
        ) {
            batchRenameFailure(BatchRenameScriptCodes.INTERNAL, "the Batch Rename plugin answered a plan that does not match the request")
        }
        return answer
    }

    private fun execute(
        request: BatchRenamePlanRequest,
        answer: BatchRenamePlanAnswer,
        renames: List<Int>,
        progress: BatchRenameScriptProgress?,
    ): BatchRenameJournalRecord {
        val operations = renames.map { index ->
            val target = request.items[index]
            BatchRenameOperation(
                source = target.file,
                newName = answer.items[index].newName!!,
                directory = target.directory,
                expectedLastModified = target.lastModified.takeUnless { target.directory },
                expectedLength = target.length.takeUnless { target.directory },
                overwrite = answer.items[index].overwrites,
            )
        }
        val callback = progress?.let {
            BatchRenameProgress { done, total, entry ->
                it.onProgress(done, total, linkedMapOf("path" to entry.oldPath, "newPath" to entry.newPath, "status" to statusOf(entry), "reason" to entry.reason))
            }
        }
        return try {
            executor().execute(operations, BatchRenameSource.SCRIPT, progress = callback)
        } catch (e: IOException) {
            throw BatchRenameScriptException(BatchRenameScriptCodes.IO_FAILED, "could not record the rename batch: ${e.message}", e)
        }
    }

    private fun planView(id: String, request: BatchRenamePlanRequest, answer: BatchRenamePlanAnswer): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "options" to LinkedHashMap(request.options),
        "items" to request.items.mapIndexed { index, target ->
            val item = answer.items[index]
            linkedMapOf(
                "path" to target.file.path,
                "name" to target.file.name,
                "kind" to if (target.directory) KIND_DIRECTORY else KIND_FILE,
                "depth" to target.depth,
                "newName" to item.newName,
                "status" to item.status,
                "reason" to item.reason,
                "excluded" to request.excluded[index],
                "overwrites" to item.overwrites,
                "metadataMissing" to item.metadataMissing,
            )
        },
        "counts" to linkedMapOf(*PLAN_STATUSES.map { it to (answer.counts[it] ?: 0) }.toTypedArray()),
        "rejected" to answer.rejected,
    )

    private fun resultView(
        request: BatchRenamePlanRequest,
        answer: BatchRenamePlanAnswer,
        renames: List<Int>,
        record: BatchRenameJournalRecord?,
    ): Map<String, Any?> {
        val entries = renames.withIndex().associate { (position, index) -> index to record!!.entries[position] }
        val items = request.items.mapIndexed { index, target ->
            val item = answer.items[index]
            val entry = entries[index]
            val path = target.file.path
            when {
                entry != null -> linkedMapOf<String, Any?>(
                    "path" to path,
                    "newPath" to record!!.locationOf(entry).path,
                    "status" to statusOf(entry),
                    "reason" to entry.reason,
                )
                else -> linkedMapOf(
                    "path" to path,
                    "newPath" to (record?.relocate(path)?.path ?: path),
                    "status" to if (item.status == BatchRenameValues.STATUS_EXCLUDED) STATUS_EXCLUDED else STATUS_SKIPPED,
                    "reason" to item.reason,
                )
            }
        }
        return linkedMapOf(
            "batchId" to record?.batchId,
            "counts" to countsOf(items, listOf(STATUS_RENAMED, STATUS_SKIPPED, STATUS_FAILED, STATUS_STALE, STATUS_EXCLUDED)),
            "items" to items,
            "undoable" to (record?.undoable ?: false),
        )
    }

    private fun rejected(request: BatchRenamePlanRequest, answer: BatchRenamePlanAnswer): Nothing {
        val refused = answer.items.withIndex().filter {
            it.value.status == BatchRenameValues.STATUS_CONFLICT || it.value.status == BatchRenameValues.STATUS_INVALID
        }
        val conflicts = refused.count { it.value.status == BatchRenameValues.STATUS_CONFLICT }
        val examples = refused.take(MAX_REJECTION_EXAMPLES).joinToString { (index, item) ->
            "${request.items[index].file.name} -> ${item.newName} (${item.reason})"
        }
        val more = if (refused.size > MAX_REJECTION_EXAMPLES) ", ..." else ""
        batchRenameFailure(
            BatchRenameScriptCodes.PLAN_REJECTED,
            "the plan has $conflicts conflicting and ${refused.size - conflicts} invalid items " +
                "(onConflict \"${request.options[BatchRenameOptionKeys.ON_CONFLICT]}\"): $examples$more",
        )
    }

    private fun countsOf(items: List<Map<String, Any?>>, statuses: List<String>): Map<String, Int> =
        linkedMapOf(*statuses.map { status -> status to items.count { it["status"] == status } }.toTypedArray())

    private fun statusOf(entry: BatchRenameJournalEntry): String = when (entry.state) {
        BatchRenameEntryState.RENAMED -> STATUS_RENAMED
        BatchRenameEntryState.STALE -> STATUS_STALE
        else -> STATUS_FAILED
    }

    private fun newPlanId(): String = "p_" + ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    companion object {
        /** Plans a script keeps for `apply`. zh-CN: 每个脚本为 `apply` 保留的方案数. */
        const val MAX_PLANS = 16

        private const val MAX_REJECTION_EXAMPLES = 5

        /** The one directory of a preview request; no file is read. zh-CN: 试算请求中唯一的目录; 不读取任何文件. */
        private const val PREVIEW_PARENT = "preview"

        /** Images read at the same time. zh-CN: 同时读取的图片数. */
        private const val METADATA_PARALLELISM = 4

        /**
         * Reads image files [METADATA_PARALLELISM] at a time on the IO dispatcher; other files get null.
         * zh-CN: 在 IO 调度器上每次并发读取 [METADATA_PARALLELISM] 个图片文件; 其他文件为 null.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        suspend fun readMetadata(files: List<File>): List<Map<String, Any>?> = coroutineScope {
            val dispatcher = Dispatchers.IO.limitedParallelism(METADATA_PARALLELISM)
            files.map { file -> async(dispatcher) { if (BatchRenameMetadataReader.isImage(file)) BatchRenameMetadataReader.read(file) else null } }.awaitAll()
        }

        const val KIND_FILE = "file"
        const val KIND_DIRECTORY = "dir"

        const val STATUS_RENAMED = "renamed"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_FAILED = "failed"
        const val STATUS_STALE = "stale"
        const val STATUS_EXCLUDED = "excluded"
        const val STATUS_RESTORED = "restored"

        private val PLAN_STATUSES = listOf(
            BatchRenameValues.STATUS_RENAME,
            BatchRenameValues.STATUS_UNCHANGED,
            BatchRenameValues.STATUS_CONFLICT,
            BatchRenameValues.STATUS_INVALID,
            BatchRenameValues.STATUS_EXCLUDED,
        )
    }
}
