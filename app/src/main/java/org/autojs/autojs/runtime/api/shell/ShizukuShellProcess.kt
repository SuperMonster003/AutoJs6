package org.autojs.autojs.runtime.api.shell

import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import org.autojs.autojs.core.shizuku.IRemoteShellProcess
import java.io.InputStream
import java.io.OutputStream

/**
 * A process running in the Shizuku user service, seen by the host as an ordinary [Process]: the streams are the host
 * ends of the service's pipes, the rest goes over Binder. [client] is the token whose death makes the service end the
 * process. When the service itself dies the process counts as exited with code -1.
 * zh-CN: 运行在 Shizuku 用户服务中的进程, 在宿主看来是普通的 [Process]: 流是服务管道的宿主端, 其余经 Binder 调用. [client] 是令牌, 它死亡时服务结束该进程.
 * 服务本身死亡时, 进程视为已退出, 退出码为 -1.
 */
internal class ShizukuShellProcess(
    private val remote: IRemoteShellProcess,
    @Suppress("unused") private val client: Binder,
) : Process() {

    private val stdin: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
    private val stdout: InputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
    private val stderr: InputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)

    @Volatile
    private var deadExitCode: Int? = null

    val pid: Int = remoteOr(-1) { it.pid() }

    override fun getOutputStream(): OutputStream = stdin

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int {
        while (!waitFor(POLL_MILLIS)) {
            if (Thread.interrupted()) throw InterruptedException()
        }
        return exitValue()
    }

    /** Waits at most [timeoutMillis]; true once the process exited. zh-CN: 至多等待 [timeoutMillis]; 进程退出后返回 true. */
    fun waitFor(timeoutMillis: Long): Boolean = remoteOr(true) { it.waitForTimeout(timeoutMillis) }

    override fun exitValue(): Int {
        if (alive()) throw IllegalThreadStateException("Process has not exited")
        return deadExitCode ?: remoteOr(-1) { it.exitValue() }
    }

    fun alive(): Boolean = remoteOr(false) { it.alive() }

    override fun destroy() {
        remoteOr(Unit) { it.destroy() }
    }

    private fun <T> remoteOr(fallback: T, call: (IRemoteShellProcess) -> T): T {
        if (deadExitCode != null) return fallback
        return try {
            call(remote)
        } catch (_: RemoteException) {
            deadExitCode = -1
            fallback
        }
    }

    private companion object {
        const val POLL_MILLIS = 500L
    }

}
