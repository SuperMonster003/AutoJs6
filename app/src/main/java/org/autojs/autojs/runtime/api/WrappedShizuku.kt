package org.autojs.autojs.runtime.api

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.App
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.core.shizuku.UserService
import org.autojs.autojs.runtime.api.shell.ShizukuShellProcess
import org.autojs.autojs.util.App.SHIZUKU
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.autojs.runtime.api.AbstractShell.Result as ShellResult

/**
 * Created by SuperMonster003 on Oct 19, 2023.
 */
object WrappedShizuku {

    @JvmField
    var service: IUserService? = null

    private const val TAG = "WrappedShizuku"
    private const val SERVICE_BIND_TIMEOUT_MILLIS = 10_000L

    private val mRequestCode = when {
        isInrt -> "shizuku-request-code-inrt".hashCode()
        else -> "shizuku-request-code".hashCode()
    }
    private var mHasBinder = false

    private val mServiceWaiters = CopyOnWriteArrayList<CountDownLatch>()

    // Lives as long as this process: the user service exits once it dies (docs/shizuku-oom/ROADMAP.md D5 / T4).
    // zh-CN: 与本进程同生命周期: 它死亡后用户服务随之退出 (docs/shizuku-oom/ROADMAP.md D5 / T4).
    private val mHostToken = Binder()

    private val mUserServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected: ${componentName.className}")
            if (binder?.pingBinder() == true) {
                service = IUserService.Stub.asInterface(binder).also { userService ->
                    runCatching { userService.attachHost(mHostToken) }.onFailure { Log.w(TAG, "attachHost failed", it) }
                }

                // Wake all waiters when binder is ready.
                // zh-CN: 当 binder 就绪时, 唤醒所有等待者.
                mServiceWaiters.forEach { it.countDown() }
                mServiceWaiters.clear()
            } else {
                Log.w(TAG, "invalid binder for $componentName received")
                service = null
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "onServiceDisconnected: ${componentName.className}")
            service = null
        }
    }

    private val mUserServiceArgs by lazy {
        val pkg = GlobalAppContext.get().packageName
        Shizuku.UserServiceArgs(ComponentName(pkg, UserService::class.java.name))
            .processNameSuffix("shizuku-service-for-${pkg.substringAfterLast(".")}")
            // Recreate an older user process when its AIDL implementation changes after an update.
            // The extra revision also replaces a service from an earlier build with the same versionCode
            // (+ 6: attachHost, docs/shizuku-oom/ROADMAP.md D5).
            .version(BuildConfig.VERSION_CODE + 6)
            .daemon(false)
    }

    private val mBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (Shizuku.isPreV11()) {
            Log.d(TAG, "Shizuku pre-v11 is not supported")
        } else {
            Log.d(TAG, "Binder received")
            mHasBinder = true
        }
    }

    private val mBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Binder dead")
        mHasBinder = false
    }

    private val mRequestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED && requestCode == mRequestCode) {
            bindUserService()
        }
    }

    internal fun onCreate() {
        Log.d(TAG, "${App::class.java.simpleName} onCreate | Process=${App.getProcessNameCompat()}")
        Shizuku.addBinderReceivedListenerSticky(mBinderReceivedListener)
        Shizuku.addBinderDeadListener(mBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(mRequestPermissionResultListener)
    }

    internal fun bindUserServiceIfNeeded() {
        if (service?.asBinder()?.pingBinder() == true) {
            return
        }
        if (hasPermission()) {
            bindUserService()
        }
    }

    private fun bindUserService() {
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.bindUserService(mUserServiceArgs, mUserServiceConnection)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun unbindUserService() {
        runCatching {
            if (!Shizuku.isPreV11()) {
                Shizuku.unbindUserService(mUserServiceArgs, mUserServiceConnection, true)
            }
        }
    }

    @ScriptInterface
    fun isInstalled(context: Context) = getLaunchIntent(context) != null

    @ScriptInterface
    fun hasPermission() = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrElse { e ->
        if (e.message?.contains(Regex("binder .+n[o']t been received", RegexOption.IGNORE_CASE)) == false) {
            e.printStackTrace()
        }
        return@getOrElse false
    }

    @ScriptInterface
    fun isOperational() = isRunning() && hasPermission()

    @ScriptInterface
    fun isRunning() = mHasBinder

    @ScriptInterface
    fun hasService() = service != null

    /**
     * Returns the top activity of the foreground task, as ActivityManager.getRunningTasks(1) run by the Shizuku server,
     * or null when Shizuku is not operational or the query fails. The server can be used as soon as its binder is
     * received, while a user service process takes 1 s or more to start after AutoJs6 does.
     * zh-CN: 由 Shizuku 服务端执行 ActivityManager.getRunningTasks(1), 返回前台任务顶部的 Activity; Shizuku 不可用或查询失败时返回 null.
     * 服务端在收到其 binder 后即可使用, 而 AutoJs6 启动后用户服务进程需要 1 秒或更久才能启动.
     */
    @JvmStatic
    fun getTopActivityOrNull(): ComponentName? {
        if (!isOperational()) return null
        return runCatching {
            val query = mTopTaskQuery ?: return null
            val binder = SystemServiceHelper.getSystemService(query.serviceName) ?: return null
            val service = query.asInterface.invoke(null, ShizukuBinderWrapper(binder))
            val tasks = query.getTasks.invoke(service, *query.arguments) as List<*>?
            (tasks?.firstOrNull() as ActivityManager.RunningTaskInfo?)?.topActivity
        }.onFailure { Log.w(TAG, "getTopActivityOrNull failed", it) }.getOrNull()
    }

    @VisibleForTesting
    internal fun hasTopTaskQuery() = mTopTaskQuery != null

    private class TopTaskQuery(val serviceName: String, val asInterface: Method, val getTasks: Method, val arguments: Array<Any>)

    private val mTopTaskQuery: TopTaskQuery? by lazy {
        runCatching {
            val (serviceName, interfaceName) = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "activity_task" to "android.app.IActivityTaskManager"
                else -> Context.ACTIVITY_SERVICE to "android.app.IActivityManager"
            }
            val asInterface = getDeclaredMethods(Class.forName("$interfaceName\$Stub")).first { it.name == "asInterface" }
            getDeclaredMethods(Class.forName(interfaceName)).filter { it.name == "getTasks" }.firstNotNullOfOrNull { getTasks ->
                getTopTaskArguments(getTasks.parameterTypes)?.let { TopTaskQuery(serviceName, asInterface, getTasks, it) }
            }
        }.onFailure { Log.w(TAG, "Top task query is unavailable", it) }.getOrNull()
    }

    // The arguments ActivityManager.getRunningTasks(1) passes to getTasks, or null for an unknown signature.
    // zh-CN: ActivityManager.getRunningTasks(1) 传给 getTasks 的参数; 签名未知时为 null.
    private fun getTopTaskArguments(types: Array<Class<*>>): Array<Any>? {
        val int = Int::class.java
        val boolean = Boolean::class.java
        return when (types.toList()) {
            // maxNum, flags (Android 7-8.1).
            listOf(int, int) -> arrayOf(1, 0)
            // maxNum (Android 9-11).
            listOf(int) -> arrayOf(1)
            // maxNum, filterOnlyVisibleRecents, keepIntentExtra (Android 12-12L).
            listOf(int, boolean, boolean) -> arrayOf(1, false, false)
            // maxNum, filterOnlyVisibleRecents, keepIntentExtra, displayId (Android 13+).
            listOf(int, boolean, boolean, int) -> arrayOf(1, false, false, Display.INVALID_DISPLAY)
            else -> null
        }
    }

    private fun getDeclaredMethods(clazz: Class<*>): List<Method> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> HiddenApiBypass.getDeclaredMethods(clazz).filterIsInstance<Method>()
        else -> clazz.declaredMethods.toList()
    }

    @JvmStatic
    fun getServiceOrNull(): IUserService? {
        service?.let { return it }
        bindUserServiceIfNeeded()
        initializeShizukuServiceAndWait(5000L)
        service?.let { return it }
        return null
    }

    @ScriptInterface
    fun requestPermission() = Shizuku.requestPermission(mRequestCode)

    @ScriptInterface
    @JvmOverloads
    fun config(isRequest: Boolean? = null) = configWithContext(GlobalAppContext.get(), isRequest)

    fun configWithContext(context: Context, isRequest: Boolean? = null): Intent? = getLaunchIntent(context)?.also {
        it.startSafely(context)
        val message = when (isRequest) {
            true -> "${context.getString(R.string.text_grant_autojs6_access_in_shizuku_app)} (${context.getString(R.string.text_shizuku_service_may_need_to_be_run_first)})"
            false -> context.getString(R.string.text_revoke_autojs6_access_in_shizuku_app)
            else -> null
        }
        ViewUtils.showToast(context, message, true)
    }

    @ScriptInterface
    fun execCommand(cmd: String): ShellResult = execCommand(GlobalAppContext.get(), cmd)

    @ScriptInterface
    fun execCommand(context: Context, cmd: String): ShellResult {
        return execCommandWithAutoReconnect(context, cmd, allowRetry = true)
    }

    /**
     * Starts [command] as a long-lived process in the user service, with the uid Shizuku runs as (shell or root), and
     * returns it as a local [Process] whose streams are pipes. The process ends when it is destroyed or this process dies.
     * zh-CN: 在用户服务中以 Shizuku 的运行身份 (shell 或 root) 启动常驻进程 [command], 并以本地 [Process] 的形式返回, 其流为管道. 进程在被销毁或本进程
     * 死亡时结束.
     */
    @JvmOverloads
    fun newProcess(command: Array<String>, env: Array<String>? = null, dir: String? = null, pty: Boolean = false): Process {
        val context = GlobalAppContext.get()
        val client = Binder()
        val remote = try {
            connectedService(context).newShellProcess(command, env, dir, pty, client)
        } catch (e: DeadObjectException) {
            Log.d(TAG, "newProcess: binder died, reconnecting", e)
            this.service = null
            connectedService(context).newShellProcess(command, env, dir, pty, client)
        } ?: throw IllegalStateException(context.getString(R.string.error_unable_to_use_shizuku_service))
        return ShizukuShellProcess(remote, client)
    }

    private fun connectedService(context: Context): IUserService {
        if (!hasService() && hasPermission()) {
            onCreate()
            bindUserServiceIfNeeded()
            initializeShizukuServiceAndWait(SERVICE_BIND_TIMEOUT_MILLIS)
            if (service == null && !isMainThread()) {
                // Right after AutoJs6 is updated, Shizuku may keep a service record of the old APK whose process exits at
                // once; new binds wait on it until Shizuku gives up (30 s). Removing the record lets a fresh bind start.
                // zh-CN: AutoJs6 刚更新时, Shizuku 可能保留旧 APK 的服务记录, 其进程会立即退出; 新的绑定会一直等它, 直到 Shizuku 放弃 (30 秒).
                // 移除该记录后, 重新绑定即可启动服务.
                Log.d(TAG, "connectedService: no service after ${SERVICE_BIND_TIMEOUT_MILLIS} ms, rebinding")
                unbindUserService()
                bindUserServiceIfNeeded()
                initializeShizukuServiceAndWait(SERVICE_BIND_TIMEOUT_MILLIS)
            }
        }
        return service ?: when {
            !hasPermission() -> R.string.error_no_permission_to_access_shizuku
            !isRunning() -> R.string.error_shizuku_service_may_be_not_running
            else -> R.string.error_unable_to_use_shizuku_service
        }.let { throw IllegalStateException(context.getString(it)) }
    }

    private fun execCommandWithAutoReconnect(context: Context, cmd: String, allowRetry: Boolean): ShellResult {
        val service = connectedService(context)

        return try {
            ShellResult.fromJson(
                service.execCommand(
                    cmd.replace(Regex("^\\s*adb\\s+shell\\s+", RegexOption.IGNORE_CASE), "")
                )
            )
        } catch (e: Throwable) {
            Log.d(TAG, "execCommand failed", e)
            // Reconnect and retry once when binder is dead.
            // zh-CN: 当 binder 已死亡时, 自动重连并重试一次.
            if (allowRetry && e is DeadObjectException) {
                this.service = null
                runCatching {
                    onCreate()
                    bindUserServiceIfNeeded()
                    initializeShizukuServiceAndWait(5000L)
                }
                return execCommandWithAutoReconnect(context, cmd, allowRetry = false)
            }

            ShellResult().apply {
                code = 1
                error = e.message ?: when {
                    !hasPermission() -> context.getString(R.string.error_no_permission_to_access_shizuku)
                    !isRunning() -> context.getString(R.string.error_shizuku_service_may_be_not_running)
                    else -> ""
                }
                result = ""
            }.also { e.printStackTrace() }
        }
    }

    @ScriptInterface
    fun execCommand(cmdList: Array<String>) = execCommand(GlobalAppContext.get(), cmdList.joinToString("\n"))

    @ScriptInterface
    fun execCommand(context: Context, cmdList: Array<String>) = execCommand(context, cmdList.joinToString("\n"))

    internal fun onDestroy() {
        unbindUserService()
        Shizuku.removeBinderReceivedListener(mBinderReceivedListener)
        Shizuku.removeBinderDeadListener(mBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(mRequestPermissionResultListener)
    }

    fun getLaunchIntent(context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(SHIZUKU.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

    private fun initializeShizukuServiceAndWait(@Suppress("SameParameterValue") timeout: Long) {
        if (service?.asBinder()?.pingBinder() == true) {
            return
        }

        // Shizuku posts onServiceConnected() to the main looper, so waiting on the main thread always runs out the timeout.
        // Callers there fail at once instead, while the bind started before goes on (docs/shizuku-oom/ROADMAP.md D18).
        // zh-CN: Shizuku 把 onServiceConnected() 投递到主线程 Looper, 在主线程等待必然等满超时. 因此主线程调用方立即失败,
        // 之前发起的绑定继续进行 (docs/shizuku-oom/ROADMAP.md D18).
        if (isMainThread()) {
            Log.w(TAG, "User service is not connected yet, not waiting for it on the main thread")
            return
        }

        val latch = CountDownLatch(1)
        mServiceWaiters.add(latch)

        // Re-check after registering waiter to avoid missing a fast onServiceConnected().
        // zh-CN: 注册等待者后再次检查, 避免 onServiceConnected() 很快到来导致错过唤醒.
        if (service?.asBinder()?.pingBinder() == true) {
            mServiceWaiters.remove(latch)
            return
        }

        runCatching {
            latch.await(timeout, TimeUnit.MILLISECONDS)
        }.also {
            mServiceWaiters.remove(latch)
        }
    }

}
