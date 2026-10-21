package org.autojs.autojs.runtime.api.augment.mail

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.mail.MailError
import org.autojs.autojs.core.plugin.mail.MailErrorMapper
import org.autojs.autojs.core.plugin.mail.MailJson
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.autojs.core.plugin.mail.MailSessionClient
import org.autojs.autojs.core.plugin.mail.MailWatchBridge
import org.autojs.autojs.core.plugin.mail.MailWatchHandle
import org.autojs.autojs.core.plugin.mail.MailWatchSink
import org.autojs.autojs.runtime.api.mail.MailScriptValues
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Drives one plugin watch for a [MailWatch] (mail roadmap P5): opens it through
 * [MailSessionClient.watch], turns the accepted events of the [MailWatchBridge] into emitter
 * transitions, stops it for `watch.stop()` / the client's close / the script's exit, and, when the
 * plugin process dies, reports `error(PLUGIN_UNAVAILABLE)` and (with `reconnect: true`) watches
 * again on the reopened session with a fresh generation, retrying with a bounded backoff while the
 * failure is retryable; a `closed` event with reason `error` after `AUTH_FAILED` on a token account
 * with a `tokenProvider` refreshes the token and watches again the same way. A script that stops
 * draining its events (dispatcher overflow) gets `error(LIMIT_EXCEEDED)` and `close(overflow)`.
 * Pure Kotlin on top of the client, so the JVM tests drive it with a fake session port.
 * zh-CN: 为一个 [MailWatch] 驱动一个插件监听 (邮件路线图 P5): 经 [MailSessionClient.watch] 打开, 把 [MailWatchBridge] 接受的事件转成发射器的
 * 状态变更, 在 `watch.stop()` / 客户端关闭 / 脚本退出时停止; 插件进程死亡时报告 `error(PLUGIN_UNAVAILABLE)` 并 (`reconnect: true` 时) 在重开的
 * 会话上以新 generation 重新监听, 失败可重试时按有界退避重试; 令牌账户带 `tokenProvider` 时 `AUTH_FAILED` 之后的 `closed(error)` 会刷新令牌并
 * 同样重新监听. 脚本不再消费事件 (分发器溢出) 时得到 `error(LIMIT_EXCEEDED)` 与 `close(overflow)`. 纯 Kotlin, JVM 测试用假会话端口驱动.
 */
internal class MailWatchRunner(
    private val scope: CoroutineScope,
    private val session: MailSessionClient,
    private val options: MailJson.WatchOptions,
    private val reconnect: Boolean,
    private val generations: () -> Long,
    private val controller: MailWatchTarget,
    private val decorate: (Any?) -> Any? = { it },
    private val convertError: (MailError) -> Any? = { it },
    private val onFinished: (MailWatchRunner) -> Unit = {},
    private val retryDelayMs: (Int) -> Long = ::defaultRetryDelay,
) : MailWatchSink {

    private val finished = AtomicBoolean(false)
    private val rewatching = AtomicBoolean(false)

    @Volatile
    private var handle: MailWatchHandle? = null

    @Volatile
    private var bridge: MailWatchBridge? = null

    @Volatile
    private var lastError: MailError? = null

    private val sessionLost: (MailError?) -> Unit = { error -> onSessionLost(error) }

    val isFinished: Boolean
        get() = finished.get()

    /** How many plugin watches were opened so far, including the reopenings. zh-CN: 至今打开过的插件监听数, 含重开. */
    @Volatile
    var openCount: Int = 0
        private set

    /** Opens the plugin watch; failures propagate (the caller discards the emitter). zh-CN: 打开插件监听; 失败向上抛 (调用方丢弃发射器). */
    suspend fun open() {
        attach(session.watch(options, generations(), this))
        session.addSessionLossListener(sessionLost)
        controller.bindStop { stop(MailWatch.REASON_STOPPED) }
    }

    /** `watch.stop()` or the client's close: the plugin watch stops, then `close(reason)`. zh-CN: `watch.stop()` 或客户端关闭: 插件监听停止, 然后 `close(reason)`. */
    fun stop(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        detach()
        runCatching { handle?.stop() }
        controller.close(reason)
        onFinished(this)
    }

    /** Script exit: the session's close stops the plugin watch; no event is emitted. zh-CN: 脚本退出: 会话关闭会停止插件监听; 不发出事件. */
    fun cancelSilently() {
        if (!finished.compareAndSet(false, true)) return
        detach()
        controller.cancelSilently()
        onFinished(this)
    }

    // ------------------------------------------------------------------ MailWatchSink

    override fun onEvent(event: MailJson.Event) {
        if (finished.get()) return
        try {
            when (event.type) {
                MailContract.EVENT_MESSAGE -> controller.message(MailScriptValues.toPlain(event.message), decorate)
                MailContract.EVENT_MODE -> event.mode?.let { controller.mode(it) }
                MailContract.EVENT_RESYNC -> controller.resync(event.reason ?: REASON_UNKNOWN)
                MailContract.EVENT_ERROR -> {
                    val error = event.error ?: MailErrorMapper.normalize(MailErrorCodes.INTERNAL, "the mail plugin reported an error without details", retryable = true)
                    lastError = error
                    controller.error(error, convertError)
                }
                MailContract.EVENT_CLOSED -> onClosed(event.reason ?: MailWatch.REASON_CLIENT_CLOSED)
            }
        } catch (t: Throwable) {
            overflow(t)
        }
    }

    override fun onDropped(reason: String, generation: Long, seq: Long) = Unit

    // ------------------------------------------------------------------ transitions

    private fun attach(opened: Pair<MailWatchBridge, MailWatchHandle>) {
        bridge = opened.first
        handle = opened.second
        openCount++
        controller.generation(opened.first.generation)
        val mode = runCatching { modeOf(opened.second.status()) }.getOrNull()
        if (mode != null && mode != controller.currentMode) {
            if (openCount == 1) controller.initialMode(mode) else controller.mode(mode)
        }
    }

    private fun detach() {
        session.removeSessionLossListener(sessionLost)
        bridge?.close()
    }

    private fun finish(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        detach()
        controller.close(reason)
        onFinished(this)
    }

    /** The plugin ended the watch: `stopped` / `closed` pass through; `error` after `AUTH_FAILED` may refresh the token and watch again. zh-CN: 插件结束了监听: `stopped` / `closed` 透传; `AUTH_FAILED` 后的 `error` 可刷新令牌再监听. */
    private fun onClosed(reason: String) {
        if (reason == MailWatch.REASON_ERROR && reconnect && lastError?.code == MailErrorCodes.AUTH_FAILED && session.canRefreshToken) {
            rewatch(refreshToken = true)
            return
        }
        finish(reason)
    }

    /** The plugin process died (death recipient): `error(PLUGIN_UNAVAILABLE)`, then a new watch on the reopened session or `close(plugin-died)`. zh-CN: 插件进程死亡 (死亡回调): `error(PLUGIN_UNAVAILABLE)`, 然后在重开的会话上新建监听或 `close(plugin-died)`. */
    private fun onSessionLost(error: MailError?) {
        if (finished.get()) return
        val reported = MailErrorMapper.normalize(MailErrorCodes.PLUGIN_UNAVAILABLE, error?.message ?: "mail plugin process died", retryable = true)
        lastError = reported
        try {
            controller.error(reported, convertError)
        } catch (t: Throwable) {
            overflow(t)
            return
        }
        if (!reconnect) {
            finish(MailWatch.REASON_PLUGIN_DIED)
            return
        }
        rewatch(refreshToken = false)
    }

    private fun rewatch(refreshToken: Boolean) {
        if (!rewatching.compareAndSet(false, true)) return
        bridge?.close()
        scope.launch {
            try {
                var attempt = 0
                while (!finished.get()) {
                    try {
                        if (refreshToken && attempt == 0 && !session.refreshToken()) {
                            finish(MailWatch.REASON_ERROR)
                            return@launch
                        }
                        val opened = session.watch(options, generations(), this@MailWatchRunner)
                        if (finished.get()) {
                            runCatching { opened.second.stop() }
                            return@launch
                        }
                        attach(opened)
                        controller.resync(MailWatch.RESYNC_REWATCHED)
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        val error = MailErrorMapper.fromThrowable(t, MailErrorMapper.Phase.CALL)
                        lastError = error
                        try {
                            controller.error(error, convertError)
                        } catch (overflowed: Throwable) {
                            overflow(overflowed)
                            return@launch
                        }
                        if (!error.retryable) {
                            finish(MailWatch.REASON_ERROR)
                            return@launch
                        }
                    }
                    delay(retryDelayMs(attempt++))
                }
            } finally {
                rewatching.set(false)
            }
        }
    }

    /** The dispatcher refused an event: the script does not drain them any more. zh-CN: 分发器拒绝了事件: 脚本不再消费. */
    private fun overflow(cause: Throwable) {
        if (!finished.compareAndSet(false, true)) return
        detach()
        runCatching { handle?.stop() }
        val error = MailErrorMapper.normalize(MailErrorCodes.LIMIT_EXCEEDED, "the script did not drain the watch events: ${cause.message.orEmpty()}", retryable = false)
        runCatching { controller.fail(error, MailWatch.REASON_OVERFLOW, convertError) }
        onFinished(this)
    }

    private fun modeOf(status: MailJson.Status): String? {
        val root = runCatching { JsonParser.parseString(status.json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return root.get(MailContract.FIELD_MODE)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it in MailContract.WATCH_MODES }
    }

    companion object {
        const val REASON_UNKNOWN = "unknown"

        /** 1 s, 2 s, 4 s, ... capped at 30 s between re-watch attempts. zh-CN: 重新监听的尝试间隔 1 s, 2 s, 4 s, ... 上限 30 s. */
        fun defaultRetryDelay(attempt: Int): Long = min(1_000L shl attempt.coerceIn(0, 10), 30_000L)
    }
}
