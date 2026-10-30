package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs.core.plugin.batchrename.BatchRenameHostServices
import org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginHost
import org.autojs.autojs.core.plugin.batchrename.BatchRenameRegExp
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptArguments
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptCodes
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptException
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptProgress
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptService
import org.json.JSONArray
import java.io.Closeable
import java.io.File

/**
 * Host side of `files.batchRename` for the JS runtimes (api-coverage Q82 (a)): the `batch_rename` bridge module, Node and Bun
 * sharing it. [BatchRenameScriptService] does what it does for Rhino's module; a plan belongs to the execution, so a Worker can
 * apply a plan of the main thread. `apply` reports its progress as events of the operation, dropping them while the script
 * does not drain (the batch is journaled either way).
 *
 * zh-CN: JS 运行时的 files.batchRename 宿主侧 (api-coverage Q82 (a)): batch_rename 桥接模块, Node 与 Bun 共用. [BatchRenameScriptService] 的工作同 Rhino 模块; 方案属于本次执行, 因此 Worker 可以执行主线程的方案.
 * apply 以操作事件报告进度, 脚本不取事件时丢弃进度 (批次照常记入日志).
 */
internal class RuntimeBatchRenameBridge(private val context: Context, private val resolve: (String) -> File?) :
    RuntimeOperationClients<Closeable>("batch_rename", "BATCH_RENAME") {

    private val service by lazy {
        BatchRenameScriptService(
            executor = { BatchRenameHostServices.executor(context) },
            planner = { request -> BatchRenamePluginHost.planForScript(context, request) },
            presets = { BatchRenamePluginHost.presetsForScript(context) },
        )
    }

    override val operations = setOf("isAvailable", "plan", "preview", "apply", "undo", "history", "presets", "importPresets", "deleteHistory", "clearHistory")

    override fun newSession() = Closeable {}

    override fun errorOf(error: Throwable): Pair<String, String> = when (error) {
        is BatchRenameScriptException -> error.code to error.message.orEmpty()
        is IllegalArgumentException, is org.json.JSONException -> BatchRenameScriptCodes.INVALID_OPTIONS to (error.message ?: "Invalid batch rename arguments")
        else -> BatchRenameScriptCodes.INTERNAL to (error.message ?: error.toString())
    }

    override suspend fun invoke(client: Client, id: String, method: String, args: JSONArray): Any? = when (method) {
        "isAvailable" -> BatchRenamePluginHost.isAvailable(context)
        "plan" -> {
            val files = BatchRenameScriptArguments.paths(args.json(0)).map(::file)
            service.plan(files, options(args))
        }
        "preview" -> {
            val (items, array) = BatchRenameScriptArguments.previewItems(args.json(0))
            val results = service.preview(items, options(args))
            if (array) results else results.single()
        }
        "apply" -> {
            val excluded = args.getJSONArray(1).let { flags -> List(flags.length()) { flags.getBoolean(it) } }
            val onConflict = BatchRenameScriptArguments.conflictOverride(args.json(2))
            val progress = if (!args.optBoolean(3)) null else BatchRenameScriptProgress { done, total, item ->
                if (client.pending(id) < MAX_PENDING_EVENTS) client.emit(id, "progress", listOf(done, total, item))
            }
            service.apply(args.getString(0), excluded, onConflict, progress)
        }
        "undo" -> service.undo(BatchRenameScriptArguments.batchId(args.json(0)))
        "history" -> service.history(BatchRenameScriptArguments.historyLimit(args.json(0), Int.MAX_VALUE))
        "presets" -> BatchRenamePluginHost.presetsForScript(context).map { linkedMapOf("name" to it.name, "options" to LinkedHashMap(it.options)) }
        "importPresets" -> {
            val onConflict = BatchRenameScriptArguments.presetConflict(args.json(1))
            val result = BatchRenamePluginHost.importPresetsForScript(context, args.getString(0), onConflict)
            linkedMapOf("imported" to result.imported, "skipped" to result.skipped, "rejected" to result.rejected)
        }
        "deleteHistory" -> service.deleteHistory(BatchRenameScriptArguments.historyBatchId(args.json(0)))
        "clearHistory" -> service.clearHistory(BatchRenameScriptArguments.historySource(args.json(0)))
        else -> throw IllegalArgumentException("Unknown batch rename operation: $method")
    }

    /** `args[1]` the options, `args[2]` the `{ source, flags }` of a `RegExp` search. zh-CN: args[1] 为选项, args[2] 为 RegExp 查找的 { source, flags }. */
    private fun options(args: JSONArray) = BatchRenameScriptArguments.options(args.json(1), args.optJSONObject(2)?.let { BatchRenameRegExp(it.getString("source"), it.getString("flags")) })

    private fun file(path: String) = resolve(path) ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_PATHS, "invalid path: $path")
}
