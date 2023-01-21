package org.autojs.autojs.runtime.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.util.App
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import java.lang.ref.WeakReference

/**
 * Created by Stardust on 2017/4/2.
 * Modified by SuperMonster003 as of Jul 13, 2022.
 */
class AppUtils {

    private val mContext: Context
    private val mPackageManager: PackageManager

    @Volatile
    private var mCurrentActivity = WeakReference<Activity?>(null)

    @get:ScriptInterface
    val fileProviderAuthority: String?

    constructor(context: Context) {
        mContext = context
        mPackageManager = mContext.packageManager
        fileProviderAuthority = null
    }

    constructor(context: Context, fileProviderAuthority: String?) {
        mContext = context
        mPackageManager = mContext.packageManager
        this.fileProviderAuthority = fileProviderAuthority
    }

    @ScriptInterface
    fun launchPackage(packageName: String?): Boolean {
        try {
            packageName?.let {
                mPackageManager.getLaunchIntentForPackage(it)?.run {
                    mContext.startActivity(addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                }
            }
        } catch (_: Exception) {
            // Ignored
        }
        return false
    }

    @ScriptInterface
    fun sendLocalBroadcastSync(intent: Intent?) {
        intent?.let { LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(it) }
    }

    @ScriptInterface
    fun launchApp(appName: String) = getPackageName(appName)?.let { launchPackage(it) } ?: false

    @ScriptInterface
    fun getPackageName(appName: String): String? = mPackageManager.let {
        getInstalledApplications(mContext).forEach { applicationInfo ->
            if (appName.contentEquals(it.getApplicationLabel(applicationInfo))) {
                return applicationInfo.packageName
            }
        }
        return null
    }

    @ScriptInterface
    fun getAppName(packageName: String?) = try {
        packageName?.let { "${mPackageManager.getApplicationLabel(getApplicationInfoCompat(mPackageManager, it, 0))}" }
    } catch (ignore: NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun getApplicationInfoCompat(packageManager: PackageManager, packageName: String, flags: Int) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        }
        else -> packageManager.getApplicationInfo(packageName, flags)
    }

    @ScriptInterface
    @Deprecated("Use openAppSettings instead.", ReplaceWith("this.openAppSettings(packageName)"))
    fun openAppSetting(packageName: String): Boolean = openAppSettings(packageName)

    @ScriptInterface
    fun openAppSettings(packageName: String): Boolean = IntentUtils.goToAppDetailSettings(mContext, packageName)

    val currentActivity: Activity?
        get() {
            mCurrentActivity.run {
                Log.d("App", "getCurrentActivity: ${get()}")
                return get()
            }
        }

    @ScriptInterface
    fun isInstalled(packageName: String?) = try {
        getPackageInfoCompat(mPackageManager, packageName!!, 0) != null
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfoCompat(packageManager: PackageManager, packageName: String, flags: Int) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        }
        else -> packageManager.getPackageInfo(packageName, flags)
    }

    @ScriptInterface
    @Throws(Exception::class)
    fun ensureInstalled(packageName: String?) {
        try {
            getPackageInfoCompat(mPackageManager, packageName!!, 0)
        } catch (_: Exception) {
            throw Exception(mContext.getString(R.string.error_app_not_installed, packageName))
        }
    }

    @ScriptInterface
    @Throws(Exception::class)
    fun ensureInstalled(app: App) {
        try {
            getPackageInfoCompat(mPackageManager, app.packageName, 0)
        } catch (_: Exception) {
            throw Exception("${mContext.getString(R.string.error_app_not_installed, app.getAppName())} [ ${app.packageName} ]")
        }
    }

    @ScriptInterface
    fun isInstalledAndEnabled(packageName: String?) = try {
        getApplicationInfoCompat(mPackageManager, packageName!!, 0).enabled
    } catch (e: NameNotFoundException) {
        false
    }

    @ScriptInterface
    fun uninstall(packageName: String) {
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { mContext.startActivity(it) }
    }

    @ScriptInterface
    fun viewFile(path: String?) {
        if (path == null) {
            throw NullPointerException(
                mContext.getString(
                    R.string.error_method_called_with_null_argument,
                    "AppUtils.viewFile", "path"
                )
            )
        }
        IntentUtils.viewFile(mContext, path, fileProviderAuthority)
    }

    @ScriptInterface
    fun editFile(path: String?) {
        if (path == null) {
            throw NullPointerException(
                mContext.getString(
                    R.string.error_method_called_with_null_argument,
                    "AppUtils.editFile", "path"
                )
            )
        }
        IntentUtils.editFile(mContext, path, fileProviderAuthority)
    }

    @ScriptInterface
    fun openUrl(url: String) {
        val prefix = "http://".takeUnless { url.matches("^https?://".toRegex()) } ?: ""
        Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse(prefix + url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { mContext.startActivity(it) }
    }

    fun setCurrentActivity(currentActivity: Activity?) {
        currentActivity.let {
            mCurrentActivity = WeakReference(it)
            Log.d("App", "setCurrentActivity: $it")
        }
    }

    companion object {

        fun getInstalledApplications(context: Context): List<ApplicationInfo> = context.packageManager.let {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    it.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                }
                else -> {
                    @Suppress("DEPRECATION")
                    it.getInstalledApplications(PackageManager.GET_META_DATA)
                }
            }
        }

    }

}