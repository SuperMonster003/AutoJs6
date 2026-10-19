package org.autojs.autojs.runtime.api.shell

import android.util.Log
import androidx.annotation.GuardedBy
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.Shell
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * The pipe-based shell behind `new Shell(options)` (Auto.js Pro 9.3 compatible): one long-lived `sh` / `su` process,
 * local or in the Shizuku user service, fed through stdin.
 *
 * Its output is read in one of two ways, chosen by the first call and kept for the life of the shell:
 * - raw: [getInputStream] / [getErrorStream] hand the streams to the script, and [exec] writes nothing but the command;
 * - managed: [setCallback], [execAndWaitFor] and [execForResult] start reader threads. [execForResult] appends a marker
 *   with `$?` to stdout and stderr after its command, so the output of each waited command and its exit code are split
 *   reliably; output that belongs to no waited command reaches only the callback (and [exitAndReadOutput]).
 * zh-CN: `new Shell(options)` 背后基于管道的 shell (兼容 Auto.js Pro 9.3): 一个常驻的 `sh` / `su` 进程, 运行在本地或 Shizuku 用户服务中, 经 stdin 输入命令.
 * 输出只能以两种方式之一读取, 由首次调用决定并在 shell 存续期间保持:
 * - 原始: [getInputStream] / [getErrorStream] 把流交给脚本, [exec] 只写入命令本身;
 * - 托管: [setCallback], [execAndWaitFor] 与 [execForResult] 启动读取线程. [execForResult] 在命令后向 stdout 与 stderr 追加带 `$?` 的标记, 从而可靠地
 *   分割每条等待命令的输出与退出码; 不属于任何等待命令的输出只交给回调 (以及 [exitAndReadOutput]).
 */
internal class PipeShell(
    private val process: Process,
    val options: ShellLauncher.Options,
    private val reportCallbackError: (Throwable) -> Unit,
    private val onClosed: (PipeShell) -> Unit,
) : Closeable {

    private enum class Claim { NONE, RAW, MANAGED }

    /** One command of [execForResult], complete once both markers (or the ends of both streams) arrived. zh-CN: [execForResult] 的一条命令, 两个标记 (或两个流的结尾) 都到达后完成. */
    private class Execution {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        @Volatile
        var code: Int? = null

        private val stdoutDone = AtomicBoolean(false)
        private val stderrDone = AtomicBoolean(false)
        private val latch = CountDownLatch(2)

        fun append(isError: Boolean, text: String) {
            (if (isError) stderr else stdout).append(text)
        }

        fun finish(isError: Boolean) {
            if ((if (isError) stderrDone else stdoutDone).compareAndSet(false, true)) latch.countDown()
        }

        fun await() = latch.await()
    }

    private val lock = Any()

    @GuardedBy("lock")
    private var stdoutClaim = Claim.NONE

    @GuardedBy("lock")
    private var stderrClaim = Claim.NONE

    @GuardedBy("lock")
    private val looseOutput = StringBuilder()

    private val pumps = CopyOnWriteArrayList<Thread>()
    private val writeLock = Any()
    private val callbackLock = Any()
    private val execLock = ReentrantLock()
    private val closed = AtomicBoolean(false)
    private val marker = "__autojs6_shell_end_" + UUID.randomUUID().toString().replace("-", "") + "__"

    @Volatile
    private var pending: Execution? = null

    @Volatile
    private var callback: Shell.Callback? = null

    /** On a pseudo-terminal stderr arrives in stdout. zh-CN: 在伪终端上 stderr 并入 stdout. */
    private val merged = options.pty

    init {
        if (!options.embedsPrelude) ShellLauncher.prelude(options).takeIf { it.isNotEmpty() }?.let { write(it) }
    }

    fun exec(command: String) = write(withLineEnd(command))

    fun execAndWaitFor(command: String): String = execForResult(command).result

    fun execForResult(command: String): AbstractShell.Result {
        startManaged("execForResult / execAndWaitFor")
        try {
            execLock.lockInterruptibly()
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }
        try {
            check(exitCode() == null && !closed.get()) { "The shell has exited" }
            val execution = Execution()
            pending = execution
            if (merged) {
                execution.finish(isError = true)
                write(withLineEnd(command) + "echo \"$marker \$?\"\n")
            } else {
                write(withLineEnd(command) + "echo \"$marker \$?\"\n" + "echo $marker >&2\n")
            }
            try {
                execution.await()
            } catch (_: InterruptedException) {
                throw ScriptInterruptedException()
            }
            return AbstractShell.Result().apply {
                code = execution.code ?: exitCodeWithin(EXIT_GRACE_MILLIS) ?: -1
                result = execution.stdout.toString()
                error = execution.stderr.toString()
            }
        } finally {
            pending = null
            execLock.unlock()
        }
    }

    fun setCallback(callback: Shell.Callback?) {
        if (callback != null) startManaged("setCallback")
        this.callback = callback
        callback?.let { cb -> invokeCallback { cb.onInitialized() } }
    }

    fun getInputStream(): InputStream {
        claimRaw(isError = false, caller = "getInputStream")
        return process.inputStream
    }

    fun getErrorStream(): InputStream {
        claimRaw(isError = true, caller = "getErrorStream")
        return process.errorStream
    }

    fun getOutputStream(): OutputStream = process.outputStream

    fun waitFor(): Int = try {
        process.waitFor()
    } catch (_: InterruptedException) {
        throw ScriptInterruptedException()
    }

    fun exitCode(): Int? = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        null
    }

    fun raw(): Process = process

    /** Ends the process and what it started at once. zh-CN: 立即结束进程及其启动的进程. */
    fun exit() = close()

    /** Lets the shell run what it already received, then waits for it to exit. zh-CN: 让 shell 执行完已收到的命令, 然后等待它退出. */
    fun exitAndWaitFor() {
        runCatching { write("exit\n") }
        closeQuietly(process.outputStream)
        waitFor()
        pumps.forEach { it.join(EXIT_GRACE_MILLIS) }
        release()
    }

    /** Auto.js Pro's exitAndReadOutput: closes stdin and returns the remaining stdout once the shell exited. zh-CN: Auto.js Pro 的 exitAndReadOutput: 关闭 stdin, shell 退出后返回剩余的 stdout. */
    fun exitAndReadOutput(): String {
        val managed = synchronized(lock) {
            if (stdoutClaim == Claim.NONE) stdoutClaim = Claim.RAW
            if (stderrClaim == Claim.NONE) {
                stderrClaim = Claim.RAW
                startDiscarding(process.errorStream)
            }
            stdoutClaim == Claim.MANAGED
        }
        closeQuietly(process.outputStream)
        val text = when {
            managed -> {
                pumps.forEach { it.join() }
                synchronized(lock) { looseOutput.toString() }
            }
            else -> try {
                process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } catch (_: IOException) {
                ""
            }
        }
        waitFor()
        release()
        return text
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ProcessTree.terminate(process)
        closeQuietly(process.outputStream)
        // Readers get EOF once the process is gone. A root command behind su keeps the pipes open, so they are closed
        // after a short grace: the readers end, and the command gets SIGPIPE on its next write.
        // zh-CN: 进程结束后读取方会读到 EOF. su 背后的 root 命令会让管道保持打开, 因此稍候关闭管道: 读取方随之结束, 该命令在下一次写入时收到 SIGPIPE.
        Thread({
            pumps.forEach { it.join(EXIT_GRACE_MILLIS) }
            exitCodeWithin(EXIT_GRACE_MILLIS)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            pending?.let { it.finish(false); it.finish(true) }
        }, "PipeShell-close").apply { isDaemon = true }.start()
        onClosed(this)
    }

    override fun toString() = "PipeShell { mode: ${options.mode}, pid: ${ProcessTree.pidOf(process)}, exitCode: ${exitCode()} }"

    private fun release() {
        if (closed.compareAndSet(false, true)) onClosed(this)
    }

    private fun write(text: String) = synchronized(writeLock) {
        try {
            process.outputStream.apply {
                write(text.toByteArray(Charsets.UTF_8))
                flush()
            }
        } catch (e: IOException) {
            throw IllegalStateException("The shell has exited", e)
        }
    }

    private fun startManaged(caller: String) {
        synchronized(lock) {
            if (stdoutClaim == Claim.RAW || stderrClaim == Claim.RAW) {
                throw IllegalStateException(
                    "$caller cannot be used on this Shell: its output is already read through getInputStream / getErrorStream. " +
                        "A Shell is read either through its streams or through setCallback / execAndWaitFor / execForResult"
                )
            }
            if (stdoutClaim == Claim.MANAGED) return
            stdoutClaim = Claim.MANAGED
            stderrClaim = Claim.MANAGED
        }
        startPump(process.inputStream, isError = false)
        if (!merged) startPump(process.errorStream, isError = true)
    }

    private fun claimRaw(isError: Boolean, caller: String) = synchronized(lock) {
        if ((if (isError) stderrClaim else stdoutClaim) == Claim.MANAGED) {
            throw IllegalStateException(
                "$caller cannot be used on this Shell: its output is already read by setCallback / execAndWaitFor / execForResult. " +
                    "A Shell is read either through its streams or through those methods"
            )
        }
        if (isError) stderrClaim = Claim.RAW else stdoutClaim = Claim.RAW
    }

    private fun startPump(stream: InputStream, isError: Boolean) {
        Thread({ pump(stream, isError) }, if (isError) "PipeShell-stderr" else "PipeShell-stdout").apply {
            isDaemon = true
            pumps += this
            start()
        }
    }

    private fun pump(stream: InputStream, isError: Boolean) {
        try {
            val reader = stream.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                onLine(line, isError)
            }
        } catch (_: IOException) {
            // The stream was closed by exit().
            // zh-CN: 流已被 exit() 关闭.
        } finally {
            pending?.let { execution ->
                if (!isError && execution.code == null) execution.code = exitCodeWithin(EXIT_GRACE_MILLIS)
                execution.finish(isError)
            }
        }
    }

    private fun onLine(line: String, isError: Boolean) {
        val at = line.indexOf(marker)
        if (at >= 0) {
            val rest = line.substring(at + marker.length).trim()
            // A terminal that still echoes input (Magisk's root terminal on Android 9 and below) returns the marker
            // commands themselves; only "marker code" (stdout) and a bare marker (stderr) are output.
            // zh-CN: 仍回显输入的终端 (Android 9 及以下 Magisk 的 root 终端) 会返回标记命令本身; 只有 "标记 退出码" (stdout) 与单独的标记 (stderr) 才是输出.
            if (rest.isNotEmpty() && rest.toIntOrNull() == null) return
            val partial = line.substring(0, at)
            val execution = pending
            if (partial.isNotEmpty()) {
                // The command printed a last line without a line end.
                // zh-CN: 命令最后一行没有换行符.
                execution?.append(isError, partial) ?: appendLoose(isError, partial)
                deliver(partial)
            }
            if (execution != null) {
                if (!isError) rest.toIntOrNull()?.let { execution.code = it }
                execution.finish(isError)
            }
            return
        }
        pending?.append(isError, line + "\n") ?: appendLoose(isError, line + "\n")
        deliver(line)
    }

    private fun appendLoose(isError: Boolean, text: String) {
        if (isError) return
        synchronized(lock) {
            looseOutput.append(text)
            if (looseOutput.length > MAX_LOOSE_OUTPUT_CHARS) looseOutput.delete(0, looseOutput.length - MAX_LOOSE_OUTPUT_CHARS)
        }
    }

    private fun deliver(line: String) {
        val cb = callback ?: return
        invokeCallback {
            cb.onOutput(line + "\n")
            cb.onNewLine(line)
        }
    }

    private inline fun invokeCallback(block: () -> Unit) = synchronized(callbackLock) {
        try {
            block()
        } catch (_: ScriptInterruptedException) {
            // The script is stopping.
            // zh-CN: 脚本正在停止.
        } catch (t: Throwable) {
            reportCallbackError(t)
        }
    }

    private fun startDiscarding(stream: InputStream) {
        Thread({
            try {
                val buffer = ByteArray(8192)
                do {
                    val count = stream.read(buffer)
                } while (count >= 0)
            } catch (_: IOException) {
                // Closed.
            }
        }, "PipeShell-discard").apply { isDaemon = true }.start()
    }

    private fun exitCodeWithin(millis: Long): Int? {
        try {
            ProcessTree.waitFor(process, millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return exitCode()
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Log.d(TAG, "close: ${e.message}")
        }
    }

    private fun withLineEnd(command: String) = if (command.endsWith('\n')) command else command + "\n"

    private companion object {
        const val TAG = "PipeShell"
        const val EXIT_GRACE_MILLIS = 500L
        const val MAX_LOOSE_OUTPUT_CHARS = 4 * 1024 * 1024
    }

}
