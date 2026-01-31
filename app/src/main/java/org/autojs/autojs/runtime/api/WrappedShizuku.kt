package org.autojs.autojs.runtime.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.App
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.core.shizuku.UserService
import org.autojs.autojs.util.App.SHIZUKU
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import rikka.shizuku.Shizuku
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

    private val mRequestCode = when {
        isInrt -> "shizuku-request-code-inrt".hashCode()
        else -> "shizuku-request-code".hashCode()
    }
    private var mHasBinder = false

    private val mServiceWaiters = CopyOnWriteArrayList<CountDownLatch>()

    private val mUserServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected: ${componentName.className}")
            if (binder?.pingBinder() == true) {
                service = IUserService.Stub.asInterface(binder)

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

    private val mRequestPermissionResultListener: (requestCode: Int, grantResult: Int) -> Unit = { requestCode: Int, grantResult: Int ->
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

    private fun execCommandWithAutoReconnect(context: Context, cmd: String, allowRetry: Boolean): ShellResult {
        if (!hasService() && hasPermission()) {
            onCreate()
            bindUserServiceIfNeeded()
            initializeShizukuServiceAndWait(5000L)
        }

        val service = service ?: when {
            !hasPermission() -> R.string.error_no_permission_to_access_shizuku
            !isRunning() -> R.string.error_shizuku_service_may_be_not_running
            else -> R.string.error_unable_to_use_shizuku_service
        }.let { throw IllegalStateException(context.getString(it)) }

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

    private fun initializeShizukuServiceAndWait(@Suppress("SameParameterValue") timeout: Long) {
        if (service?.asBinder()?.pingBinder() == true) {
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