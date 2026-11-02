package org.autojs.autojs.runtime.api.terminal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.core.plugin.terminal.TerminalError
import org.autojs.autojs.core.plugin.terminal.TerminalErrorMapper
import org.autojs.autojs.core.plugin.terminal.TerminalInspection
import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.core.plugin.terminal.TerminalLauncher
import org.autojs.autojs.core.plugin.terminal.TerminalOpenRequest
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.core.plugin.terminal.TerminalPluginHost
import org.autojs.autojs.core.plugin.terminal.TerminalPluginInspector
import org.autojs.autojs.core.plugin.terminal.TerminalPluginState
import org.autojs.autojs.core.plugin.terminal.TerminalPluginStateMonitor
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs6.R
import org.autojs.plugin.terminal.api.ITerminalCallback
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-script owner of the `terminal` script API (plugin roadmap P4.1 and P4.2): the Binder calls
 * of the 3-Shell Terminal plugin a script makes, the `TERMINAL_OPEN` screen starts (D19), the
 * script's working directory, and (P4.2) the plugin callback with its exit codes and overflow
 * reports, the output subscriptions with their pipe pumps, and the waits behind
 * `exec({ wait: true })` and `waitFor`. The callback is registered over a dedicated binding lease
 * the first time a script needs events, so the plugin stays bound for the script's life (D26).
 * Sessions belong to the plugin and its user, not to the script: [close] runs on script exit,
 * refuses later calls, cancels the subscriptions, drops the callback and the lease, but closes no
 * session; a session started with `keepOpen: false` ends itself when its command ends.
 * Plugin-availability refusals (`PLUGIN_UNAVAILABLE`, `PLUGIN_INCOMPATIBLE`) get the localized
 * hint of the plugin center in front of the technical message.
 * zh-CN: `terminal` 脚本 API 的每脚本属主 (插件路线图 P4.1 与 P4.2): 脚本对 3-Shell Terminal 插件发起的 Binder 调用, `TERMINAL_OPEN` 界面启动 (D19), 脚本
 * 工作目录, 以及 (P4.2) 带退出码与溢出报告的插件回调, 带管道泵的输出订阅, `exec({ wait: true })` 与 `waitFor` 背后的等待. 脚本首次需要事件时经专用绑定租约
 * 注册回调, 因此插件在脚本存续期间保持绑定 (D26). 会话属于插件及其用户而非脚本: [close] 在脚本退出时运行, 拒绝之后的调用, 取消订阅, 丢弃回调与租约, 但不
 * 关闭任何会话; 以 `keepOpen: false` 启动的会话在命令结束时自行结束. 插件可用性拒绝 (`PLUGIN_UNAVAILABLE`, `PLUGIN_INCOMPATIBLE`) 在技术信息前加上插件中心的
 * 本地化提示.
 *
 * The working directory comes from [cwdProvider], so the JS runtimes' `terminal` bridge (api-coverage P3.3) owns a service per
 * execution without a [ScriptRuntime].
 * zh-CN: 工作目录来自 [cwdProvider], 因此 JS 运行时的 `terminal` 桥接 (api-coverage P3.3) 不需要 [ScriptRuntime] 也能为每次执行持有一个服务.
 */
class TerminalService(context: Context, private val cwdProvider: () -> String?) : Closeable {

    constructor(context: Context, scriptRuntime: ScriptRuntime) : this(context, { scriptRuntime.files.cwd() })

    /** One live output subscription of this script; [cancel] ends it. zh-CN: 本脚本的一个活动输出订阅; [cancel] 结束它. */
    inner class Subscription internal constructor(val id: String, val sessionId: String, internal val pump: TerminalOutputPump) {
        val isFinished: Boolean
            get() = pump.isFinished

        fun cancel() = unsubscribe(this)
        internal fun start() = pump.start()
    }

    /** A wait for an output line whose subscription runs ([startLineWait]). zh-CN: 订阅已运行的输出行等待 ([startLineWait]). */
    inner class LineWait internal constructor(internal val sessionId: String, internal val outcome: CompletableDeferred<String?>, internal val subscription: Subscription)

    /** The application context the plugin is bound with. zh-CN: 绑定插件所用的应用上下文. */
    val appContext: Context = context.applicationContext

    private val closed = AtomicBoolean(false)

    private val callbackLock = Any()
    private val callbackMutex = Mutex()
    private var callbackLease: Closeable? = null

    private val exitLock = Any()
    private val exitCodes = LinkedHashMap<String, Int>()
    private val exitListeners = HashMap<String, MutableList<(Int) -> Unit>>()

    private val overflowListeners = ConcurrentHashMap<String, (Long) -> Unit>()
    private val subscriptions = ConcurrentHashMap<String, Subscription>()

    private val callback = object : ITerminalCallback.Stub() {
        override fun onSessionsChanged(count: Int, sessionsJson: String?) = Unit

        override fun onSessionExited(sessionId: String?, exitCode: Int) {
            val id = sessionId?.takeIf { it.isNotBlank() } ?: return
            val listeners = synchronized(exitLock) {
                exitCodes[id] = exitCode
                while (exitCodes.size > MAX_REMEMBERED_EXITS) exitCodes.remove(exitCodes.keys.first())
                exitListeners.remove(id)
            }
            listeners?.forEach { listener ->
                runCatching { listener(exitCode) }.onFailure { Log.w(TAG, "exit listener of session $id failed: ${it.message}") }
            }
        }

        override fun onOutputOverflow(sessionId: String?, subscriptionId: String?, droppedBytes: Long) {
            val listener = subscriptionId?.let(overflowListeners::get) ?: return
            runCatching { listener(droppedBytes) }.onFailure { Log.w(TAG, "overflow listener of $subscriptionId failed: ${it.message}") }
        }
    }

    val isClosed: Boolean
        get() = closed.get()

    /** How many output subscriptions are live right now. zh-CN: 当前活动的输出订阅数. */
    val subscriptionCount: Int
        get() = subscriptions.size

    /** The script's working directory, the default `cwd` of every request. zh-CN: 脚本工作目录, 每个请求的默认 `cwd`. */
    fun cwd(): String? = cwdProvider()

    /** Whether a compatible, enabled and authorized plugin answers right now; never throws. zh-CN: 当前是否有兼容, 启用且已授权的插件应答; 从不抛出. */
    suspend fun isAvailable(): Boolean = runCatching { TerminalPluginHost.isAvailable(appContext) }.getOrDefault(false)

    /**
     * The five-state name of `terminal.state()` (appendix A.1): the manifest-level inspection of
     * the host, downgraded to `unavailable` when the plugin looks fine but does not answer.
     * zh-CN: `terminal.state()` 的五态名称 (附录 A.1): 宿主的清单级检查, 插件看似正常却不应答时降为 `unavailable`.
     */
    suspend fun state(): String = withContext(Dispatchers.IO) {
        val inspected = TerminalPluginInspector.inspect(appContext).state
        val state = if (inspected == TerminalPluginState.AVAILABLE && !isAvailable()) TerminalPluginState.UNAVAILABLE else inspected
        TerminalScriptArguments.stateName(state)
    }

    suspend fun sessions(): List<TerminalJson.Session> = guarded { TerminalPluginHost.listSessions(appContext) }

    suspend fun session(id: String): TerminalJson.Session? = sessions().firstOrNull { it.id == id }

    suspend fun requireSession(id: String): TerminalJson.Session = session(id)
        ?: throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_NOT_FOUND, "no terminal session $id", id))

    /** Opens a session from the validated [request] and shows its screen when [show]. zh-CN: 以已校验的 [request] 打开会话, [show] 时显示其界面. */
    suspend fun open(request: TerminalJson.OpenRequest, show: Boolean): TerminalJson.Session {
        ensureCallback()
        val session = guarded { TerminalPluginHost.openSession(appContext, request) }
        TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED)
        if (show) show(session.id)
        return session
    }

    suspend fun close(id: String): Boolean = guarded { TerminalPluginHost.closeSession(appContext, id) }
        .also { TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED) }

    suspend fun closeAll(): Int = guarded { TerminalPluginHost.closeAllSessions(appContext) }
        .also { TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED) }

    suspend fun write(id: String, data: ByteArray) {
        guarded { TerminalPluginHost.writeInput(appContext, id, data) }
    }

    suspend fun environment(): TerminalJson.Environment = guarded { TerminalPluginHost.getEnvironment(appContext) }

    /** `transcript(maxBytes?)`: the session's screen and scrollback text. zh-CN: `transcript(maxBytes?)`: 会话的屏幕与回滚文本. */
    suspend fun transcript(id: String, maxBytes: Int): TerminalPluginHost.Transcript = guarded { TerminalPluginHost.readTranscript(appContext, id, maxBytes) }

    /**
     * Shows session [id], or the plugin's most recent session (a new one at home when none) for
     * null, through the `TERMINAL_OPEN` entry (D19). Behaves like `app.startActivity` (D34): a
     * start Android refuses, for example from the background, is `INTERNAL` with the system's reason.
     * zh-CN: 经 `TERMINAL_OPEN` 入口 (D19) 显示会话 [id], 为 null 时显示插件最近的会话 (没有时在主目录新建). 行为同 `app.startActivity` (D34):
     * Android 拒绝的启动 (例如来自后台) 为带系统原因的 `INTERNAL`.
     */
    suspend fun show(id: String?) {
        ensureOpen()
        val request = if (id == null) TerminalOpenRequest.open() else TerminalOpenRequest.session(requireSession(id).id)
        withContext(Dispatchers.IO) {
            val inspection = TerminalPluginInspector.inspect(appContext)
            if (!inspection.available) throw unavailable(inspection, "the terminal plugin is ${TerminalScriptArguments.stateName(inspection.state)}", null)
            val component = TerminalPluginInspector.resolveEntry(appContext)
                ?: throw unavailable(inspection, "the terminal plugin declares no TERMINAL_OPEN entry", null)
            val intent = TerminalLauncher.intent(component, request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                appContext.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                throw unavailable(inspection, "the terminal entry ${component.flattenToShortString()} is missing", e)
            } catch (e: SecurityException) {
                throw unavailable(inspection, "the terminal entry ${component.flattenToShortString()} refused the host", e)
            } catch (e: RuntimeException) {
                throw TerminalPluginException(
                    TerminalErrorMapper.normalize(TerminalErrorCodes.INTERNAL, "the terminal screen could not be started: ${e.message ?: e.javaClass.simpleName}", request.sessionId),
                    e,
                )
            }
        }
    }

    // ------------------------------------------------------------------ events (P4.2)

    /**
     * Registers the script's plugin callback once, over a dedicated binding lease that is held
     * until [close]; later calls are free. Must run before a session whose exit matters is opened,
     * so a fast exit is not missed.
     * zh-CN: 经专用绑定租约注册一次脚本的插件回调, 租约持有到 [close]; 之后的调用无开销. 须在打开关心退出的会话之前运行, 以免错过很快的退出.
     */
    suspend fun ensureCallback() = callbackMutex.withLock {
        ensureOpen()
        if (synchronized(callbackLock) { callbackLease != null }) return@withLock
        // Binder registration cannot be cancelled halfway: retain or release the acquired lease.
        withContext(NonCancellable) {
            val lease = guarded { TerminalPluginHost.registerCallbackLeased(appContext, callback) }
            val kept = synchronized(callbackLock) {
                if (!closed.get()) {
                    callbackLease = lease
                    true
                } else false
            }
            if (!kept) {
                runCatching { TerminalPluginHost.unregisterCallback(appContext, callback) }
                runCatching { lease.close() }
            }
        }
        ensureOpen()
    }

    /** The exit code the plugin reported for [sessionId], when this script's callback saw it. zh-CN: 插件为 [sessionId] 报告过的退出码 (本脚本的回调看到时). */
    fun exitCodeOf(sessionId: String): Int? = synchronized(exitLock) { exitCodes[sessionId] }

    /**
     * Calls [listener] once with the exit code of [sessionId]: right away when it is already known,
     * otherwise from the plugin callback's thread. Returns the handle that withdraws the listener.
     * zh-CN: 以 [sessionId] 的退出码调用 [listener] 一次: 已知时立即调用, 否则在插件回调线程上调用. 返回撤回监听器的句柄.
     */
    fun onExit(sessionId: String, listener: (Int) -> Unit): () -> Unit {
        val known = synchronized(exitLock) {
            exitCodes[sessionId] ?: run {
                exitListeners.getOrPut(sessionId) { mutableListOf() }.add(listener)
                null
            }
        }
        if (known != null) {
            listener(known)
            return {}
        }
        return {
            synchronized(exitLock) {
                exitListeners[sessionId]?.let { list ->
                    list.remove(listener)
                    if (list.isEmpty()) exitListeners.remove(sessionId)
                }
            }
        }
    }

    /**
     * Waits until [session] exited and returns its document with the exit code the plugin
     * reported through the callback; a session the plugin no longer lists without a report counts
     * as exited with the code last seen. [timeoutMillis] `0` means no limit, otherwise `TIMEOUT`.
     * zh-CN: 等待 [session] 退出并返回带插件经回调报告的退出码的文档; 插件不再列出却无报告的会话以最后所见退出码视为已退出. [timeoutMillis] 为 `0` 表示不限,
     * 否则超时为 `TIMEOUT`.
     */
    suspend fun awaitExit(session: TerminalJson.Session, timeoutMillis: Long): TerminalJson.Session {
        ensureCallback()
        exitCodeOf(session.id)?.let { return exited(session, it) }
        val reported = CompletableDeferred<Int>()
        val withdraw = onExit(session.id) { code -> reported.complete(code) }
        try {
            suspend fun poll(): TerminalJson.Session {
                var latest = session
                while (true) {
                    withTimeoutOrNull(EXIT_POLL_MILLIS) { reported.await() }?.let { return exited(latest, it) }
                    val current = session(session.id)
                    if (current == null) {
                        // Gone from the list: the exit report may still be a moment behind. zh-CN: 已不在列表: 退出报告可能稍后才到.
                        withTimeoutOrNull(EXIT_GRACE_MILLIS) { reported.await() }?.let { return exited(latest, it) }
                        return exited(latest, exitCodeOf(session.id) ?: latest.exitCode)
                    }
                    latest = current
                    if (current.isExited) return exited(current, exitCodeOf(session.id) ?: current.exitCode)
                }
            }
            if (timeoutMillis <= 0L) return poll()
            return withTimeoutOrNull(timeoutMillis) { poll() }
                ?: throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.TIMEOUT, "terminal session ${session.id} did not exit within $timeoutMillis ms", session.id))
        } finally {
            withdraw()
        }
    }

    /**
     * Subscribes to the output of [sessionId] and pumps the pipe into [reader] on a worker thread;
     * the plugin's overflow reports reach [TerminalOutputReader.noteOverflow]. [fromStart] replays
     * the transcript first.
     * zh-CN: 订阅 [sessionId] 的输出并在工作线程上把管道泵入 [reader]; 插件的溢出报告送达 [TerminalOutputReader.noteOverflow]. [fromStart] 先回放转录.
     */
    internal suspend fun subscribe(sessionId: String, fromStart: Boolean, reader: TerminalOutputReader, startImmediately: Boolean = true): Subscription {
        ensureCallback()
        // The receiver must take ownership of an acquired fd even if script exit races Binder.
        val opened = withContext(NonCancellable) { guarded { TerminalPluginHost.subscribeOutput(appContext, sessionId, fromStart) } }
        val pump = TerminalOutputPump(opened.readEnd, reader) { finished ->
            subscriptions.values.firstOrNull { it.pump === finished }?.let { ended ->
                subscriptions.remove(ended.id)
                overflowListeners.remove(ended.id)
            }
        }
        val subscription = Subscription(opened.id, sessionId, pump)
        subscriptions[opened.id] = subscription
        overflowListeners[opened.id] = reader::noteOverflow
        if (closed.get()) {
            // The script exited while the plugin was answering. zh-CN: 插件应答期间脚本已退出.
            unsubscribe(subscription)
            ensureOpen()
        }
        if (startImmediately) pump.start()
        return subscription
    }

    /** Cancels [subscription]: the plugin closes the write end (the pump ends at end of stream), then the read end is closed. zh-CN: 取消 [subscription]: 插件关闭写端 (泵在流结束时结束), 之后关闭读端. */
    fun unsubscribe(subscription: Subscription) {
        subscriptions.remove(subscription.id)
        overflowListeners.remove(subscription.id)
        subscription.pump.close()
        subscription.pump.start()
        releaseScope.launch {
            runCatching { TerminalPluginHost.unsubscribeOutput(appContext, subscription.id) }
        }
    }

    /**
     * `waitFor`: the first output line of [sessionId] that satisfies [matcher], read from a
     * subscription of its own that starts now; `TIMEOUT` after [timeoutMillis] (`0` = no limit),
     * `SESSION_CLOSED` when the session ended first.
     * zh-CN: `waitFor`: [sessionId] 自此刻起的专属订阅里第一条满足 [matcher] 的输出行; [timeoutMillis] (`0` 表示不限) 后为 `TIMEOUT`, 会话先结束则为
     * `SESSION_CLOSED`.
     */
    suspend fun waitForLine(sessionId: String, stripAnsi: Boolean, matcher: (String) -> Boolean, timeoutMillis: Long): String =
        awaitLine(startLineWait(sessionId, stripAnsi, matcher), timeoutMillis)

    /**
     * The first half of [waitForLine], for callers whose next step must not run before the wait
     * reads the output: returns once the subscription runs. [awaitLine] ends the wait; [close]
     * ends the waits left over.
     * zh-CN: [waitForLine] 的前半段, 供下一步必须晚于订阅生效的调用方使用: 订阅运行后才返回. [awaitLine] 结束等待; 遗留的等待由 [close] 结束.
     */
    suspend fun startLineWait(sessionId: String, stripAnsi: Boolean, matcher: (String) -> Boolean): LineWait {
        val outcome = CompletableDeferred<String?>()
        val reader = TerminalOutputReader(
            object : TerminalOutputReader.Sink {
                override fun onLine(line: String) {
                    if (!outcome.isCompleted && matcher(line)) outcome.complete(line)
                }

                override fun onOverflow(droppedBytes: Long) = Unit

                override fun onEnd() {
                    outcome.complete(null)
                }
            },
            stripAnsi,
        )
        return LineWait(sessionId, outcome, subscribe(sessionId, fromStart = false, reader))
    }

    /** The second half of [waitForLine]. zh-CN: [waitForLine] 的后半段. */
    suspend fun awaitLine(wait: LineWait, timeoutMillis: Long): String {
        try {
            val settled = if (timeoutMillis <= 0L) {
                wait.outcome.await()
                true
            } else {
                withTimeoutOrNull(timeoutMillis) {
                    wait.outcome.await()
                    true
                } ?: false
            }
            if (!settled) throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.TIMEOUT, "no output line matched within $timeoutMillis ms", wait.sessionId))
            return wait.outcome.await()
                ?: throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_CLOSED, "terminal session ${wait.sessionId} ended before a line matched", wait.sessionId))
        } finally {
            unsubscribe(wait.subscription)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** The plugin-availability failures with the localized hint in front (script-facing). zh-CN: 带本地化提示前缀的插件可用性失败 (面向脚本). */
    fun localized(e: TerminalPluginException): TerminalPluginException {
        if (e.code != TerminalErrorCodes.PLUGIN_UNAVAILABLE && e.code != TerminalErrorCodes.PLUGIN_INCOMPATIBLE) return e
        val hint = hint(TerminalPluginInspector.inspect(appContext)) ?: return e
        if (e.error.message.startsWith(hint)) return e
        return TerminalPluginException(TerminalError(e.code, TerminalErrorMapper.truncate("$hint (${e.error.message})", TerminalErrorMapper.MAX_MESSAGE_BYTES), e.error.sessionId), e)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val active = subscriptions.values.toList()
        subscriptions.clear()
        overflowListeners.clear()
        synchronized(exitLock) { exitListeners.clear() }
        val lease = synchronized(callbackLock) { callbackLease.also { callbackLease = null } }
        active.forEach { it.pump.close(); it.pump.start() }
        if (active.isEmpty() && lease == null) {
            return
        }
        releaseScope.launch {
            active.forEach { subscription -> runCatching { TerminalPluginHost.unsubscribeOutput(appContext, subscription.id) } }
            if (lease != null) {
                runCatching { TerminalPluginHost.unregisterCallback(appContext, callback) }
                runCatching { lease.close() }
            }
        }
    }

    internal fun ensureOpen() {
        if (closed.get()) {
            throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_CLOSED, "the script is exiting"))
        }
    }

    private fun exited(session: TerminalJson.Session, code: Int?): TerminalJson.Session =
        session.copy(alive = false, state = TerminalContract.STATE_EXITED, exitCode = code ?: session.exitCode)

    private suspend fun <T> guarded(block: suspend () -> T): T {
        ensureOpen()
        try {
            return block()
        } catch (e: TerminalPluginException) {
            throw withContext(Dispatchers.IO) { localized(e) }
        }
    }

    private fun unavailable(inspection: TerminalInspection, detail: String, cause: Throwable?): TerminalPluginException {
        val code = if (inspection.state == TerminalPluginState.INCOMPATIBLE) TerminalErrorCodes.PLUGIN_INCOMPATIBLE else TerminalErrorCodes.PLUGIN_UNAVAILABLE
        val hint = hint(inspection)
        val message = if (hint == null) detail else "$hint ($detail)"
        return TerminalPluginException(TerminalErrorMapper.normalize(code, message), cause)
    }

    /** The plugin center's wording for a plugin that is not usable, by state (same texts as the host entries). zh-CN: 插件不可用时按状态取插件中心的文案 (与宿主入口相同). */
    private fun hint(inspection: TerminalInspection): String? {
        val facts = inspection.facts
        return when (inspection.state) {
            TerminalPluginState.NOT_INSTALLED -> appContext.getString(R.string.prompt_terminal_plugin_install_required)
            TerminalPluginState.DISABLED -> appContext.getString(
                if (facts.applicationEnabled) R.string.prompt_terminal_plugin_enable_required else R.string.prompt_terminal_plugin_application_disabled,
            )
            TerminalPluginState.INCOMPATIBLE -> if (facts.requiresNewerHost) {
                appContext.getString(R.string.prompt_terminal_plugin_requires_host, facts.requiredHostVersion)
            } else {
                appContext.getString(R.string.prompt_terminal_plugin_update_required)
            }
            TerminalPluginState.AVAILABLE, TerminalPluginState.UNAVAILABLE -> null
        }
    }

    private companion object {
        // Release jobs outlive a stopped script; closing one service must not cancel a queued
        // unsubscribe while another script in the same process still owns the callback binding.
        val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val TAG = "TerminalService"
        const val EXIT_POLL_MILLIS = 250L
        const val EXIT_GRACE_MILLIS = 1_000L
        const val MAX_REMEMBERED_EXITS = 64
    }
}
