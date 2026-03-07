package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs6.R
import java.io.File

class LegacyInstalledPluginRepository {

    data class LegacyInstalledPlugin(
        val packageName: String,
        val title: String,
        val description: String?,
        val author: String?,
        val versionName: String,
        val versionCode: Long?,
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
            val packageName = appInfo.packageName
            val label = appInfo.loadLabel(pm).takeUnless { it.isBlank() }?.toString()
            val icon = appInfo.loadIcon(pm)
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            val versionName = pkgInfo?.versionName ?: context.getString(R.string.text_unknown)
            val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
            val firstInstallTime = pkgInfo?.firstInstallTime
            val lastUpdateTime = pkgInfo?.lastUpdateTime
            val packageSize = calcPackageSize(appInfo)

            val metaDescriptionRes = meta.getInt(KEY_DESCRIPTION_RESOURCE, 0)
            val description = when {
                metaDescriptionRes != 0 -> {
                    val metaContext = context.createPackageContext(packageName, 0)
                    val configuration = context.resources.configuration.apply {
                        setLocale(Language.getPrefLanguage().locale)
                    }
                    val localizedMetaContext = metaContext.createConfigurationContext(configuration)
                    localizedMetaContext.getString(metaDescriptionRes)
                }
                else -> {
                    meta.getString(KEY_DESCRIPTION)
                }
            }

            LegacyInstalledPlugin(
                packageName = packageName,
                title = label ?: packageName,
                description = description,
                author = meta.getString(KEY_AUTHOR),
                versionName = versionName,
                versionCode = versionCode,
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

    companion object {
        private const val KEY_REGISTRY = "org.autojs.plugin.sdk.registry"
        private const val KEY_AUTHOR = "org.autojs.plugin.info.AUTHOR"
        private const val KEY_DESCRIPTION_RESOURCE = "org.autojs.plugin.info.DESCRIPTION_RESOURCE"
        private const val KEY_DESCRIPTION = "org.autojs.plugin.info.DESCRIPTION"
    }
}
