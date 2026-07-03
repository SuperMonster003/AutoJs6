package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.util.StringUtils.truncateForSafeBinder
import org.autojs.autojs6.R
import java.io.File

class LegacyInstalledPluginRepository {

    data class LegacyInstalledPlugin(
        val packageName: String,
        val title: String,
        val description: String?,
        val instructionHardCoded: String?,
        val author: String?,
        val versionName: String,
        val versionCode: Long?,
        val versionDate: String?,
        val supportedAbis: List<String>?,
        val packageSize: Long,
        val firstInstallTime: Long?,
        val lastUpdateTime: Long?,
        val icon: Drawable?,
        val registryClass: String?,
        val isStopped: Boolean,
    )

    @Suppress("DEPRECATION")
    suspend fun discoverInstalled(context: Context): List<LegacyInstalledPlugin> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(ApplicationInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledApplications(flags)
        }
        apps.mapNotNull { appInfo ->
            val meta = appInfo.metaData ?: return@mapNotNull null
            val registry = meta.getString(KEY_REGISTRY) ?: return@mapNotNull null
            val description = meta.getString(KEY_DESCRIPTION)
            val versionDate = meta.getString(KEY_VERSION_DATE)
            val pluginName = meta.getString(KEY_NAME)
            val author = meta.getString(KEY_AUTHOR)
            val instructionHardCoded = meta.getString(KEY_INSTRUCTION)?.truncateForSafeBinder()
            val forceIgnoreLocalIcon = meta.getBoolean(KEY_FORCE_IGNORE_LOCAL_ICON, false)

            val packageName = appInfo.packageName
            val label = appInfo.loadLabel(pm).takeUnless { it.isBlank() }?.toString()
            val icon = if (forceIgnoreLocalIcon) null else PluginInstalledIconResolver.load(context, appInfo).takeUnless {
                isDefaultAndroidAppIcon(pm, appInfo, it)
            }
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            val versionName = pkgInfo?.versionName ?: context.getString(R.string.text_unknown)
            val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
            val firstInstallTime = pkgInfo?.firstInstallTime
            val lastUpdateTime = pkgInfo?.lastUpdateTime
            val packageSize = calcPackageSize(appInfo)

            LegacyInstalledPlugin(
                packageName = packageName,
                title = pluginName ?: label ?: packageName,
                description = description,
                instructionHardCoded = instructionHardCoded,
                author = author,
                versionName = versionName,
                versionCode = versionCode,
                versionDate = versionDate,
                supportedAbis = null,
                packageSize = packageSize,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = lastUpdateTime,
                icon = icon,
                registryClass = registry,
                isStopped = isStopped,
            )
        }
    }

    private fun calcPackageSize(appInfo: ApplicationInfo?): Long {
        val baseApkSize = appInfo?.publicSourceDir?.let { File(it).length() }
            ?: appInfo?.sourceDir?.let { File(it).length() }
        val splitApkTotalSize = when {
            appInfo?.splitPublicSourceDirs != null -> appInfo.splitPublicSourceDirs!!.sumOf { File(it).length() }
            appInfo?.splitSourceDirs != null -> appInfo.splitSourceDirs!!.sumOf { File(it).length() }
            else -> 0L
        }
        return baseApkSize?.let { it + splitApkTotalSize } ?: 0L
    }

    private fun isDefaultAndroidAppIcon(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        icon: Drawable?,
    ): Boolean {
        if (icon == null) return true
        if (appInfo.icon == 0) return true
        if (isAndroidFrameworkIconResource(pm, appInfo)) return true

        val systemDefaultIcon = runCatching { pm.defaultActivityIcon }.getOrNull() ?: return false
        val currentState = icon.constantState
        val defaultState = systemDefaultIcon.constantState
        if (currentState != null && defaultState != null && currentState == defaultState) {
            return true
        }
        return drawablesPixelEquals(icon, systemDefaultIcon)
    }

    private fun isAndroidFrameworkIconResource(pm: PackageManager, appInfo: ApplicationInfo): Boolean {
        val iconResId = appInfo.icon
        if (iconResId == 0) return true
        val appRes = runCatching { pm.getResourcesForApplication(appInfo.packageName) }.getOrNull() ?: return false
        val resPackage = runCatching { appRes.getResourcePackageName(iconResId) }.getOrNull() ?: return false
        return resPackage.equals("android", ignoreCase = true)
    }

    private fun drawablesPixelEquals(first: Drawable, second: Drawable): Boolean {
        val a = first.renderToBitmap(DEFAULT_ICON_COMPARE_SIZE_PX, DEFAULT_ICON_COMPARE_SIZE_PX)
        val b = second.renderToBitmap(DEFAULT_ICON_COMPARE_SIZE_PX, DEFAULT_ICON_COMPARE_SIZE_PX)
        if (a.width != b.width || a.height != b.height) return false
        val p1 = IntArray(a.width * a.height)
        val p2 = IntArray(b.width * b.height)
        a.getPixels(p1, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(p2, 0, b.width, 0, 0, b.width, b.height)
        return p1.contentEquals(p2)
    }

    private fun Drawable.renderToBitmap(width: Int, height: Int): Bitmap {
        val safeWidth = if (width > 0) width else DEFAULT_ICON_COMPARE_SIZE_PX
        val safeHeight = if (height > 0) height else DEFAULT_ICON_COMPARE_SIZE_PX
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = constantState?.newDrawable()?.mutate() ?: mutate()
        drawable.setBounds(0, 0, safeWidth, safeHeight)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val DEFAULT_ICON_COMPARE_SIZE_PX = 48
        private const val KEY_REGISTRY = "org.autojs.plugin.sdk.registry"
        private const val KEY_AUTHOR = "org.autojs.plugin.info.AUTHOR"
        private const val KEY_DESCRIPTION = "org.autojs.plugin.info.DESCRIPTION"
        private const val KEY_VERSION_DATE = "org.autojs.plugin.info.VERSION_DATE"
        private const val KEY_NAME = "org.autojs.plugin.info.NAME"
        private const val KEY_INSTRUCTION = "org.autojs.plugin.info.INSTRUCTION"
        private const val KEY_FORCE_IGNORE_LOCAL_ICON = "org.autojs.plugin.info.FORCE_IGNORE_LOCAL_ICON"
    }
}
