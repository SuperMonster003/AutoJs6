package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.util.StringUtils.truncateForSafeBinder
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import java.io.File

/**
 * Local installed plugin discovery.
 *
 * zh-CN: 本地已安装插件发现.
 *
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 * Modified by SuperMonster003 as of Feb 13, 2026.
 */
class InstalledPluginRepository {

    data class InstalledPlugin(
        val packageName: String,
        val title: String,
        val description: String?,
        val instructionHardCoded: String?,
        val author: String?,
        val versionName: String,
        val versionCode: Long?,
        val versionDate: String?,
        val requiresHostVersion: Long?,
        val supportedAbis: List<String>?,
        val packageSize: Long,
        val firstInstallTime: Long?,
        val lastUpdateTime: Long?,
        val icon: Drawable?,
        val pluginInfo: PluginInfo?,
        val bindError: Throwable?,
        val isStopped: Boolean,
    )

    private data class Discovery(
        val serviceInfo: android.content.pm.ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
    )

    suspend fun discoverInstalled(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val discovered = (
                PluginInfoServiceHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error)
                } + OcrPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error)
                }
                ).distinctBy { it.serviceInfo.packageName }

        discovered.map { d ->
            val serviceInfo = d.serviceInfo
            val packageName = serviceInfo.packageName
            val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            val appLabel = appInfo?.loadLabel(pm)?.toString()
            val icon = appInfo?.let { PluginInstalledIconResolver.load(context, it) }
            val isStopped = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            val versionName = pkgInfo?.versionName ?: d.pluginInfo?.versionName ?: context.getString(R.string.text_unknown)
            val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: d.pluginInfo?.versionCode
            val versionDate = d.pluginInfo?.versionDate
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
                description = PluginResourceResolver.resolve(
                    context,
                    packageName,
                    "plugin_description" to PluginResourceResolver.ResType.STRING,
                    d.pluginInfo?.description,
                ),
                instructionHardCoded = d.pluginInfo?.instruction?.truncateForSafeBinder(),
                author = d.pluginInfo?.author,
                versionName = versionName,
                versionCode = versionCode,
                versionDate = versionDate,
                requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(d.pluginInfo?.capabilities),
                supportedAbis = d.pluginInfo?.supportedAbis?.toList()?.let {
                    PluginAbiUtils.resolveSupportedAbisFromValue(it)
                },
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
