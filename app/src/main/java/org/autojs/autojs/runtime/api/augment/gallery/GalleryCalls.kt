package org.autojs.autojs.runtime.api.augment.gallery

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.toJsPromise
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/** The `xAsync` results and the viewer events of the gallery API reach the script thread through the shared dispatcher. zh-CN: 相册 API 的 `xAsync` 结果与查看器事件经共享分发器回到脚本线程. */
internal typealias GalleryAsyncDispatcher = ScriptAsyncDispatcher

/** The work of one script method: runs in a coroutine of the script runtime. zh-CN: 脚本方法的工作: 在脚本运行时的协程里运行. */
internal typealias GalleryWork = suspend () -> Any?

/**
 * The Rhino-side plumbing of the `gallery` global (3-Maple Image roadmap M6, 10.1): the synchronous
 * form of a method blocks the script thread inside the runtime's coroutine context (so stopping the
 * script cancels the waiting side, which closes the plugin session), the `Async` form returns a
 * Promise settled on the script thread through a [GalleryAsyncDispatcher] that also keeps the
 * script alive until then, script arguments become Gson trees like `JSON.stringify` sees them, and
 * every failure leaves as `GalleryError`. The forms that wait for the user (`pick`, `edit`) or for
 * the converter refuse to block the UI thread.
 * zh-CN: `gallery` 全局对象的 Rhino 侧管道 (3-Maple Image 路线图 M6, 10.1): 方法的同步形态在运行时协程上下文里阻塞脚本线程 (停止脚本即取消等待方,
 * 进而关闭插件会话), `Async` 形态返回经 [GalleryAsyncDispatcher] 在脚本线程落定的 Promise, 分发器同时让脚本活到落定, 脚本参数像 `JSON.stringify`
 * 所见那样成为 Gson 树, 每个失败都以 `GalleryError` 离开. 等待用户 (`pick`, `edit`) 或转换器的形态拒绝阻塞 UI 线程.
 */
internal object GalleryCalls {

    /** Runs [work] on the script thread until it completes and returns its outcome as a script value. zh-CN: 在脚本线程上运行 [work] 直到完成并把结果作为脚本值返回. */
    fun sync(scriptRuntime: ScriptRuntime, work: GalleryWork): Any? {
        try {
            val value = runBlocking(scriptRuntime.coroutineContext) { work() }
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)
        } catch (t: Throwable) {
            GalleryJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** [sync] of the work [build] produces; argument failures throw as `GalleryError`. zh-CN: [build] 产出的工作的 [sync]; 参数失败以 `GalleryError` 抛出. */
    fun syncOf(scriptRuntime: ScriptRuntime, build: () -> GalleryWork): Any? = try {
        sync(scriptRuntime, build())
    } catch (t: Throwable) {
        GalleryJsErrors.throwInto(scriptRuntime, t)
    }

    /**
     * Like [syncOf] for a form that may wait minutes ([function] names it in the refusal): the UI
     * thread must not block, so it fails there with `INVALID_ARGUMENT` before anything opens.
     * zh-CN: 同 [syncOf], 用于可能等待数分钟的形态 ([function] 用于拒绝信息): UI 线程不能阻塞, 因此在那里先以 `INVALID_ARGUMENT` 失败, 不打开任何东西.
     */
    fun blockingOf(scriptRuntime: ScriptRuntime, function: String, build: () -> GalleryWork): Any? {
        if (RhinoUtils.isUiThread()) {
            GalleryJsErrors.throwInto(scriptRuntime, GalleryJsErrors.failure(GalleryErrorCodes.INVALID_ARGUMENT, str(R.string.error_function_called_in_ui_thread, function)))
        }
        return syncOf(scriptRuntime, build)
    }

    /** The Promise of [build]'s work; argument failures reject it. Must be called on the script thread. zh-CN: [build] 的工作的 Promise; 参数失败拒绝它. 必须在脚本线程上调用. */
    fun asyncOf(scriptRuntime: ScriptRuntime, build: () -> GalleryWork): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val dispatcher = GalleryAsyncDispatcher(scriptRuntime)
        val work = try {
            build()
        } catch (t: Throwable) {
            deliver(scriptRuntime, dispatcher, adapter, Result.failure(t))
            return adapter.toJsPromise(scriptRuntime) as Scriptable
        }
        scriptRuntime.coroutineScope.launch {
            deliver(scriptRuntime, dispatcher, adapter, runCatching { work() })
        }
        return adapter.toJsPromise(scriptRuntime) as Scriptable
    }

    /** A script argument as a Gson tree, like `JSON.stringify` would see it; `undefined` and null become null. zh-CN: 脚本参数的 Gson 树, 如 `JSON.stringify` 所见; `undefined` 与 null 成为 null. */
    fun json(value: Any?): JsonElement? = when (value) {
        null, is Undefined -> null
        is CharSequence -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Scriptable -> {
            val serialized = js_json_stringify(value)
            if (serialized is Undefined) throw GalleryJsErrors.failure(GalleryErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible, got ${value.jsBrief()}")
            try {
                JsonParser.parseString(Context.toString(serialized))
            } catch (e: RuntimeException) {
                throw GalleryJsErrors.failure(GalleryErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible: ${e.message.orEmpty()}")
            }
        }
        else -> throw GalleryJsErrors.failure(GalleryErrorCodes.INVALID_ARGUMENT, "unsupported argument ${value.jsBrief()}")
    }

    private fun deliver(scriptRuntime: ScriptRuntime, dispatcher: GalleryAsyncDispatcher, adapter: ScriptPromiseAdapter, outcome: Result<Any?>) {
        try {
            outcome.fold(
                onSuccess = { value -> dispatcher.resolve(adapter, value) },
                onFailure = { error -> dispatcher.terminalValues(emptyArray()) { adapter.reject(GalleryJsErrors.toJs(scriptRuntime, error)) } },
            )
        } catch (_: Throwable) {
            // The script is gone (its looper no longer accepts work): nothing is left to settle.
            // zh-CN: 脚本已不在 (其 looper 不再接受工作): 没有可落定的对象了.
            runCatching { dispatcher.cancelSilently() }
        }
    }
}
