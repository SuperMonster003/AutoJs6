package org.autojs.autojs.runtime.api.terminal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-script owner of the `terminal` script API (plugin roadmap P4.1): the Binder calls of the
 * 3-Shell Terminal plugin a script makes, the `TERMINAL_OPEN` screen starts (D19), the exit
 * wait of `exec({ wait: true })` and the script's working directory. Sessions belong to the
 * plugin and its user, not to the script: [close] runs on script exit, refuses later calls and
 * (from P4.2 on) drops the output subscriptions, but closes no session; a session started with
 * `keepOpen: false` ends itself when its command ends. Plugin-availability refusals
 * (`PLUGIN_UNAVAILABLE`, `PLUGIN_INCOMPATIBLE`) get the localized hint of the plugin center in
 * front of the technical message.
 * zh-CN: `terminal` 脚本 API 的每脚本属主 (插件路线图 P4.1): 脚本对 3-Shell Terminal 插件发起的 Binder 调用, `TERMINAL_OPEN` 界面启动 (D19),
 * `exec({ wait: true })` 的退出等待与脚本工作目录. 会话属于插件及其用户而非脚本: [close] 在脚本退出时运行, 拒绝之后的调用并 (自 P4.2 起) 丢弃输出订阅,
 * 但不关闭任何会话; 以 `keepOpen: false` 启动的会话在命令结束时自行结束. 插件可用性拒绝 (`PLUGIN_UNAVAILABLE`, `PLUGIN_INCOMPATIBLE`) 在技术信息前加上
 * 插件中心的本地化提示.
 */
class TerminalService(context: Context, private val scriptRuntime: ScriptRuntime) : Closeable {

    /** The application context the plugin is bound with. zh-CN: 绑定插件所用的应用上下文. */
    val appContext: Context = context.applicationContext

    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    /** The script's working directory, the default `cwd` of every request. zh-CN: 脚本工作目录, 每个请求的默认 `cwd`. */
    fun cwd(): String? = scriptRuntime.files.cwd()

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
        ensureOpen()
        val session = guarded { TerminalPluginHost.openSession(appContext, request) }
        TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED)
        if (show) show(session.id)
        return session
    }

    /**
     * Waits until [session] exited and returns its last known document; a session the plugin no
     * longer lists (a `keepOpen: false` session is dropped as soon as its command ended) counts as
     * exited with the exit code last seen. [timeoutMillis] `0` means no limit, otherwise `TIMEOUT`.
     * P4.2 replaces the polling with the plugin's exit callback, which also carries the exit code.
     * zh-CN: 等待 [session] 退出并返回其最后已知文档; 插件不再列出的会话 (`keepOpen: false` 的会话在命令结束时即被移除) 以最后所见退出码视为已退出.
     * [timeoutMillis] 为 `0` 表示不限, 否则超时为 `TIMEOUT`. P4.2 以插件的退出回调取代轮询, 回调同时带退出码.
     */
    suspend fun awaitExit(session: TerminalJson.Session, timeoutMillis: Long): TerminalJson.Session {
        suspend fun poll(): TerminalJson.Session {
            var latest = session
            while (!latest.isExited) {
                latest = session(session.id) ?: return latest.copy(alive = false, state = TerminalContract.STATE_EXITED)
                if (!latest.isExited) delay(EXIT_POLL_MILLIS)
            }
            return latest
        }
        if (timeoutMillis <= 0L) return poll()
        return withTimeoutOrNull(timeoutMillis) { poll() }
            ?: throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.TIMEOUT, "terminal session ${session.id} did not exit within $timeoutMillis ms", session.id))
    }

    suspend fun close(id: String): Boolean = guarded { TerminalPluginHost.closeSession(appContext, id) }
        .also { TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED) }

    suspend fun closeAll(): Int = guarded { TerminalPluginHost.closeAllSessions(appContext) }
        .also { TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED) }

    suspend fun write(id: String, data: ByteArray) {
        guarded { TerminalPluginHost.writeInput(appContext, id, data) }
    }

    suspend fun environment(): TerminalJson.Environment = guarded { TerminalPluginHost.getEnvironment(appContext) }

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

    /** The plugin-availability failures with the localized hint in front (script-facing). zh-CN: 带本地化提示前缀的插件可用性失败 (面向脚本). */
    fun localized(e: TerminalPluginException): TerminalPluginException {
        if (e.code != TerminalErrorCodes.PLUGIN_UNAVAILABLE && e.code != TerminalErrorCodes.PLUGIN_INCOMPATIBLE) return e
        val hint = hint(TerminalPluginInspector.inspect(appContext)) ?: return e
        if (e.error.message.startsWith(hint)) return e
        return TerminalPluginException(TerminalError(e.code, TerminalErrorMapper.truncate("$hint (${e.error.message})", TerminalErrorMapper.MAX_MESSAGE_BYTES), e.error.sessionId), e)
    }

    override fun close() {
        closed.compareAndSet(false, true)
    }

    internal fun ensureOpen() {
        if (closed.get()) {
            throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_CLOSED, "the script is exiting"))
        }
    }

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
        const val EXIT_POLL_MILLIS = 250L
    }
}
