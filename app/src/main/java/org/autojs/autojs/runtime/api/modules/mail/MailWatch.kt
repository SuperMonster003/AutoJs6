package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.mail.MailError
import org.autojs.autojs.runtime.ScriptBridges
import org.mozilla.javascript.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `MailWatch` (mail roadmap P5, appendix A.3): an `EventEmitter` with the
 * events `message(message, watch)`, `mode(mode)`, `resync(reason)`, `error(err)` and
 * `close(reason)`, the properties `folder`, `mode`, `generation`, `isActive` / `isClosed` and
 * `stop()`. Every event reaches the listeners on the script thread that created the watch through
 * a [MailAsyncDispatcher], which also keeps the script alive until the watch closed; `close` is
 * the single terminal event, after which nothing else is emitted. State changes come from the
 * unexposed [Controller] (like `AiStream`), so scripts only see the event and `stop` surface.
 * zh-CN: 脚本可见的 `MailWatch` (邮件路线图 P5, 附录 A.3): 带事件 `message(message, watch)`, `mode(mode)`, `resync(reason)`, `error(err)` 与
 * `close(reason)` 的 `EventEmitter`, 属性 `folder`, `mode`, `generation`, `isActive` / `isClosed` 与 `stop()`. 每个事件经 [MailAsyncDispatcher]
 * 回到创建监听的脚本线程, 分发器同时让脚本活到监听关闭; `close` 是唯一的终结事件, 之后不再发出任何事件. 状态变更来自不对脚本暴露的
 * [Controller] (同 `AiStream`), 脚本只看到事件与 `stop` 表面.
 */
class MailWatch internal constructor(
    private val dispatcher: MailAsyncDispatcher,
    bridges: ScriptBridges,
    val folder: String,
    initialMode: String?,
    private val onTerminal: (() -> Unit)?,
) : EventEmitter(bridges) {

    private val stopAction = AtomicReference<(() -> Unit)?>()
    private val terminalLock = Any()

    @Volatile
    private var currentMode: String? = initialMode

    @Volatile
    private var currentGeneration: Long = 0L

    @Volatile
    private var closeReason: String? = null

    /** `idle` or `poll` once known; changes with the `mode` event. zh-CN: 已知时为 `idle` 或 `poll`; 随 `mode` 事件变化. */
    val mode: String?
        get() = currentMode

    /** The generation of the plugin watch behind this object; grows when the watch was reopened after a plugin death. zh-CN: 本对象背后插件监听的 generation; 插件死亡后重开时递增. */
    val generation: Long
        get() = currentGeneration

    @get:JvmName("getIsActive")
    val isActive: Boolean
        get() = !dispatcher.isTerminal

    @get:JvmName("getIsClosed")
    val isClosed: Boolean
        get() = dispatcher.isTerminal

    /** `active` or `closed`. zh-CN: `active` 或 `closed`. */
    val state: String
        get() = if (dispatcher.isTerminal) STATE_CLOSED else STATE_ACTIVE

    /** The reason of the `close` event once closed. zh-CN: 关闭后为 `close` 事件的原因. */
    val reason: String?
        get() = closeReason

    override fun on(eventName: String, listener: Callable): MailWatch {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): MailWatch {
        super.once(eventName, listener)
        return this
    }

    /**
     * Stops the plugin watch; the events already received are still delivered, then `close`
     * with reason `stopped`. Idempotent.
     * zh-CN: 停止插件监听; 已收到的事件仍会投递, 之后是原因为 `stopped` 的 `close`. 幂等.
     */
    fun stop(): MailWatch {
        val action = stopAction.getAndSet(null)
        if (action != null) {
            action()
        } else {
            close(REASON_STOPPED)
        }
        return this
    }

    override fun toString(): String = "MailWatch { folder: $folder, mode: ${mode ?: "?"}, state: $state${reason?.let { ", reason: $it" }.orEmpty()} }"

    // ------------------------------------------------------------------ transitions (Controller only)

    private fun bindStop(action: () -> Unit) {
        val closedAlready = synchronized(terminalLock) {
            if (dispatcher.isTerminal) true else {
                stopAction.set(action)
                false
            }
        }
        if (closedAlready) action()
    }

    private fun message(plain: Any?, decorate: (Any?) -> Any?): Boolean =
        dispatcher.dispatchValues(arrayOf(plain)) { values -> emit(EVENT_MESSAGE, decorate(values[0]), this) }

    private fun mode(mode: String): Boolean {
        currentMode = mode
        return dispatcher.dispatchValues(emptyArray()) { emit(EVENT_MODE, mode) }
    }

    private fun resync(reason: String): Boolean =
        dispatcher.dispatchValues(emptyArray()) { emit(EVENT_RESYNC, reason) }

    private fun error(error: MailError, convert: (MailError) -> Any?): Boolean =
        dispatcher.dispatchValues(emptyArray()) { emit(EVENT_ERROR, convert(error)) }

    /** The terminal `close`; queued events go first. zh-CN: 终结的 `close`; 排队中的事件先投递. */
    private fun close(reason: String): Boolean = terminate(reason) {
        dispatcher.terminalValues(emptyArray()) {
            try {
                emit(EVENT_CLOSE, reason)
            } finally {
                notifyTerminal()
            }
        }
    }

    /** `error` then `close` in one terminal action that discards the queued events (the script stopped draining). zh-CN: 一个丢弃排队事件的终结动作里先 `error` 后 `close` (脚本不再消费事件). */
    private fun fail(error: MailError, convert: (MailError) -> Any?, reason: String): Boolean = terminate(reason) {
        dispatcher.replaceQueuedWithTerminalValues(emptyArray()) {
            try {
                var failure: Throwable? = null
                try {
                    emit(EVENT_ERROR, convert(error))
                } catch (t: Throwable) {
                    failure = t
                }
                try {
                    emit(EVENT_CLOSE, reason)
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
            stopAction.set(null)
            return true
        }
    }

    private fun terminate(reason: String, enqueue: () -> Boolean): Boolean {
        synchronized(terminalLock) {
            if (dispatcher.isTerminal) return false
            closeReason = reason
            stopAction.set(null)
            val accepted = try {
                enqueue()
            } catch (t: Throwable) {
                notifyTerminal()
                throw t
            }
            return accepted
        }
    }

    private fun notifyTerminal() {
        runCatching { onTerminal?.invoke() }
    }

    /**
     * Transport-side capability kept off the Java object exposed to Rhino: Kotlin `internal`
     * members are JVM-public and reachable through reflection, so every transition lives here.
     * zh-CN: 不暴露给 Rhino 的传输侧能力: Kotlin `internal` 成员在 JVM 上是 public 且可经反射到达, 因此每个状态变更都在这里.
     */
    internal class Controller internal constructor(val watch: MailWatch) : MailWatchTarget {

        constructor(dispatcher: MailAsyncDispatcher, bridges: ScriptBridges, folder: String, initialMode: String?, onTerminal: (() -> Unit)? = null) :
            this(MailWatch(dispatcher, bridges, folder, initialMode, onTerminal))

        override val currentMode: String?
            get() = watch.mode

        override val isClosed: Boolean
            get() = watch.isClosed

        /** Binds `stop()`; runs [action] at once when the watch closed already. zh-CN: 绑定 `stop()`; 监听已关闭时立即运行 [action]. */
        override fun bindStop(action: () -> Unit): Controller = apply { watch.bindStop(action) }

        override fun generation(generation: Long): Controller = apply { watch.currentGeneration = generation }

        /** The mode the plugin reported when the watch opened; no event. zh-CN: 监听打开时插件报告的模式; 不发事件. */
        override fun initialMode(mode: String?): Controller = apply { watch.currentMode = mode }

        override fun message(plain: Any?, decorate: (Any?) -> Any?): Boolean = watch.message(plain, decorate)

        override fun mode(mode: String): Boolean = watch.mode(mode)

        override fun resync(reason: String): Boolean = watch.resync(reason)

        override fun error(error: MailError, convert: (MailError) -> Any?): Boolean = watch.error(error, convert)

        override fun close(reason: String): Boolean = watch.close(reason)

        override fun fail(error: MailError, reason: String, convert: (MailError) -> Any?): Boolean = watch.fail(error, convert, reason)

        override fun cancelSilently(): Boolean = watch.cancelSilently()
    }

    companion object {
        const val EVENT_MESSAGE = "message"
        const val EVENT_MODE = "mode"
        const val EVENT_RESYNC = "resync"
        const val EVENT_ERROR = "error"
        const val EVENT_CLOSE = "close"

        const val STATE_ACTIVE = "active"
        const val STATE_CLOSED = "closed"

        /** `close` reasons raised on the host side; the plugin's own reasons (`stopped`, `error`, `closed`) pass through. zh-CN: 宿主侧产生的 `close` 原因; 插件自己的原因 (`stopped`, `error`, `closed`) 原样透传. */
        const val REASON_STOPPED = "stopped"
        const val REASON_ERROR = "error"
        const val REASON_CLIENT_CLOSED = "closed"
        const val REASON_EXIT = "script-exit"
        const val REASON_PLUGIN_DIED = "plugin-died"
        const val REASON_OVERFLOW = "overflow"

        /** `resync` reason after a new plugin watch replaced one lost with the plugin process: list the folder again for what arrived meanwhile. zh-CN: 新插件监听替换随插件进程丢失的监听后的 `resync` 原因: 重新列出文件夹以获取其间到达的邮件. */
        const val RESYNC_REWATCHED = "rewatched"
    }
}
