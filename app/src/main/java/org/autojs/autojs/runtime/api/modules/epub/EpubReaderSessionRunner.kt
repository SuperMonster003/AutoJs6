package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.core.plugin.epub.EpubError
import org.autojs.autojs.core.plugin.epub.EpubErrorMapper
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.autojs.core.plugin.epub.EpubReaderClient
import org.autojs.autojs.core.plugin.epub.EpubReaderEvent
import org.autojs.autojs.core.plugin.epub.EpubReaderSink
import org.autojs.autojs.runtime.api.epub.EpubScriptArguments
import org.autojs.autojs.runtime.api.epub.EpubScriptValues
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives one plugin reader session for an [EpubReaderSession] (plugin roadmap P6.2): as the
 * [EpubReaderSink] of the host client it turns the accepted events of the bridge into emitter
 * transitions (`open`, `progress`, `bookmark`, `highlight` (contract version 2), `error`, and the
 * terminal `close` with the plugin's reason), as the session's [EpubReaderSession.Port] it forwards `goTo`, `navigate`,
 * `setPreferences`, `bookmarks` and `close` to the [EpubReaderClient] bound by [attach], and it
 * ends the session for the script's exit without finishing the reader (plugin decision D32). A
 * script that stops draining its events (dispatcher overflow) gets `error(LIMIT_EXCEEDED)` and
 * `close(overflow)`. Pure Kotlin on top of the client, so the JVM tests drive it with events.
 * zh-CN: 为一个 [EpubReaderSession] 驱动一个插件阅读器会话 (插件路线图 P6.2): 作为宿主客户端的 [EpubReaderSink], 把桥接受的事件转成发射器的状态变更
 * (`open`, `progress`, `bookmark`, `highlight` (契约版本 2), `error`, 以及带插件原因的终结 `close`); 作为会话的 [EpubReaderSession.Port], 把 `goTo`, `navigate`,
 * `setPreferences`, `bookmarks` 与 `close` 转发给 [attach] 绑定的 [EpubReaderClient]; 脚本退出时结束会话而不结束阅读器 (插件决策 D32). 脚本不再消费
 * 事件 (分发器溢出) 时得到 `error(LIMIT_EXCEEDED)` 与 `close(overflow)`. 纯 Kotlin, 基于客户端, JVM 测试用事件驱动.
 */
internal class EpubReaderSessionRunner(
    private val controller: EpubReaderSession.Controller,
    private val convertError: (EpubError) -> Any? = { it },
    private val onFinished: (EpubReaderSessionRunner) -> Unit = {},
) : EpubReaderSink, EpubReaderSession.Port {

    private val finished = AtomicBoolean(false)

    @Volatile
    private var client: EpubReaderClient? = null

    val session: EpubReaderSession
        get() = controller.session

    val isFinished: Boolean
        get() = finished.get()

    /** Binds the plugin session after `openReader` answered; a session that ended meanwhile releases the client at once. zh-CN: `openReader` 应答后绑定插件会话; 其间已结束的会话立即释放客户端. */
    fun attach(client: EpubReaderClient) {
        this.client = client
        if (finished.get() || !controller.bindPort(this)) {
            runCatching { client.close(finish = false) }
        }
    }

    /** Script exit: the plugin session ends, the reader stays, no event is emitted. zh-CN: 脚本退出: 插件会话结束, 阅读器保留, 不发出事件. */
    fun cancelSilently() {
        if (!finished.compareAndSet(false, true)) return
        controller.cancelSilently()
        runCatching { client?.close(finish = false) }
        onFinished(this)
    }

    // ------------------------------------------------------------------ EpubReaderSink

    override fun onEvent(event: EpubReaderEvent) {
        if (finished.get()) return
        try {
            when (event.type) {
                EpubContract.EVENT_OPEN -> controller.open(EpubScriptValues.toPlain(event.locatorJson), event.title, event.href, event.positions)
                EpubContract.EVENT_PROGRESS -> controller.progress(EpubScriptValues.toPlain(event.locatorJson), event.progression, event.title, event.href)
                EpubContract.EVENT_BOOKMARK -> controller.bookmark(event.change ?: CHANGE_UNKNOWN, EpubScriptValues.list(event.bookmarksJson).firstOrNull())
                EpubContract.EVENT_HIGHLIGHT -> controller.highlight(event.change ?: CHANGE_UNKNOWN, EpubScriptValues.list(event.annotationsJson).firstOrNull())
                EpubContract.EVENT_ERROR -> controller.error(EpubErrorMapper.normalize(event.errorCode, event.errorMessage), convertError)
                EpubContract.EVENT_CLOSE -> end(event.reason ?: EpubContract.REASON_ERROR)
            }
        } catch (t: Throwable) {
            overflow(t)
        }
    }

    override fun onDropped(reason: String, generation: Long, seq: Long) = Unit

    // ------------------------------------------------------------------ EpubReaderSession.Port

    override fun goTo(target: EpubScriptArguments.GoTo) {
        requireClient().goTo(target.locatorJson, target.href, target.progression)
    }

    override fun navigate(direction: Int) {
        requireClient().navigate(direction)
    }

    override fun setPreferences(preferencesJson: String) {
        requireClient().setPreferences(preferencesJson)
    }

    override fun bookmarks(): List<Any?> = EpubScriptValues.list(requireClient().bookmarks())

    /** The host's close: the bridge delivers the synthetic `close(host)`, which [end] turns into the terminal event. zh-CN: 宿主的关闭: 桥投递合成的 `close(host)`, 由 [end] 变为终结事件. */
    override fun close(finish: Boolean) {
        val bound = client ?: return
        bound.close(finish)
        end(EpubContract.REASON_HOST)
    }

    // ------------------------------------------------------------------ transitions

    private fun end(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        controller.close(reason)
        runCatching { client?.close(finish = false) }
        onFinished(this)
    }

    private fun overflow(cause: Throwable) {
        if (!finished.compareAndSet(false, true)) return
        val error = EpubErrorMapper.normalize(EpubErrorCodes.LIMIT_EXCEEDED, "the script did not drain the reader events: ${cause.message.orEmpty()}")
        runCatching { controller.fail(error, EpubReaderSession.REASON_OVERFLOW, convertError) }
        runCatching { client?.close(finish = false) }
        onFinished(this)
    }

    private fun requireClient(): EpubReaderClient =
        client ?: throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.SESSION_CLOSED, "the reader session is closed"))

    private companion object {
        const val CHANGE_UNKNOWN = "changed"
    }
}
