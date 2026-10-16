package org.autojs.autojs.runtime.api.augment.speech

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.toJsPromise
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJavaArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.typedarrays.NativeArrayBuffer
import org.mozilla.javascript.typedarrays.NativeArrayBufferView
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor

/** The `xAsync` results and the session events of the speech API reach the script thread through the shared dispatcher. zh-CN: 语音 API 的 `xAsync` 结果与会话事件经共享分发器回到脚本线程. */
internal typealias SpeechAsyncDispatcher = ScriptAsyncDispatcher

/** The work of one script method: runs in a coroutine of the script runtime. zh-CN: 脚本方法的工作: 在脚本运行时的协程里运行. */
internal typealias SpeechWork = suspend () -> Any?

/** The work of a method with progress: [emit] reports `done` and `total` from any thread. zh-CN: 带进度的方法的工作: [emit] 可在任意线程上报告 `done` 与 `total`. */
internal typealias SpeechProgressWork = suspend (emit: (Long, Long) -> Unit) -> Any?

/**
 * The Rhino-side plumbing of the `speech` global (3-Macaw Speech roadmap P1.4): the synchronous form of a method blocks
 * the script thread inside the runtime's coroutine context (so stopping the script cancels the waiting side, which
 * cancels the plugin call), the `Async` form returns a Promise settled on the script thread, script arguments become
 * Gson trees like `JSON.stringify` sees them, PCM arguments become bytes, and every failure leaves as `SpeechError`.
 * Forms that may wait minutes (recognition, synthesis, downloads) refuse to block the UI thread; `onProgress` callbacks
 * always run on the script thread.
 * zh-CN: `speech` 全局对象的 Rhino 侧管道 (3-Macaw Speech 路线图 P1.4): 方法的同步形态在运行时协程上下文里阻塞脚本线程 (停止脚本即取消等待方,
 * 进而取消插件调用), `Async` 形态返回在脚本线程落定的 Promise, 脚本参数像 `JSON.stringify` 所见那样成为 Gson 树, PCM 参数成为字节, 每个失败都以
 * `SpeechError` 离开. 可能等待数分钟的形态 (识别, 合成, 下载) 拒绝阻塞 UI 线程; `onProgress` 回调总在脚本线程上运行.
 */
internal object SpeechCalls {

    /** Runs [work] on the script thread until it completes and returns its outcome as a script value. zh-CN: 在脚本线程上运行 [work] 直到完成并把结果作为脚本值返回. */
    fun sync(scriptRuntime: ScriptRuntime, work: SpeechWork): Any? {
        try {
            val value = runBlocking(scriptRuntime.coroutineContext) { work() }
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)
        } catch (t: Throwable) {
            SpeechJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** [sync] of the work [build] produces; argument failures throw as `SpeechError`. zh-CN: [build] 产出的工作的 [sync]; 参数失败以 `SpeechError` 抛出. */
    fun syncOf(scriptRuntime: ScriptRuntime, build: () -> SpeechWork): Any? = try {
        sync(scriptRuntime, build())
    } catch (t: Throwable) {
        SpeechJsErrors.throwInto(scriptRuntime, t)
    }

    /**
     * Like [syncOf] for a form that may wait minutes ([function] names it in the refusal): the UI thread must not block,
     * so it fails there before anything reaches the plugin.
     * zh-CN: 同 [syncOf], 用于可能等待数分钟的形态 ([function] 用于拒绝信息): UI 线程不能阻塞, 因此在那里先行失败, 不触达插件.
     */
    fun blockingOf(scriptRuntime: ScriptRuntime, function: String, build: () -> SpeechWork): Any? {
        refuseUiThread(scriptRuntime, function)
        return syncOf(scriptRuntime, build)
    }

    /**
     * The synchronous form of a method with an `onProgress` callback: the work runs on the IO dispatcher, its progress
     * (conflated) reaches [onProgress] on this script thread, and the first error the callback throws is rethrown once
     * the work finished.
     * zh-CN: 带 `onProgress` 回调的方法的同步形态: 工作在 IO 分发器上运行, 其进度 (合并) 在本脚本线程上到达 [onProgress], 回调抛出的第一个
     * 错误在工作结束后再抛出.
     */
    fun blockingWithProgress(scriptRuntime: ScriptRuntime, function: String, onProgress: Function?, build: () -> SpeechProgressWork): Any? {
        refuseUiThread(scriptRuntime, function)
        try {
            val work = build()
            val callbackError = AtomicReference<Throwable?>()
            // Keep the job (cancellation), drop the dispatcher: the progress loop stays on this thread.
            // zh-CN: 保留 Job (取消), 去掉调度器: 进度循环留在当前线程上.
            val value = runBlocking(scriptRuntime.coroutineContext.minusKey(ContinuationInterceptor)) {
                val ticks = Channel<LongArray>(Channel.CONFLATED)
                val job = async(Dispatchers.IO) { work { done, total -> ticks.trySend(longArrayOf(done, total)) } }
                job.invokeOnCompletion { ticks.close() }
                for (tick in ticks) {
                    if (onProgress == null || callbackError.get() != null) continue
                    try {
                        RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
                            val scope = scriptRuntime.topLevelScope
                            onProgress.call(cx, scope, scope, arrayOf<Any?>(tick[0].toDouble(), tick[1].toDouble()))
                        }
                    } catch (t: Throwable) {
                        if (ScriptInterruptedException.causedByInterrupt(t)) throw t
                        callbackError.set(t)
                    }
                }
                job.await()
            }
            callbackError.get()?.let { throw it }
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)
        } catch (t: Throwable) {
            SpeechJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** The Promise of [build]'s work; argument failures reject it. Must be called on the script thread. zh-CN: [build] 的工作的 Promise; 参数失败拒绝它. 必须在脚本线程上调用. */
    fun asyncOf(scriptRuntime: ScriptRuntime, build: () -> SpeechWork): Scriptable = asyncWithProgress(scriptRuntime, null) {
        val work = build()
        return@asyncWithProgress { _ -> work() }
    }

    /** The Promise of a method with an `onProgress` callback, which runs on the script thread. zh-CN: 带 `onProgress` 回调的方法的 Promise, 回调在脚本线程上运行. */
    fun asyncWithProgress(scriptRuntime: ScriptRuntime, onProgress: Function?, build: () -> SpeechProgressWork): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val dispatcher = SpeechAsyncDispatcher(scriptRuntime)
        val work = try {
            build()
        } catch (t: Throwable) {
            deliver(scriptRuntime, dispatcher, adapter, Result.failure(t), null)
            return adapter.toJsPromise(scriptRuntime) as Scriptable
        }
        val callbackError = AtomicReference<Throwable?>()
        val emit: (Long, Long) -> Unit = if (onProgress == null) { _, _ -> } else { done, total ->
            runCatching {
                dispatcher.dispatchValues(arrayOf(done.toDouble(), total.toDouble())) { values ->
                    if (callbackError.get() != null) return@dispatchValues
                    try {
                        RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
                            val scope = scriptRuntime.topLevelScope
                            onProgress.call(cx, scope, scope, values)
                        }
                    } catch (t: Throwable) {
                        callbackError.set(t)
                    }
                }
            }
        }
        scriptRuntime.coroutineScope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { work(emit) } }
            deliver(scriptRuntime, dispatcher, adapter, outcome, callbackError)
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
            if (serialized is Undefined) throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "argument must be JSON-compatible, got ${value.jsBrief()}")
            try {
                JsonParser.parseString(Context.toString(serialized))
            } catch (e: RuntimeException) {
                throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "argument must be JSON-compatible: ${e.message.orEmpty()}")
            }
        }
        else -> throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "unsupported argument ${value.jsBrief()}")
    }

    /** The `onProgress` function of an options object, or null; any other value is `INVALID_OPTIONS`. zh-CN: 选项对象中的 `onProgress` 函数或 null; 其他值为 `INVALID_OPTIONS`. */
    fun progressOf(options: Any?): Function? {
        val holder = options as? Scriptable ?: return null
        val value = ScriptableObject.getProperty(holder, "onProgress")
        if (value == Scriptable.NOT_FOUND || value.isJsNullish()) return null
        return value as? Function ?: throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "'options.onProgress' must be a function, got ${value.jsBrief()}")
    }

    /**
     * PCM16 LE bytes of a script value: an `ArrayBuffer`, a typed array view (`Int16Array`, `Uint8Array`, ...) or a Java
     * `byte[]`; null when [value] is none of them. Big-endian buffers are swapped to little-endian.
     * zh-CN: 脚本值中的 PCM16 LE 字节: `ArrayBuffer`, 类型数组视图 (`Int16Array`, `Uint8Array` 等) 或 Java `byte[]`; 都不是时为 null.
     * 大端缓冲区会被交换为小端.
     */
    fun pcmBytes(value: Any?): ByteArray? {
        val unwrapped = (value as? Wrapper)?.unwrap() ?: value
        return when (unwrapped) {
            is ByteArray -> unwrapped.copyOf()
            is NativeArrayBuffer -> slice(unwrapped, 0, unwrapped.length, swap = false)
            is NativeArrayBufferView -> {
                val buffer = unwrapped.buffer
                val swap = unwrapped.javaClass.simpleName == "NativeInt16Array" && buffer.byteBuffer.order() == ByteOrder.BIG_ENDIAN
                slice(buffer, unwrapped.byteOffset, unwrapped.byteLength, swap)
            }
            is NativeJavaArray -> (unwrapped.unwrap() as? ByteArray)?.copyOf()
            else -> null
        }
    }

    private fun slice(buffer: NativeArrayBuffer, offset: Int, length: Int, swap: Boolean): ByteArray {
        val source = buffer.byteBuffer.duplicate()
        source.position(offset)
        val bytes = ByteArray(length)
        source.get(bytes)
        if (swap) {
            var i = 0
            while (i + 1 < bytes.size) {
                val first = bytes[i]
                bytes[i] = bytes[i + 1]
                bytes[i + 1] = first
                i += 2
            }
        }
        return bytes
    }

    /** A non-blank text argument (`speak`, `synthesize`). zh-CN: 非空白的文本参数 (`speak`, `synthesize`). */
    fun text(value: Any?, label: String): String {
        if (value.isJsNullish()) throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "'$label' must not be nullish")
        return Context.toString(value).also {
            if (it.isBlank()) throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "'$label' must not be blank")
        }
    }

    private fun refuseUiThread(scriptRuntime: ScriptRuntime, function: String) {
        if (RhinoUtils.isUiThread()) {
            SpeechJsErrors.throwInto(scriptRuntime, SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, str(R.string.error_function_called_in_ui_thread, function)))
        }
    }

    private fun deliver(
        scriptRuntime: ScriptRuntime,
        dispatcher: SpeechAsyncDispatcher,
        adapter: ScriptPromiseAdapter,
        outcome: Result<Any?>,
        callbackError: AtomicReference<Throwable?>?,
    ) {
        try {
            val error = callbackError?.get() ?: outcome.exceptionOrNull()
            if (error == null) {
                dispatcher.resolve(adapter, outcome.getOrNull())
            } else {
                dispatcher.terminalValues(emptyArray()) { adapter.reject(SpeechJsErrors.toJs(scriptRuntime, error)) }
            }
        } catch (_: Throwable) {
            // The script is gone (its looper no longer accepts work): nothing is left to settle.
            // zh-CN: 脚本已不在 (其 looper 不再接受工作): 没有可落定的对象了.
            runCatching { dispatcher.cancelSilently() }
        }
    }
}
