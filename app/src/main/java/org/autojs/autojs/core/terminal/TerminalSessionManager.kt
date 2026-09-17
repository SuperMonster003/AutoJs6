package org.autojs.autojs.core.terminal

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide registry of terminal sessions so an activity can be recreated (rotation, theme
 * change) or left and re-entered without losing the running shell. While any session exists,
 * [TerminalSessionService] keeps the process in the foreground.
 *
 * zh-CN: 进程级终端会话注册表, 使 Activity 重建 (旋转, 主题切换) 或离开再进入时不丢失正在运行的 shell.
 * 只要存在会话, [TerminalSessionService] 就让进程保持前台.
 */
object TerminalSessionManager {

    class Session internal constructor(
        val id: String,
        val pty: TerminalPtySession,
        val initialDirectory: String,
        val paths: TerminalPaths,
        val createdAt: Long = System.currentTimeMillis(),
    )

    private val nextId = AtomicLong(1)
    private val sessions = LinkedHashMap<String, Session>()
    private val listeners = ArrayList<() -> Unit>()

    val activeSessions: List<Session>
        @Synchronized get() = sessions.values.filter { it.pty.isProcessAlive }

    val hasSessions: Boolean
        @Synchronized get() = sessions.values.any { it.pty.isProcessAlive }

    @Synchronized
    fun get(id: String?): Session? = id?.let(sessions::get)

    /**
     * Prepares the on-disk layout, builds the environment and starts an interactive shell in [directory].
     * zh-CN: 准备磁盘目录布局, 构建环境变量, 并在 [directory] 启动交互式 shell.
     */
    fun create(context: Context, directory: String, extraEnvironment: Map<String, String?> = emptyMap()): Session {
        val paths = TerminalPaths.of(context).ensureLayout()
        val environment = TerminalEnvironment.build(buildSpec(paths, extraEnvironment))
        val command = TerminalSessionLauncher.buildCommand(directory)
        val pty = TerminalPtySession(command, environment)
        val session = Session(nextId.getAndIncrement().toString(), pty, directory, paths)
        synchronized(this) { sessions[session.id] = session }
        pty.setFinishCallback { remove(session.id) }
        pty.onProcessReaped = { remove(session.id) }
        pty.start()
        TerminalSessionService.ensureStarted(context)
        notifyChanged()
        return session
    }

    fun buildSpec(paths: TerminalPaths, extraEnvironment: Map<String, String?> = emptyMap()) = TerminalEnvironment.Spec(
        home = paths.home.path,
        prefix = paths.prefix.path,
        tmpDir = paths.tmp.path,
        profile = paths.profile.path,
        pathPrepend = listOf(paths.bin.path),
        extras = extraEnvironment,
    )

    fun close(id: String) {
        val session = synchronized(this) { sessions[id] } ?: return
        session.pty.finish()
        remove(id)
    }

    fun closeAll() {
        activeSessions.forEach { close(it.id) }
    }

    @Synchronized
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun remove(id: String) {
        val removed = synchronized(this) { sessions.remove(id) } ?: return
        removed.pty.onProcessExited = null
        notifyChanged()
    }

    private fun notifyChanged() {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { runCatching { it() } }
    }

}
