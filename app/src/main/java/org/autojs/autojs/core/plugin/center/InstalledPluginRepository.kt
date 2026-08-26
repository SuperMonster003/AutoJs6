package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.BundleCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import org.autojs.plugin.common.api.PluginActions
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
        val serviceInfo: ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
        val isOcrService: Boolean,
        val isBarcodeService: Boolean,
        val serviceAction: String?,
    )

    /**
     * Builds a package-manager-only snapshot without starting or binding any plugin process.
     * This is intentionally lightweight so Plugin Center can render installed entries first
     * and replace their metadata after the complete discovery finishes in the background.
     */
    suspend fun discoverInstalledSnapshot(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        queryDeclaredPluginServices(context)
            .mergeByPackage()
            .mapIsolated(context, lightweight = true)
    }

    suspend fun discoverInstalled(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val declared = queryDeclaredPluginServices(context)
        val bound = discoverBoundPluginServices(context, declared)

        // A failure in one discovery source must not make packages from that source disappear.
        // Package-manager declarations are retained as a metadata fallback when necessary.
        (bound + declared)
            .mergeByPackage()
            .mapIsolated(context, lightweight = false)
    }

    private suspend fun discoverBoundPluginServices(
        context: Context,
        declared: List<Discovery>,
    ): List<Discovery> = supervisorScope {
        val semaphore = Semaphore(MAX_PARALLEL_PLUGIN_PROBES)
        declared
            .groupBy { it.serviceInfo.packageName }
            .values
            .map { candidates ->
                async {
                    semaphore.withPermit {
                        val preferredAction = PluginDiscoveryEndpointPolicy.selectAction(
                            candidates.map { it.serviceAction },
                        )
                        val preferred = candidates.first { it.serviceAction == preferredAction }
                        val packageName = preferred.serviceInfo.packageName
                        try {
                            discoverPackage(context, packageName, preferred)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed to discover plugin services for $packageName", e)
                            PluginCenterProcessLogger.i(
                                "Failed to discover plugin services for $packageName: ${e.message ?: e.javaClass.simpleName}.",
                            )
                            listOf(preferred.copy(error = e))
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private suspend fun discoverPackage(
        context: Context,
        packageName: String,
        preferred: Discovery,
    ): List<Discovery> = when (preferred.serviceAction) {
        null -> PluginInfoServiceHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = null)
        }
        OcrPluginHost.ACTION_OCR -> OcrPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = true, isBarcodeService = false, serviceAction = OcrPluginHost.ACTION_OCR)
        }
        BarcodePluginHost.ACTION_BARCODE -> BarcodePluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = true, serviceAction = BarcodePluginHost.ACTION_BARCODE)
        }
        OpenccPluginHost.ACTION_OPENCC -> OpenccPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = OpenccPluginHost.ACTION_OPENCC)
        }
        PinyinPluginHost.ACTION_PINYIN -> PinyinPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = PinyinPluginHost.ACTION_PINYIN)
        }
        Pinyin4jPluginHost.ACTION_PINYIN4J -> Pinyin4jPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = Pinyin4jPluginHost.ACTION_PINYIN4J)
        }
        MediainfoPluginHost.ACTION_MEDIAINFO -> MediainfoPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = MediainfoPluginHost.ACTION_MEDIAINFO)
        }
        ExplorerActionPluginHost.ACTION_EXPLORER_ACTION -> ExplorerActionPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = ExplorerActionPluginHost.ACTION_EXPLORER_ACTION)
        }
        else -> listOf(preferred)
    }

    private fun queryDeclaredPluginServices(context: Context): List<Discovery> {
        val specs = listOf(
            ServiceQuery(PluginActions.INFO),
            ServiceQuery(OcrPluginHost.ACTION_OCR, isOcrService = true),
            ServiceQuery(BarcodePluginHost.ACTION_BARCODE, isBarcodeService = true),
            ServiceQuery(OpenccPluginHost.ACTION_OPENCC),
            ServiceQuery(PinyinPluginHost.ACTION_PINYIN),
            ServiceQuery(Pinyin4jPluginHost.ACTION_PINYIN4J),
            ServiceQuery(MediainfoPluginHost.ACTION_MEDIAINFO),
            ServiceQuery(ExplorerActionPluginHost.ACTION_EXPLORER_ACTION),
        )
        return specs.flatMap { spec ->
            runCatching {
                queryIntentServices(context, spec.action).map { serviceInfo ->
                    Discovery(
                        serviceInfo = serviceInfo,
                        pluginInfo = null,
                        error = null,
                        isOcrService = spec.isOcrService,
                        isBarcodeService = spec.isBarcodeService,
                        serviceAction = spec.action.takeUnless { it == PluginActions.INFO },
                    )
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to query declared plugin services for ${spec.action}", error)
            }.getOrDefault(emptyList())
        }
    }

    private fun queryIntentServices(context: Context, action: String): List<ServiceInfo> {
        val intent = Intent(action).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun List<Discovery>.mapIsolated(
        context: Context,
        lightweight: Boolean,
    ): List<InstalledPlugin> = map { discovery ->
        runCatching {
            buildInstalledPlugin(context, discovery, lightweight)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read plugin metadata for ${discovery.serviceInfo.packageName}", error)
            PluginCenterProcessLogger.i(
                "Failed to read plugin metadata for ${discovery.serviceInfo.packageName}: ${error.message ?: error.javaClass.simpleName}.",
            )
            buildFallbackInstalledPlugin(context, discovery, error)
        }
    }

    private fun buildFallbackInstalledPlugin(
        context: Context,
        discovery: Discovery,
        metadataError: Throwable,
    ): InstalledPlugin {
        val packageName = discovery.serviceInfo.packageName
        return InstalledPlugin(
            packageName = packageName,
            title = packageName,
            description = null,
            instructionHardCoded = null,
            author = null,
            versionName = runCatching { context.getString(R.string.text_unknown) }.getOrDefault("unknown"),
            versionCode = null,
            versionDate = null,
            requiresHostVersion = null,
            supportedAbis = null,
            packageSize = 0L,
            firstInstallTime = null,
            lastUpdateTime = null,
            icon = null,
            pluginInfo = null,
            bindError = discovery.error ?: metadataError,
            isOcrService = discovery.isOcrService,
            isBarcodeService = discovery.isBarcodeService,
            serviceAction = discovery.serviceAction,
            isStopped = runCatching {
                (discovery.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            }.getOrDefault(false),
        )
    }

    private fun buildInstalledPlugin(
        context: Context,
        discovery: Discovery,
        lightweight: Boolean,
    ): InstalledPlugin {
        val pm = context.packageManager
        val serviceInfo = discovery.serviceInfo
        val packageName = serviceInfo.packageName
        val appInfo = getApplicationInfo(pm, packageName) ?: serviceInfo.applicationInfo
        val appLabel = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull()
        val icon = if (lightweight) null else runCatching { PluginInstalledIconResolver.load(context, appInfo) }.getOrNull()
        val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val pluginInfo = discovery.pluginInfo
        val pluginName = if (lightweight) null else PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.name)
        val pluginDescription = if (lightweight) null else PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.description)
        val pluginInstruction = if (lightweight) null else PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.instruction)
        val pluginAuthor = if (lightweight) null else PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.author)
        val manifestAuthor = if (lightweight) null else runCatching {
            PluginResourceResolver.resolveStringValue(
                context,
                packageName,
                appInfo.metaData?.let { BundleCompat.getSerializable(it, KEY_AUTHOR, Serializable::class.java) },
            )
        }.getOrNull()

        val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        val versionName = pkgInfo?.versionName ?: pluginInfo?.versionName ?: context.getString(R.string.text_unknown)
        val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: pluginInfo?.versionCode
        val packageSize = calculatePackageSize(appInfo)

        return InstalledPlugin(
            packageName = packageName,
            title = pluginName ?: appLabel ?: packageName,
            description = if (lightweight) null else PluginResourceResolver.resolve(
                context,
                packageName,
                "plugin_description" to PluginResourceResolver.ResType.STRING,
                pluginDescription,
            ),
            instructionHardCoded = pluginInstruction?.truncateForSafeBinder(),
            author = pluginAuthor ?: manifestAuthor,
            versionName = versionName,
            versionCode = versionCode,
            versionDate = pluginInfo?.versionDate,
            requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(pluginInfo?.capabilities),
            supportedAbis = pluginInfo?.supportedAbis?.toList()?.let(PluginAbiUtils::resolveSupportedAbisFromValue),
            packageSize = packageSize,
            firstInstallTime = pkgInfo?.firstInstallTime,
            lastUpdateTime = pkgInfo?.lastUpdateTime,
            icon = icon,
            pluginInfo = pluginInfo,
            bindError = discovery.error,
            isOcrService = discovery.isOcrService,
            isBarcodeService = discovery.isBarcodeService,
            serviceAction = discovery.serviceAction,
            isStopped = isStopped,
        )
    }

    private fun calculatePackageSize(appInfo: ApplicationInfo): Long = runCatching {
        val baseApkSize = appInfo.publicSourceDir?.let { File(it).length() }
            ?: appInfo.sourceDir?.let { File(it).length() }
        val splitApkTotalSize = when {
            appInfo.splitPublicSourceDirs != null -> appInfo.splitPublicSourceDirs!!.sumOf { File(it).length() }
            appInfo.splitSourceDirs != null -> appInfo.splitSourceDirs!!.sumOf { File(it).length() }
            else -> 0L
        }
        baseApkSize?.let { it + splitApkTotalSize } ?: 0L
    }.getOrDefault(0L)

    private fun List<Discovery>.mergeByPackage(): List<Discovery> {
        return groupBy { it.serviceInfo.packageName }.values.map { discoveries ->
            val preferred = discoveries.firstOrNull { it.pluginInfo != null } ?: discoveries.first()
            preferred.copy(
                // A package is usable when at least one metadata endpoint succeeds. Only
                // retain an endpoint failure when the package has no successful result.
                error = if (preferred.pluginInfo != null) {
                    null
                } else {
                    preferred.error ?: discoveries.firstNotNullOfOrNull { it.error }
                },
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
        private const val TAG = "InstalledPluginRepo"
        private const val MAX_PARALLEL_PLUGIN_PROBES = 4
        private const val KEY_AUTHOR = "org.autojs.plugin.info.AUTHOR"
    }

    private data class ServiceQuery(
        val action: String,
        val isOcrService: Boolean = false,
        val isBarcodeService: Boolean = false,
    )

}
