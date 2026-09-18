package org.autojs.autojs.runtime.api.augment.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autojs.core.plugin.mail.MailJson
import org.autojs.autojs.core.plugin.mail.MailSessionClient
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsIsEmpty
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.mail.MailScriptOptions
import org.autojs.autojs.runtime.api.mail.MailScriptValues
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.mozilla.javascript.NativeObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The script-visible `MailClient` (mail roadmap P3.1, appendix A.3): `account` (the options
 * without secrets), `isConnected`, `isClosed`, `test()` / `testAsync()` and `close()`. Every
 * network method has a synchronous form that blocks the script thread inside the runtime's
 * coroutine context (so stopping the script cancels the call on the plugin side) and an `Async`
 * form that returns a Promise settled on the script thread. With `debug: true` (decision D28)
 * the redacted protocol trace the plugin sends before each result goes to `console.verbose`.
 * zh-CN: 脚本可见的 `MailClient` (邮件路线图 P3.1, 附录 A.3): `account` (不含秘密的选项), `isConnected`, `isClosed`, `test()` / `testAsync()`
 * 与 `close()`. 每个网络方法都有在运行时协程上下文里阻塞脚本线程的同步形态 (停止脚本即在插件侧取消调用) 与返回在脚本线程落定的 Promise 的
 * `Async` 形态. `debug: true` (决策 D28) 时插件在每个结果前发来的脱敏协议摘要输出到 `console.verbose`.
 */
class MailClientNativeObject(
    private val scriptRuntime: ScriptRuntime,
    private val service: MailService,
    internal val session: MailSessionClient,
    internal val options: MailScriptOptions,
) : NativeObject(), Closeable {

    private val closed = AtomicBoolean(false)

    private val progressListener: ((MailJson.Progress) -> Unit)? = if (options.debug) {
        { progress -> progress.debug?.forEach { line -> scriptRuntime.console.verbose(line) } }
    } else null

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("account", RhinoUtils.toJsValue(scriptRuntime.topLevelScope, MailScriptValues.toPlain(options.snapshot)), READONLY or PERMANENT)
        defineProperty("isConnected", { session.isOpen }, null, READONLY or PERMANENT)
        defineProperty("isClosed", { closed.get() }, null, READONLY or PERMANENT)
        defineFunction("test") { args -> ensureArgumentsIsEmpty(args) { sync { test() } } }
        defineFunction("testAsync") { args -> ensureArgumentsIsEmpty(args) { async { test() } } }
        defineFunction("close") { args ->
            ensureArgumentsIsEmpty(args) {
                close()
                UNDEFINED
            }
        }
        defineFunction("toString") { describe() }
    }

    override fun getClassName(): String = "MailClient"

    val isClosed: Boolean
        get() = closed.get()

    fun describe(): String = "MailClient { ${options.describe()} }"

    /** The `session.test` document as a plain value. zh-CN: `session.test` 文档的普通值. */
    internal suspend fun test(): Any? = MailScriptValues.toPlain(call(MailContract.OP_SESSION_TEST))

    /** One operation of the contract; the debug trace is forwarded when the account asked for it. zh-CN: 契约的一个操作; 账户要求时转发调试摘要. */
    internal suspend fun call(op: String, argsJson: String? = null): String =
        session.call(op, argsJson, onProgress = progressListener)

    /**
     * Runs [block] on the script thread until it completes and returns its outcome as a script
     * value; failures reach the script as `MailError`.
     * zh-CN: 在脚本线程上运行 [block] 直到完成并把结果作为脚本值返回; 失败以 `MailError` 到达脚本.
     */
    internal fun sync(block: suspend () -> Any?): Any? {
        try {
            ensureOpen()
            val value = runBlocking(scriptRuntime.coroutineContext) { block() }
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)
        } catch (t: Throwable) {
            MailJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** Runs [block] as a coroutine and returns the Promise of its outcome. zh-CN: 把 [block] 作为协程运行并返回其结果的 Promise. */
    internal fun async(block: suspend () -> Any?): Any {
        runCatching { ensureOpen() }.exceptionOrNull()?.let { return MailPromises.rejected(scriptRuntime, it) }
        return MailPromises.launch(scriptRuntime, block)
    }

    /** Closes the plugin session; the script sees a `MailError` when the plugin refused. zh-CN: 关闭插件会话; 插件拒绝时脚本看到 `MailError`. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        service.untrack(this)
        try {
            runBlocking(scriptRuntime.coroutineContext) { session.close() }
        } catch (t: Throwable) {
            MailJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** Drops a client whose open failed: nothing to close on the plugin side. zh-CN: 丢弃打开失败的客户端: 插件侧没有可关闭的东西. */
    internal fun discard() {
        closed.set(true)
        service.untrack(this)
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(CLOSE_ON_EXIT_TIMEOUT_MS) { session.close() } }
    }

    /** Script exit: close without the runtime context (already cancelled) and without throwing. zh-CN: 脚本退出: 不经运行时上下文 (已取消) 且不抛出地关闭. */
    internal fun closeOnExit() {
        if (!closed.compareAndSet(false, true)) return
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(CLOSE_ON_EXIT_TIMEOUT_MS) { session.close() } }
    }

    private fun ensureOpen() {
        if (closed.get()) throw MailJsErrors.failure(MailErrorCodes.SESSION_CLOSED, "mail client closed by the caller")
    }

    /** Argument guards and host failures leave every method as `MailError`. zh-CN: 参数守卫与宿主失败以 `MailError` 离开每个方法. */
    private fun defineFunction(name: String, body: (Array<out Any?>) -> Any?) {
        val guarded: (Array<out Any?>) -> Any? = { args ->
            try {
                body(args)
            } catch (t: Throwable) {
                MailJsErrors.throwInto(scriptRuntime, t)
            }
        }
        defineProperty(name, newBaseFunction(scriptRuntime.topLevelScope, name, guarded, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
    }

    private companion object {
        const val CLOSE_ON_EXIT_TIMEOUT_MS = 3_000L
    }
}
