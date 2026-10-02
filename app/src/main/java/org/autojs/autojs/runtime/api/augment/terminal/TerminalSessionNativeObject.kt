package org.autojs.autojs.runtime.api.augment.terminal

import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsLengthInRange
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.terminal.TerminalScriptArguments
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.plugin.terminal.api.TerminalContract
import org.mozilla.javascript.NativeObject
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `TerminalSession` of plugin roadmap P4.1 (appendix A.3, first part): the
 * session document as properties (`id`, `cwd`, `title`, `createdAt`, `pid`, `state`, `exitCode`;
 * refreshed by `isAlive()` and by `exec({ wait: true })`), `isAlive()`, `write(text | bytes)`,
 * `show()` and `close()` with their `Async` forms. P4.2 turns it into an EventEmitter with
 * `waitFor`, `transcript` and the `output` / `exit` / `overflow` events. The session itself
 * belongs to the plugin: the script's exit does not close it.
 * zh-CN: 插件路线图 P4.1 的脚本可见 `TerminalSession` (附录 A.3 前半): 作为属性的会话文档 (`id`, `cwd`, `title`, `createdAt`, `pid`, `state`,
 * `exitCode`; 由 `isAlive()` 与 `exec({ wait: true })` 刷新), `isAlive()`, `write(text | bytes)`, `show()` 与 `close()` 及其 `Async` 形态. P4.2 把它
 * 变成带 `waitFor`, `transcript` 与 `output` / `exit` / `overflow` 事件的 EventEmitter. 会话本身属于插件: 脚本退出不关闭它.
 */
class TerminalSessionNativeObject internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val service: TerminalService,
    initial: TerminalJson.Session,
) : NativeObject() {

    private val snapshot = AtomicReference(initial)

    /** The plugin's session id, stable for the life of the session. zh-CN: 插件的会话 id, 会话存续期间不变. */
    val id: String = initial.id

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("id", id, READONLY or PERMANENT)
        defineProperty("cwd", { snapshot.get().cwd }, null, READONLY or PERMANENT)
        defineProperty("title", { snapshot.get().title }, null, READONLY or PERMANENT)
        defineProperty("createdAt", { snapshot.get().createdAt.toDouble() }, null, READONLY or PERMANENT)
        defineProperty("pid", { snapshot.get().pid }, null, READONLY or PERMANENT)
        defineProperty("state", { snapshot.get().state }, null, READONLY or PERMANENT)
        defineProperty("exitCode", { snapshot.get().exitCode }, null, READONLY or PERMANENT)

        // Appendix A.3, in table order. zh-CN: 附录 A.3, 按表序.
        defineOp("isAlive", 0..0) { _ -> { _ -> refresh().alive } }
        defineOp("write", 1..1) { a -> writeWork(a[0]) }
        defineOp("show", 0..0) { _ -> { _ -> service.show(id); UNDEFINED } }
        defineOp("close", 0..0) { _ -> { _ -> service.close(id) } }
        defineFunction("toString") { describe() }
    }

    override fun getClassName(): String = "TerminalSession"

    /** The last document seen for this session. zh-CN: 这个会话最后看到的文档. */
    val session: TerminalJson.Session
        get() = snapshot.get()

    fun describe(): String = snapshot.get().let { "TerminalSession { id: ${it.id}, cwd: ${it.cwd}, state: ${it.state}${it.exitCode?.let { code -> ", exitCode: $code" } ?: ""} }" }

    /** Re-reads the session from the plugin; a session that is gone counts as exited. zh-CN: 从插件重读会话; 已消失的会话算作已退出. */
    internal suspend fun refresh(): TerminalJson.Session {
        val latest = service.session(id) ?: snapshot.get().copy(alive = false, state = TerminalContract.STATE_EXITED)
        snapshot.set(latest)
        return latest
    }

    internal fun update(latest: TerminalJson.Session) {
        snapshot.set(latest)
    }

    private fun writeWork(data: Any?): TerminalWork {
        val bytes = TerminalScriptArguments.input(TerminalCalls.json(data))
        return { _ ->
            service.write(id, bytes)
            UNDEFINED
        }
    }

    // ------------------------------------------------------------------ definition helpers

    /** Defines `name` (sync) and `nameAsync`; argument errors throw or reject as `TerminalError`. zh-CN: 定义 `name` (同步) 与 `nameAsync`; 参数错误以 `TerminalError` 抛出或拒绝. */
    private fun defineOp(name: String, arity: IntRange, build: (Array<Any?>) -> TerminalWork) {
        defineFunction(name) { args -> ensureArgumentsLengthInRange(args, arity) { a -> TerminalCalls.sync(scriptRuntime, build(a)) } }
        defineFunction("${name}Async") { args ->
            val work = try {
                ensureArgumentsLengthInRange(args, arity) { a -> build(a) }
            } catch (t: Throwable) {
                return@defineFunction TerminalPromises.rejected(scriptRuntime, t)
            }
            TerminalCalls.async(scriptRuntime, work)
        }
    }

    private fun defineFunction(name: String, body: (Array<out Any?>) -> Any?) =
        TerminalCalls.defineFunction(scriptRuntime, this, name, body)
}
