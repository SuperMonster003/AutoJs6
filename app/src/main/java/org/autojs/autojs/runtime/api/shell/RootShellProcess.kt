package org.autojs.autojs.runtime.api.shell

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A command started through `su -c`, locally or inside the Shizuku service. The root shell behind su is no child the
 * caller may signal, so [destroy] also starts [killer]: another root shell that finds the processes tagged with this
 * command's marker (see [ShellLauncher]) and ends them with their descendants.
 * zh-CN: 经 `su -c` 启动的命令, 位于本地或 Shizuku 服务中. su 背后的 root shell 不是调用方有权发信号的子进程, 因此 [destroy] 还会启动 [killer]: 另一个
 * root shell, 它找出带有本命令标记 (见 [ShellLauncher]) 的进程, 连同其子孙进程一起结束.
 */
internal class RootShellProcess(val delegate: Process, private val killer: () -> Process) : Process() {

    private val destroyed = AtomicBoolean(false)

    override fun getOutputStream(): OutputStream = delegate.outputStream

    override fun getInputStream(): InputStream = delegate.inputStream

    override fun getErrorStream(): InputStream = delegate.errorStream

    override fun waitFor(): Int = delegate.waitFor()

    override fun exitValue(): Int = delegate.exitValue()

    override fun destroy() {
        if (destroyed.compareAndSet(false, true)) {
            Thread({
                runCatching {
                    val process = killer()
                    runCatching { process.outputStream.close() }
                    ProcessTree.waitFor(process, KILLER_TIMEOUT_MILLIS)
                    process.destroy()
                }
            }, "RootShellProcess-kill").apply { isDaemon = true }.start()
        }
        ProcessTree.terminate(delegate)
    }

    private companion object {
        const val KILLER_TIMEOUT_MILLIS = 10_000L
    }

}
