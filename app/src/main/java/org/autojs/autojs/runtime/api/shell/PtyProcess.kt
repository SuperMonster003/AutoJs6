package org.autojs.autojs.runtime.api.shell

import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.OsConstants
import jackpal.androidterm.TermExec
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A process whose stdin, stdout and stderr are the slave side of a pseudo-terminal (`pty: true`), started through the
 * jackpal termexec library that the terminal-session Shell already uses. Programs that buffer output on pipes (getevent,
 * awk, ...) see a terminal and flush every line. stderr arrives merged into [getInputStream] and [getErrorStream] is
 * empty. Echo and the carriage return before line feeds are turned off ([PtyTermios]), so written text does not come
 * back and lines end with `\n` as on a pipe. The child leads its own session, so [destroy] signals the whole process
 * group.
 * zh-CN: stdin, stdout 与 stderr 均为伪终端从端的进程 (`pty: true`), 经终端会话 Shell 已在使用的 jackpal termexec 库启动. 在管道上缓冲输出的程序 (getevent,
 * awk 等) 会看到终端, 从而逐行刷新. stderr 并入 [getInputStream], [getErrorStream] 为空. 回显与换行前的回车已关闭 ([PtyTermios]), 因此写入的文本不会返回,
 * 行尾与管道一样为 `\n`. 子进程是其会话的首进程, 因此 [destroy] 向整个进程组发信号.
 */
internal class PtyProcess private constructor(private val master: ParcelFileDescriptor, val pid: Int) : Process() {

    private val exited = CountDownLatch(1)
    private val destroyed = AtomicBoolean(false)
    private val masterClosed = AtomicBoolean(false)

    @Volatile
    private var exitCode: Int? = null

    private val input: InputStream = MasterInputStream(FileInputStream(master.fileDescriptor))
    private val output: OutputStream = FileOutputStream(master.fileDescriptor)

    init {
        Thread({
            val code = TermExec.waitFor(pid)
            // termexec reports 0 for a child ended by a signal.
            // zh-CN: termexec 对被信号结束的子进程报告 0.
            exitCode = if (code == 0 && destroyed.get()) KILLED_EXIT_CODE else code
            exited.countDown()
        }, "PtyProcess-wait").apply { isDaemon = true }.start()
    }

    override fun getOutputStream(): OutputStream = output

    override fun getInputStream(): InputStream = input

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int {
        exited.await()
        return exitCode ?: -1
    }

    override fun exitValue(): Int = exitCode ?: throw IllegalThreadStateException("Process has not exited")

    override fun destroy() {
        destroyed.set(true)
        runCatching { TermExec.sendSignal(-pid, SIGKILL) }
        runCatching { TermExec.sendSignal(pid, SIGKILL) }
    }

    private fun closeMaster() {
        if (masterClosed.compareAndSet(false, true)) runCatching { master.close() }
    }

    /**
     * Reading the master side fails with EIO once no slave is open any more, which here means the end of the output.
     * zh-CN: 没有打开的从端后, 读取主端会以 EIO 失败, 此处即表示输出结束.
     */
    private inner class MasterInputStream(private val delegate: InputStream) : InputStream() {

        override fun read(): Int = guard { delegate.read() }

        override fun read(b: ByteArray, off: Int, len: Int): Int = guard { delegate.read(b, off, len) }

        override fun available(): Int = if (masterClosed.get()) 0 else runCatching { delegate.available() }.getOrDefault(0)

        override fun close() = closeMaster()

        private inline fun guard(read: () -> Int): Int {
            if (masterClosed.get()) return -1
            return try {
                read().also { if (it < 0) closeMaster() }
            } catch (e: IOException) {
                if (masterClosed.get() || isEio(e)) {
                    closeMaster()
                    -1
                } else {
                    throw e
                }
            }
        }

        private fun isEio(e: IOException) = generateSequence<Throwable>(e) { it.cause }.any { (it as? ErrnoException)?.errno == OsConstants.EIO } ||
            e.message?.contains("EIO") == true
    }

    companion object {

        private const val SIGKILL = 9
        private const val KILLED_EXIT_CODE = 128 + SIGKILL

        /**
         * Starts [command] (program name or path, then its arguments) on a new pseudo-terminal. termexec refuses the main
         * thread, so a call from there is run on a helper thread.
         * zh-CN: 在新的伪终端上启动 [command] (程序名或路径, 其后为参数). termexec 拒绝在主线程调用, 因此来自主线程的调用改在辅助线程上执行.
         */
        fun start(command: List<String>, env: Map<String, String> = emptyMap()): PtyProcess {
            require(command.isNotEmpty()) { "Empty command" }
            val launch = {
                val master = ParcelFileDescriptor.open(File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE)
                // termexec takes the executable path first, then the complete argv (execv does not search PATH).
                // zh-CN: termexec 先取可执行文件路径, 再取完整的 argv (execv 不搜索 PATH).
                val exec = TermExec(mutableListOf(resolveExecutable(command[0])).apply { addAll(command) })
                exec.environment().putAll(env)
                val pid = try {
                    exec.start(master)
                } catch (t: Throwable) {
                    runCatching { master.close() }
                    throw t
                }
                // Before anything is written, so no input comes back as output.
                // zh-CN: 在写入任何内容之前设置, 因此输入不会作为输出返回.
                PtyTermios.applyPipeLike(master.fd)
                PtyProcess(master, pid)
            }
            if (Looper.myLooper() != Looper.getMainLooper()) return launch()
            val executor = Executors.newSingleThreadExecutor()
            return try {
                executor.submit(launch).get()
            } catch (e: java.util.concurrent.ExecutionException) {
                throw e.cause ?: e
            } finally {
                executor.shutdown()
            }
        }

        private fun resolveExecutable(name: String): String {
            if (name.contains('/')) return name
            val dirs = (System.getenv("PATH") ?: "").split(':').filter { it.isNotEmpty() } + listOf("/system/bin", "/system/xbin", "/sbin", "/vendor/bin")
            return dirs.asSequence().map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.path
                ?: throw IOException("Cannot find \"$name\" in PATH")
        }

    }

}
