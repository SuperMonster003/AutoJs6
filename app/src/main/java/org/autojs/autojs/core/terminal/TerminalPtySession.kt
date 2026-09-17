package org.autojs.autojs.core.terminal

import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import jackpal.androidterm.PtyBridge
import jackpal.androidterm.emulatorview.TermSession
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * A [TermSession] backed by a pty the host owns: the master side is opened here, the child is
 * forked / exec'd with a fully controlled argv and environment, its exit is reaped by a watcher
 * thread, and the window size and UTF-8 mode are pushed to the pty through [PtyBridge].
 *
 * Sticky Ctrl / Alt modifiers armed by the toolbar are applied to the next chunk of keyboard input.
 *
 * zh-CN: 由宿主自有 pty 支撑的 [TermSession]: 在此打开主端, 以完全受控的 argv 与环境 fork / exec 子进程,
 * 由看护线程回收其退出状态, 并通过 [PtyBridge] 向 pty 下发窗口尺寸与 UTF-8 模式.
 * 工具栏设置的粘滞 Ctrl / Alt 修饰键会应用到下一段键盘输入.
 */
class TerminalPtySession(
    private val command: List<String>,
    private val environment: Map<String, String>,
) : TermSession(false) {

    private val ptmx: ParcelFileDescriptor = ParcelFileDescriptor.open(File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val processLock = Any()
    @Volatile
    private var finished = false
    @Volatile
    private var started = false
    private var emulatorInitialized = false
    private var pendingMessages = ArrayList<String>()

    @Volatile
    var pid: Int = -1
        private set

    @Volatile
    var exitCode: Int? = null
        private set

    val isProcessAlive: Boolean get() = started && !finished && exitCode == null

    var ctrlArmed: Boolean = false
        private set

    var altArmed: Boolean = false
        private set

    /** Main-thread callback with the exit code once the child has been reaped. zh-CN: 子进程被回收后在主线程回调退出码. */
    var onProcessExited: ((Int) -> Unit)? = null

    /** Called on any keyboard input after the child exited. zh-CN: 子进程退出后收到任意键盘输入时回调. */
    var onInputAfterExit: (() -> Unit)? = null

    /**
     * Registry hook invoked (main thread) after [onProcessExited] so the session can be dropped even
     * when no activity is attached.
     * zh-CN: 注册表钩子, 在 [onProcessExited] 之后于主线程调用, 使没有 Activity 依附时会话也能被移除.
     */
    internal var onProcessReaped: ((Int) -> Unit)? = null

    var onModifiersChanged: ((ctrl: Boolean, alt: Boolean) -> Unit)? = null

    init {
        setTermOut(ParcelFileDescriptor.AutoCloseOutputStream(ptmx))
        setTermIn(ParcelFileDescriptor.AutoCloseInputStream(ptmx))
        setDefaultUTF8Mode(true)
    }

    /**
     * Forks the child on a worker thread and keeps that thread as the exit watcher.
     * zh-CN: 在工作线程 fork 子进程, 并让该线程继续担任退出看护.
     */
    fun start() {
        check(!started) { "Session already started" }
        started = true
        thread(name = "TerminalPtySession-$command") {
            val childPid = try {
                synchronized(processLock) {
                    if (finished) null else PtyBridge.createSubprocess(
                        ptmx,
                        command.first(),
                        command.toTypedArray(),
                        TerminalEnvironment.toEnvp(environment),
                    ).also { pid = it }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to start terminal child: ${e.message}")
                exitCode = START_FAILURE_CODE
                mainHandler.post {
                    appendMessage("\r\n[Failed to start shell: ${e.message}]\r\n")
                    onProcessExited?.invoke(START_FAILURE_CODE)
                    onProcessReaped?.invoke(START_FAILURE_CODE)
                }
                return@thread
            }
            val code = childPid?.let(PtyBridge::waitFor) ?: 0
            exitCode = code
            mainHandler.post {
                appendMessage("\r\n[Process completed (code $code)]\r\n")
                onProcessExited?.invoke(code)
                onProcessReaped?.invoke(code)
            }
        }
    }

    override fun initializeEmulator(columns: Int, rows: Int) {
        super.initializeEmulator(columns, rows)
        emulatorInitialized = true
        runCatching { PtyBridge.setUtf8Mode(ptmx.fd, true) }
        applyWindowSize(columns, rows)
        val pending = pendingMessages
        pendingMessages = ArrayList()
        pending.forEach(::appendMessage)
    }

    override fun updateSize(columns: Int, rows: Int) {
        super.updateSize(columns, rows)
        applyWindowSize(columns, rows)
    }

    private fun applyWindowSize(columns: Int, rows: Int) {
        runCatching { PtyBridge.setWindowSize(ptmx.fd, rows, columns, 0, 0) }
            .onFailure { Log.w(TAG, "Failed to set pty window size: ${it.message}") }
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (finished || exitCode != null) {
            onInputAfterExit?.invoke()
            return
        }
        if (!ctrlArmed && !altArmed) {
            super.write(data, offset, count)
            return
        }
        val ctrl = ctrlArmed
        val alt = altArmed
        clearModifiers()
        super.write(TerminalKeySequences.applyModifiers(String(data, offset, count, Charsets.UTF_8), ctrl, alt))
    }

    fun toggleCtrl() = setModifiers(!ctrlArmed, altArmed)

    fun toggleAlt() = setModifiers(ctrlArmed, !altArmed)

    fun clearModifiers() = setModifiers(ctrl = false, alt = false)

    private fun setModifiers(ctrl: Boolean, alt: Boolean) {
        if (ctrlArmed == ctrl && altArmed == alt) return
        ctrlArmed = ctrl
        altArmed = alt
        onModifiersChanged?.invoke(ctrl, alt)
    }

    /**
     * Sends a raw key sequence, bypassing the sticky modifiers (Alt is still honored for cursor keys).
     * zh-CN: 发送原始按键序列, 绕过粘滞修饰键 (光标键仍应用 Alt).
     */
    fun sendKey(key: TerminalKeySequences.Key) {
        if (finished || exitCode != null) {
            onInputAfterExit?.invoke()
            return
        }
        val alt = altArmed
        clearModifiers()
        val sequence = TerminalKeySequences.sequence(key, applicationCursorKeys = false)
        super.write(if (alt) TerminalKeySequences.ESC + sequence else sequence)
    }

    /**
     * The shell's current working directory, read from procfs; null before start or after exit.
     * zh-CN: 从 procfs 读取的 shell 当前工作目录; 启动前或退出后为 null.
     */
    fun currentDirectory(): File? {
        val childPid = pid
        if (childPid <= 0 || exitCode != null) return null
        return runCatching { File("/proc/$childPid/cwd").canonicalFile }.getOrNull()?.takeIf { it.isDirectory }
    }

    /** True while any process still runs under the shell. zh-CN: shell 下仍有进程运行时为 true. */
    fun hasLiveChildren(): Boolean {
        val parent = pid
        if (parent <= 0 || exitCode != null) return false
        val proc = File("/proc").listFiles { file -> file.isDirectory && file.name.all(Char::isDigit) } ?: return false
        return proc.any { dir ->
            runCatching {
                val stat = File(dir, "stat").readText()
                // "<pid> (<comm>) <state> <ppid> ..."; comm may contain spaces, so parse after the last ')'.
                // zh-CN: comm 可能含空格, 因此从最后一个 ')' 之后解析.
                val rest = stat.substringAfterLast(')').trim().split(' ')
                rest.getOrNull(1)?.toIntOrNull() == parent
            }.getOrDefault(false)
        }
    }

    /** Hangs up the whole process group so background jobs die with the shell. zh-CN: 挂断整个进程组, 使后台作业随 shell 一起结束. */
    fun hangup() {
        val childPid = pid
        if (childPid > 0 && exitCode == null) {
            runCatching { PtyBridge.sendSignal(-childPid, PtyBridge.SIGHUP) }
        }
    }

    fun interrupt() {
        val childPid = pid
        if (childPid > 0 && exitCode == null) {
            runCatching { PtyBridge.sendSignal(-childPid, PtyBridge.SIGINT) }
        }
    }

    override fun finish() {
        synchronized(processLock) {
            if (finished) return
            finished = true
            hangup()
            // A just-forked child may not have called setsid() yet, and can inherit ignored SIGHUP.
            // Explicitly stopping a session must also terminate that child, before closing its pty.
            // zh-CN: 刚 fork 的子进程可能尚未调用 setsid(), 也可能继承被忽略的 SIGHUP.
            // 显式停止会话时, 还需在关闭 pty 前终止该子进程.
            if (pid > 0 && exitCode == null) {
                runCatching { PtyBridge.sendSignal(pid, PtyBridge.SIGKILL) }
            }
        }
        if (!emulatorInitialized) {
            // The library's finish() assumes initializeEmulator() has already run.
            // Before a view is attached, only the pty descriptor needs closing.
            runCatching { ptmx.close() }
        } else {
            super.finish()
        }
        pendingMessages.clear()
    }

    private fun appendMessage(message: String) {
        if (finished) return
        if (!isRunning) {
            pendingMessages.add(message)
            return
        }
        val bytes = message.toByteArray(Charsets.UTF_8)
        runCatching { appendToEmulator(bytes, 0, bytes.size) }
        notifyUpdate()
    }

    companion object {
        private const val TAG = "TerminalPtySession"
        const val START_FAILURE_CODE = -1
    }

}
