package org.autojs.autojs.runtime.api

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import org.autojs.autojs.App
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.core.shizuku.UserService
import org.autojs.autojs6.BuildConfig
import rikka.shizuku.Shizuku
import org.autojs.autojs.runtime.api.AbstractShell.Result as ShellResult

/**
 * Created by SuperMonster003 on Oct 19, 2023.
 */
object WrappedShizuku {

    @JvmField
    var service: IUserService? = null

    private val TAG: String = WrappedShizuku::class.java.simpleName

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
            // ... ...
        }
    }

    private val mUserServiceArgs = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
        .daemon(false)
        .processNameSuffix("service-for-${BuildConfig.APPLICATION_ID.split(".").lastOrNull() ?: "autojs"}")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val mBinderReceivedListener = {
        // if (Shizuku.isPreV11()) {
        //     binding.text1.setText("Shizuku pre-v11 is not supported")
        // } else {
        //     binding.text1.setText("Binder received")
        // }
    }

    private val mBinderDeadListener = {
        // binding.text1.setText("Binder dead")
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

    private fun bindUserService() {
        val res = StringBuilder()
        try {
            if (Shizuku.getVersion() < 10) {
                res.append("requires Shizuku API 10")
            } else {
                Shizuku.bindUserService(mUserServiceArgs, mUserServiceConnection)
            }
        } catch (tr: Throwable) {
            tr.printStackTrace()
            res.append(tr)
        }
        // binding.text3.setText(res.toString().trim { it <= ' ' })
    }

    private fun unbindUserService() {
        val res = StringBuilder()
        try {
            if (Shizuku.getVersion() < 10) {
                res.append("requires Shizuku API 10")
            } else {
                Shizuku.unbindUserService(mUserServiceArgs, mUserServiceConnection, true)
            }
        } catch (tr: Throwable) {
            tr.printStackTrace()
            res.append(tr)
        }
        // binding.text3.setText(res.toString().trim { it <= ' ' })
    }

    internal fun checkShizukuPermission() = try {
        when {
            Shizuku.isPreV11() -> false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> true.also { bindUserService() }
            Shizuku.shouldShowRequestPermissionRationale() -> false
            else -> false.also { Shizuku.requestPermission(mRequestCode) }
        }
    } catch (e: Throwable) {
        false.also { e.printStackTrace() }
    }

    @ScriptInterface
    fun execCommand(cmd: String): ShellResult {
        ensureService()
        return try {
            ShellResult.fromJson(service!!.execCommand(cmd))
        } catch (e: Exception) {
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
        service ?: throw IllegalStateException("Shizuku service is not available")
    }

    internal fun onDestroy() {
        unbindUserService()
        Shizuku.removeBinderReceivedListener(mBinderReceivedListener)
        Shizuku.removeBinderDeadListener(mBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(mRequestPermissionResultListener)
    }

}