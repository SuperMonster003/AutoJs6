package org.autojs.autojs.runtime.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.ui.doc.DocumentationActivity
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.util.App
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import java.lang.ref.WeakReference
import java.net.URI
import org.autojs.autojs.runtime.api.augment.app.App as AugmentableApp

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of Jul 13, 2022.
 */
class AppUtils(context: Context, @get:ScriptInterface val fileProviderAuthority: String? = AppFileProvider.AUTHORITY) {

    private val mContext: Context = context
    private val mPackageManager = mContext.packageManager

    @Volatile
    private var mCurrentActivityRef = WeakReference<Activity?>(null)

    @ScriptInterface
    fun launch(o: Any?) = when (o) {
        is App -> launchPackage(o)
        is String -> launchPackage(o)
        null -> launchPackage(null)
        else -> throw IllegalArgumentException(mContext.getString(R.string.error_illegal_argument, "o for app.launch()", o))
    }

    @ScriptInterface
    fun launchPackage(app: App) = launchPackage(app.packageName)

    /**
     * @param o < alias | packageName >
     */
    @ScriptInterface
    fun launchPackage(o: String?) = runCatching {
        o ?: return false
        val nicePackageName = getAppByAlias(alias = o)?.packageName ?: (/* packageName = */ o)
        val intent = mPackageManager.getLaunchIntentForPackage(nicePackageName) ?: return false
        mContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    @ScriptInterface
    @Suppress("unused")
    fun sendLocalBroadcastSync(intent: Intent?) {
        AugmentableApp.sendLocalBroadcastSyncInternal(intent)
    }

    @ScriptInterface
    fun launchApp(appName: String) = getPackageName(appName)?.let { launchPackage(it) } == true

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
    } catch (_: NameNotFoundException) {
        null
    }

    private fun getApplicationInfoCompat(packageManager: PackageManager, packageName: String, @Suppress("SameParameterValue") flags: Int) = when {
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
            mCurrentActivityRef.run {
                return get().also { Log.d("App", "getCurrentActivity: $it") }
            }
        }

    @ScriptInterface
    fun isInstalled(packageName: String?) = try {
        getPackageInfoCompat(mPackageManager, packageName!!, 0) != null
    } catch (_: Exception) {
        false
    }

    private fun getPackageInfoCompat(packageManager: PackageManager, packageName: String, @Suppress("SameParameterValue") flags: Int) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            packageManager.getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
        }
        else -> packageManager.getPackageInfo(packageName, flags)
    }

    @ScriptInterface
    @Throws(Exception::class)
    @Suppress("unused")
    fun ensureInstalled(packageName: String?) {
        runCatching {
            getPackageInfoCompat(mPackageManager, packageName!!, 0)
        }.getOrElse {
            throw Exception(mContext.getString(R.string.error_app_not_installed_with_name, packageName))
        }
    }

    @ScriptInterface
    @Throws(Exception::class)
    fun ensureInstalled(app: App) {
        runCatching {
            getPackageInfoCompat(mPackageManager, app.packageName, 0)
        }.getOrElse {
            throw Exception("${mContext.getString(R.string.error_app_not_installed_with_name, app.getAppName())} [ ${app.packageName} ]")
        }
    }

    @ScriptInterface
    @Suppress("unused")
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
        AugmentableApp.openUrlInternal(url)
    }

    @ScriptInterface
    fun openUrl(url: URI) = openUrl(url.host)

    fun setCurrentActivity(currentActivity: Activity?) {
        currentActivity.let {
            mCurrentActivityRef = WeakReference(it)
            Log.d("App", "setCurrentActivity: $it")
        }
    }

    private fun getAppByAlias(alias: String) = App.getAppByAlias(alias)

    companion object {

        const val ACTIVITY_SHORT_FORM_PREFIX = "class."
        const val BROADCAST_SHORT_FORM_PREFIX = "broadcast."

        private val packageManager by lazy {
            GlobalAppContext.get().packageManager
        }

        enum class ActivityShortForm(val shortName: String, val classType: Class<out Activity>) {

            SETTINGS("settings", PreferencesActivity::class.java),
            PREFERENCES("preferences", PreferencesActivity::class.java),
            PREF("pref", PreferencesActivity::class.java),

            DOCUMENTATION("documentation", DocumentationActivity::class.java),
            DOCS("docs", DocumentationActivity::class.java),
            DOC("doc", DocumentationActivity::class.java),

            CONSOLE("console", LogActivity::class.java),
            LOG("log", LogActivity::class.java),

            HOMEPAGE("homepage", MainActivity::class.java),
            HOME("home", MainActivity::class.java),

            ABOUT("about", AboutActivity::class.java),

            BUILD("build", BuildActivity::class.java),
            ;

            val fullName
                get() = ACTIVITY_SHORT_FORM_PREFIX + shortName

        }

        enum class BroadcastShortForm(val shortName: String, private val classType: Class<out FloatyWindow>) {

            INSPECT_LAYOUT_BOUNDS("inspect_layout_bounds", LayoutBoundsFloatyWindow::class.java),
            LAYOUT_BOUNDS("layout_bounds", LayoutBoundsFloatyWindow::class.java),
            BOUNDS("bounds", LayoutBoundsFloatyWindow::class.java),

            INSPECT_LAYOUT_HIERARCHY("inspect_layout_hierarchy", LayoutHierarchyFloatyWindow::class.java),
            LAYOUT_HIERARCHY("layout_hierarchy", LayoutHierarchyFloatyWindow::class.java),
            HIERARCHY("hierarchy", LayoutHierarchyFloatyWindow::class.java),
            ;

            val fullName: String
                get() = BROADCAST_SHORT_FORM_PREFIX + shortName

            val className: String
                get() = classType.name

        }

        @JvmStatic
        @ScriptInterface
        @Suppress("unused")
        fun isBroadcastShortForm(s: String) = (@Suppress("EnumValuesSoftDeprecate") BroadcastShortForm.values()).any { it.shortName.contentEquals(s) }

        @JvmStatic
        @ScriptInterface
        @Suppress("unused")
        fun isActivityShortForm(s: String) = (@Suppress("EnumValuesSoftDeprecate") ActivityShortForm.values()).any { it.shortName.contentEquals(s) }

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

        @JvmStatic
        fun getInstalledAppIcon(packageName: String): Drawable? = runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
        }.getOrNull()

        @JvmStatic
        fun generateNextVersionInfo(packageName: String): SimpleVersionInfo? {
            return getInstalledVersionInfo(packageName)?.let {
                val currentVersionName = it.versionName
                val newVersionName = incrementVersionName(currentVersionName)
                val currentVersionCode = it.versionCode
                val newVersionCode = currentVersionCode + 1
                SimpleVersionInfo(newVersionName, newVersionCode)
            }
        }

        fun getInstalledVersionInfo(packageName: String): SimpleVersionInfo? = runCatching {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionCode = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> packageInfo.longVersionCode
                else -> @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            }
            val versionName = packageInfo.versionName
            SimpleVersionInfo(versionName, versionCode)
        }.getOrNull()

        private fun incrementVersionName(versionName: String): String {
            val numericSuffixPattern = Regex("(\\d+)$")
            return when {
                versionName.contains(numericSuffixPattern) -> {
                    val matchResult = numericSuffixPattern.find(versionName)!!
                    val numberPart = matchResult.value
                    versionName.replace(numericSuffixPattern, "${numberPart.toLong() + 1}")
                }
                versionName.contains(Regex("\\w$")) -> "${versionName}2"
                else -> versionName
            }
        }

        data class SimpleVersionInfo(@JvmField val versionName: String, @JvmField val versionCode: Long) {
            @JvmField
            val versionCodeString = versionCode.toString()
        }

    }

}