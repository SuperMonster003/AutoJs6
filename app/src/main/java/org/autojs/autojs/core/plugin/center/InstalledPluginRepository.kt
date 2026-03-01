package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.ocr.PaddleOcrPluginHost
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.api.PluginInfo
import java.io.File

/**
 * Local installed plugin discovery (based on existing PaddleOcrPluginHost.discover).
 *
 * zh-CN: 本地已安装插件发现 (基于现有的 PaddleOcrPluginHost.discover).
 *
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 * Modified by SuperMonster003 as of Feb 13, 2026.
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
        val bindError: Throwable?,
        val isStopped: Boolean,
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
            val isStopped = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

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
                description = PluginDescriptionResolver.resolve(context, packageName, d.pluginInfo?.description),
                author = d.pluginInfo?.author,
                versionName = versionName,
                versionCode = versionCode,
                packageSize = packageSize,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = lastUpdateTime,
                icon = icon,
                pluginInfo = d.pluginInfo,
                bindError = d.error,
                isStopped = isStopped,
            )
        }
    }
}
