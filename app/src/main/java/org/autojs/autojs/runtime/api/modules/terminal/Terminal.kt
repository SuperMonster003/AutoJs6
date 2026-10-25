package org.autojs.autojs.runtime.api.modules.terminal

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.terminal.TerminalScriptArguments
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.mozilla.javascript.NativeObject

/**
 * The global `terminal` object (plugin roadmap P4.1, appendix A.1 and A.2), alias `$terminal`.
 * First tier, session management: `open(dir? | options?)` / `openAsync` -> `TerminalSession`,
 * `sessions()` / `sessionsAsync`, `session(id)`, `close(id)` / `closeAsync`, `closeAll()` /
 * `closeAllAsync`, `show(id?)` / `showAsync` (the `TERMINAL_OPEN` screen of the plugin, D19),
 * `state()` (`available` / `not_installed` / `disabled` / `incompatible` / `unavailable`),
 * `isAvailable()` / `isAvailableAsync()`. Second tier, visible execution: `exec(command,
 * options?)` / `execAsync`, `npm.run(script, dir?, options?)` / `npm.runAsync`, `env()` /
 * `envAsync()`, and the `TerminalError` constructor. Paths follow the script's working-directory
 * rules (`files.path`); `content://` and other URIs are refused. Argument shapes are checked in
 * [TerminalScriptArguments] (pure Kotlin); only the Rhino conversion happens here. Every method
 * needs the 3-Shell Terminal plugin: without it the call fails with `PLUGIN_UNAVAILABLE` (or
 * `PLUGIN_INCOMPATIBLE`) and the plugin center's hint.
 * zh-CN: 全局 `terminal` 对象 (插件路线图 P4.1, 附录 A.1 与 A.2), 别名 `$terminal`. 第一档会话管理: `open(dir? | options?)` / `openAsync` ->
 * `TerminalSession`, `sessions()` / `sessionsAsync`, `session(id)`, `close(id)` / `closeAsync`, `closeAll()` / `closeAllAsync`, `show(id?)` / `showAsync`
 * (插件的 `TERMINAL_OPEN` 界面, D19), `state()` (`available` / `not_installed` / `disabled` / `incompatible` / `unavailable`), `isAvailable()` /
 * `isAvailableAsync()`. 第二档可见执行: `exec(command, options?)` / `execAsync`, `npm.run(script, dir?, options?)` / `npm.runAsync`, `env()` /
 * `envAsync()`, 以及 `TerminalError` 构造器. 路径遵循脚本工作目录规则 (`files.path`); 拒绝 `content://` 等 URI. 参数形态在 [TerminalScriptArguments]
 * (纯 Kotlin) 检查; 这里只做 Rhino 转换. 每个方法都需要 3-Shell Terminal 插件: 没有它时调用以 `PLUGIN_UNAVAILABLE` (或 `PLUGIN_INCOMPATIBLE`) 失败并附
 * 插件中心的提示.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Terminal(
    private val scriptRuntime: ScriptRuntime,
    private val service: TerminalService,
) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("terminal"))

    override val selfAssignmentFunctions = listOf(
        ::open.name,
        ::openAsync.name,
        ::sessions.name,
        ::sessionsAsync.name,
        ::session.name,
        ::close.name,
        ::closeAsync.name,
        ::closeAll.name,
        ::closeAllAsync.name,
        ::show.name,
        ::showAsync.name,
        ::state.name,
        ::isAvailable.name,
        ::isAvailableAsync.name,
        ::exec.name,
        ::execAsync.name,
        ::env.name,
        ::envAsync.name,
    )

    /** `TerminalError` instances thrown by these functions reach the script unwrapped. zh-CN: 这些函数抛出的 `TerminalError` 实例不经包装到达脚本. */
    override val scriptErrorPassThroughFunctions: Set<String>
        get() = selfAssignmentFunctions.toSet()

    override val selfAssignmentProperties: List<Pair<Any, Any?>> get() = scriptProperties

    /**
     * The data properties the `terminal` script module defines on its module object, in their order (its install hook
     * defines them, roadmap P5.2).
     * zh-CN: `terminal` 脚本模块在模块对象上定义的数据属性, 按顺序 (由其 install 钩子定义, P5.2).
     */
    val scriptProperties: List<Pair<Any, Any?>> by lazy {
        listOf<Pair<Any, Any?>>(
            TerminalJsErrors.NAME to TerminalJsErrors.constructor(scriptRuntime),
            "npm" to npmObject(),
        )
    }

    // ------------------------------------------------------------------ first tier: sessions

    /** `terminal.open(dir? | options?)`: a new session, shown unless `show: false`. zh-CN: `terminal.open(dir? | options?)`: 新会话, 除非 `show: false` 否则显示. */
    @RhinoRuntimeFunctionInterface
    fun open(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 0..1) { a ->
            TerminalCalls.syncOf(scriptRuntime) { openWork(a.getOrNull(0)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun openAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 0..1) { a ->
            TerminalCalls.asyncOf(scriptRuntime) { openWork(a.getOrNull(0)) }
        }
    }

    /** `terminal.sessions()`: every session of the plugin, not only the script's. zh-CN: `terminal.sessions()`: 插件的全部会话, 不只是脚本的. */
    @RhinoRuntimeFunctionInterface
    fun sessions(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.syncOf(scriptRuntime) { sessionsWork() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun sessionsAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.asyncOf(scriptRuntime) { sessionsWork() }
        }
    }

    /** `terminal.session(id)`: the session or null. zh-CN: `terminal.session(id)`: 会话或 null. */
    @RhinoRuntimeFunctionInterface
    fun session(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            TerminalCalls.syncOf(scriptRuntime) { sessionWork(raw) }
        }
    }

    /** `terminal.close(id)`: whether a session was closed. zh-CN: `terminal.close(id)`: 是否关闭了会话. */
    @RhinoRuntimeFunctionInterface
    fun close(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            TerminalCalls.syncOf(scriptRuntime) { closeWork(raw) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun closeAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            TerminalCalls.asyncOf(scriptRuntime) { closeWork(raw) }
        }
    }

    /** `terminal.closeAll()`: how many sessions were closed. zh-CN: `terminal.closeAll()`: 关闭了多少会话. */
    @RhinoRuntimeFunctionInterface
    fun closeAll(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.syncOf(scriptRuntime) { closeAllWork() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun closeAllAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.asyncOf(scriptRuntime) { closeAllWork() }
        }
    }

    /** `terminal.show(id?)`: the plugin screen of a session, or of the most recent one. zh-CN: `terminal.show(id?)`: 某会话或最近会话的插件界面. */
    @RhinoRuntimeFunctionInterface
    fun show(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 0..1) { a ->
            TerminalCalls.syncOf(scriptRuntime) { showWork(a.getOrNull(0)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun showAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 0..1) { a ->
            TerminalCalls.asyncOf(scriptRuntime) { showWork(a.getOrNull(0)) }
        }
    }

    /** `terminal.state()`: the five-state name of the plugin (appendix A.1); never throws for a missing plugin. zh-CN: `terminal.state()`: 插件的五态名称 (附录 A.1); 插件缺失时不抛出. */
    @RhinoRuntimeFunctionInterface
    fun state(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.syncOf(scriptRuntime) { { _ -> service.state() } }
        }
    }

    /** Whether a compatible, enabled plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的插件应答; 从不抛出. */
    @RhinoRuntimeFunctionInterface
    fun isAvailable(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.syncOf(scriptRuntime) { { _ -> service.isAvailable() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun isAvailableAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.asyncOf(scriptRuntime) { { _ -> service.isAvailable() } }
        }
    }

    // ------------------------------------------------------------------ second tier: visible execution

    /** `terminal.exec(command, options?)`: runs the command in a new session (appendix A.2). zh-CN: `terminal.exec(command, options?)`: 在新会话里运行命令 (附录 A.2). */
    @RhinoRuntimeFunctionInterface
    fun exec(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            TerminalCalls.syncOf(scriptRuntime) { execWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun execAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            TerminalCalls.asyncOf(scriptRuntime) { execWork(a[0], a.getOrNull(1)) }
        }
    }

    /** `terminal.env()`: the plugin's environment document (appendix A.4). zh-CN: `terminal.env()`: 插件的环境文档 (附录 A.4). */
    @RhinoRuntimeFunctionInterface
    fun env(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.syncOf(scriptRuntime) { envWork() }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun envAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            TerminalCalls.asyncOf(scriptRuntime) { envWork() }
        }
    }

    /** Host failures and argument guards leave as `TerminalError`; script values pass unchanged. zh-CN: 宿主失败与参数守卫以 `TerminalError` 离开; 脚本值原样通过. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        TerminalJsErrors.throwInto(scriptRuntime, t)
    }

    // ------------------------------------------------------------------ internals

    /** `terminal.npm`: `run(script, dir?, options?)` and `runAsync`. zh-CN: `terminal.npm`: `run(script, dir?, options?)` 与 `runAsync`. */
    private fun npmObject(): NativeObject {
        val npm = RhinoUtils.newNativeObject(scriptRuntime.topLevelScope)
        TerminalCalls.defineFunction(scriptRuntime, npm, "run") { args ->
            ensureArgumentsLengthInRange(args, 1..3) { a ->
                TerminalCalls.syncOf(scriptRuntime) { npmRunWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
            }
        }
        TerminalCalls.defineFunction(scriptRuntime, npm, "runAsync") { args ->
            ensureArgumentsLengthInRange(args, 1..3) { a ->
                TerminalCalls.asyncOf(scriptRuntime) { npmRunWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
            }
        }
        return npm
    }

    private fun openWork(raw: Any?): TerminalWork {
        val options = TerminalScriptArguments.open(TerminalCalls.json(raw))
        val request = TerminalScriptArguments.request(options, service.cwd())
        return { _ ->
            val session = service.open(request, show = options.show)
            TerminalPromises.OnScriptThread { TerminalSessionNativeObject(scriptRuntime, service, session, replayOutput = true) }
        }
    }

    private fun sessionsWork(): TerminalWork = { _ ->
        val sessions = service.sessions()
        TerminalPromises.OnScriptThread { sessions.map { TerminalSessionNativeObject(scriptRuntime, service, it) } }
    }

    private fun sessionWork(raw: Any?): TerminalWork {
        val id = TerminalScriptArguments.sessionId(TerminalCalls.json(raw))
        return { _ ->
            val session = service.session(id)
            if (session == null) null else TerminalPromises.OnScriptThread { TerminalSessionNativeObject(scriptRuntime, service, session) }
        }
    }

    private fun closeWork(raw: Any?): TerminalWork {
        val id = TerminalScriptArguments.sessionId(TerminalCalls.json(raw))
        return { _ -> service.close(id) }
    }

    private fun closeAllWork(): TerminalWork = { _ -> service.closeAll() }

    private fun showWork(raw: Any?): TerminalWork {
        val id = TerminalScriptArguments.optionalSessionId(TerminalCalls.json(raw))
        return { _ ->
            service.show(id)
            UNDEFINED
        }
    }

    private fun execWork(command: Any?, options: Any?): TerminalWork =
        runWork(TerminalScriptArguments.exec(TerminalCalls.json(command), TerminalCalls.json(options)))

    private fun npmRunWork(script: Any?, dir: Any?, options: Any?): TerminalWork =
        runWork(TerminalScriptArguments.npmRun(TerminalCalls.json(script), TerminalCalls.json(dir), TerminalCalls.json(options)))

    /** `exec` / `npm.run`: registers the exit callback, opens with the command, then waits for the exit when `wait`. zh-CN: `exec` / `npm.run`: 登记退出回调, 带命令打开, `wait` 时再等待退出. */
    private fun runWork(exec: TerminalScriptArguments.Exec): TerminalWork {
        val request = TerminalScriptArguments.request(exec.open, service.cwd(), exec.command, exec.keepOpen)
        return { _ ->
            service.ensureCallback()
            var session = service.open(request, show = exec.open.show)
            if (exec.wait) session = service.awaitExit(session, exec.timeoutMillis)
            TerminalPromises.OnScriptThread { TerminalSessionNativeObject(scriptRuntime, service, session, exec.stripAnsi, replayOutput = true) }
        }
    }

    private fun envWork(): TerminalWork = { _ -> environmentMap(service.environment()) }

    /** The `TerminalEnvironmentInfo` shape of appendix A.4. zh-CN: 附录 A.4 的 `TerminalEnvironmentInfo` 形态. */
    private fun environmentMap(environment: TerminalJson.Environment): Map<String, Any?> = linkedMapOf(
        "home" to environment.home,
        "prefix" to environment.prefix,
        "shell" to environment.shell,
        "node" to linkedMapOf(
            "available" to environment.node.available,
            "version" to environment.node.version,
            "packageName" to environment.node.packageName,
            "pluginVersion" to environment.node.pluginVersion,
            "reason" to environment.node.reason,
        ),
        "npmRegistry" to environment.npmRegistry,
        "storageAccess" to environment.storageAccess,
        "pluginVersion" to environment.pluginVersion,
        "contractVersion" to environment.contractVersion,
    )
}
