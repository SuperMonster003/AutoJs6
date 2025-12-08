package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.ocr.PaddleOcrPluginHost
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.PluginInfo
import java.io.File

/**
 * Local installed plugin discovery (based on existing PaddleOcrPluginHost.discover).
 *
 * zh-CN: 本地已安装插件发现 (基于现有的 PaddleOcrPluginHost.discover).
 */
class InstalledPluginRepository {

    data class InstalledPlugin(
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
        val pluginInfo: PluginInfo?,
    )

    suspend fun discoverInstalled(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val discovered = runCatching { PaddleOcrPluginHost.discover(context) }.getOrElse { emptyList() }

        discovered.map { d ->
            val serviceInfo = d.serviceInfo
            val packageName = serviceInfo.packageName
            val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            val appLabel = appInfo?.loadLabel(pm)?.toString()
            val icon = appInfo?.loadIcon(pm)

            val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            val versionName = pkgInfo?.versionName ?: d.pluginInfo?.versionName ?: context.getString(R.string.text_unknown)
            val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: d.pluginInfo?.versionCode
            val firstInstallTime = pkgInfo?.firstInstallTime
            val lastUpdateTime = pkgInfo?.lastUpdateTime
            val packageSize = run calcPackageSize@{
                val baseApkSize = appInfo?.publicSourceDir?.let { File(it).length() }
                    ?: appInfo?.sourceDir?.let { File(it).length() }
                val splitApkTotalSize = when {
                    appInfo?.splitPublicSourceDirs != null -> appInfo.splitPublicSourceDirs!!.sumOf { File(it).length() }
                    appInfo?.splitSourceDirs != null -> appInfo.splitSourceDirs!!.sumOf { File(it).length() }
                    else -> 0L
                }
                baseApkSize?.let { it + splitApkTotalSize } ?: 0L
            }

            InstalledPlugin(
                packageName = packageName,
                title = d.pluginInfo?.name ?: appLabel ?: packageName,
                description = d.pluginInfo?.description,
                author = d.pluginInfo?.author,
                versionName = versionName,
                versionCode = versionCode,
                packageSize = packageSize,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = lastUpdateTime,
                icon = icon,
                pluginInfo = d.pluginInfo,
            )
        }
    }
}
