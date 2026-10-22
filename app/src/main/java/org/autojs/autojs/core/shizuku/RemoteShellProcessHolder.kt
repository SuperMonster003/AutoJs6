package org.autojs.autojs.core.shizuku

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import org.autojs.autojs.runtime.api.shell.ProcessTree
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs inside the Shizuku user service and owns one [Process] started for the host, with the uid Shizuku runs as (shell
 * or root). Its three streams reach the host through pipes, like Shizuku's own remote process: the host gets one end of
 * each pipe, a thread here copies between the other end and the process. The process ends with [destroy], when the
 * host process dies ([client] death), or when the service exits ([terminate], the holder is registered with the
 * service's [UserServiceLifecycle]); [onDestroyed] unregisters it.
 * zh-CN: 运行在 Shizuku 用户服务内, 持有一个为宿主启动的 [Process], 以 Shizuku 的运行身份 (shell 或 root) 运行. 它的三个流经管道到达宿主, 与 Shizuku
 * 自带的远程进程相同: 宿主拿到每个管道的一端, 这里的线程在另一端与进程之间复制数据. 进程随 [destroy] 结束, 在宿主进程死亡时 ([client] 死亡) 结束,
 * 或在服务退出时结束 ([terminate], holder 登记在服务的 [UserServiceLifecycle] 中); [onDestroyed] 注销登记.
 */
internal class RemoteShellProcessHolder(
    private val process: Process,
    client: IBinder?,
    private val onDestroyed: (RemoteShellProcessHolder) -> Unit = {},
) : IRemoteShellProcess.Stub(), UserServiceLifecycle.Child {

    private val hostStdin = AtomicReference<ParcelFileDescriptor?>()
    private val hostStdout = AtomicReference<ParcelFileDescriptor?>()
    private val hostStderr = AtomicReference<ParcelFileDescriptor?>()
    private val deathRecipient = IBinder.DeathRecipient { destroy() }
    private val client: IBinder? = client?.takeIf { runCatching { it.linkToDeath(deathRecipient, 0) }.isSuccess }

    // The client death and the service shutdown race when the host dies; the later caller waits for the tree to be gone.
    // zh-CN: 宿主死亡时 client 死亡与服务退出相互竞争; 后到的调用方等进程树结束.
    private val ending = UserServiceLifecycle.Once {
        this.client?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
        ProcessTree.terminate(process)
        listOf(hostStdin, hostStdout, hostStderr).forEach { ref -> ref.getAndSet(null)?.let { runCatching { it.close() } } }
        onDestroyed(this)
    }

    init {
        // createPipe() returns [read end, write end].
        // zh-CN: createPipe() 返回 [读端, 写端].
        val stdin = ParcelFileDescriptor.createPipe()
        val stdout = ParcelFileDescriptor.createPipe()
        val stderr = ParcelFileDescriptor.createPipe()
        hostStdin.set(stdin[1])
        hostStdout.set(stdout[0])
        hostStderr.set(stderr[0])
        transfer("stdin", ParcelFileDescriptor.AutoCloseInputStream(stdin[0]), process.outputStream)
        transfer("stdout", process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(stdout[1]))
        transfer("stderr", process.errorStream, ParcelFileDescriptor.AutoCloseOutputStream(stderr[1]))
    }

    override fun getOutputStream(): ParcelFileDescriptor = take(hostStdin, "stdin")

    override fun getInputStream(): ParcelFileDescriptor = take(hostStdout, "stdout")

    override fun getErrorStream(): ParcelFileDescriptor = take(hostStderr, "stderr")

    override fun waitForTimeout(timeoutMillis: Long): Boolean = try {
        ProcessTree.waitFor(process, timeoutMillis)
    } catch (_: InterruptedException) {
        !alive()
    }

    override fun exitValue(): Int = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        -1
    }

    override fun alive(): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    override fun destroy() {
        ending.run()
    }

    override fun terminate() = destroy()

    override fun pid(): Int = ProcessTree.pidOf(process)

    private fun take(ref: AtomicReference<ParcelFileDescriptor?>, name: String): ParcelFileDescriptor =
        ref.getAndSet(null) ?: throw IllegalStateException("The $name pipe of this process has already been handed out")

    private fun transfer(name: String, input: InputStream, output: OutputStream) {
        Thread({
            val buffer = ByteArray(8192)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    output.flush()
                }
            } catch (e: IOException) {
                // The host closed its end, or the process ended. Closing both sides lets the other one notice too.
                // zh-CN: 宿主关闭了它那一端, 或进程已结束. 关闭两侧, 让另一侧也能察觉.
                Log.d(TAG, "$name transfer ended: ${e.message}")
            } finally {
                runCatching { input.close() }
                runCatching { output.close() }
            }
        }, "RemoteShellProcess-$name").apply { isDaemon = true }.start()
    }

    private companion object {
        const val TAG = "RemoteShellProcess"
    }

}
