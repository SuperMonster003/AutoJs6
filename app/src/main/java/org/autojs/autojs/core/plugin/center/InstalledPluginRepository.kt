package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.BundleCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPluginHost
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.util.StringUtils.truncateForSafeBinder
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import java.io.File
import java.io.Serializable

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
        val isOcrService: Boolean,
        val isBarcodeService: Boolean,
        val serviceAction: String?,
        val isStopped: Boolean,
    )

    private data class Discovery(
        val serviceInfo: android.content.pm.ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
        val isOcrService: Boolean,
        val isBarcodeService: Boolean,
        val serviceAction: String?,
    )

    suspend fun discoverInstalled(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val discovered = (
                PluginInfoServiceHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = null)
                } + OcrPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = true, isBarcodeService = false, serviceAction = OcrPluginHost.ACTION_OCR)
                } + BarcodePluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = true, serviceAction = BarcodePluginHost.ACTION_BARCODE)
                } + OpenccPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = OpenccPluginHost.ACTION_OPENCC)
                } + PinyinPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = PinyinPluginHost.ACTION_PINYIN)
                } + Pinyin4jPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = Pinyin4jPluginHost.ACTION_PINYIN4J)
                } + MediainfoPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = MediainfoPluginHost.ACTION_MEDIAINFO)
                } + ExplorerActionPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = ExplorerActionPluginHost.ACTION_EXPLORER_ACTION)
                }
                ).mergeByPackage()

        discovered.map { d ->
            val serviceInfo = d.serviceInfo
            val packageName = serviceInfo.packageName
            val appInfo = getApplicationInfo(pm, packageName)
            val appLabel = appInfo?.loadLabel(pm)?.toString()
            val icon = appInfo?.let { PluginInstalledIconResolver.load(context, it) }
            val isStopped = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            val pluginName = PluginResourceResolver.resolveStringValue(context, packageName, d.pluginInfo?.name)
            val pluginDescription = PluginResourceResolver.resolveStringValue(context, packageName, d.pluginInfo?.description)
            val pluginInstruction = PluginResourceResolver.resolveStringValue(context, packageName, d.pluginInfo?.instruction)
            val pluginAuthor = PluginResourceResolver.resolveStringValue(context, packageName, d.pluginInfo?.author)
            val manifestAuthor = PluginResourceResolver.resolveStringValue(
                context,
                packageName,
                appInfo?.metaData?.let { BundleCompat.getSerializable(it, KEY_AUTHOR, Serializable::class.java) },
            )

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
                title = pluginName ?: appLabel ?: packageName,
                description = PluginResourceResolver.resolve(
                    context,
                    packageName,
                    "plugin_description" to PluginResourceResolver.ResType.STRING,
                    pluginDescription,
                ),
                instructionHardCoded = pluginInstruction?.truncateForSafeBinder(),
                author = pluginAuthor ?: manifestAuthor,
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
                isOcrService = d.isOcrService,
                isBarcodeService = d.isBarcodeService,
                serviceAction = d.serviceAction,
                isStopped = isStopped,
            )
        }
    }

    private fun List<Discovery>.mergeByPackage(): List<Discovery> {
        return groupBy { it.serviceInfo.packageName }.values.map { discoveries ->
            val preferred = discoveries.firstOrNull { it.pluginInfo != null } ?: discoveries.first()
            preferred.copy(
                isOcrService = discoveries.any { it.isOcrService },
                isBarcodeService = discoveries.any { it.isBarcodeService },
                serviceAction = preferred.serviceAction ?: discoveries.firstNotNullOfOrNull { it.serviceAction },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getApplicationInfo(pm: PackageManager, packageName: String): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }
        }.getOrNull()
    }

    companion object {
        private const val KEY_AUTHOR = "org.autojs.plugin.info.AUTHOR"
    }

}
