package org.autojs.autojs.runtime.api.speech

import android.content.Context
import org.autojs.autojs.core.plugin.speech.SpeechErrorMapper
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.plugin.speech.api.SpeechErrorCodes
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-script owner of the `speech` script API (3-Macaw Speech roadmap P1.4): the dictation and wake sessions a script
 * opened through the speech plugin and the path rules of the script's working directory. [close] runs on script exit
 * and cancels every session that is still open (the plugin releases the microphone and the binding ends), so nothing of
 * the plugin outlives its script. Recognized text is never written to the host log (roadmap appendix D Q8 (a)).
 * zh-CN: `speech` 脚本 API 的每脚本属主 (3-Macaw Speech 路线图 P1.4): 脚本经语音插件打开的听写与唤醒会话, 以及脚本工作目录的路径规则. [close]
 * 在脚本退出时运行, 取消仍打开的每个会话 (插件释放麦克风, 绑定结束), 因此插件里的任何东西都不会比脚本活得更久. 识别文本从不写入宿主日志
 * (路线图附录 D Q8 (a)).
 */
class SpeechService(context: Context, private val scriptRuntime: ScriptRuntime) : Closeable {

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
    fun resolvePath(path: String): String = scriptRuntime.files.path(path) ?: path

    fun resolveFile(path: String): File = File(resolvePath(path))

    /** Whether a compatible, enabled speech plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的语音插件应答; 从不抛出. */
    suspend fun isAvailable(): Boolean = SpeechPluginHost.isAvailable(appContext)

    /** Runs a host-client call unless the script is exiting. zh-CN: 除非脚本正在退出, 否则运行宿主客户端调用. */
    internal suspend fun <T> host(block: suspend (Context) -> T): T {
        ensureOpen()
        return block(appContext)
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
            throw SpeechErrorMapper.failure(SpeechErrorCodes.SESSION_CLOSED, "the script is exiting")
        }
    }
}
