package org.autojs.autojs.runtime.api.gallery

import android.content.Context
import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryErrorMapper
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.autojs.core.plugin.gallery.GalleryPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs6.R
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-script owner of the `gallery` script API (3-Maple Image roadmap M6, 10.1): the sessions a
 * script opened through the gallery plugin (pickers and editors it waits for, viewers it follows)
 * and the path rules of the script's working directory. [close] runs on script exit: waiting
 * pickers and editors are finished (their outputs are discarded), followed viewers end without
 * closing the viewer the user is looking at. Plugin-availability refusals (`PLUGIN_UNAVAILABLE`,
 * `PLUGIN_DISABLED`, `PLUGIN_INCOMPATIBLE`) get the localized hint of the Plugin Center in front of
 * the technical message.
 * zh-CN: `gallery` 脚本 API 的每脚本属主 (3-Maple Image 路线图 M6, 10.1): 脚本经相册插件打开的会话 (等待中的选择器与编辑器, 跟随中的查看器) 与脚本
 * 工作目录的路径规则. [close] 在脚本退出时运行: 等待中的选择器与编辑器被结束 (其输出被丢弃), 跟随中的查看器结束会话但不关闭用户正在看的查看器.
 * 插件可用性拒绝 (`PLUGIN_UNAVAILABLE`, `PLUGIN_DISABLED`, `PLUGIN_INCOMPATIBLE`) 在技术信息前加上插件中心的本地化提示.
 */
class GalleryService(context: Context, private val resolver: (String) -> String) : Closeable {

    constructor(context: Context, scriptRuntime: ScriptRuntime) : this(context, { path -> scriptRuntime.files.path(path) ?: path })

    /** A session the service ends on script exit. zh-CN: 脚本退出时由服务结束的会话. */
    internal fun interface Task {
        fun cancelSilently()
    }

    /** The application context the plugin is bound with. zh-CN: 绑定插件所用的应用上下文. */
    val appContext: Context = context.applicationContext

    private val tasks = ConcurrentHashMap.newKeySet<Task>()
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    /** How many sessions are open right now. zh-CN: 当前打开的会话数. */
    val openSessionCount: Int
        get() = tasks.size

    /** A script path resolved against the script's working directory; absolute paths pass through. zh-CN: 相对脚本工作目录解析的脚本路径; 绝对路径原样通过. */
    fun resolvePath(path: String): String = resolver(path)

    /** Whether a compatible, enabled gallery plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的相册插件应答; 从不抛出. */
    suspend fun isAvailable(): Boolean = GalleryPluginHost.isAvailable(appContext)

    /** Runs a host-client call; availability failures leave with the localized hint. zh-CN: 运行宿主客户端调用; 可用性失败带本地化提示离开. */
    internal suspend fun <T> host(block: suspend (Context) -> T): T {
        ensureOpen()
        return try {
            block(appContext)
        } catch (e: GalleryPluginException) {
            throw localized(e)
        }
    }

    /** The plugin-availability failures with the localized hint in front (script-facing). zh-CN: 带本地化提示前缀的插件可用性失败 (面向脚本). */
    fun localized(e: GalleryPluginException): GalleryPluginException {
        if (e.code !in AVAILABILITY_CODES) return e
        val hint = appContext.getString(R.string.error_gallery_plugin_unavailable)
        if (e.error.message.startsWith(hint)) return e
        return GalleryPluginException(GalleryError(e.code, GalleryErrorMapper.truncate("$hint (${e.error.message})", GalleryContract.MAX_ERROR_MESSAGE_BYTES)), e)
    }

    internal fun track(task: Task) {
        ensureOpen()
        tasks.add(task)
        if (closed.get() && tasks.remove(task)) {
            // The script exited while the session was registering. zh-CN: 会话注册期间脚本已退出.
            runCatching { task.cancelSilently() }
            ensureOpen()
        }
    }

    internal fun untrack(task: Task) {
        tasks.remove(task)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val active = tasks.toList()
        tasks.clear()
        active.forEach { task -> runCatching { task.cancelSilently() } }
    }

    internal fun ensureOpen() {
        if (closed.get()) {
            throw GalleryPluginException(GalleryErrorMapper.normalize(GalleryErrorCodes.SESSION_CLOSED, "the script is exiting"))
        }
    }

    private companion object {
        val AVAILABILITY_CODES = setOf(GalleryErrorCodes.PLUGIN_UNAVAILABLE, GalleryErrorCodes.PLUGIN_DISABLED, GalleryErrorCodes.PLUGIN_INCOMPATIBLE)
    }
}
