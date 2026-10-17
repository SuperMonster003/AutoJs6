package org.autojs.autojs.core.shizuku

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import org.autojs.autojs.model.explorer.PrivilegedExplorerDirectoryService
import org.autojs.autojs.model.explorer.PrivilegedFileEngine
import org.autojs.autojs.model.explorer.PrivilegedFileNative
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine
import org.autojs.autojs.model.explorer.PrivilegedFileCommand
import org.autojs.autojs.model.explorer.PrivilegedExplorerProcess
import android.util.Base64
import java.io.File
import java.io.InputStream
import org.json.JSONObject
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.ProcessShell
import kotlin.system.exitProcess

class UserService : IUserService.Stub {

    private var mContext: Context? = null
    private val explorerDirectories = PrivilegedExplorerDirectoryService()

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
        Log.i("UserService", "destroy")

        // Ensure the user service process terminates when Shizuku server requests destroy.
        // zh-CN: 确保 Shizuku server 请求 destroy 时, user service 进程能够真正退出.
        runCatching {
            Process.killProcess(Process.myPid())
        }

        // Fallback to exit the process if killProcess doesn't stop it immediately.
        // zh-CN: 如果 killProcess 未能立刻终止, 则使用 exitProcess 作为兜底退出.
        runCatching {
            exitProcess(0)
        }
    }

    // Exit method defined by user.
    // zh-CN: 用户定义的退出方法.
    override fun exit() {
        destroy()
    }

    @Throws(RemoteException::class)
    override fun execCommand(command: String): String {
        return try {
            ProcessShell
                .execCommand(command.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(), ProcessShell.getShellProcess())
                .toJson()
        } catch (e: Exception) {
            AbstractShell.Result(1, e).toJson()
        }
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
            if (request.optString("operation") == "save" && !result.stderr.contains(PrivilegedFileCommand.SAVE_STARTED_MARKER)) {
                return JSONObject().put("ok", false).put("code", "ACCESS_UNAVAILABLE")
                    .put("message", result.stderr).toString()
            }
            throw IllegalStateException(result.stderr.ifBlank { "File operation process exited before reporting its result" })
        }
        return result.stdout.toString(Charsets.UTF_8)
    }

}
