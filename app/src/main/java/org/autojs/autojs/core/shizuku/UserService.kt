package org.autojs.autojs.core.shizuku

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.ProcessShell
import kotlin.system.exitProcess

class UserService : IUserService.Stub {

    private var mContext: Context? = null

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

}
