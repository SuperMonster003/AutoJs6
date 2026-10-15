package org.autojs.autojs.runtime.api.augment.gallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.gallery.GalleryScriptArguments
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.mozilla.javascript.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `GalleryViewSession` (3-Maple Image roadmap M6, 10.1): an `EventEmitter` with
 * the events `open({ count })`, `page({ index, path })`, `error(GalleryError)` and `close({ reason
 * })`, the properties `paths`, `count`, `index`, `path`, `state`, `reason`, `isOpen` / `isClosed`
 * and the method `close(options?)`. Every event reaches the listeners on the script thread that
 * opened the viewer through a [GalleryAsyncDispatcher], which also keeps the script alive until the
 * session closed; `close` is the single terminal event. State changes come from the unexposed
 * [Controller] and the plugin call goes through the unexposed [Port] bound by the runner, so
 * scripts only see the event and method surface.
 * zh-CN: 脚本可见的 `GalleryViewSession` (3-Maple Image 路线图 M6, 10.1): 带事件 `open({ count })`, `page({ index, path })`, `error(GalleryError)` 与
 * `close({ reason })` 的 `EventEmitter`, 属性 `paths`, `count`, `index`, `path`, `state`, `reason`, `isOpen` / `isClosed`, 方法 `close(options?)`.
 * 每个事件经 [GalleryAsyncDispatcher] 回到打开查看器的脚本线程, 分发器同时让脚本活到会话关闭; `close` 是唯一的终结事件. 状态变更来自不对脚本暴露的
 * [Controller], 插件调用经运行器绑定的不暴露的 [Port], 脚本只看到事件与方法表面.
 */
class GalleryViewSession internal constructor(
    private val dispatcher: GalleryAsyncDispatcher,
    bridges: ScriptBridges,
    paths: List<String>,
    private val scriptRuntime: ScriptRuntime?,
    private val convertValue: (Any?) -> Any?,
    private val onTerminal: (() -> Unit)?,
) : EventEmitter(bridges) {

    /** The plugin call behind `close()`; bound by the runner once the plugin session exists. zh-CN: `close()` 背后的插件调用; 插件会话存在后由运行器绑定. */
    internal interface Port {
        fun close(finish: Boolean)
    }

    private val pages: List<String> = paths.toList()
    private val port = AtomicReference<Port?>()
    private val terminalLock = Any()

    @Volatile
    private var currentIndex = NO_PAGE

    @Volatile
    private var opened = false

    @Volatile
    private var closeReason: String? = null

    /** The image paths of the viewer, in page order. zh-CN: 查看器的图片路径, 按页序. */
    val paths: Any?
        get() = convertValue(pages)

    val count: Int
        get() = pages.size

    /** The page the viewer shows, or -1 before the first `page` event. zh-CN: 查看器显示的页, 首个 `page` 事件前为 -1. */
    val index: Int
        get() = currentIndex

    /** The path of the page the viewer shows, or null before the first `page` event. zh-CN: 查看器显示的页的路径, 首个 `page` 事件前为 null. */
    val path: String?
        get() = pages.getOrNull(currentIndex)

    @get:JvmName("getIsOpen")
    val isOpen: Boolean
        get() = opened && !dispatcher.isTerminal

    @get:JvmName("getIsClosed")
    val isClosed: Boolean
        get() = dispatcher.isTerminal

    /** `pending` until the `open` event, then `open`, then `closed`. zh-CN: `open` 事件前为 `pending`, 之后 `open`, 关闭后 `closed`. */
    val state: String
        get() = when {
            dispatcher.isTerminal -> STATE_CLOSED
            opened -> STATE_OPEN
            else -> STATE_PENDING
        }

    /** The reason of the `close` event once closed. zh-CN: 关闭后为 `close` 事件的原因. */
    val reason: String?
        get() = closeReason

    override fun on(eventName: String, listener: Callable): GalleryViewSession {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): GalleryViewSession {
        super.once(eventName, listener)
        return this
    }

    /**
     * Ends the session and closes the viewer, or leaves the viewer to the user with
     * `{ finish: false }`. The `close` event follows with reason `host`. Idempotent.
     * zh-CN: 结束会话并关闭查看器, 用 `{ finish: false }` 时把查看器留给用户. 随后是原因为 `host` 的 `close` 事件. 幂等.
     */
    @JvmOverloads
    fun close(options: Any? = null): GalleryViewSession = guarded {
        val request = GalleryScriptArguments.close(GalleryCalls.json(options))
        val bound = port.get() ?: return@guarded this
        val runtime = scriptRuntime
        if (runtime == null) {
            bound.close(request.finish)
        } else {
            GalleryCalls.sync(runtime) {
                withContext(Dispatchers.IO) { withTimeout(GalleryContract.CALL_TIMEOUT_MS) { bound.close(request.finish) } }
            }
        }
        this
    }

    override fun toString(): String = buildString {
        append("GalleryViewSession { count: ").append(pages.size).append(", state: ").append(state)
        if (currentIndex != NO_PAGE) append(", index: ").append(currentIndex)
        closeReason?.let { append(", reason: ").append(it) }
        append(" }")
    }

    /** Argument and plugin failures leave as `GalleryError`; script values pass unchanged. zh-CN: 参数与插件失败以 `GalleryError` 离开; 脚本值原样通过. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        val runtime = scriptRuntime ?: throw t
        GalleryJsErrors.throwInto(runtime, t)
    }

    // ------------------------------------------------------------------ transitions (Controller only)

    private fun bindPort(bound: Port): Boolean = synchronized(terminalLock) {
        if (dispatcher.isTerminal) false else {
            port.set(bound)
            true
        }
    }

    private fun open(): Boolean {
        opened = true
        val payload = LinkedHashMap<String, Any?>()
        payload[FIELD_COUNT] = pages.size
        return dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(EVENT_OPEN, values[0]) }
    }

    /** A page outside the viewer's range is dropped. zh-CN: 超出查看器范围的页被丢弃. */
    private fun page(index: Int): Boolean {
        if (index !in pages.indices) return false
        currentIndex = index
        val payload = LinkedHashMap<String, Any?>()
        payload[FIELD_INDEX] = index
        payload[FIELD_PATH] = pages[index]
        return dispatcher.dispatchValues(arrayOf(payload)) { values -> emit(EVENT_PAGE, values[0]) }
    }

    private fun error(error: GalleryError, convert: (GalleryError) -> Any?): Boolean =
        dispatcher.dispatchValues(emptyArray()) { emit(EVENT_ERROR, convert(error)) }

    /** The terminal `close`; queued events go first. zh-CN: 终结的 `close`; 排队中的事件先投递. */
    private fun close(reason: String): Boolean = terminate(reason) {
        dispatcher.terminalValues(arrayOf(closePayload(reason))) { values ->
            try {
                emit(EVENT_CLOSE, values[0])
            } finally {
                notifyTerminal()
            }
        }
    }

    /** `error` then `close` in one terminal action that discards the queued events (the script stopped draining). zh-CN: 一个丢弃排队事件的终结动作里先 `error` 后 `close` (脚本不再消费事件). */
    private fun fail(error: GalleryError, convert: (GalleryError) -> Any?, reason: String): Boolean = terminate(reason) {
        dispatcher.replaceQueuedWithTerminalValues(arrayOf(closePayload(reason))) { values ->
            try {
                var failure: Throwable? = null
                try {
                    emit(EVENT_ERROR, convert(error))
                } catch (t: Throwable) {
                    failure = t
                }
                try {
                    emit(EVENT_CLOSE, values[0])
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
            if (!dispatcher.cancelSilently(::notifyTerminal)) return false
            closeReason = closeReason ?: REASON_EXIT
            port.set(null)
            return true
        }
    }

    private fun terminate(reason: String, enqueue: () -> Boolean): Boolean {
        synchronized(terminalLock) {
            if (dispatcher.isTerminal) return false
            closeReason = reason
            port.set(null)
            return try {
                enqueue()
            } catch (t: Throwable) {
                notifyTerminal()
                throw t
            }
        }
    }

    private fun closePayload(reason: String): Map<String, Any?> = LinkedHashMap<String, Any?>().apply { put(FIELD_REASON, reason) }

    private fun notifyTerminal() {
        runCatching { onTerminal?.invoke() }
    }

    /**
     * Transport-side capability kept off the Java object exposed to Rhino: Kotlin `internal`
     * members are JVM-public and reachable through reflection, so every transition lives here.
     * zh-CN: 不暴露给 Rhino 的传输侧能力: Kotlin `internal` 成员在 JVM 上是 public 且可经反射到达, 因此每个状态变更都在这里.
     */
    internal class Controller internal constructor(val session: GalleryViewSession) {

        constructor(
            dispatcher: GalleryAsyncDispatcher,
            bridges: ScriptBridges,
            paths: List<String>,
            scriptRuntime: ScriptRuntime? = null,
            convertValue: (Any?) -> Any? = { it },
            onTerminal: (() -> Unit)? = null,
        ) : this(GalleryViewSession(dispatcher, bridges, paths, scriptRuntime, convertValue, onTerminal))

        val isClosed: Boolean
            get() = session.isClosed

        /** Binds the plugin call; false when the session closed already. zh-CN: 绑定插件调用; 会话已关闭时为 false. */
        fun bindPort(port: Port): Boolean = session.bindPort(port)

        fun open(): Boolean = session.open()

        fun page(index: Int): Boolean = session.page(index)

        fun error(error: GalleryError, convert: (GalleryError) -> Any? = { it }): Boolean = session.error(error, convert)

        fun close(reason: String): Boolean = session.close(reason)

        fun fail(error: GalleryError, reason: String, convert: (GalleryError) -> Any? = { it }): Boolean = session.fail(error, convert, reason)

        fun cancelSilently(): Boolean = session.cancelSilently()
    }

    companion object {
        const val EVENT_OPEN = GalleryContract.EVENT_OPEN
        const val EVENT_PAGE = GalleryContract.EVENT_PAGE
        const val EVENT_ERROR = GalleryContract.EVENT_ERROR
        const val EVENT_CLOSE = GalleryContract.EVENT_CLOSE

        const val STATE_PENDING = "pending"
        const val STATE_OPEN = "open"
        const val STATE_CLOSED = "closed"

        const val NO_PAGE = -1

        /** `close` reasons raised on the host side; the plugin's own (`user`, `host`, `timeout`, `replaced`, `error`) pass through. zh-CN: 宿主侧产生的 `close` 原因; 插件自己的 (`user`, `host`, `timeout`, `replaced`, `error`) 原样透传. */
        const val REASON_EXIT = "script-exit"
        const val REASON_OVERFLOW = "overflow"

        const val FIELD_COUNT = "count"
        const val FIELD_INDEX = "index"
        const val FIELD_PATH = "path"
        const val FIELD_REASON = "reason"

        /** The failure of a session that ended before the viewer opened. zh-CN: 查看器打开前就结束的会话的失败. */
        internal fun closedBeforeOpen(reason: String, reported: GalleryError?): GalleryError = reported ?: when (reason) {
            GalleryContract.REASON_TIMEOUT -> GalleryError(GalleryErrorCodes.TIMEOUT, "the gallery plugin did not show the viewer in time")
            GalleryContract.REASON_REPLACED -> GalleryError(GalleryErrorCodes.SESSION_REPLACED, "another gallery session replaced the viewer")
            else -> GalleryError(GalleryErrorCodes.SESSION_CLOSED, "the viewer closed before it opened ($reason)")
        }
    }
}
