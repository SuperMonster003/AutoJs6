package org.autojs.autojs.core.plugin.speech

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.speech.api.ISpeechSession
import org.autojs.plugin.speech.api.ISpeechSessionCallback
import org.autojs.plugin.speech.api.ISpeechWakeCallback
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import org.autojs.plugin.speech.api.SpeechResultKeys
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * One session event as the host reads it off a callback (roadmap appendix B.3): [type] is one of
 * `SpeechContract.EVENTS`, [payload] the event Bundle as plain values (`{ code, message }` for warnings), [error] the
 * failure of an `error` event, and [round] the dictation round of a wake session (null on the wake channel and for
 * plain dictation sessions).
 * zh-CN: 宿主从回调读出的一个会话事件 (路线图附录 B.3): [type] 为 `SpeechContract.EVENTS` 之一, [payload] 为普通值形式的事件 Bundle
 * (警告为 `{ code, message }`), [error] 为 `error` 事件的失败, [round] 为唤醒会话的听写轮次 (唤醒通道与普通听写会话为 null).
 */
data class SpeechEvent(
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
    val error: SpeechError? = null,
    val round: Int? = null,
)

/** Receives the events a [SpeechEventGate] accepted; the script-side session runners implement it. zh-CN: 接收 [SpeechEventGate] 放行的事件; 由脚本侧的会话运行器实现. */
fun interface SpeechEventSink {
    fun onEvent(event: SpeechEvent)
}

/**
 * Enforces the life cycle of one event channel (roadmap D16): unknown event types are dropped, the first `end` without a
 * round ends the channel and everything after it is dropped, and [closeLocally] delivers one synthetic `end` (after an
 * optional `error`) when the host ends the session itself (the plugin process died, the script stopped). Round events
 * of a wake session pass through; their own `end` does not end the channel. Pure Kotlin; the Binder adapters are
 * [SpeechSessionCallbackBinder] and [SpeechWakeCallbackBinder].
 * zh-CN: 管理一个事件通道的生命周期 (路线图 D16): 丢弃未知事件类型; 首个不带轮次的 `end` 结束通道, 其后的一切都被丢弃; 宿主自行结束会话
 * (插件进程死亡, 脚本停止) 时, [closeLocally] 投递一个合成的 `end` (之前可带一个 `error`). 唤醒会话的轮次事件直接通过, 其 `end` 不结束通道.
 * 纯 Kotlin; Binder 适配器为 [SpeechSessionCallbackBinder] 与 [SpeechWakeCallbackBinder].
 */
class SpeechEventGate(sink: SpeechEventSink) {

    // Dropped once the channel has ended or the session failed to open: the plugin process holds the callback Binder, and with
    // it this gate, for as long as it keeps its reference, and the sink leads to the script runtime (docs/memory/ROADMAP.md
    // H23, H30).
    // zh-CN: 通道结束或会话打开失败后即丢弃: 插件进程持有回调 Binder (进而持有本闸门) 的时间由它自己决定, 而 sink 通向脚本运行时
    // (docs/memory/ROADMAP.md H23, H30).
    @Volatile
    private var sink: SpeechEventSink? = sink

    @Volatile
    var isEnded: Boolean = false
        private set

    @Volatile
    var droppedCount: Int = 0
        private set

    /** Returns true when the event reached the sink. zh-CN: 事件到达接收方时返回 true. */
    fun accept(event: SpeechEvent): Boolean {
        if (event.type !in SpeechContract.EVENTS) return drop()
        val target: SpeechEventSink
        val ending: Boolean
        synchronized(this) {
            if (isEnded) return drop()
            target = sink ?: return drop()
            ending = event.round == null && event.type == SpeechContract.EVENT_END
            if (ending) isEnded = true
        }
        target.onEvent(event)
        if (ending) sink = null
        return true
    }

    /** Ends the channel on the host side unless it ended already. zh-CN: 若通道尚未结束, 在宿主侧结束它. */
    fun closeLocally(reason: String, error: SpeechError? = null) {
        val target = synchronized(this) {
            if (isEnded) return
            isEnded = true
            sink
        } ?: return
        if (error != null) target.onEvent(SpeechEvent(SpeechContract.EVENT_ERROR, error = error))
        target.onEvent(SpeechEvent(SpeechContract.EVENT_END, linkedMapOf(SpeechResultKeys.REASON to reason)))
        sink = null
    }

    /**
     * Ends the channel without delivering anything: the session did not open, so the caller gets the failure as an
     * exception and the sink must not see an `end` (docs/memory/ROADMAP.md H30).
     * zh-CN: 不投递任何事件地结束通道: 会话没有打开, 调用方以异常得到失败, sink 不应收到 `end` (docs/memory/ROADMAP.md H30).
     */
    fun discard() {
        synchronized(this) { isEnded = true }
        sink = null
    }

    /** Whether the gate still leads to its sink (JVM tests). zh-CN: 闸门是否仍通向其 sink (JVM 测试用). */
    internal val holdsSink: Boolean get() = sink != null

    private fun drop(): Boolean {
        synchronized(this) { droppedCount++ }
        return false
    }
}

/**
 * Forwards a dictation channel into a [SpeechEventGate]. On the listen channel of a wake session ([roundChannel]) every
 * event belongs to a round: `ready` and `end` name it with `SpeechResultKeys.ROUND`, the events in between belong to the
 * round of the last `ready` (a oneway Binder keeps the call order of one callback).
 * zh-CN: 把听写通道转发进 [SpeechEventGate]. 在唤醒会话的听写通道 ([roundChannel]) 上每个事件都属于一个轮次: `ready` 与 `end` 以
 * `SpeechResultKeys.ROUND` 标明, 其间的事件属于最近一个 `ready` 的轮次 (同一回调的 oneway Binder 保持调用顺序).
 */
internal class SpeechSessionCallbackBinder(
    private val gate: SpeechEventGate,
    private val roundChannel: Boolean = false,
) : ISpeechSessionCallback.Stub() {

    @Volatile
    private var currentRound = 0

    override fun onReady(info: Bundle?) = deliver(SpeechContract.EVENT_READY, SpeechBundles.plain(info))

    override fun onPartial(result: Bundle?) = deliver(SpeechContract.EVENT_PARTIAL, SpeechBundles.plain(result))

    override fun onFinal(result: Bundle?) = deliver(SpeechContract.EVENT_FINAL, SpeechBundles.plain(result))

    override fun onEndpoint() = deliver(SpeechContract.EVENT_ENDPOINT, emptyMap())

    override fun onVoiceActivity(activity: Bundle?) = deliver(SpeechContract.EVENT_VAD, SpeechBundles.plain(activity))

    override fun onWarning(warning: Bundle?) = deliver(SpeechContract.EVENT_WARNING, SpeechBundles.problem(warning))

    override fun onError(error: Bundle?) {
        val failure = SpeechErrorMapper.fromBundle(error) ?: SpeechErrorMapper.normalize(SpeechErrorCodes.INTERNAL, null)
        gate.accept(SpeechEvent(SpeechContract.EVENT_ERROR, error = failure, round = roundOf(null)))
    }

    override fun onEnd(end: Bundle?) = deliver(SpeechContract.EVENT_END, SpeechBundles.plain(end))

    private fun deliver(type: String, payload: Map<String, Any?>) {
        gate.accept(SpeechEvent(type, payload, round = roundOf(payload)))
    }

    private fun roundOf(payload: Map<String, Any?>?): Int? {
        if (!roundChannel) return null
        (payload?.get(SpeechResultKeys.ROUND) as? Number)?.toInt()?.let { currentRound = it }
        return currentRound
    }
}

/** Forwards the wake channel of a wake session into a [SpeechEventGate]. zh-CN: 把唤醒会话的唤醒通道转发进 [SpeechEventGate]. */
internal class SpeechWakeCallbackBinder(private val gate: SpeechEventGate) : ISpeechWakeCallback.Stub() {

    override fun onReady(info: Bundle?) {
        gate.accept(SpeechEvent(SpeechContract.EVENT_READY, SpeechBundles.plain(info)))
    }

    override fun onWake(hit: Bundle?) {
        gate.accept(SpeechEvent(SpeechContract.EVENT_WAKE, SpeechBundles.plain(hit)))
    }

    override fun onWarning(warning: Bundle?) {
        gate.accept(SpeechEvent(SpeechContract.EVENT_WARNING, SpeechBundles.problem(warning)))
    }

    override fun onError(error: Bundle?) {
        val failure = SpeechErrorMapper.fromBundle(error) ?: SpeechErrorMapper.normalize(SpeechErrorCodes.INTERNAL, null)
        gate.accept(SpeechEvent(SpeechContract.EVENT_ERROR, error = failure))
    }

    override fun onEnd(end: Bundle?) {
        gate.accept(SpeechEvent(SpeechContract.EVENT_END, SpeechBundles.plain(end)))
    }
}

/**
 * `ISpeechSession` held on a dedicated binding lease (roadmap D16, D22): the binding carries `BIND_INCLUDE_CAPABILITIES`
 * for as long as the session lives, the host links to the death of the plugin process (one synthetic `error` +
 * `end(error)`), and [close] cancels the plugin session, releases the binding and ends the channel exactly once.
 * zh-CN: 持有专用绑定租约的 `ISpeechSession` (路线图 D16, D22): 会话存活期间绑定一直带 `BIND_INCLUDE_CAPABILITIES`; 宿主监听插件进程
 * 死亡 (一个合成的 `error` 与 `end(error)`); [close] 取消插件会话, 释放绑定并只结束一次通道.
 */
open class SpeechSessionProxy internal constructor(
    private val leased: AidlPluginHost.LeasedCall<ISpeechSession>,
    protected val gate: SpeechEventGate,
    /** Whether the session takes external PCM through [feed]. zh-CN: 会话是否经 [feed] 接收外部 PCM. */
    val external: Boolean,
) : Closeable {

    private val released = AtomicBoolean(false)
    private val died = AtomicBoolean(false)
    private val deathRecipient = IBinder.DeathRecipient { onDied() }

    protected val session: ISpeechSession get() = leased.value

    val isEnded: Boolean get() = gate.isEnded

    val isReleased: Boolean get() = released.get()

    init {
        try {
            session.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            onDied()
        }
    }

    /** Appends PCM16 LE audio (external sessions, at most `MAX_FEED_BYTES` per call). zh-CN: 追加 PCM16 LE 音频 (外部会话, 每次至多 `MAX_FEED_BYTES`). */
    fun feed(pcm: ByteArray) {
        if (!external) throw SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, "feed() applies to sessions opened with source \"external\"")
        if (pcm.size > SpeechLimits.MAX_FEED_BYTES) {
            throw SpeechErrorMapper.failure(SpeechErrorCodes.LIMIT_EXCEEDED, "one feed() call takes at most ${SpeechLimits.MAX_FEED_BYTES} bytes, not ${pcm.size}")
        }
        if (pcm.size % 2 != 0) throw SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, "PCM16 audio has an even byte count, not ${pcm.size}")
        if (pcm.isEmpty()) return
        call("feed") { session.feed(pcm) }
    }

    /** Flushes and ends the session (`end(stopped)` follows). zh-CN: 冲刷并结束会话 (随后是 `end(stopped)`). */
    fun finish() {
        if (isEnded || released.get()) return
        call("finish") { session.finish() }
    }

    /** Ends the session without a final result (`end(cancelled)` follows). zh-CN: 不产生最终结果地结束会话 (随后是 `end(cancelled)`). */
    fun cancel() {
        if (isEnded || released.get()) return
        call("cancel") { session.cancel() }
    }

    fun isActive(): Boolean {
        if (isEnded || released.get()) return false
        return runCatching { session.isActive }.getOrDefault(false)
    }

    /** `sessionId`, `state`, `modelId` and the accumulated `text`. zh-CN: `sessionId`, `state`, `modelId` 与累计的 `text`. */
    fun snapshot(): Map<String, Any?> = call("snapshot") { SpeechBundles.plain(SpeechBundles.answer(session.snapshot(), "snapshot")) }

    /** Releases the binding once the plugin ended the session (its `end` event arrived). zh-CN: 插件结束会话 (其 `end` 事件到达) 后释放绑定. */
    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { session.asBinder().unlinkToDeath(deathRecipient, 0) }
        runCatching { leased.bindingLease.close() }
    }

    /**
     * The host ends the session (script exit, an argument error after opening): the plugin session is cancelled, the
     * binding released and the channel ends with `end(cancelled)` unless it ended already.
     * zh-CN: 宿主结束会话 (脚本退出, 打开后的参数错误): 取消插件会话, 释放绑定, 通道以 `end(cancelled)` 结束 (除非已经结束).
     */
    override fun close() {
        if (!died.get() && !gate.isEnded) runCatching { session.cancel() }
        release()
        gate.closeLocally(SpeechContract.REASON_CANCELLED)
    }

    /**
     * The session opened but its opener failed afterwards (it was cancelled): the plugin session is cancelled and the
     * binding released; the gates were discarded already, so no event is delivered.
     * zh-CN: 会话已打开但打开方随后失败 (被取消): 取消插件会话并释放绑定; 闸门已被丢弃, 不投递任何事件.
     */
    internal fun abandon() {
        if (!died.get()) runCatching { session.cancel() }
        release()
    }

    private fun onDied() {
        died.set(true)
        release()
        gate.closeLocally(SpeechContract.REASON_ERROR, SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "speech plugin process died"))
    }

    protected inline fun <T> call(label: String, block: () -> T): T {
        if (isReleased) {
            throw SpeechErrorMapper.failure(SpeechErrorCodes.SESSION_CLOSED, "$label: the session is closed")
        }
        return try {
            block()
        } catch (e: SpeechPluginException) {
            throw e
        } catch (t: Throwable) {
            throw SpeechErrorMapper.wrap(t, SpeechErrorMapper.Phase.CALL)
        }
    }
}

/**
 * A wake session (roadmap D18): the wake channel ends the whole session, the listen channel carries the dictation
 * rounds of `thenListen`, and [finishRound] ends the current round so keyword spotting resumes.
 * zh-CN: 唤醒会话 (路线图 D18): 唤醒通道结束整个会话, 听写通道承载 `thenListen` 的听写轮次, [finishRound] 结束当前轮次以恢复关键词检测.
 */
class SpeechWakeSessionProxy internal constructor(
    leased: AidlPluginHost.LeasedCall<ISpeechSession>,
    gate: SpeechEventGate,
    /** The gate of the listen channel, null without `thenListen`. zh-CN: 听写通道的闸门, 没有 `thenListen` 时为 null. */
    val listenGate: SpeechEventGate?,
) : SpeechSessionProxy(leased, gate, external = false) {

    fun finishRound(discard: Boolean) {
        if (isEnded || isReleased) return
        call("finishRound") { session.finishRound(discard) }
    }

    override fun close() {
        super.close()
        listenGate?.closeLocally(SpeechContract.REASON_CANCELLED)
    }
}

/**
 * Opening a session without leaking its gates (docs/memory/ROADMAP.md H30): the callback Binders reach the plugin process
 * before it answers, and it may keep them after refusing (MODEL_MISSING and so on), so every failure discards the gates
 * before the exception goes on. A session that opened before its opener failed (the script stopped meanwhile) is
 * cancelled and its binding released, and so is an open call the plugin answers too late ([lease], Q55).
 * zh-CN: 打开会话而不泄漏其闸门 (docs/memory/ROADMAP.md H30): 回调 Binder 在插件作答之前就已到达插件进程, 插件拒绝 (MODEL_MISSING 等) 后仍可能
 * 持有它们, 因此任何失败都先丢弃闸门再继续抛出异常. 打开方失败前会话已打开的 (其间脚本已停止), 取消该会话并释放绑定; 插件过迟应答的打开调用同样处理
 * ([lease], Q55).
 */
internal object SpeechSessionOpening {

    /**
     * Runs [open] for the session behind [gates]; [open] hands the proxy to `opened` as soon as it exists.
     * zh-CN: 为 [gates] 背后的会话运行 [open]; [open] 在代理建成后立即交给 `opened`.
     */
    suspend fun <P : SpeechSessionProxy> open(gates: List<SpeechEventGate>, open: suspend (opened: (P) -> Unit) -> P): P {
        val opened = AtomicReference<P?>()
        try {
            return open { opened.set(it) }
        } catch (t: Throwable) {
            gates.forEach { it.discard() }
            opened.get()?.abandon()
            throw t
        }
    }

    /**
     * Runs the open call [call] (a session, or the start of an operation) within [timeoutMs] without losing a result that
     * arrives too late (docs/memory/ROADMAP.md Q55): [call] suspends while the dedicated binding connects, so after the
     * timeout or the cancellation of the caller `withTimeout` drops the value of a plugin call that still returns, and the
     * binding would stay bound with the plugin session or operation running until the plugin process ends. Such a result
     * goes to [abandon] (it cancels what the plugin opened) and its binding is released, then the cancellation goes on; a
     * result returned in time belongs to the caller and is not touched.
     * zh-CN: 在 [timeoutMs] 内运行打开调用 [call] (会话, 或操作的启动), 不丢失过迟到达的结果 (docs/memory/ROADMAP.md Q55): [call] 在专用绑定连接期间
     * 挂起, 因此超时或调用方被取消之后, 仍然返回的插件调用的值会被 `withTimeout` 丢弃, 绑定将一直保持, 插件会话或操作一直运行到插件进程结束. 这样的结果
     * 交给 [abandon] (取消插件已打开的内容) 并释放其绑定, 然后继续抛出取消; 按时返回的结果归调用方所有, 不受影响.
     */
    suspend fun <T> lease(timeoutMs: Long, abandon: (T) -> Unit, call: suspend () -> AidlPluginHost.LeasedCall<T>): AidlPluginHost.LeasedCall<T> {
        val arrived = AtomicReference<AidlPluginHost.LeasedCall<T>?>()
        try {
            return withTimeout(timeoutMs) { call().also(arrived::set) }
        } catch (e: CancellationException) {
            arrived.get()?.let { late ->
                runCatching { abandon(late.value) }
                runCatching { late.bindingLease.close() }
            }
            throw e
        }
    }

    fun requireOpened(session: ISpeechSession?, what: String): ISpeechSession =
        session ?: throw SpeechErrorMapper.failure(SpeechErrorCodes.INTERNAL, "the plugin opened no $what")
}
