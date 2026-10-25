package org.autojs.autojs.runtime.api.modules.files

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.batchrename.BatchRenameHostServices
import org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginHost
import org.autojs.autojs.core.plugin.batchrename.BatchRenameRegExp
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptArguments
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptCodes
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptException
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptProgress
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptService
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.regexp.NativeRegExp
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.cancellation.CancellationException

/**
 * `files.batchRename` (plugin roadmap P3.2, D2, D9, appendix B): the callable `files.batchRename(paths, options)`
 * (plan and apply in one step) with `plan`, `apply`, `undo`, `history`, `isAvailable`, `presets`, `importPresets`
 * (plugin roadmap D36), `preview` (D37), the `Async` forms of `plan`, `apply`, `undo`, `presets`, `importPresets` and
 * `preview`, and the `BatchRenameError` constructor. `$files.batchRename` is the same object. The Batch Rename
 * plugin computes the names; the host enumerates the targets and performs, journals and undoes the renames
 * ([BatchRenameScriptService]). Only the Rhino conversion happens here.
 *
 * The synchronous forms block the script thread with the script's job (stopping the script cancels the wait) and run
 * the renames and the `onProgress` callbacks on that thread. The `Async` forms run in the script's coroutine scope and
 * deliver progress and the outcome on the script thread. A throwing `onProgress` never interrupts a batch: the batch
 * finishes and is journaled, then the call throws (or rejects) with the callback's error.
 *
 * zh-CN: `files.batchRename` (插件路线图 P3.2, D2, D9, 附录 B): 可调用的 `files.batchRename(paths, options)` (一步完成方案与执行),
 * 以及 `plan`, `apply`, `undo`, `history`, `isAvailable`, `presets`, `importPresets` (插件路线图 D36), `preview` (D37), `plan` /
 * `apply` / `undo` / `presets` / `importPresets` / `preview` 的 `Async` 形态与 `BatchRenameError` 构造器.
 * `$files.batchRename` 为同一对象. 名称由 Batch Rename 插件计算; 宿主负责枚举目标, 执行, 记录与撤销改名 ([BatchRenameScriptService]).
 * 这里只做 Rhino 转换. 同步形态以脚本的 Job 阻塞脚本线程 (停止脚本即取消等待), 改名与 `onProgress` 回调都在该线程上运行. `Async`
 * 形态在脚本的协程作用域里运行, 进度与结果在脚本线程上送达. 抛出异常的 `onProgress` 不会中断批次: 批次完成并记入日志后, 调用再抛出
 * (或拒绝) 该回调的错误.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class BatchRename(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    private val service by lazy {
        val context = ScriptRuntime.applicationContext
        BatchRenameScriptService(
            executor = { BatchRenameHostServices.executor(context) },
            planner = { request -> BatchRenamePluginHost.planForScript(context, request) },
            presets = { BatchRenamePluginHost.presetsForScript(context) },
        )
    }

    /** `files.batchRename(paths, options)`: `apply(plan(paths, options), { onProgress })`. */
    fun invoke(vararg args: Any?): Any? = guarded {
        ensureArgumentsLength(args, 2) { (paths, options) ->
            val planWork = planWork(paths, options)
            val progress = progressOf(options)
            sync { binder ->
                val plan = planWork()
                @Suppress("UNCHECKED_CAST")
                val items = plan["items"] as List<Map<String, Any?>>
                service.apply(plan["id"] as String, items.map { it["excluded"] == true }, null, binder.bind(progress))
            }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun plan(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLength(args, 2) { (paths, options) ->
            val work = planWork(paths, options)
            sync { work() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun planAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLength(args, 2) { (paths, options) ->
            async {
                val work = planWork(paths, options)
                val run: suspend (ProgressBinder) -> Any? = { work() }
                run
            }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun applyPlan(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            val work = applyWork(a[0], a.getOrNull(1))
            sync { callback -> work(callback) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun applyAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            async { applyWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun undo(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { target ->
            val batchId = BatchRenameScriptArguments.batchId(json(target))
            sync { service.undo(batchId) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun undoAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { target ->
            async {
                val batchId = BatchRenameScriptArguments.batchId(json(target))
                val run: suspend (ProgressBinder) -> Any? = { service.undo(batchId) }
                run
            }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun history(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsAtMost(args, 1) { a ->
            val limit = BatchRenameScriptArguments.historyLimit(json(a.getOrNull(0)), Int.MAX_VALUE)
            sync { service.history(limit) }
        }
    }

    /**
     * `preview(nameOrItems, options)`: what the options make of names that need not exist (plugin roadmap D37); an
     * array answers an array.
     * zh-CN: `preview(nameOrItems, options)`: 选项作用于不必存在的名称的结果 (插件路线图 D37); 传入数组则返回数组.
     */
    @RhinoRuntimeFunctionInterface
    fun preview(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLength(args, 2) { (items, options) ->
            val work = previewWork(items, options)
            sync { work() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun previewAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLength(args, 2) { (items, options) ->
            async {
                val work = previewWork(items, options)
                val run: suspend (ProgressBinder) -> Any? = { work() }
                run
            }
        }
    }

    /** `presets()`: `{ name, options }` of every preset the plugin keeps. zh-CN: `presets()`: 插件保存的每个预设的 `{ name, options }`. */
    @RhinoRuntimeFunctionInterface
    fun presets(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            sync { presetsView() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun presetsAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            async {
                val run: suspend (ProgressBinder) -> Any? = { presetsView() }
                run
            }
        }
    }

    /**
     * `importPresets(json, { onConflict })`: a JSON string or an object of the exported format; `{ imported, skipped,
     * rejected }` (plugin roadmap D36 / D43).
     * zh-CN: `importPresets(json, { onConflict })`: 导出格式的 JSON 字符串或对象; 返回 `{ imported, skipped, rejected }`.
     */
    @RhinoRuntimeFunctionInterface
    fun importPresets(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            val work = importWork(a[0], a.getOrNull(1))
            sync { work() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun importPresetsAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            async {
                val work = importWork(a[0], a.getOrNull(1))
                val run: suspend (ProgressBinder) -> Any? = { work() }
                run
            }
        }
    }

    /** `deleteHistory(result | batchId)` (plugin roadmap D39). zh-CN: 删除一个批次的历史 (插件路线图 D39). */
    @RhinoRuntimeFunctionInterface
    fun deleteHistory(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { target ->
            val batchId = BatchRenameScriptArguments.historyBatchId(json(target))
            sync { service.deleteHistory(batchId) }
        }
    }

    /** `clearHistory({ source })` (plugin roadmap D39). zh-CN: 清空全部或某个来源的历史 (插件路线图 D39). */
    @RhinoRuntimeFunctionInterface
    fun clearHistory(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsAtMost(args, 1) { a ->
            val source = BatchRenameScriptArguments.historySource(json(a.getOrNull(0)))
            sync { service.clearHistory(source) }
        }
    }

    /** Whether a compatible, enabled plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的插件应答; 从不抛出. */
    @RhinoRuntimeFunctionInterface
    fun isAvailable(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            sync { BatchRenamePluginHost.isAvailable(ScriptRuntime.applicationContext) }
        }
    }

    // ------------------------------------------------------------------ internals

    /** Wraps a progress function for one call. zh-CN: 为一次调用包装进度函数. */
    private fun interface ProgressBinder {
        fun bind(function: Function?): BatchRenameScriptProgress?
    }

    /** Checks the arguments of `plan` on the script thread and returns the work that asks the plugin. */
    private fun planWork(paths: Any?, options: Any?): suspend () -> Map<String, Any?> {
        val files = BatchRenameScriptArguments.paths(json(paths)).map { File(scriptRuntime.files.nonNullPath(it)) }
        val normalized = BatchRenameScriptArguments.options(json(options), regExpOf(options))
        return { service.plan(files, normalized) }
    }

    /** Checks the arguments of `apply` on the script thread and returns the work that runs the plan. */
    private fun applyWork(plan: Any?, applyOptions: Any?): suspend (ProgressBinder) -> Map<String, Any?> {
        val planObject = plan as? Scriptable
            ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_OPTIONS, "apply needs a plan returned by plan(), got ${plan.jsBrief()}")
        val id = ScriptableObject.getProperty(planObject, "id") as? CharSequence
            ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_OPTIONS, "apply needs a plan returned by plan(): the plan has no id")
        val items = ScriptableObject.getProperty(planObject, "items") as? NativeArray
            ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_OPTIONS, "apply needs a plan returned by plan(): the plan has no items")
        val excluded = (0 until items.length.toInt()).map { index ->
            (items.get(index, items) as? Scriptable)?.let { ScriptableObject.getProperty(it, "excluded") } == true
        }
        val onConflict = BatchRenameScriptArguments.conflictOverride(json(applyOptions))
        val progress = progressOf(applyOptions)
        return { binder -> service.apply(id.toString(), excluded, onConflict, binder.bind(progress)) }
    }

    /** Checks the arguments of `preview` on the script thread and returns the work that asks the plugin. */
    private fun previewWork(items: Any?, options: Any?): suspend () -> Any? {
        val (list, array) = BatchRenameScriptArguments.previewItems(json(items))
        val normalized = BatchRenameScriptArguments.options(json(options), regExpOf(options))
        return {
            val results = service.preview(list, normalized)
            if (array) results else results.single()
        }
    }

    private suspend fun presetsView(): List<Map<String, Any?>> =
        BatchRenamePluginHost.presetsForScript(ScriptRuntime.applicationContext).map { linkedMapOf("name" to it.name, "options" to LinkedHashMap(it.options)) }

    /** Checks the arguments of `importPresets` on the script thread and returns the work that asks the plugin. */
    private fun importWork(document: Any?, options: Any?): suspend () -> Map<String, Any?> {
        val text = when (document) {
            is CharSequence -> document.toString()
            is Scriptable -> json(document).toString()
            else -> throw BatchRenameScriptException(
                BatchRenameScriptCodes.INVALID_OPTIONS,
                "importPresets needs a JSON string or an object of exported presets, got ${document.jsBrief()}",
            )
        }
        val onConflict = BatchRenameScriptArguments.presetConflict(json(options))
        return {
            val result = BatchRenamePluginHost.importPresetsForScript(ScriptRuntime.applicationContext, text, onConflict)
            linkedMapOf("imported" to result.imported, "skipped" to result.skipped, "rejected" to result.rejected)
        }
    }

    private fun progressOf(options: Any?): Function? {
        val holder = options as? Scriptable ?: return null
        val value = ScriptableObject.getProperty(holder, "onProgress")
        if (value == Scriptable.NOT_FOUND || value.isJsNullish()) return null
        return value as? Function
            ?: throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_OPTIONS, "onProgress must be a function, got ${value.jsBrief()}")
    }

    /** The `RegExp` passed as `options.search`, or null. zh-CN: 作为 `options.search` 传入的 `RegExp`, 或 null. */
    private fun regExpOf(options: Any?): BatchRenameRegExp? {
        val holder = options as? Scriptable ?: return null
        val search = ScriptableObject.getProperty(holder, "search") as? NativeRegExp ?: return null
        return BatchRenameRegExp(
            source = Context.toString(ScriptableObject.getProperty(search, "source")),
            flags = Context.toString(ScriptableObject.getProperty(search, "flags")),
        )
    }

    /**
     * Runs [work] on the script thread under the script's job and returns its result as a script value. Progress
     * callbacks run directly; the first error one throws is rethrown once the work finished.
     */
    private fun sync(work: suspend (ProgressBinder) -> Any?): Any? {
        val callbackError = AtomicReference<Throwable?>()
        val binder = ProgressBinder { function ->
            function?.let {
                BatchRenameScriptProgress { done, total, item ->
                    if (callbackError.get() != null) return@BatchRenameScriptProgress
                    try {
                        RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
                            val scope = scriptRuntime.topLevelScope
                            it.call(cx, scope, scope, arrayOf(done, total, RhinoUtils.toJsValue(scope, item)))
                        }
                    } catch (t: Throwable) {
                        if (ScriptInterruptedException.causedByInterrupt(t)) throw t
                        callbackError.set(t)
                    }
                }
            }
        }
        // Keep the job (cancellation), drop the dispatcher: the work stays on this thread.
        // zh-CN: 保留 Job (取消), 去掉调度器: 工作留在当前线程上.
        val value = runBlocking(scriptRuntime.coroutineContext.minusKey(ContinuationInterceptor)) { work(binder) }
        callbackError.get()?.let { throw it }
        return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)
    }

    /**
     * Builds the work on the script thread (argument failures reject the Promise) and runs it in the script's
     * coroutine scope; progress and the outcome come back on the script thread.
     */
    private fun async(build: () -> suspend (ProgressBinder) -> Any?): Any {
        val adapter = ScriptPromiseAdapter()
        val dispatcher = ScriptAsyncDispatcher(scriptRuntime)
        val work = try {
            build()
        } catch (t: Throwable) {
            dispatcher.terminalValues(emptyArray()) { adapter.reject(BatchRenameJsErrors.toJs(scriptRuntime, t)) }
            return adapter.toJsPromise(scriptRuntime)
        }
        val callbackError = AtomicReference<Throwable?>()
        val binder = ProgressBinder { function ->
            function?.let {
                BatchRenameScriptProgress { done, total, item ->
                    dispatcher.dispatchValues(arrayOf(done, total, item)) { values ->
                        if (callbackError.get() != null) return@dispatchValues
                        try {
                            RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
                                val scope = scriptRuntime.topLevelScope
                                it.call(cx, scope, scope, values)
                            }
                        } catch (t: Throwable) {
                            callbackError.set(t)
                        }
                    }
                }
            }
        }
        scriptRuntime.coroutineScope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { work(binder) } }
            try {
                dispatcher.terminalValues(emptyArray()) {
                    val error = callbackError.get() ?: outcome.exceptionOrNull()
                    if (error != null) {
                        adapter.reject(BatchRenameJsErrors.toJs(scriptRuntime, error))
                    } else {
                        adapter.resolve(RhinoUtils.toJsValue(scriptRuntime.topLevelScope, outcome.getOrNull()))
                    }
                }
            } catch (_: Throwable) {
                // The script is gone: nothing is left to settle. zh-CN: 脚本已不在: 没有可落定的对象了.
                runCatching { dispatcher.cancelSilently() }
            }
        }
        return adapter.toJsPromise(scriptRuntime)
    }

    /** Failures leave as `BatchRenameError`; stopping the script stays an interruption. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        when {
            ScriptInterruptedException.causedByInterrupt(t) -> throw t
            t is CancellationException -> throw ScriptInterruptedException(t)
            else -> BatchRenameJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** A script argument as a JSON tree, like `JSON.stringify` sees it; `undefined` and null become null. */
    private fun json(value: Any?): JsonElement? = when (value) {
        null, is Undefined -> null
        is CharSequence -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Scriptable -> {
            val serialized = js_json_stringify(value)
            if (serialized is Undefined) {
                throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_OPTIONS, "argument must be JSON-compatible, got ${value.jsBrief()}")
            }
            JsonParser.parseString(Context.toString(serialized))
        }
        else -> throw BatchRenameScriptException(BatchRenameScriptCodes.INVALID_OPTIONS, "unsupported argument ${value.jsBrief()}")
    }

    private companion object {
        val PASS_THROUGH = setOf(
            "plan", "applyPlan", "undo", "history", "isAvailable", "planAsync", "applyAsync", "undoAsync",
            "presets", "presetsAsync", "importPresets", "importPresetsAsync", "preview", "previewAsync", "deleteHistory", "clearHistory",
        )
    }
}
