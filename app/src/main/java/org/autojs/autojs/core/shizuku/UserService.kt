package org.autojs.autojs.core.shizuku

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import org.autojs.autojs.model.explorer.PrivilegedExplorerDirectoryService
import org.autojs.autojs.model.explorer.PrivilegedFileEngine
import org.autojs.autojs.model.explorer.PrivilegedFileNative
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine
import org.autojs.autojs.model.explorer.PrivilegedFileBackupEngine
import org.autojs.autojs.model.explorer.PrivilegedFileCommand
import org.autojs.autojs.model.explorer.PrivilegedExplorerProcess
import android.util.Base64
import java.io.File
import java.io.InputStream
import org.json.JSONObject
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.shell.ProcessTree
import org.autojs.autojs.runtime.api.shell.PtyProcess
import kotlin.system.exitProcess

class UserService : IUserService.Stub {

    private var mContext: Context? = null
    private val explorerDirectories = PrivilegedExplorerDirectoryService()
    private val lifecycle = UserServiceLifecycle(MainThreadScheduler, ::exitProcessFor)

    init {
        lifecycle.start()
    }

    @Suppress("DEPRECATION")
    private val currentActivity: ComponentName?
        get() {
            val manager = mContext?.getSystemService(ActivityManager::class.java) ?: return null
            val tasks = manager.getRunningTasks(1) ?: return null
            if (tasks.isEmpty()) return null
            return tasks[0].topActivity
        }

    /**
     * Constructor is required.
     */
    constructor() {
        Log.i("UserService", "constructor")
    }

    /**
     * Constructor with Context. This is only available from Shizuku API v13.
     *
     *
     * This method need to be annotated with [Keep] to prevent ProGuard from removing it.
     *
     * @param context Context created with createPackageContextAsUser
     * @see [code used to create the instance of this class](https://github.com/RikkaApps/Shizuku-API/blob/672f5efd4b33c2441dbf609772627e63417587ac/server-shared/src/main/java/rikka/shizuku/server/UserService.java.L66)
     */
    @Keep
    constructor(context: Context) {
        Log.i("UserService", "constructor with Context: context=$context")
        mContext = context
    }

    /**
     * Reserved destroy method
     */
    override fun destroy() {
        Log.i(TAG, "destroy")
        lifecycle.shutdown(UserServiceLifecycle.REASON_DESTROY)
    }

    // Exit method defined by user.
    // zh-CN: 用户定义的退出方法.
    override fun exit() {
        lifecycle.shutdown(UserServiceLifecycle.REASON_EXIT)
    }

    override fun attachHost(host: IBinder?) {
        if (host == null || !lifecycle.attachHost(host)) return
        try {
            host.linkToDeath({ lifecycle.onHostDied(host) }, 0)
            Log.i(TAG, "host attached")
        } catch (_: RemoteException) {
            // The host died before its death could be linked.
            // zh-CN: 宿主在关联其死亡之前已死亡.
            lifecycle.onHostDied(host)
        }
    }

    @Throws(RemoteException::class)
    override fun execCommand(command: String): String {
        return try {
            val process = ProcessShell.getShellProcess()
            // A command still running when the service exits (getevent waiting for a key, for example) ends with it.
            // zh-CN: 服务退出时仍在运行的命令 (例如等待按键的 getevent) 随之结束.
            val child = UserServiceLifecycle.Child { ProcessTree.terminate(process) }
            if (!lifecycle.register(child)) {
                return AbstractShell.Result(1, IllegalStateException(SHUTTING_DOWN_MESSAGE)).toJson()
            }
            try {
                ProcessShell
                    .execCommand(command.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(), process)
                    .toJson()
            } finally {
                lifecycle.unregister(child)
            }
        } catch (e: Exception) {
            AbstractShell.Result(1, e).toJson()
        }
    }

    override fun newShellProcess(command: Array<String>, env: Array<String>?, dir: String?, pty: Boolean, client: IBinder?): IRemoteShellProcess {
        val variables = env.orEmpty().mapNotNull { entry ->
            entry.indexOf('=').takeIf { it > 0 }?.let { entry.substring(0, it) to entry.substring(it + 1) }
        }.toMap()
        val process = when {
            pty -> {
                require(dir == null) { "A working directory is not supported with pty" }
                try {
                    PtyProcess.start(command.toList(), variables)
                } catch (e: LinkageError) {
                    // The termexec library could not be loaded in this process.
                    // zh-CN: 此进程中无法加载 termexec 库.
                    throw IllegalStateException("A pseudo-terminal is not available in the Shizuku service: $e")
                }
            }
            else -> ProcessBuilder(command.toList()).apply {
                dir?.let { directory(File(it)) }
                environment().putAll(variables)
            }.start()
        }
        val holder = RemoteShellProcessHolder(process, client, lifecycle::unregister)
        // A holder registered after the shutdown began has been destroyed by register().
        // zh-CN: 退出开始后登记的 holder 已由 register() 销毁.
        check(lifecycle.register(holder)) { SHUTTING_DOWN_MESSAGE }
        return holder
    }

    override fun currentPackage() = currentActivity?.packageName ?: ""

    override fun currentActivity() = currentActivity?.className ?: ""

    override fun currentComponent() = currentActivity?.flattenToString() ?: ""

    override fun currentComponentShort() = currentActivity?.flattenToShortString() ?: ""

    override fun listExplorerDirectory(path: String, snapshotToken: String, offset: Int): String =
        explorerDirectories.listDirectory(path, snapshotToken, offset)

    override fun closeExplorerDirectory(snapshotToken: String) = explorerDirectories.closeDirectory(snapshotToken)

    override fun executeExplorerFileOperation(requestJson: String): String {
        val request = JSONObject(requestJson)
        val client64Bit = request.optBoolean("client64Bit", Process.is64Bit())
        // Shizuku starts a 64-bit service on a 64-bit device even for a 32-bit host APK.
        // Run native moves in the host's architecture under the same authorized UID.
        if (request.optString("operation") in listOf("move", "rename") && client64Bit != Process.is64Bit()) {
            return executeMatchingProcess(request)
        }
        PrivilegedFileNative.configure(request.optString("nativeLibrary").takeIf { it.isNotEmpty() })
        return PrivilegedFileEngine.execute(requestJson)
    }

    override fun openExplorerFile(requestJson: String) = try {
        PrivilegedFileEngine.openReadDescriptor(requestJson)
    } catch (error: Exception) {
        throw IllegalStateException(error.message ?: "Unable to open file", error)
    }

    override fun snapshotExplorerFile(requestJson: String): String = PrivilegedFileSaveEngine.statSnapshot(requestJson)

    override fun manageExplorerFileBackup(requestJson: String): String {
        val request = JSONObject(requestJson)
        if (request.optBoolean("client64Bit", Process.is64Bit()) != Process.is64Bit()) return executeMatchingProcess(request)
        PrivilegedFileNative.configure(request.optString("nativeLibrary").takeIf { it.isNotEmpty() })
        return PrivilegedFileBackupEngine.execute(requestJson)
    }

    override fun reconcileExplorerFileSave(requestJson: String): String {
        val request = JSONObject(requestJson)
        if (request.optBoolean("client64Bit", Process.is64Bit()) != Process.is64Bit()) return executeMatchingProcess(request)
        PrivilegedFileNative.configure(request.optString("nativeLibrary").takeIf { it.isNotEmpty() })
        return PrivilegedFileSaveEngine.reconcile(requestJson)
    }

    override fun saveExplorerFile(requestJson: String, contents: ParcelFileDescriptor): String =
        ParcelFileDescriptor.AutoCloseInputStream(contents).use { input ->
            val request = JSONObject(requestJson)
            if (request.optBoolean("client64Bit", Process.is64Bit()) != Process.is64Bit()) {
                executeMatchingProcess(request, input)
            } else {
                PrivilegedFileNative.configure(request.optString("nativeLibrary").takeIf { it.isNotEmpty() })
                PrivilegedFileSaveEngine.save(requestJson, input)
            }
        }

    private fun executeMatchingProcess(request: JSONObject, input: InputStream? = null): String {
        val appProcess = if (request.optBoolean("client64Bit")) "/system/bin/app_process64" else "/system/bin/app_process32"
        val apkPath = request.getString("apkPath")
        require(File(apkPath).isAbsolute && apkPath.endsWith(".apk"))
        val timeout = (request.optLong("deadlineMillis") - System.currentTimeMillis()).coerceIn(1, 600_000)
        val result = PrivilegedExplorerProcess.run(listOf(
            "/system/bin/env", "CLASSPATH=$apkPath", "/system/bin/toybox", "timeout", "-k", "2", "-s", "TERM",
            (timeout / 1000 + 3).toString(), appProcess, "/system/bin", PrivilegedFileCommand::class.java.name,
            Base64.encodeToString(request.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
        ), timeout + 10000, input)
        if (result.exitCode != 0) {
            if (request.optString("operation") in listOf("save", "backup_delete", "backup_restore") && !result.stderr.contains(PrivilegedFileCommand.SAVE_STARTED_MARKER)) {
                return JSONObject().put("ok", false).put("code", "ACCESS_UNAVAILABLE")
                    .put("message", result.stderr).toString()
            }
            throw IllegalStateException(result.stderr.ifBlank { "File operation process exited before reporting its result" })
        }
        return result.stdout.toString(Charsets.UTF_8)
    }

    private fun exitProcessFor(reason: String) {
        // The probe (tools/shizuku-oom/zombie_probe.py) reads the reason from this line.
        // zh-CN: 探针 (tools/shizuku-oom/zombie_probe.py) 从这一行读取退出原因.
        Log.i(TAG, "exit: $reason")
        runCatching { Process.killProcess(Process.myPid()) }
        // Fallback to exit the process if killProcess doesn't stop it immediately.
        // zh-CN: 如果 killProcess 未能立刻终止, 则使用 exitProcess 作为兜底退出.
        runCatching { exitProcess(0) }
    }

    /**
     * The attach watchdog runs on the looper Shizuku's service starter loops on after it created the service.
     * zh-CN: 附着看门狗运行在 Shizuku 服务启动器创建服务后进入循环的 Looper 上.
     */
    private object MainThreadScheduler : UserServiceLifecycle.Scheduler {
        override fun schedule(delayMillis: Long, action: Runnable): UserServiceLifecycle.Cancellable {
            val looper = Looper.getMainLooper() ?: Looper.myLooper()
            if (looper == null) {
                val thread = Thread({
                    try {
                        Thread.sleep(delayMillis)
                        action.run()
                    } catch (_: InterruptedException) {
                        // Cancelled.
                        // zh-CN: 已取消.
                    }
                }, "UserService-Watchdog").apply { isDaemon = true; start() }
                return UserServiceLifecycle.Cancellable { thread.interrupt() }
            }
            val handler = Handler(looper)
            handler.postDelayed(action, delayMillis)
            return UserServiceLifecycle.Cancellable { handler.removeCallbacks(action) }
        }
    }

    private companion object {
        const val TAG = "UserService"
        const val SHUTTING_DOWN_MESSAGE = "The Shizuku user service is exiting"
    }

}
