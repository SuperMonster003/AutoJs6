package org.autojs.autojs.runtime.api.mail

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.mail.MailErrorMapper
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.mail.MailClientNativeObject
import org.autojs.plugin.mail.api.MailErrorCodes
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-script owner of the `mail` script API (mail roadmap P3.1): the clients a script opened,
 * the default client behind the global convenience methods, the provider catalog and the saved
 * account list of the plugin. [close] runs on script exit and closes every client that is
 * still open, so a plugin session never outlives its script.
 * zh-CN: `mail` 脚本 API 的每脚本属主 (邮件路线图 P3.1): 脚本打开的客户端, 全局便捷方法背后的默认客户端, 插件的服务商目录与已保存账户列表.
 * [close] 在脚本退出时运行并关闭仍打开的每个客户端, 因此插件会话不会比脚本活得更久.
 */
class MailService(context: Context, private val scriptRuntime: ScriptRuntime) : Closeable {

    private val appContext: Context = context.applicationContext
    private val clients = ConcurrentHashMap.newKeySet<MailClientNativeObject>()
    private val closed = AtomicBoolean(false)
    private val providersDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MailProviderCatalog(fetch { MailPluginHost.listProviders(appContext) })
    }

    /** The client behind `mail.default` and the global methods, or null. zh-CN: `mail.default` 与全局方法背后的客户端, 或 null. */
    @Volatile
    var defaultClient: MailClientNativeObject? = null
        private set

    val isClosed: Boolean
        get() = closed.get()

    /** How many clients are open right now. zh-CN: 当前打开的客户端数. */
    val openClientCount: Int
        get() = clients.size

    /** The plugin's preset table, fetched once per script. zh-CN: 插件的预设表, 每脚本取一次. */
    fun providers(): MailProviderCatalog = providersDelegate.value

    /** The saved accounts of the plugin without secrets, fetched on every call. zh-CN: 插件的已保存账户 (无秘密), 每次调用都取. */
    fun savedAccounts(): List<Any?> {
        val accounts = MailScriptValues.toPlain(fetch { MailPluginHost.listSavedAccounts(appContext) })
        return (accounts as? List<*>)?.toList().orEmpty()
    }

    /**
     * A client for [options]; nothing is bound until the client opens. [tokenProvider] is called
     * on a background thread when the plugin answers `AUTH_FAILED` on a token account (decision D4).
     * zh-CN: [options] 的客户端; 客户端打开前不绑定任何东西. 令牌账户收到 `AUTH_FAILED` 时在后台线程调用 [tokenProvider] (决策 D4).
     */
    fun newClient(options: MailScriptOptions, tokenProvider: (suspend () -> String)?): MailClientNativeObject {
        ensureOpen()
        val session = MailPluginHost.newClient(appContext, options.spec, tokenProvider)
        val client = MailClientNativeObject(scriptRuntime, this, session, options)
        clients.add(client)
        return client
    }

    /** Makes [client] the default; it must be one of this script's open clients. zh-CN: 把 [client] 设为默认; 它必须是本脚本打开着的客户端之一. */
    fun setDefault(client: MailClientNativeObject) {
        ensureOpen()
        if (client !in clients) {
            throw MailPluginException(MailErrorMapper.normalize(MailErrorCodes.INVALID_ARGUMENT, "the client is closed or belongs to another script", retryable = false))
        }
        defaultClient = client
    }

    internal fun untrack(client: MailClientNativeObject) {
        clients.remove(client)
        if (defaultClient === client) defaultClient = null
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val open = clients.toList()
        clients.clear()
        defaultClient = null
        open.forEach { client -> runCatching { client.closeOnExit() } }
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw MailPluginException(MailErrorMapper.normalize(MailErrorCodes.SESSION_CLOSED, "the script is exiting", retryable = false))
        }
    }

    /** Metadata calls block the script thread inside the runtime's coroutine context, so a stopped script cancels them. zh-CN: 元数据调用在运行时协程上下文里阻塞脚本线程, 脚本停止即取消. */
    private fun <T> fetch(block: suspend () -> T): T = try {
        runBlocking(scriptRuntime.coroutineContext) { block() }
    } catch (e: MailPluginException) {
        throw e
    } catch (t: Throwable) {
        throw MailPluginException(MailErrorMapper.fromThrowable(t, MailErrorMapper.Phase.DISCOVERY), t)
    }
}
