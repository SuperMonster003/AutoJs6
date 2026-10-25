package org.autojs.autojs.runtime.api.modules.terminal

import androidx.annotation.GuardedBy
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptDispatchQueueOverflowException
import org.autojs.autojs.runtime.api.terminal.TerminalOutputReader
import org.autojs.autojs.runtime.api.terminal.TerminalScriptArguments
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.mozilla.javascript.Callable
import org.mozilla.javascript.regexp.NativeRegExp
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `TerminalSession` (plugin roadmap P4.1 and P4.2, appendix A.3): an
 * `EventEmitter` with the properties `id`, `cwd`, `title`, `createdAt`, `pid`, `state` and
 * `exitCode`, the methods `isAlive()`, `write(text | bytes)`, `show()`, `close()`,
 * `waitFor(pattern, timeout?)` (RegExp or substring, the matching line, `TIMEOUT` otherwise),
 * `transcript(maxBytes?)` with their `Async` forms, and the events `output(line)`, `exit(code)`
 * and `overflow(droppedBytes)`. The plugin's output is subscribed when the first `output` or
 * `overflow` listener arrives and released when the last one leaves; events reach the listeners
 * on the script thread through a [TerminalDispatch] that keeps the script alive while listeners
 * exist; `exit` is the terminal event, delivered after the output pipe drained, and nothing
 * follows it. A session this script opened (`open`, `exec`, `npm.run`) replays what it printed
 * before the first listener arrived, so no early line is lost; a session taken from `sessions()`
 * or `session(id)` starts at the present. A script that does not drain its events has lines
 * dropped on the host side and reported as `overflow` too, at most once a second. The session
 * itself belongs to the plugin: the script's exit does not close it.
 * zh-CN: 脚本可见的 `TerminalSession` (插件路线图 P4.1 与 P4.2, 附录 A.3): 带属性 `id`, `cwd`, `title`, `createdAt`, `pid`, `state` 与 `exitCode`, 方法
 * `isAlive()`, `write(text | bytes)`, `show()`, `close()`, `waitFor(pattern, timeout?)` (RegExp 或子串, 返回匹配行, 否则 `TIMEOUT`), `transcript(maxBytes?)`
 * 及其 `Async` 形态, 以及事件 `output(line)`, `exit(code)` 与 `overflow(droppedBytes)` 的 `EventEmitter`. 第一个 `output` 或 `overflow` 监听器到来时订阅插件
 * 输出, 最后一个离开时释放; 事件经让脚本在有监听器期间保持存活的 [TerminalDispatch] 回到脚本线程; `exit` 是终结事件, 在输出管道排空后投递, 之后再无事件.
 * 本脚本打开的会话 (`open`, `exec`, `npm.run`) 会回放第一个监听器到来之前已打印的内容, 早期的行不会丢失; 取自 `sessions()` 或 `session(id)` 的会话从当下开始.
 * 不消费事件的脚本会在宿主侧丢行并同样以 `overflow` 报告, 每秒至多一次. 会话本身属于插件: 脚本退出不关闭它.
 */
class TerminalSessionNativeObject internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val service: TerminalService,
    initial: TerminalJson.Session,
    private val stripAnsi: Boolean = true,
    private val replayOutput: Boolean = false,
) : EventEmitter(scriptRuntime.bridges) {

    private val snapshot = AtomicReference(initial)
    private val eventLock = Any()

    @GuardedBy("eventLock")
    private var dispatch: TerminalDispatch? = null

    @GuardedBy("eventLock")
    private var subscription: TerminalService.Subscription? = null

    @GuardedBy("eventLock")
    private var withdrawExitListener: (() -> Unit)? = null

    @GuardedBy("eventLock")
    private var outputOpen = false

    @GuardedBy("eventLock")
    private var subscribing = false

    @GuardedBy("eventLock")
    private var generation = 0L

    @GuardedBy("eventLock")
    private var replayPending = replayOutput

    @GuardedBy("eventLock")
    private var exitDelivered = false

    @GuardedBy("eventLock")
    private var pendingHostDrop = 0L

    @GuardedBy("eventLock")
    private var hostDropReportedAt = 0L

    @Volatile
    private var knownExitCode: Int? = initial.exitCode

    /** The plugin's session id, stable for the life of the session. zh-CN: 插件的会话 id, 会话存续期间不变. */
    val id: String = initial.id

    val cwd: String
        get() = snapshot.get().cwd

    val title: String
        get() = snapshot.get().title

    val createdAt: Double
        get() = snapshot.get().createdAt.toDouble()

    val pid: Int
        get() = snapshot.get().pid

    val state: String
        get() = if (knownExitCode != null) TerminalContract.STATE_EXITED else snapshot.get().state

    /** The exit code once the plugin reported it (through `exec({ wait })`, `isAlive()` or the callback), else null. zh-CN: 插件报告后的退出码 (经 `exec({ wait })`, `isAlive()` 或回调), 否则为 null. */
    val exitCode: Int?
        get() = knownExitCode ?: service.exitCodeOf(id)?.also { knownExitCode = it }

    /** The last document seen for this session. zh-CN: 这个会话最后看到的文档. */
    val session: TerminalJson.Session
        get() = snapshot.get()

    // ------------------------------------------------------------------ methods (appendix A.3)

    fun isAlive(): Boolean = guarded {
        if (exitCode != null) false else sync { refresh().alive }
    }

    fun write(data: Any?): TerminalSessionNativeObject = guarded {
        val bytes = TerminalScriptArguments.input(TerminalCalls.json(data))
        sync { service.write(id, bytes) }
        this
    }

    fun writeAsync(data: Any?): Any = TerminalCalls.asyncOf(scriptRuntime) {
        val bytes = TerminalScriptArguments.input(TerminalCalls.json(data))
        val work: TerminalWork = { _ ->
            service.write(id, bytes)
            UNDEFINED
        }
        work
    }

    fun show(): TerminalSessionNativeObject = guarded {
        sync { service.show(id) }
        this
    }

    fun showAsync(): Any = TerminalCalls.async(scriptRuntime) { _ ->
        service.show(id)
        UNDEFINED
    }

    fun close(): Boolean = guarded { sync { service.close(id) } }

    fun closeAsync(): Any = TerminalCalls.async(scriptRuntime) { _ -> service.close(id) }

    /** `waitFor(pattern, timeout?)`: the first output line from now on that matches; `output` listeners keep firing meanwhile. zh-CN: `waitFor(pattern, timeout?)`: 自此刻起第一条匹配的输出行; 期间 `output` 监听器照常触发. */
    @JvmOverloads
    fun waitFor(pattern: Any?, timeout: Any? = null): String = guarded {
        val matcher = matcher(pattern)
        val timeoutMillis = TerminalScriptArguments.waitTimeout(TerminalCalls.json(timeout))
        sync { service.waitForLine(id, stripAnsi, matcher, timeoutMillis) }
    }

    @JvmOverloads
    fun waitForAsync(pattern: Any?, timeout: Any? = null): Any = TerminalCalls.asyncOf(scriptRuntime) {
        val matcher = matcher(pattern)
        val timeoutMillis = TerminalScriptArguments.waitTimeout(TerminalCalls.json(timeout))
        val work: TerminalWork = { _ -> service.waitForLine(id, stripAnsi, matcher, timeoutMillis) }
        work
    }

    /** `transcript(maxBytes?)`: the screen and scrollback text, at most `MAX_TRANSCRIPT_BYTES`. zh-CN: `transcript(maxBytes?)`: 屏幕与回滚文本, 至多 `MAX_TRANSCRIPT_BYTES`. */
    @JvmOverloads
    fun transcript(maxBytes: Any? = null): String = guarded {
        val limit = TerminalScriptArguments.transcriptMaxBytes(TerminalCalls.json(maxBytes))
        sync { service.transcript(id, limit).text }
    }

    @JvmOverloads
    fun transcriptAsync(maxBytes: Any? = null): Any = TerminalCalls.asyncOf(scriptRuntime) {
        val limit = TerminalScriptArguments.transcriptMaxBytes(TerminalCalls.json(maxBytes))
        val work: TerminalWork = { _ -> service.transcript(id, limit).text }
        work
    }

    override fun toString(): String = snapshot.get().let { doc ->
        buildString {
            append("TerminalSession { id: ").append(doc.id).append(", cwd: ").append(doc.cwd).append(", state: ").append(state)
            exitCode?.let { append(", exitCode: ").append(it) }
            append(" }")
        }
    }

    // ------------------------------------------------------------------ listeners

    override fun on(eventName: String, listener: Callable): TerminalSessionNativeObject {
        super.on(eventName, listener)
        listenersChanged()
        return this
    }

    override fun once(eventName: String, listener: Callable): TerminalSessionNativeObject {
        super.once(eventName, listener)
        listenersChanged()
        return this
    }

    override fun prependListener(eventName: String, listener: Callable): TerminalSessionNativeObject {
        super.prependListener(eventName, listener)
        listenersChanged()
        return this
    }

    override fun prependOnceListener(eventName: String, listener: Callable): TerminalSessionNativeObject {
        super.prependOnceListener(eventName, listener)
        listenersChanged()
        return this
    }

    override fun removeListener(eventName: String, listener: Callable): TerminalSessionNativeObject {
        super.removeListener(eventName, listener)
        listenersChanged()
        return this
    }

    fun off(eventName: String, listener: Callable): TerminalSessionNativeObject = removeListener(eventName, listener)

    override fun emit(eventName: String, vararg args: Any?): Boolean = try {
        super.emit(eventName, *args)
    } finally {
        // EventEmitter removes once listeners before invocation without calling removeListener.
        if (eventName == EVENT_OUTPUT || eventName == EVENT_OVERFLOW) listenersChanged()
    }

    override fun removeAllListeners(): TerminalSessionNativeObject {
        super.removeAllListeners()
        listenersChanged()
        return this
    }

    override fun removeAllListeners(eventName: String): TerminalSessionNativeObject {
        super.removeAllListeners(eventName)
        listenersChanged()
        return this
    }

    /** Must run on the script thread (listeners are added and removed there). zh-CN: 须在脚本线程上运行 (监听器在那里增删). */
    private fun listenersChanged() = guarded {
        val wantsOutput = listenerCount(EVENT_OUTPUT) > 0 || listenerCount(EVENT_OVERFLOW) > 0
        val wantsAny = wantsOutput || listenerCount(EVENT_EXIT) > 0
        if (!wantsAny) {
            teardown()
            return@guarded
        }
        val connect = synchronized(eventLock) { !exitDelivered && dispatch == null }
        if (connect) sync { service.ensureCallback() }
        synchronized(eventLock) {
            if (exitDelivered) return@guarded
            if (wantsAny && dispatch == null) {
                dispatch = TerminalPromises.dispatch(scriptRuntime) { teardown() }
                outputOpen = wantsOutput
                withdrawExitListener = service.onExit(id) { code -> onExited(code) }
            }
        }
        if (wantsOutput) ensureSubscribed() else dropSubscription()
    }

    private fun sink(token: Long) = object : TerminalOutputReader.Sink {
        override fun onLine(line: String) {
            if (synchronized(eventLock) { token == generation }) deliver(EVENT_OUTPUT, line, line.toByteArray(Charsets.UTF_8).size.toLong())
        }

        override fun onOverflow(droppedBytes: Long) {
            if (synchronized(eventLock) { token == generation }) deliver(EVENT_OVERFLOW, droppedBytes, droppedBytes)
        }

        override fun onEnd() {
            synchronized(eventLock) {
                if (token != generation) return
                outputOpen = false
                subscription = null
            }
            synchronized(eventLock) { dispatch }?.let { flushHostDrop(it, force = true) }
            maybeDeliverExit()
        }
    }

    private fun ensureSubscribed() {
        val token = synchronized(eventLock) {
            if (subscription != null || subscribing || exitDelivered) return
            subscribing = true
            outputOpen = true
            ++generation
        }
        val reader = TerminalOutputReader(sink(token), stripAnsi)
        val created = try {
            val fromStart = synchronized(eventLock) { replayPending }
            sync { service.subscribe(id, fromStart, reader, startImmediately = false) }
        } catch (e: TerminalPluginException) {
            synchronized(eventLock) {
                if (token == generation) { subscribing = false; outputOpen = false }
            }
            if (e.code == TerminalErrorCodes.SESSION_CLOSED || e.code == TerminalErrorCodes.SESSION_NOT_FOUND) {
                // The session is already over: its exit arrives (or is known) through the callback. zh-CN: 会话已结束: 其退出经回调到来 (或已知).
                knownExitCode = knownExitCode ?: service.exitCodeOf(id)
                maybeDeliverExit()
                return
            }
            teardown()
            throw e
        }
        val keep = synchronized(eventLock) {
            if (token == generation && dispatch != null && !exitDelivered && (listenerCount(EVENT_OUTPUT) > 0 || listenerCount(EVENT_OVERFLOW) > 0)) {
                subscribing = false
                subscription = created
                replayPending = false
                true
            } else {
                false
            }
        }
        if (keep) created.start() else created.cancel()
    }

    private fun dropSubscription() {
        val active = synchronized(eventLock) {
            subscription.also {
                subscription = null
                outputOpen = false
                subscribing = false
                generation++
            }
        }
        active?.cancel()
        maybeDeliverExit()
    }

    /** Pump or callback thread: hands [value] to the script thread; a full queue drops the line and counts it as host-side overflow. zh-CN: 泵或回调线程: 把 [value] 交给脚本线程; 队列已满时丢弃该行并计入宿主侧溢出. */
    private fun deliver(event: String, value: Any, bytes: Long) {
        val current = synchronized(eventLock) { dispatch } ?: return
        flushHostDrop(current, force = false)
        try {
            current.dispatcher.dispatchValues(arrayOf(value)) { mapped -> emit(event, mapped[0]) }
        } catch (_: ScriptDispatchQueueOverflowException) {
            synchronized(eventLock) { pendingHostDrop += bytes }
        }
    }

    /** Reports the bytes dropped on the host side as one `overflow` event, at most once per [HOST_DROP_REPORT_MILLIS] unless [force]. zh-CN: 把宿主侧丢弃的字节作为一个 `overflow` 事件报告, 除非 [force], 否则每 [HOST_DROP_REPORT_MILLIS] 至多一次. */
    private fun flushHostDrop(current: TerminalDispatch, force: Boolean) {
        val backlog = synchronized(eventLock) {
            val now = System.currentTimeMillis()
            if (pendingHostDrop <= 0L || (!force && now - hostDropReportedAt < HOST_DROP_REPORT_MILLIS)) return
            hostDropReportedAt = now
            pendingHostDrop.also { pendingHostDrop = 0L }
        }
        try {
            current.dispatcher.dispatchValues(arrayOf(backlog)) { mapped -> emit(EVENT_OVERFLOW, mapped[0]) }
        } catch (_: ScriptDispatchQueueOverflowException) {
            synchronized(eventLock) { pendingHostDrop += backlog }
        }
    }

    private fun onExited(code: Int) {
        knownExitCode = code
        snapshot.updateAndGet { it.copy(alive = false, exitCode = code, state = TerminalContract.STATE_EXITED) }
        maybeDeliverExit()
    }

    /** `exit` is delivered once the code is known and the output pipe (if any) drained; it ends the dispatcher. zh-CN: 退出码已知且输出管道 (若有) 排空后投递 `exit`; 它结束分发器. */
    private fun maybeDeliverExit() {
        val (current, code) = synchronized(eventLock) {
            val known = knownExitCode
            val target = dispatch
            if (exitDelivered || known == null || outputOpen || target == null) return
            exitDelivered = true
            dispatch = null
            withdrawExitListener?.invoke()
            withdrawExitListener = null
            target to known
        }
        flushHostDrop(current, force = true)
        try {
            current.dispatcher.terminalValues(arrayOf(code)) { mapped ->
                try {
                    val remaining = synchronized(eventLock) { pendingHostDrop.also { pendingHostDrop = 0L } }
                    if (remaining > 0) emit(EVENT_OVERFLOW, remaining)
                    emit(EVENT_EXIT, mapped[0])
                } finally {
                    current.finish()
                }
            }
        } catch (_: Throwable) {
            current.close()
        }
    }

    /** Script exit or no listener left: stop everything without a script-visible event. zh-CN: 脚本退出或再无监听器: 停止一切, 不产生脚本可见事件. */
    private fun teardown() {
        val (current, active, withdraw) = synchronized(eventLock) {
            Triple(dispatch, subscription, withdrawExitListener).also {
                dispatch = null
                subscription = null
                withdrawExitListener = null
                outputOpen = false
                subscribing = false
                generation++
            }
        }
        withdraw?.invoke()
        active?.cancel()
        current?.close()
    }

    // ------------------------------------------------------------------ internals

    internal suspend fun refresh(): TerminalJson.Session {
        val latest = service.session(id) ?: snapshot.get().copy(alive = false, state = TerminalContract.STATE_EXITED, exitCode = exitCode)
        snapshot.set(latest)
        latest.exitCode?.let { knownExitCode = it }
        return latest
    }

    private fun matcher(pattern: Any?): (String) -> Boolean = when (pattern) {
        is NativeRegExp -> TerminalScriptArguments.lineMatcher(pattern.toString(), null)
        is CharSequence -> TerminalScriptArguments.lineMatcher(null, pattern.toString())
        else -> throw TerminalScriptArguments.invalid("'pattern' must be a string or a RegExp, got ${pattern.jsBrief()}")
    }

    private fun <T> sync(block: suspend () -> T): T = TerminalPromises.sync(scriptRuntime, block)

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        TerminalJsErrors.throwInto(scriptRuntime, t)
    }

    companion object {
        const val EVENT_OUTPUT = "output"
        const val EVENT_EXIT = "exit"
        const val EVENT_OVERFLOW = "overflow"
        private const val HOST_DROP_REPORT_MILLIS = 1_000L
    }
}
