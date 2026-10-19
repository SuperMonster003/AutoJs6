package org.autojs.autojs.runtime.api.shell

import org.autojs.autojs.util.ProcessUtils
import java.io.File

/**
 * Ends a shell process together with what it started. `sh -c "getevent | grep x"` leaves `getevent` and `grep` running
 * when only `sh` is destroyed, so the descendants the caller may signal (same uid) are collected from `/proc` first and
 * signalled before the process itself. Descendants of another uid (a root command behind `su`) are out of reach: they end
 * with SIGPIPE on their next write once the caller closed the read ends.
 * zh-CN: 连同 shell 进程启动的子孙进程一起结束. 只销毁 `sh` 时, `sh -c "getevent | grep x"` 中的 `getevent` 与 `grep` 会继续运行, 因此先从 `/proc`
 * 收集调用方有权发信号的子孙进程 (同 uid), 在进程本身之前向它们发信号. 其他 uid 的子孙进程 (`su` 背后的 root 命令) 无法触及: 调用方关闭读端后,
 * 它们会在下一次写入时因 SIGPIPE 结束.
 */
internal object ProcessTree {

    private const val SIGKILL = 9
    private const val SIGSTOP = 19
    private const val POLL_MILLIS = 10L

    fun pidOf(process: Process): Int = when (process) {
        is ShizukuShellProcess -> process.pid
        is RootShellProcess -> pidOf(process.delegate)
        is PtyProcess -> process.pid
        else -> ProcessUtils.getProcessPid(process)
    }

    fun terminate(process: Process) {
        // These wrappers own a process group or a remote cleanup token even after the leader exits.
        if(process is PtyProcess || process is ShizukuShellProcess || process is RootShellProcess){runCatching { process.destroy() };return}
        // Do not signal a cached PID after its original local process has already exited.
        if (runCatching { process.exitValue() }.isSuccess) return
        if (process !is ShizukuShellProcess && process !is RootShellProcess) {
            val pid = pidOf(process)
            if (pid > 0) {
                // Freeze the tree before collecting it: a loop can otherwise fork between collection and kill.
                // SIGKILL also releases pipes when a command ignores or traps SIGTERM.
                runCatching { android.os.Process.sendSignal(pid, SIGSTOP) }
                val stopped = linkedSetOf<Int>()
                while (true) {
                    val found=descendantsOf(pid).filterNot { it in stopped }
                    if(found.isEmpty())break
                    found.forEach { child -> runCatching { android.os.Process.sendSignal(child,SIGSTOP) };stopped.add(child) }
                }
                stopped.toList().asReversed().forEach { child -> runCatching { android.os.Process.sendSignal(child,SIGKILL) } }
                runCatching { android.os.Process.sendSignal(pid,SIGKILL) }
            }
        }
        // A Shizuku process forwards destroy() to the user service, which runs this method next to the real process;
        // a root process also ends its tagged root shell.
        // zh-CN: Shizuku 进程把 destroy() 转给用户服务, 由它在真实进程旁运行本方法; root 进程还会结束带标记的 root shell.
        runCatching { process.destroy() }
    }

    /**
     * Waits at most [millis]; true once [process] exited. Process.waitFor(timeout, unit) needs Android 8.0, so local
     * processes are polled.
     * zh-CN: 至多等待 [millis]; [process] 退出后返回 true. Process.waitFor(timeout, unit) 需要 Android 8.0, 因此对本地进程轮询.
     */
    @Throws(InterruptedException::class)
    fun waitFor(process: Process, millis: Long): Boolean {
        if (process is ShizukuShellProcess) return process.waitFor(millis)
        if (process is RootShellProcess) return waitFor(process.delegate, millis)
        val deadline = System.nanoTime() + millis.coerceAtLeast(0L) * 1_000_000L
        while (true) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                // Still running.
                // zh-CN: 仍在运行.
            }
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0L) return false
            Thread.sleep(remaining.coerceAtMost(POLL_MILLIS))
        }
    }

    /** Descendants of [pid] visible in `/proc`, parents before their children. zh-CN: `/proc` 中可见的 [pid] 子孙进程, 父进程在前. */
    fun descendantsOf(pid: Int): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        File("/proc").listFiles()?.forEach { dir ->
            val child = dir.name.toIntOrNull() ?: return@forEach
            val stat = runCatching { File(dir, "stat").readText() }.getOrNull() ?: return@forEach
            // The command name in parentheses may contain spaces; the fields after it are "state ppid ...".
            // zh-CN: 括号中的命令名可能含空格; 其后的字段为 "state ppid ...".
            val ppid = stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toIntOrNull() ?: return@forEach
            children.getOrPut(ppid) { mutableListOf() }.add(child)
        }
        val result = ArrayList<Int>()
        val pending = ArrayDeque(listOf(pid))
        while (pending.isNotEmpty()) {
            children[pending.removeFirst()]?.forEach { child ->
                if (child != pid && child !in result) {
                    result += child
                    pending.addLast(child)
                }
            }
        }
        return result
    }

}
