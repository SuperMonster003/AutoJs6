package org.autojs.autojs.runtime.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.*
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.util.App
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import java.lang.ref.WeakReference
import java.net.URI

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

    private fun getApplicationInfoCompat(packageManager: PackageManager, packageName: String, flags: Int) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            packageManager.getApplicationInfo(packageName, ApplicationInfoFlags.of(flags.toLong()))
        }
        else -> packageManager.getApplicationInfo(packageName, flags)
    }

    // @ScriptInterface
    // @Deprecated("Use openAppSettings instead.", ReplaceWith("this.launchSettings(packageName)"))
    // fun openAppSetting(packageName: String): Boolean = launchSettings(packageName)

    @ScriptInterface
    fun launchSettings(packageName: String): Boolean = IntentUtils.goToAppDetailSettings(mContext, packageName)

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

    private fun getPackageInfoCompat(packageManager: PackageManager, packageName: String, flags: Int) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            packageManager.getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
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
    fun viewFile(path: String) = IntentUtils.viewFile(mContext, path, fileProviderAuthority)

    @ScriptInterface
    fun editFile(path: String) = IntentUtils.editFile(mContext, path, fileProviderAuthority)

    @ScriptInterface
    fun openUrl(url: String) {
        val prefix = "http://".takeUnless { url.contains("://") } ?: ""
        Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse(prefix + url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { mContext.startActivity(it) }
    }

    @ScriptInterface
    fun openUrl(url: URI) = openUrl(url.host)

    fun setCurrentActivity(currentActivity: Activity?) {
        currentActivity.let {
            mCurrentActivity = WeakReference(it)
            Log.d("App", "setCurrentActivity: $it")
        }
    }

    companion object {

        const val activityShortFormPrefix = "class."

        const val broadcastShortFormPrefix = "broadcast."

        enum class ActivityShortForm(val shortName: String) {

            SETTINGS("settings"),
            PREFERENCES("preferences"),
            PREF("pref"),

            DOCUMENTATION("documentation"),
            DOCS("docs"),
            DOC("doc"),

            CONSOLE("console"),
            LOG("log"),

            HOMEPAGE("homepage"),
            HOME("home"),

            ABOUT("about"),

            BUILD("build"),
            ;

            val fullName
                get() = activityShortFormPrefix + shortName

        }

        enum class BroadcastShortForm(val shortName: String) {

            INSPECT_LAYOUT_BOUNDS("inspect_layout_bounds"),
            LAYOUT_BOUNDS("layout_bounds"),
            BOUNDS("bounds"),
            INSPECT_LAYOUT_HIERARCHY("inspect_layout_hierarchy"),
            LAYOUT_HIERARCHY("layout_hierarchy"),
            HIERARCHY("hierarchy"),
            ;

            val fullName
                get() = broadcastShortFormPrefix + shortName

        }

        @JvmStatic
        fun isBroadcastShortForm(s: String) = BroadcastShortForm.entries.any { it.shortName.contentEquals(s) }

        @JvmStatic
        fun isActivityShortForm(s: String) = ActivityShortForm.entries.any { it.shortName.contentEquals(s) }

        fun getInstalledApplications(context: Context): List<ApplicationInfo> = context.packageManager.let {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    it.getInstalledApplications(ApplicationInfoFlags.of(GET_META_DATA.toLong()))
                }
                else -> {
                    it.getInstalledApplications(GET_META_DATA)
                }
            }
        }

    }

}