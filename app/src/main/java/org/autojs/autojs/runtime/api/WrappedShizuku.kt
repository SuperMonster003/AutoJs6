package org.autojs.autojs.runtime.api

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import org.autojs.autojs.App
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.core.shizuku.UserService
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import rikka.shizuku.Shizuku
import org.autojs.autojs.runtime.api.AbstractShell.Result as ShellResult

/**
 * Created by SuperMonster003 on Oct 19, 2023.
 */
object WrappedShizuku {

    @JvmField
    var service: IUserService? = null

    private val TAG: String = WrappedShizuku::class.java.simpleName

    private val context
        get() = GlobalAppContext.get()

    private val mRequestCode = "shizuku-request-code".hashCode()

    private val mUserServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected: ${componentName.className}")
            if (binder?.pingBinder() == true) {
                service = IUserService.Stub.asInterface(binder)
            } else {
                Log.w(TAG, "invalid binder for $componentName received")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "onServiceDisconnected: ${componentName.className}")
        }
    }

    private val mUserServiceArgs = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
        .daemon(false)
        .processNameSuffix("service-for-${BuildConfig.APPLICATION_ID.split(".").lastOrNull() ?: "autojs"}")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val mBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (Shizuku.isPreV11()) {
            Log.d(TAG, "Shizuku pre-v11 is not supported")
        } else {
            Log.d(TAG, "Binder received")
        }
    }

    private val mBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Binder dead")
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
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.unbindUserService(mUserServiceArgs, mUserServiceConnection, true)
            }
        } catch (_: Throwable) {
            // Ignored.
        }
    }

    @ScriptInterface
    fun isInstalled() = getLaunchIntent() != null

    @ScriptInterface
    fun hasPermission() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false.also { e.printStackTrace() }
    }

    @ScriptInterface
    fun requestPermission() = Shizuku.requestPermission(mRequestCode)

    @ScriptInterface
    @JvmOverloads
    fun config(isRequest: Boolean? = null): Intent? {
        return getLaunchIntent()?.also {
            context.startActivity(it)
            val message = when (isRequest) {
                true -> "${context.getString(R.string.text_grant_autojs6_access_in_shizuku_app)} (${context.getString(R.string.text_shizuku_service_may_need_to_be_run_first)})"
                false -> context.getString(R.string.text_revoke_autojs6_access_in_shizuku_app)
                else -> null
            }
            ViewUtils.showToast(context, message, true)
        }
    }

    @ScriptInterface
    fun execCommand(cmd: String): ShellResult {
        ensureService()
        return try {
            ShellResult.fromJson(service!!.execCommand(cmd))
        } catch (e: Throwable) {
            ShellResult().apply {
                code = 1
                error = e.message ?: ""
                result = ""
            }.also { e.printStackTrace() }
        }
    }

    @ScriptInterface
    fun execCommand(cmdList: Array<String>): ShellResult {
        return execCommand(cmdList.joinToString("\n"))
    }

    @ScriptInterface
    fun ensureService() {
        service ?: throw IllegalStateException(context.getString(R.string.error_unable_to_use_shizuku_service))
    }

    internal fun onDestroy() {
        unbindUserService()
        Shizuku.removeBinderReceivedListener(mBinderReceivedListener)
        Shizuku.removeBinderDeadListener(mBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(mRequestPermissionResultListener)
    }

    private fun getLaunchIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(org.autojs.autojs.util.App.SHIZUKU.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

}