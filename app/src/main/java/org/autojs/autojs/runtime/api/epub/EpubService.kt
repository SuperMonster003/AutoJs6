package org.autojs.autojs.runtime.api.epub

import android.content.Context
import org.autojs.autojs6.R
import org.autojs.autojs.core.plugin.epub.EpubBookClient
import org.autojs.autojs.core.plugin.epub.EpubError
import org.autojs.autojs.core.plugin.epub.EpubErrorMapper
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.epub.EpubReaderSessionRunner
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-script owner of the `epub` script API (plugin roadmap P6.1 and P6.2): the books a script
 * opened through the EPUB plugin, its reader sessions and the path rules of the script's working
 * directory. [close] runs on script exit and closes every book that is still open and ends every
 * reader session without finishing the reader (plugin decision D32), so nothing of the plugin
 * outlives its script. Plugin-availability refusals (`PLUGIN_UNAVAILABLE`, `PLUGIN_DISABLED`,
 * `PLUGIN_INCOMPATIBLE`) get the localized hint of the plugin center in front of the technical
 * message.
 * zh-CN: `epub` 脚本 API 的每脚本属主 (插件路线图 P6.1 与 P6.2): 脚本经 EPUB 插件打开的书籍, 它的阅读器会话与脚本工作目录的路径规则. [close] 在脚本
 * 退出时运行, 关闭仍打开的每本书并结束每个阅读器会话而不结束阅读器 (插件决策 D32), 因此插件里的任何东西都不会比脚本活得更久. 插件可用性拒绝 (`PLUGIN_UNAVAILABLE`, `PLUGIN_DISABLED`, `PLUGIN_INCOMPATIBLE`) 在技术信息前加上
 * 插件中心的本地化提示.
 */
class EpubService(context: Context, private val scriptRuntime: ScriptRuntime) : Closeable {

    /** The application context the plugin is bound with. zh-CN: 绑定插件所用的应用上下文. */
    val appContext: Context = context.applicationContext

    private val books = ConcurrentHashMap.newKeySet<EpubBookClient>()
    private val sessions = ConcurrentHashMap.newKeySet<EpubReaderSessionRunner>()
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    /** How many books are open right now. zh-CN: 当前打开的书籍数. */
    val openBookCount: Int
        get() = books.size

    /** How many reader sessions are open right now. zh-CN: 当前打开的阅读器会话数. */
    val openSessionCount: Int
        get() = sessions.size

    /** A script path resolved against the script's working directory; absolute paths pass through. zh-CN: 相对脚本工作目录解析的脚本路径; 绝对路径原样通过. */
    fun resolvePath(path: String): String = scriptRuntime.files.path(path) ?: path

    /** Opens [path] (already resolved) in the plugin and tracks the book until it is closed. zh-CN: 在插件里打开 (已解析的) [path] 并跟踪这本书直到关闭. */
    suspend fun open(path: String, displayName: String?): EpubBookClient {
        ensureOpen()
        val client = try {
            EpubPluginHost.openBook(appContext, path, displayName)
        } catch (e: EpubPluginException) {
            throw localized(e)
        }
        books.add(client)
        if (closed.get()) {
            // The script exited while the plugin was opening the book. zh-CN: 插件打开书籍期间脚本已退出.
            books.remove(client)
            runCatching { client.close() }
            ensureOpen()
        }
        return client
    }

    /** Whether a compatible, enabled EPUB plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的 EPUB 插件应答; 从不抛出. */
    suspend fun isAvailable(): Boolean = try {
        EpubPluginHost.getCapabilities(appContext)
        true
    } catch (_: EpubPluginException) {
        false
    }

    /** The plugin-availability failures with the localized hint in front (script-facing). zh-CN: 带本地化提示前缀的插件可用性失败 (面向脚本). */
    fun localized(e: EpubPluginException): EpubPluginException {
        if (e.code !in AVAILABILITY_CODES) return e
        val hint = appContext.getString(R.string.error_epub_plugin_unavailable)
        if (e.error.message.startsWith(hint)) return e
        return EpubPluginException(EpubError(e.code, EpubErrorMapper.truncate("$hint (${e.error.message})", EpubContract.MAX_ERROR_MESSAGE_BYTES)), e)
    }

    internal fun untrack(client: EpubBookClient) {
        books.remove(client)
    }

    internal fun trackSession(runner: EpubReaderSessionRunner) {
        ensureOpen()
        sessions.add(runner)
    }

    internal fun untrackSession(runner: EpubReaderSessionRunner) {
        sessions.remove(runner)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val active = sessions.toList()
        sessions.clear()
        active.forEach { runner -> runCatching { runner.cancelSilently() } }
        val open = books.toList()
        books.clear()
        open.forEach { client -> runCatching { client.close() } }
    }

    internal fun ensureOpen() {
        if (closed.get()) {
            throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.SESSION_CLOSED, "the script is exiting"))
        }
    }

    private companion object {
        val AVAILABILITY_CODES = setOf(EpubErrorCodes.PLUGIN_UNAVAILABLE, EpubErrorCodes.PLUGIN_DISABLED, EpubErrorCodes.PLUGIN_INCOMPATIBLE)
    }
}
