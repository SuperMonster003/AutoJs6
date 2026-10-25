package org.autojs.autojs.runtime.api.modules.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.speech.SpeechError
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechResultKeys
import org.mozilla.javascript.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `SpeechWakeSession` (3-Macaw Speech roadmap D18, appendix B.3): an `EventEmitter` with the events
 * `ready({ sessionId, modelId })`, `wake({ keyword, score, timestampMillis, listen? })`, `warning({ code, message })`,
 * `error(SpeechError)` and `end({ reason })`, the properties `keywords`, `id`, `modelId`, `startedAt`, `state`, `reason`
 * and `isEnded`, and the methods `stop()` and `isActive()`. With `thenListen` every `wake` event carries `listen`, the
 * [SpeechSession] of the dictation round that follows the hit; the rounds share this session's dispatcher, so this
 * session's `end` is the single terminal event.
 * zh-CN: 脚本可见的 `SpeechWakeSession` (3-Macaw Speech 路线图 D18, 附录 B.3): 带事件 `ready`, `wake({ keyword, score, timestampMillis, listen? })`,
 * `warning`, `error(SpeechError)` 与 `end({ reason })` 的 `EventEmitter`, 属性 `keywords`, `id`, `modelId`, `startedAt`, `state`, `reason` 与
 * `isEnded`, 方法 `stop()` 与 `isActive()`. 有 `thenListen` 时每个 `wake` 事件带 `listen`, 即命中后听写轮次的 [SpeechSession]; 各轮次共用本会话的
 * 分发器, 因此本会话的 `end` 是唯一的终结事件.
 */
class SpeechWakeSession internal constructor(
    internal val dispatcher: SpeechAsyncDispatcher,
    bridges: ScriptBridges,
    keywords: List<String>,
    private val scriptRuntime: ScriptRuntime?,
    private val convertValue: (Any?) -> Any?,
    private val onTerminal: (() -> Unit)?,
) : EventEmitter(bridges) {

    /** The plugin call behind `stop()`; bound by the runner once the plugin session exists. zh-CN: `stop()` 背后的插件调用; 插件会话存在后由运行器绑定. */
    internal interface Port {
        fun stop()
    }

    private val words: List<String> = keywords.toList()
    private val port = AtomicReference<Port?>()
    private val terminalLock = Any()

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var model: String? = null

    @Volatile
    private var ready = false

    @Volatile
    private var ended = false

    @Volatile
    private var endReason: String? = null

    /** Epoch milliseconds when the session was requested. zh-CN: 请求会话时的纪元毫秒数. */
    val startedAt: Double = System.currentTimeMillis().toDouble()

    /** The keywords as the script wrote them. zh-CN: 脚本所写的关键词. */
    val keywords: Any?
        get() = convertValue(words)

    val id: String?
        get() = sessionId

    val modelId: String?
        get() = model

    @get:JvmName("getIsEnded")
    val isEnded: Boolean
        get() = ended || dispatcher.isTerminal

    val state: String
        get() = when {
            isEnded -> SpeechContract.STATE_ENDED
            ready -> SpeechContract.STATE_ACTIVE
            else -> SpeechContract.STATE_PENDING
        }

    val reason: String?
        get() = endReason

    override fun on(eventName: String, listener: Callable): SpeechWakeSession {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): SpeechWakeSession {
        super.once(eventName, listener)
        return this
    }

    fun isActive(): Boolean = !isEnded

    /** Ends the wake session and any running round (`end` with reason `stopped`); idempotent. zh-CN: 结束唤醒会话及正在进行的轮次 (`end` 原因为 `stopped`); 幂等. */
    fun stop(): SpeechWakeSession = guarded {
        val bound = port.get()
        if (bound != null && !isEnded) {
            val runtime = scriptRuntime
            if (runtime == null) bound.stop() else SpeechCalls.sync(runtime) {
                withContext(Dispatchers.IO) { withTimeout(SpeechContract.CALL_TIMEOUT_MS) { bound.stop() } }
            }
        }
        this
    }

    override fun toString(): String = buildString {
        append("SpeechWakeSession { keywords: ").append(words).append(", state: ").append(state)
        endReason?.let { append(", reason: ").append(it) }
        append(" }")
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        val runtime = scriptRuntime ?: throw t
        SpeechJsErrors.throwInto(runtime, t)
    }

    // ------------------------------------------------------------------ transitions (Controller only)

    private fun bindPort(bound: Port): Boolean = synchronized(terminalLock) {
        if (isEnded) false else {
            port.set(bound)
            true
        }
    }

    private fun ready(payload: Map<String, Any?>): Boolean {
        sessionId = payload[SpeechResultKeys.SESSION_ID] as? String
        model = payload[SpeechResultKeys.MODEL_ID] as? String
        ready = true
        val values = linkedMapOf<String, Any?>(SpeechResultKeys.SESSION_ID to sessionId, SpeechResultKeys.MODEL_ID to model)
        return dispatcher.dispatchValues(arrayOf(values)) { mapped -> emit(SpeechContract.EVENT_READY, mapped[0]) }
    }

    /** One hit; [listen] is the round session of `thenListen`, handed to the script as the `listen` field. zh-CN: 一次命中; [listen] 为 `thenListen` 的轮次会话, 以 `listen` 字段交给脚本. */
    private fun wake(payload: Map<String, Any?>, listen: SpeechSession?): Boolean {
        val values = linkedMapOf<String, Any?>(
            SpeechResultKeys.KEYWORD to payload[SpeechResultKeys.KEYWORD],
            SpeechResultKeys.SCORE to payload[SpeechResultKeys.SCORE],
            SpeechResultKeys.TIMESTAMP_MILLIS to payload[SpeechResultKeys.TIMESTAMP_MILLIS],
        )
        if (listen != null) values[FIELD_LISTEN] = listen
        return dispatcher.dispatchValues(arrayOf(values)) { mapped -> emit(SpeechContract.EVENT_WAKE, mapped[0]) }
    }

    private fun warning(payload: Map<String, Any?>): Boolean =
        dispatcher.dispatchValues(arrayOf(payload)) { mapped -> emit(SpeechContract.EVENT_WARNING, mapped[0]) }

    private fun error(error: SpeechError, convert: (SpeechError) -> Any?): Boolean =
        dispatcher.dispatchValues(emptyArray()) { emit(SpeechContract.EVENT_ERROR, convert(error)) }

    private fun end(reason: String): Boolean {
        synchronized(terminalLock) {
            if (isEnded) return false
            endReason = reason
            ended = true
            port.set(null)
        }
        val payload = linkedMapOf<String, Any?>(SpeechResultKeys.REASON to reason)
        return try {
            dispatcher.terminalValues(arrayOf(payload)) { mapped ->
                try {
                    emit(SpeechContract.EVENT_END, mapped[0])
                } finally {
                    notifyTerminal()
                }
            }
        } catch (t: Throwable) {
            notifyTerminal()
            throw t
        }
    }

    private fun fail(error: SpeechError, convert: (SpeechError) -> Any?, reason: String): Boolean {
        synchronized(terminalLock) {
            if (isEnded) return false
            endReason = reason
            ended = true
            port.set(null)
        }
        val payload = linkedMapOf<String, Any?>(SpeechResultKeys.REASON to reason)
        return dispatcher.replaceQueuedWithTerminalValues(arrayOf(payload)) { mapped ->
            try {
                var failure: Throwable? = null
                try {
                    emit(SpeechContract.EVENT_ERROR, convert(error))
                } catch (t: Throwable) {
                    failure = t
                }
                try {
                    emit(SpeechContract.EVENT_END, mapped[0])
                } catch (t: Throwable) {
                    failure?.addSuppressed(t) ?: run { failure = t }
                }
                failure?.let { throw it }
            } finally {
                notifyTerminal()
            }
        }
    }

    private fun cancelSilently(): Boolean {
        synchronized(terminalLock) {
            if (ended) return false
            ended = true
            endReason = endReason ?: SpeechSession.REASON_EXIT
            port.set(null)
        }
        dispatcher.cancelSilently(::notifyTerminal)
        return true
    }

    private fun notifyTerminal() {
        runCatching { onTerminal?.invoke() }
    }

    /** Transport-side transitions kept off the Rhino surface. zh-CN: 不暴露给 Rhino 的传输侧状态变更. */
    internal class Controller internal constructor(val session: SpeechWakeSession) {

        constructor(
            dispatcher: SpeechAsyncDispatcher,
            bridges: ScriptBridges,
            keywords: List<String>,
            scriptRuntime: ScriptRuntime? = null,
            convertValue: (Any?) -> Any? = { it },
            onTerminal: (() -> Unit)? = null,
        ) : this(SpeechWakeSession(dispatcher, bridges, keywords, scriptRuntime, convertValue, onTerminal))

        val isEnded: Boolean
            get() = session.isEnded

        val dispatcher: SpeechAsyncDispatcher
            get() = session.dispatcher

        fun bindPort(port: Port): Boolean = session.bindPort(port)

        fun ready(payload: Map<String, Any?>): Boolean = session.ready(payload)

        fun wake(payload: Map<String, Any?>, listen: SpeechSession?): Boolean = session.wake(payload, listen)

        fun warning(payload: Map<String, Any?>): Boolean = session.warning(payload)

        fun error(error: SpeechError, convert: (SpeechError) -> Any? = { it }): Boolean = session.error(error, convert)

        fun end(reason: String): Boolean = session.end(reason)

        fun fail(error: SpeechError, reason: String, convert: (SpeechError) -> Any? = { it }): Boolean = session.fail(error, convert, reason)

        fun cancelSilently(): Boolean = session.cancelSilently()
    }

    companion object {
        const val FIELD_LISTEN = "listen"
    }
}
