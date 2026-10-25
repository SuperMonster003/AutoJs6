package org.autojs.autojs.runtime.api.modules.speech

import org.autojs.autojs.core.plugin.speech.SpeechError
import org.autojs.autojs.core.plugin.speech.SpeechErrorMapper
import org.autojs.autojs.core.plugin.speech.SpeechEvent
import org.autojs.autojs.core.plugin.speech.SpeechEventSink
import org.autojs.autojs.core.plugin.speech.SpeechPluginException
import org.autojs.autojs.core.plugin.speech.SpeechSessionProxy
import org.autojs.autojs.core.plugin.speech.SpeechWakeSessionProxy
import org.autojs.autojs.runtime.api.speech.SpeechScriptValues
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechResultKeys
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns the events of one session channel into the transitions of a [SpeechSession] (3-Macaw Speech roadmap D16,
 * P1.4). Shared by plain dictation sessions and the rounds of a wake session; [onEnd] runs once the session ended.
 * zh-CN: 把一个会话通道的事件转为 [SpeechSession] 的状态变更 (3-Macaw Speech 路线图 D16, P1.4). 普通听写会话与唤醒会话的轮次共用; [onEnd] 在
 * 会话结束后运行一次.
 */
internal class SpeechEventRouter(
    private val controller: SpeechSession.Controller,
    private val convertError: (SpeechError) -> Any?,
) {

    /** Delivers [event]; answers true when it was the session's `end`. zh-CN: 投递 [event]; 是会话的 `end` 时返回 true. */
    fun route(event: SpeechEvent): Boolean {
        when (event.type) {
            SpeechContract.EVENT_READY -> controller.ready(event.payload)
            SpeechContract.EVENT_PARTIAL -> controller.partial(SpeechScriptValues.withoutRound(event.payload))
            SpeechContract.EVENT_FINAL -> controller.final(SpeechScriptValues.withoutRound(event.payload))
            SpeechContract.EVENT_ENDPOINT -> controller.endpoint()
            SpeechContract.EVENT_VAD -> controller.vad(SpeechScriptValues.withoutRound(event.payload))
            SpeechContract.EVENT_WARNING -> controller.warning(event.payload)
            SpeechContract.EVENT_ERROR -> controller.error(event.error ?: SpeechErrorMapper.normalize(SpeechErrorCodes.INTERNAL, null), convertError)
            SpeechContract.EVENT_END -> {
                val reason = event.payload[SpeechResultKeys.REASON] as? String ?: SpeechContract.REASON_ERROR
                controller.end(reason, SpeechScriptValues.textOf(event.payload))
                return true
            }
        }
        return false
    }
}

/**
 * Drives one plugin dictation session for a [SpeechSession] (roadmap P1.4): as the [SpeechEventSink] of the host client
 * it routes the accepted events into the emitter, as the session's [SpeechSession.Port] it forwards `stop`, `cancel` and
 * `feed` to the [SpeechSessionProxy] bound by [attach], and on script exit it cancels the plugin session without
 * emitting anything. A script that stops draining its events (dispatcher overflow) gets `error(LIMIT_EXCEEDED)` and
 * `end(overflow)`, and the plugin session is cancelled. Pure Kotlin on top of the proxy, so the JVM tests drive it with
 * events.
 * zh-CN: 为一个 [SpeechSession] 驱动一个插件听写会话 (路线图 P1.4): 作为宿主客户端的 [SpeechEventSink], 把放行的事件转入发射器; 作为会话的
 * [SpeechSession.Port], 把 `stop`, `cancel` 与 `feed` 转发给 [attach] 绑定的 [SpeechSessionProxy]; 脚本退出时取消插件会话且不发出事件. 脚本不再
 * 消费事件 (分发器溢出) 时得到 `error(LIMIT_EXCEEDED)` 与 `end(overflow)`, 插件会话随之取消. 纯 Kotlin, 基于代理, JVM 测试用事件驱动.
 */
internal class SpeechSessionRunner(
    private val controller: SpeechSession.Controller,
    convertError: (SpeechError) -> Any? = { it },
    private val onFinished: (SpeechSessionRunner) -> Unit = {},
) : SpeechEventSink, SpeechSession.Port, SpeechService.Task {

    private val router = SpeechEventRouter(controller, convertError)
    private val convert = convertError
    private val finished = AtomicBoolean(false)

    @Volatile
    private var proxy: SpeechSessionProxy? = null

    val session: SpeechSession
        get() = controller.session

    val isFinished: Boolean
        get() = finished.get()

    /** Binds the plugin session after `openSession` answered; a session that ended meanwhile releases it at once. zh-CN: `openSession` 应答后绑定插件会话; 其间已结束的会话立即释放它. */
    fun attach(proxy: SpeechSessionProxy) {
        this.proxy = proxy
        if (finished.get() || !controller.bindPort(this)) {
            if (proxy.isEnded) proxy.release() else runCatching { proxy.close() }
        }
    }

    /** Script exit: the plugin session is cancelled and released, no event is emitted. zh-CN: 脚本退出: 取消并释放插件会话, 不发出事件. */
    override fun cancelSilently() {
        if (!finished.compareAndSet(false, true)) return
        controller.cancelSilently()
        runCatching { proxy?.close() }
        onFinished(this)
    }

    override fun onEvent(event: SpeechEvent) {
        if (finished.get()) return
        try {
            if (router.route(event)) finish()
        } catch (t: Throwable) {
            overflow(t)
        }
    }

    override fun stop() {
        requireProxy().finish()
    }

    override fun cancel() {
        requireProxy().cancel()
    }

    override fun feed(pcm: ByteArray) {
        requireProxy().feed(pcm)
    }

    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        runCatching { proxy?.release() }
        onFinished(this)
    }

    private fun overflow(cause: Throwable) {
        if (!finished.compareAndSet(false, true)) return
        val error = SpeechErrorMapper.normalize(SpeechErrorCodes.LIMIT_EXCEEDED, "the script did not drain the session events: ${cause.message.orEmpty()}")
        runCatching { controller.fail(error, SpeechSession.REASON_OVERFLOW, convert) }
        runCatching { proxy?.close() }
        onFinished(this)
    }

    private fun requireProxy(): SpeechSessionProxy =
        proxy ?: throw SpeechPluginException(SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "the session is not open yet"))
}

/**
 * Drives one plugin wake session for a [SpeechWakeSession] (roadmap D18, P1.4): the wake channel feeds the wake
 * emitter; with `thenListen` the listen channel feeds one [SpeechSession] per dictation round, created when the hit or
 * the round's first event arrives (the two channels are separate oneway Binders, so either may come first) and handed
 * to the script as the `wake` event's `listen`. A round's `stop()` / `cancel()` end only that round; the wake session's
 * `stop()` ends everything. Rounds still open when the wake session ends get their own `end` first.
 * zh-CN: 为一个 [SpeechWakeSession] 驱动一个插件唤醒会话 (路线图 D18, P1.4): 唤醒通道驱动唤醒发射器; 有 `thenListen` 时听写通道为每个听写轮次驱动一个
 * [SpeechSession], 在命中或该轮首个事件到达时创建 (两个通道是不同的 oneway Binder, 先后不定), 并作为 `wake` 事件的 `listen` 交给脚本. 轮次的
 * `stop()` / `cancel()` 只结束该轮; 唤醒会话的 `stop()` 结束全部. 唤醒会话结束时仍未结束的轮次先得到各自的 `end`.
 */
internal class SpeechWakeRunner(
    private val controller: SpeechWakeSession.Controller,
    private val thenListen: Boolean,
    private val newRound: (Int) -> SpeechSession.Controller,
    private val convertError: (SpeechError) -> Any? = { it },
    private val onFinished: (SpeechWakeRunner) -> Unit = {},
) : SpeechWakeSession.Port, SpeechService.Task {

    private val finished = AtomicBoolean(false)
    private val rounds = ConcurrentHashMap<Int, Round>()
    private val hits = AtomicInteger(0)

    @Volatile
    private var proxy: SpeechWakeSessionProxy? = null

    val session: SpeechWakeSession
        get() = controller.session

    val isFinished: Boolean
        get() = finished.get()

    /** The sink of the wake channel. zh-CN: 唤醒通道的接收方. */
    val wakeSink = SpeechEventSink { event -> onWakeEvent(event) }

    /** The sink of the listen channel (`thenListen` rounds). zh-CN: 听写通道 (`thenListen` 轮次) 的接收方. */
    val listenSink = SpeechEventSink { event -> onListenEvent(event) }

    fun attach(proxy: SpeechWakeSessionProxy) {
        this.proxy = proxy
        if (finished.get() || !controller.bindPort(this)) {
            if (proxy.isEnded) proxy.release() else runCatching { proxy.close() }
        }
    }

    override fun cancelSilently() {
        if (!finished.compareAndSet(false, true)) return
        rounds.values.forEach { it.controller.cancelSilently() }
        controller.cancelSilently()
        runCatching { proxy?.close() }
        onFinished(this)
    }

    override fun stop() {
        (proxy ?: throw SpeechPluginException(SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "the wake session is not open yet"))).finish()
    }

    private fun onWakeEvent(event: SpeechEvent) {
        if (finished.get()) return
        try {
            when (event.type) {
                SpeechContract.EVENT_READY -> controller.ready(event.payload)
                SpeechContract.EVENT_WAKE -> {
                    val index = SpeechScriptValues.roundOf(event.payload) ?: hits.get()
                    hits.incrementAndGet()
                    if (!thenListen) {
                        controller.wake(event.payload, null)
                    } else {
                        val round = roundOf(index)
                        controller.wake(event.payload, round.controller.session)
                        // The script attaches its round listeners in the wake handler: only now may the round speak.
                        // zh-CN: 脚本在 wake 回调里挂上轮次监听器: 此后该轮次的事件才能投递.
                        round.announce()
                    }
                }
                SpeechContract.EVENT_WARNING -> controller.warning(event.payload)
                SpeechContract.EVENT_ERROR -> controller.error(event.error ?: SpeechErrorMapper.normalize(SpeechErrorCodes.INTERNAL, null), convertError)
                SpeechContract.EVENT_END -> end(event.payload[SpeechResultKeys.REASON] as? String ?: SpeechContract.REASON_ERROR)
            }
        } catch (t: Throwable) {
            overflow(t)
        }
    }

    private fun onListenEvent(event: SpeechEvent) {
        if (finished.get()) return
        try {
            roundOf(event.round ?: 0).deliver(event)
        } catch (t: Throwable) {
            overflow(t)
        }
    }

    private fun roundOf(index: Int): Round = rounds.computeIfAbsent(index) { i ->
        val roundController = newRound(i)
        Round(roundController, SpeechEventRouter(roundController, convertError)).also { round -> roundController.bindPort(round.port(this)) }
    }

    /** Ends the current round in the plugin (`finishRound`). zh-CN: 在插件中结束当前轮次 (`finishRound`). */
    internal fun finishRound(discard: Boolean) {
        val bound = proxy ?: throw SpeechPluginException(SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "the wake session is not open yet"))
        bound.finishRound(discard)
    }

    private fun end(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        rounds.values.filter { !it.ended.get() }.forEach { runCatching { it.controller.end(reason, null) } }
        controller.end(reason)
        runCatching { proxy?.release() }
        onFinished(this)
    }

    private fun overflow(cause: Throwable) {
        if (!finished.compareAndSet(false, true)) return
        val error = SpeechErrorMapper.normalize(SpeechErrorCodes.LIMIT_EXCEEDED, "the script did not drain the wake events: ${cause.message.orEmpty()}")
        rounds.values.forEach { runCatching { it.controller.cancelSilently() } }
        runCatching { controller.fail(error, SpeechSession.REASON_OVERFLOW, convertError) }
        runCatching { proxy?.close() }
        onFinished(this)
    }

    /**
     * One dictation round of the wake session. The wake and listen channels are separate oneway Binders, so a round's
     * events may arrive before its hit; they wait in [held] until the hit was dispatched ([announce]).
     * zh-CN: 唤醒会话的一个听写轮次. 唤醒与听写通道是不同的 oneway Binder, 轮次事件可能先于命中到达; 它们暂存在 [held] 中, 直到命中已派发
     * ([announce]).
     */
    private class Round(val controller: SpeechSession.Controller, val router: SpeechEventRouter) {
        val ended = AtomicBoolean(false)
        private val held = ArrayList<SpeechEvent>()
        private var announced = false

        fun deliver(event: SpeechEvent) {
            synchronized(this) {
                if (announced) route(event) else held.add(event)
            }
        }

        fun announce() {
            synchronized(this) {
                announced = true
                held.forEach(::route)
                held.clear()
            }
        }

        private fun route(event: SpeechEvent) {
            if (router.route(event)) ended.set(true)
        }

        fun port(runner: SpeechWakeRunner) = object : SpeechSession.Port {
            override fun stop() = runner.finishRound(discard = false)
            override fun cancel() = runner.finishRound(discard = true)
            override fun feed(pcm: ByteArray) {
                throw SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, "a wake round listens to the microphone; feed() is not available")
            }
        }
    }
}
