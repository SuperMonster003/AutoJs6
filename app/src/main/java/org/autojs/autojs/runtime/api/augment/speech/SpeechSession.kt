package org.autojs.autojs.runtime.api.augment.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.speech.SpeechError
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.speech.SpeechScriptValues
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechResultKeys
import org.mozilla.javascript.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `SpeechSession` (3-Macaw Speech roadmap D16, appendix B.3): an `EventEmitter` with the events
 * `ready({ sessionId, modelId })`, `partial({ text, segmentIndex })`, `final({ text, segmentIndex, startMillis, endMillis,
 * language?, tags? })`, `endpoint()`, `vad({ active })`, `warning({ code, message })`, `error(SpeechError)` and
 * `end({ reason, text })`, the properties `id`, `modelId`, `source`, `round`, `startedAt`, `state`, `reason` and
 * `isEnded`, and the methods `stop()`, `cancel()`, `feed(pcm)`, `isActive()` and `result()`. Every event reaches the
 * listeners on the script thread that opened the session through a [SpeechAsyncDispatcher], which also keeps the script
 * alive until the session ended; `end` is the single terminal event. A dictation round of a wake session shares the
 * wake session's dispatcher ([ownsDispatcher] false), so its `end` is an ordinary event and the wake session's `end` the
 * terminal one. State changes come from the unexposed [Controller] and the plugin calls go through the unexposed
 * [Port] bound by the runner, so scripts only see the event and method surface.
 * zh-CN: 脚本可见的 `SpeechSession` (3-Macaw Speech 路线图 D16, 附录 B.3): 带事件 `ready`, `partial`, `final`, `endpoint`, `vad`, `warning`,
 * `error(SpeechError)` 与 `end({ reason, text })` 的 `EventEmitter`, 属性 `id`, `modelId`, `source`, `round`, `startedAt`, `state`, `reason` 与
 * `isEnded`, 方法 `stop()`, `cancel()`, `feed(pcm)`, `isActive()` 与 `result()`. 每个事件经 [SpeechAsyncDispatcher] 回到打开会话的脚本线程,
 * 分发器同时让脚本活到会话结束; `end` 是唯一的终结事件. 唤醒会话的听写轮次共用唤醒会话的分发器 ([ownsDispatcher] 为 false), 因此其 `end`
 * 是普通事件, 唤醒会话的 `end` 才是终结事件. 状态变更来自不对脚本暴露的 [Controller], 插件调用经运行器绑定的不暴露的 [Port].
 */
class SpeechSession internal constructor(
    private val dispatcher: SpeechAsyncDispatcher,
    bridges: ScriptBridges,
    /** `mic` or `external` (roadmap appendix B.2). zh-CN: `mic` 或 `external` (路线图附录 B.2). */
    val source: String,
    /** The dictation round of a wake session (0 based), or -1. zh-CN: 唤醒会话的听写轮次 (从 0 开始), 或 -1. */
    val round: Int,
    private val ownsDispatcher: Boolean,
    private val scriptRuntime: ScriptRuntime?,
    private val onTerminal: (() -> Unit)?,
) : EventEmitter(bridges) {

    /** The plugin calls behind the session methods; bound by the runner once the plugin session exists. zh-CN: 会话方法背后的插件调用; 插件会话存在后由运行器绑定. */
    internal interface Port {
        fun stop()
        fun cancel()
        fun feed(pcm: ByteArray)
    }

    private val port = AtomicReference<Port?>()
    private val terminalLock = Any()
    private val finals = CopyOnWriteArrayList<String>()

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

    @Volatile
    private var endText: String? = null

    /** Epoch milliseconds when the session was requested. zh-CN: 请求会话时的纪元毫秒数. */
    val startedAt: Double = System.currentTimeMillis().toDouble()

    /** The plugin's session id from the `ready` event, or null before it. zh-CN: 来自 `ready` 事件的插件会话 id, 之前为 null. */
    val id: String?
        get() = sessionId

    /** The model that serves the session, from the `ready` event. zh-CN: 服务本会话的模型, 来自 `ready` 事件. */
    val modelId: String?
        get() = model

    @get:JvmName("getIsEnded")
    val isEnded: Boolean
        get() = ended || (ownsDispatcher && dispatcher.isTerminal)

    /** `pending` until `ready`, then `active`, then `ended` (roadmap P1.4 state machine). zh-CN: `ready` 前为 `pending`, 之后 `active`, 结束后 `ended`. */
    val state: String
        get() = when {
            isEnded -> SpeechContract.STATE_ENDED
            ready -> SpeechContract.STATE_ACTIVE
            else -> SpeechContract.STATE_PENDING
        }

    /** The reason of the `end` event once ended. zh-CN: 结束后为 `end` 事件的原因. */
    val reason: String?
        get() = endReason

    override fun on(eventName: String, listener: Callable): SpeechSession {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): SpeechSession {
        super.once(eventName, listener)
        return this
    }

    /** Whether the session still consumes audio (pending or active). zh-CN: 会话是否仍在消费音频 (pending 或 active). */
    fun isActive(): Boolean = !isEnded

    /** The text so far: the plugin's whole-session text once ended, the joined final results before. zh-CN: 目前的文本: 结束后为插件给出的整段文本, 之前为最终结果的拼接. */
    fun result(): String = endText ?: SpeechScriptValues.joinText(finals)

    /** Flushes the recognizer and ends the session (`end` with reason `stopped`); idempotent. zh-CN: 冲刷识别器并结束会话 (`end` 原因为 `stopped`); 幂等. */
    fun stop(): SpeechSession = guarded {
        port.get()?.takeUnless { isEnded }?.let { bound -> callPlugin { bound.stop() } }
        this
    }

    /** Ends the session without a final result (`end` with reason `cancelled`); idempotent. zh-CN: 不产生最终结果地结束会话 (`end` 原因为 `cancelled`); 幂等. */
    fun cancel(): SpeechSession = guarded {
        port.get()?.takeUnless { isEnded }?.let { bound -> callPlugin { bound.cancel() } }
        this
    }

    /**
     * Feeds 16 kHz mono PCM16 LE audio to a session opened with `source: "external"`: an `ArrayBuffer`, an `Int16Array`
     * (or another typed array view) or a Java `byte[]`, at most 1 MiB per call.
     * zh-CN: 向以 `source: "external"` 打开的会话输入 16 kHz 单声道 PCM16 LE 音频: `ArrayBuffer`, `Int16Array` (或其他类型数组视图) 或 Java
     * `byte[]`, 每次至多 1 MiB.
     */
    fun feed(pcm: Any?): SpeechSession = guarded {
        if (source != SpeechContract.SOURCE_EXTERNAL) {
            throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "feed() applies to sessions opened with source \"external\"")
        }
        val bytes = SpeechCalls.pcmBytes(pcm)
            ?: throw SpeechJsErrors.failure(SpeechErrorCodes.INVALID_OPTIONS, "feed() takes an ArrayBuffer, an Int16Array or a byte[]")
        if (isEnded) throw SpeechJsErrors.failure(SpeechErrorCodes.SESSION_CLOSED, "the session has ended")
        val bound = port.get() ?: throw SpeechJsErrors.failure(SpeechErrorCodes.SESSION_CLOSED, "the session is not open")
        callPlugin { bound.feed(bytes) }
        this
    }

    override fun toString(): String = buildString {
        append("SpeechSession { source: ").append(source).append(", state: ").append(state)
        if (round >= 0) append(", round: ").append(round)
        model?.let { append(", modelId: ").append(it) }
        endReason?.let { append(", reason: ").append(it) }
        append(" }")
    }

    // ------------------------------------------------------------------ calls

    /** Runs [block] against the plugin on the IO dispatcher while the script thread waits inside the runtime's context. zh-CN: 在 IO 分发器上对插件运行 [block], 脚本线程在运行时上下文里等待. */
    private fun callPlugin(block: () -> Unit) {
        val runtime = scriptRuntime ?: return block()
        SpeechCalls.sync(runtime) { withContext(Dispatchers.IO) { withTimeout(SpeechContract.CALL_TIMEOUT_MS) { block() } } }
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

    private fun emitValue(event: String, payload: Map<String, Any?>): Boolean =
        dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(event, values[0]) }

    private fun ready(payload: Map<String, Any?>): Boolean {
        sessionId = payload[SpeechResultKeys.SESSION_ID] as? String
        model = payload[SpeechResultKeys.MODEL_ID] as? String
        ready = true
        return emitValue(SpeechContract.EVENT_READY, linkedMapOf(SpeechResultKeys.SESSION_ID to sessionId, SpeechResultKeys.MODEL_ID to model))
    }

    private fun partial(payload: Map<String, Any?>): Boolean = emitValue(SpeechContract.EVENT_PARTIAL, payload)

    private fun final(payload: Map<String, Any?>): Boolean {
        SpeechScriptValues.textOf(payload)?.let { finals.add(it) }
        return emitValue(SpeechContract.EVENT_FINAL, payload)
    }

    private fun endpoint(): Boolean = dispatcher.dispatchValues(emptyArray()) { emit(SpeechContract.EVENT_ENDPOINT) }

    private fun vad(payload: Map<String, Any?>): Boolean = emitValue(SpeechContract.EVENT_VAD, payload)

    private fun warning(payload: Map<String, Any?>): Boolean = emitValue(SpeechContract.EVENT_WARNING, payload)

    private fun error(error: SpeechError, convert: (SpeechError) -> Any?): Boolean =
        dispatcher.dispatchValues(emptyArray()) { emit(SpeechContract.EVENT_ERROR, convert(error)) }

    /** The terminal `end`; queued events go first. zh-CN: 终结的 `end`; 排队中的事件先投递. */
    private fun end(reason: String, text: String?): Boolean {
        val payload = linkedMapOf<String, Any?>(SpeechResultKeys.REASON to reason, SpeechResultKeys.TEXT to (text ?: SpeechScriptValues.joinText(finals)))
        synchronized(terminalLock) {
            if (isEnded) return false
            endReason = reason
            endText = text
            ended = true
            port.set(null)
        }
        return if (ownsDispatcher) {
            try {
                dispatcher.terminalValues(arrayOf(payload)) { values ->
                    try {
                        emit(SpeechContract.EVENT_END, values[0])
                    } finally {
                        notifyTerminal()
                    }
                }
            } catch (t: Throwable) {
                notifyTerminal()
                throw t
            }
        } else {
            try {
                dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(SpeechContract.EVENT_END, values[0]) }
            } finally {
                notifyTerminal()
            }
        }
    }

    /** `error` then `end` that discards the queued events (the script stopped draining them). zh-CN: 丢弃排队事件的 `error` 与 `end` (脚本不再消费事件). */
    private fun fail(error: SpeechError, convert: (SpeechError) -> Any?, reason: String): Boolean {
        val payload = linkedMapOf<String, Any?>(SpeechResultKeys.REASON to reason, SpeechResultKeys.TEXT to SpeechScriptValues.joinText(finals))
        synchronized(terminalLock) {
            if (isEnded) return false
            endReason = reason
            ended = true
            port.set(null)
        }
        if (!ownsDispatcher) {
            notifyTerminal()
            return false
        }
        return dispatcher.replaceQueuedWithTerminalValues(arrayOf(payload)) { values ->
            try {
                var failure: Throwable? = null
                try {
                    emit(SpeechContract.EVENT_ERROR, convert(error))
                } catch (t: Throwable) {
                    failure = t
                }
                try {
                    emit(SpeechContract.EVENT_END, values[0])
                } catch (t: Throwable) {
                    failure?.addSuppressed(t) ?: run { failure = t }
                }
                failure?.let { throw it }
            } finally {
                notifyTerminal()
            }
        }
    }

    /** Script exit: no event after teardown began. zh-CN: 脚本退出: 拆除开始后不再有事件. */
    private fun cancelSilently(): Boolean {
        synchronized(terminalLock) {
            if (ended) return false
            ended = true
            endReason = endReason ?: REASON_EXIT
            port.set(null)
        }
        if (ownsDispatcher) dispatcher.cancelSilently(::notifyTerminal) else notifyTerminal()
        return true
    }

    private fun notifyTerminal() {
        runCatching { onTerminal?.invoke() }
    }

    /**
     * Transport-side capability kept off the Java object exposed to Rhino: Kotlin `internal` members are JVM-public and
     * reachable through reflection, so every transition lives here.
     * zh-CN: 不暴露给 Rhino 的传输侧能力: Kotlin `internal` 成员在 JVM 上是 public 且可经反射到达, 因此每个状态变更都在这里.
     */
    internal class Controller internal constructor(val session: SpeechSession) {

        constructor(
            dispatcher: SpeechAsyncDispatcher,
            bridges: ScriptBridges,
            source: String,
            round: Int = -1,
            ownsDispatcher: Boolean = true,
            scriptRuntime: ScriptRuntime? = null,
            onTerminal: (() -> Unit)? = null,
        ) : this(SpeechSession(dispatcher, bridges, source, round, ownsDispatcher, scriptRuntime, onTerminal))

        val isEnded: Boolean
            get() = session.isEnded

        fun bindPort(port: Port): Boolean = session.bindPort(port)

        fun ready(payload: Map<String, Any?>): Boolean = session.ready(payload)

        fun partial(payload: Map<String, Any?>): Boolean = session.partial(payload)

        fun final(payload: Map<String, Any?>): Boolean = session.final(payload)

        fun endpoint(): Boolean = session.endpoint()

        fun vad(payload: Map<String, Any?>): Boolean = session.vad(payload)

        fun warning(payload: Map<String, Any?>): Boolean = session.warning(payload)

        fun error(error: SpeechError, convert: (SpeechError) -> Any? = { it }): Boolean = session.error(error, convert)

        fun end(reason: String, text: String?): Boolean = session.end(reason, text)

        fun fail(error: SpeechError, reason: String, convert: (SpeechError) -> Any? = { it }): Boolean = session.fail(error, convert, reason)

        fun cancelSilently(): Boolean = session.cancelSilently()
    }

    companion object {
        /** `end` reasons raised on the host side; the plugin's own (roadmap appendix B.3) pass through. zh-CN: 宿主侧产生的 `end` 原因; 插件自己的原样透传. */
        const val REASON_EXIT = "script-exit"
        const val REASON_OVERFLOW = "overflow"
    }
}
