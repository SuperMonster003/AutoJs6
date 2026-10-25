package org.autojs.autojs.runtime.api.modules.gallery

import kotlinx.coroutines.CompletableDeferred
import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryErrorMapper
import org.autojs.autojs.core.plugin.gallery.GalleryEvent
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.autojs.core.plugin.gallery.GallerySessionClient
import org.autojs.autojs.core.plugin.gallery.GallerySessionSink
import org.autojs.autojs.runtime.api.gallery.GalleryScriptValues
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives one plugin viewer session for a [GalleryViewSession] (3-Maple Image roadmap M6, 10.1): as
 * the [GallerySessionSink] of the host client it turns the accepted events into emitter transitions
 * (`open`, `page`, `error`, and the terminal `close` with the plugin's reason), as the session's
 * [GalleryViewSession.Port] it forwards `close` to the [GallerySessionClient] bound by [attach], and
 * on script exit it ends the session without closing the viewer. A script that stops draining its
 * events (dispatcher overflow) gets `error(LIMIT_EXCEEDED)` and `close(overflow)`. [awaitOpen]
 * settles once the viewer is shown, for `viewAsync`. Pure Kotlin on top of the client, so the JVM
 * tests drive it with events.
 * zh-CN: 为一个 [GalleryViewSession] 驱动一个插件查看器会话 (3-Maple Image 路线图 M6, 10.1): 作为宿主客户端的 [GallerySessionSink], 把接受的事件转成
 * 发射器的状态变更 (`open`, `page`, `error`, 以及带插件原因的终结 `close`); 作为会话的 [GalleryViewSession.Port], 把 `close` 转发给 [attach] 绑定的
 * [GallerySessionClient]; 脚本退出时结束会话而不关闭查看器. 脚本不再消费事件 (分发器溢出) 时得到 `error(LIMIT_EXCEEDED)` 与 `close(overflow)`.
 * [awaitOpen] 在查看器显示后落定, 供 `viewAsync` 使用. 纯 Kotlin, 基于客户端, JVM 测试用事件驱动.
 */
internal class GalleryViewRunner(
    private val controller: GalleryViewSession.Controller,
    private val convertError: (GalleryError) -> Any? = { it },
    private val onFinished: (GalleryViewRunner) -> Unit = {},
) : GallerySessionSink, GalleryViewSession.Port, GalleryService.Task {

    private val finished = AtomicBoolean(false)
    private val opened = CompletableDeferred<Unit>()

    @Volatile
    private var client: GallerySessionClient? = null

    @Volatile
    private var reported: GalleryError? = null

    val session: GalleryViewSession
        get() = controller.session

    val isFinished: Boolean
        get() = finished.get()

    /** Binds the plugin session after `openViewer` answered; a session that ended meanwhile releases the client at once. zh-CN: `openViewer` 应答后绑定插件会话; 其间已结束的会话立即释放客户端. */
    fun attach(client: GallerySessionClient) {
        this.client = client
        if (finished.get() || !controller.bindPort(this)) {
            runCatching { client.close(finish = false) }
        }
    }

    /** Suspends until the viewer is shown; fails when the session ends before. zh-CN: 挂起直到查看器显示; 会话在此之前结束则失败. */
    suspend fun awaitOpen() = opened.await()

    /** Script exit: the plugin session ends, the viewer stays, no event is emitted. zh-CN: 脚本退出: 插件会话结束, 查看器保留, 不发出事件. */
    override fun cancelSilently() {
        if (!finished.compareAndSet(false, true)) return
        controller.cancelSilently()
        opened.cancel()
        runCatching { client?.close(finish = false) }
        onFinished(this)
    }

    // ------------------------------------------------------------------ GallerySessionSink

    override fun onEvent(event: GalleryEvent) {
        if (finished.get()) return
        try {
            when (event.type) {
                GalleryContract.EVENT_OPEN -> {
                    controller.open()
                    opened.complete(Unit)
                }
                GalleryContract.EVENT_PAGE -> event.index?.let { controller.page(it) }
                GalleryContract.EVENT_ERROR -> {
                    val error = GalleryErrorMapper.normalize(event.errorCode, event.errorMessage)
                    reported = error
                    controller.error(error, convertError)
                }
                GalleryContract.EVENT_CLOSE -> end(event.reason ?: GalleryContract.REASON_ERROR)
            }
        } catch (t: Throwable) {
            overflow(t)
        }
    }

    // ------------------------------------------------------------------ GalleryViewSession.Port

    /** The host's close: the client delivers the synthetic `close(host)`, which [end] turns into the terminal event. zh-CN: 宿主的关闭: 客户端投递合成的 `close(host)`, 由 [end] 变为终结事件. */
    override fun close(finish: Boolean) {
        val bound = client ?: return
        bound.close(finish)
        end(GalleryContract.REASON_HOST)
    }

    // ------------------------------------------------------------------ transitions

    private fun end(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        controller.close(reason)
        if (!opened.isCompleted) opened.completeExceptionally(GalleryPluginException(GalleryViewSession.closedBeforeOpen(reason, reported)))
        runCatching { client?.release() }
        onFinished(this)
    }

    private fun overflow(cause: Throwable) {
        if (!finished.compareAndSet(false, true)) return
        val error = GalleryErrorMapper.normalize(GalleryErrorCodes.LIMIT_EXCEEDED, "the script did not drain the viewer events: ${cause.message.orEmpty()}")
        runCatching { controller.fail(error, GalleryViewSession.REASON_OVERFLOW, convertError) }
        if (!opened.isCompleted) opened.completeExceptionally(GalleryPluginException(error))
        runCatching { client?.close(finish = false) }
        onFinished(this)
    }
}

/**
 * Waits for the outcome of a picker or editor session (3-Maple Image roadmap M6, 10.1): the first
 * `result` event answers [await]; a session that closes without one answers null (the user
 * cancelled) or fails with the reported `error` or the failure its reason stands for
 * ([GalleryScriptValues.closedWithoutResult]). [end] closes the session and finishes the plugin
 * Activity (a no-op once the plugin closed it); script exit cancels the wait and does the same.
 * zh-CN: 等待选择器或编辑器会话的结果 (3-Maple Image 路线图 M6, 10.1): 首个 `result` 事件应答 [await]; 未收到结果就关闭的会话应答 null (用户取消),
 * 或以已报告的 `error` 或其原因代表的失败而失败 ([GalleryScriptValues.closedWithoutResult]). [end] 关闭会话并结束插件 Activity (插件已关闭时无操作);
 * 脚本退出时取消等待并做同样的事.
 */
internal class GalleryResultWaiter : GallerySessionSink, GalleryService.Task {

    private val outcome = CompletableDeferred<GalleryEvent?>()
    private val ended = AtomicBoolean(false)

    @Volatile
    private var client: GallerySessionClient? = null

    @Volatile
    private var reported: GalleryError? = null

    /** Binds the plugin session; a wait that ended meanwhile closes it at once. zh-CN: 绑定插件会话; 其间已结束的等待立即关闭它. */
    fun attach(client: GallerySessionClient) {
        this.client = client
        if (ended.get()) runCatching { client.close(finish = true) }
    }

    /** The `result` event, or null when the user cancelled. zh-CN: `result` 事件, 用户取消时为 null. */
    suspend fun await(): GalleryEvent? = outcome.await()

    /** Closes the session (idempotent). zh-CN: 关闭会话 (幂等). */
    fun end() {
        if (!ended.compareAndSet(false, true)) return
        runCatching { client?.close(finish = true) }
    }

    override fun cancelSilently() {
        outcome.cancel()
        end()
    }

    override fun onEvent(event: GalleryEvent) {
        when (event.type) {
            GalleryContract.EVENT_RESULT -> outcome.complete(event)
            GalleryContract.EVENT_ERROR -> if (reported == null) reported = GalleryErrorMapper.normalize(event.errorCode, event.errorMessage)
            GalleryContract.EVENT_CLOSE -> {
                val failure = GalleryScriptValues.closedWithoutResult(event.reason, reported)
                if (failure == null) outcome.complete(null) else outcome.completeExceptionally(GalleryPluginException(failure))
            }
        }
    }
}
