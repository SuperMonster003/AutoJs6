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
import org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginHost
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.core.plugin.gallery.GalleryPluginHost
import org.autojs.autojs.core.plugin.terminal.TerminalPluginHost
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPluginHost
import org.autojs.autojs.core.plugin.installer.InstallerPluginHost
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPluginHost
import org.autojs.plugin.gallery.api.GalleryActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.autojs.core.plugin.mcp.McpServerPluginHost
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.screencolorpicker.ScreenColorPickerPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.util.StringUtils.truncateForSafeBinder
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.autojs.plugin.epub.api.EpubActions
import org.autojs.plugin.terminal.api.TerminalActions
import org.autojs.plugin.installer.api.InstallerActions
import org.autojs.plugin.mail.api.MailActions
import org.autojs.plugin.mcp.server.api.McpServerActions
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
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
        val iconSignature: String?,
        val pluginInfo: PluginInfo?,
        val engine: String?,
        val variant: String?,
        val engineId: String?,
        val bindError: Throwable?,
        val isOcrService: Boolean,
        val isBarcodeService: Boolean,
        val serviceAction: String?,
        val isStopped: Boolean,
        val resolvedMetadata: InstalledPluginMetadataStore.Entry?,
        val nativePageAlignment: Long? = null,

        // ABIs whose native libraries are packaged in the installed APKs (base + splits).
        // zh-CN: 已安装 APK (base + splits) 内实际打包的 ABI.
        val installedAbis: List<String>? = null,

        // The explicit build-wide declaration, separate from legacy INFO values for one APK.
        val declaredSupportedAbis: List<String>? = null,
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
     * Icons, resource-provided descriptions and the metadata persisted by the last complete
     * discovery are all available here, so the first frame matches the last known real state.
     *
     * zh-CN: 构建仅依赖 PackageManager 的快照, 不启动或绑定任何插件进程.
     * 图标, 资源提供的描述以及上次完整发现持久化的元数据在此均可用,
     * 因此首帧即与上次已知的真实状态一致.
     */
    suspend fun discoverInstalledSnapshot(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val cachedMetadata = InstalledPluginMetadataStore.readAll(context)
        queryDeclaredPluginServices(context)
            .mergeByPackage()
            .mapIsolated(context, cachedMetadata)
    }

    suspend fun discoverInstalled(context: Context): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val declared = queryDeclaredPluginServices(context)
        val bound = discoverBoundPluginServices(context, declared)
        val cachedMetadata = InstalledPluginMetadataStore.readAll(context)

        // A failure in one discovery source must not make packages from that source disappear.
        // Package-manager declarations are retained as a metadata fallback when necessary.
        val plugins = (bound + declared)
            .mergeByPackage()
            .mapIsolated(context, cachedMetadata)
        persistResolvedMetadata(context, plugins)
        plugins
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
        ScreenColorPickerPluginHost.ACTION_SCREEN_COLOR_PICKER -> {
            if (packageName != ScreenColorPickerContract.DEFAULT_PACKAGE_NAME) {
                listOf(preferred.copy(error = IllegalArgumentException("Unsupported Screen Color Picker package: $packageName")))
            } else {
                ScreenColorPickerPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = ScreenColorPickerPluginHost.ACTION_SCREEN_COLOR_PICKER)
                }
            }
        }
        McpServerPluginHost.ACTION_MCP_SERVER -> {
            if (packageName != McpServerIds.DEFAULT_PACKAGE_NAME) {
                listOf(preferred.copy(error = IllegalArgumentException("Unsupported MCP Server package: $packageName")))
            } else {
                McpServerPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = McpServerPluginHost.ACTION_MCP_SERVER)
                }
            }
        }
        ThreeStoveAgentActions.SERVICE_ACTION -> {
            if (packageName != ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME) {
                listOf(preferred.copy(error = IllegalArgumentException("Unsupported Three Stove Agent package")))
            } else {
                ThreeStoveAgentPluginHost.discover(context).map {
                    Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = ThreeStoveAgentActions.SERVICE_ACTION)
                }
            }
        }
        MailPluginHost.ACTION_MAIL -> MailPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = MailPluginHost.ACTION_MAIL)
        }
        InstallerPluginHost.ACTION_INSTALLER -> InstallerPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = InstallerPluginHost.ACTION_INSTALLER)
        }
        EpubPluginHost.ACTION_EPUB -> EpubPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = EpubPluginHost.ACTION_EPUB)
        }
        TerminalPluginHost.ACTION_TERMINAL -> TerminalPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = TerminalPluginHost.ACTION_TERMINAL)
        }
        BatchRenamePluginHost.ACTION_BATCH_RENAME -> BatchRenamePluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = BatchRenamePluginHost.ACTION_BATCH_RENAME)
        }
        GalleryPluginHost.ACTION_GALLERY -> GalleryPluginHost.discover(context, packageName).map {
            Discovery(it.serviceInfo, it.pluginInfo, it.error, isOcrService = false, isBarcodeService = false, serviceAction = GalleryPluginHost.ACTION_GALLERY)
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
            ServiceQuery(
                action = ScreenColorPickerPluginHost.ACTION_SCREEN_COLOR_PICKER,
                category = ScreenColorPickerContract.SERVICE_CATEGORY,
                packageName = ScreenColorPickerContract.DEFAULT_PACKAGE_NAME,
            ),
            ServiceQuery(
                action = McpServerPluginHost.ACTION_MCP_SERVER,
                category = McpServerActions.SERVICE_CATEGORY,
                packageName = McpServerIds.DEFAULT_PACKAGE_NAME,
            ),
            ServiceQuery(
                action = ThreeStoveAgentActions.SERVICE_ACTION,
                category = ThreeStoveAgentActions.SERVICE_CATEGORY,
                packageName = ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME,
            ),
            ServiceQuery(
                action = MailPluginHost.ACTION_MAIL,
                category = MailActions.SERVICE_CATEGORY,
            ),
            ServiceQuery(
                action = InstallerPluginHost.ACTION_INSTALLER,
                category = InstallerActions.SERVICE_CATEGORY,
            ),
            ServiceQuery(
                action = EpubPluginHost.ACTION_EPUB,
                category = EpubActions.SERVICE_CATEGORY,
            ),
            ServiceQuery(
                action = TerminalPluginHost.ACTION_TERMINAL,
                category = TerminalActions.SERVICE_CATEGORY,
            ),
            ServiceQuery(
                action = BatchRenamePluginHost.ACTION_BATCH_RENAME,
                category = BatchRenamePluginActions.SERVICE_CATEGORY,
            ),
            ServiceQuery(
                action = GalleryPluginHost.ACTION_GALLERY,
                category = GalleryActions.SERVICE_CATEGORY,
            ),
        )
        return specs.flatMap { spec ->
            runCatching {
                queryIntentServices(context, spec).map { serviceInfo ->
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

    private fun queryIntentServices(context: Context, spec: ServiceQuery): List<ServiceInfo> {
        val intent = Intent(spec.action).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            spec.category?.takeIf { it.isNotBlank() }?.let(::addCategory)
            spec.packageName?.takeIf { it.isNotBlank() }?.let(::setPackage)
        }
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
        cachedMetadata: Map<String, InstalledPluginMetadataStore.Entry>,
    ): List<InstalledPlugin> = map { discovery ->
        val cachedEntry = cachedMetadata[discovery.serviceInfo.packageName]
        runCatching {
            buildInstalledPlugin(context, discovery, cachedEntry)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read plugin metadata for ${discovery.serviceInfo.packageName}", error)
            PluginCenterProcessLogger.i(
                "Failed to read plugin metadata for ${discovery.serviceInfo.packageName}: ${error.message ?: error.javaClass.simpleName}.",
            )
            buildFallbackInstalledPlugin(context, discovery, error, cachedEntry)
        }
    }

    private fun buildFallbackInstalledPlugin(
        context: Context,
        discovery: Discovery,
        metadataError: Throwable,
        cachedEntry: InstalledPluginMetadataStore.Entry?,
    ): InstalledPlugin {
        val packageName = discovery.serviceInfo.packageName
        return InstalledPlugin(
            packageName = packageName,
            title = cachedEntry?.title ?: packageName,
            description = cachedEntry?.description,
            instructionHardCoded = cachedEntry?.instruction,
            author = cachedEntry?.author,
            versionName = runCatching { context.getString(R.string.text_unknown) }.getOrDefault("unknown"),
            versionCode = null,
            versionDate = null,
            requiresHostVersion = null,
            supportedAbis = null,
            packageSize = 0L,
            firstInstallTime = null,
            lastUpdateTime = null,
            icon = null,
            iconSignature = null,
            pluginInfo = null,
            engine = discovery.declaredAgentEngine() ?: cachedEntry?.engine,
            variant = cachedEntry?.variant,
            engineId = cachedEntry?.engineId,
            bindError = discovery.error ?: metadataError,
            isOcrService = discovery.isOcrService,
            isBarcodeService = discovery.isBarcodeService,
            serviceAction = discovery.serviceAction,
            isStopped = runCatching {
                (discovery.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            }.getOrDefault(false),
            resolvedMetadata = null,
        )
    }

    private fun buildInstalledPlugin(
        context: Context,
        discovery: Discovery,
        cachedEntry: InstalledPluginMetadataStore.Entry?,
    ): InstalledPlugin {
        val pm = context.packageManager
        val serviceInfo = discovery.serviceInfo
        val packageName = serviceInfo.packageName
        val appInfo = getApplicationInfo(pm, packageName) ?: serviceInfo.applicationInfo
        val appLabel = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull()
        val icon = runCatching { PluginInstalledIconResolver.load(context, appInfo) }.getOrNull()
        val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val pluginInfo = discovery.pluginInfo
        val hasLiveInfo = pluginInfo != null

        val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        val versionName = pkgInfo?.versionName ?: pluginInfo?.versionName ?: context.getString(R.string.text_unknown)
        val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: pluginInfo?.versionCode
        val lastUpdateTime = pkgInfo?.lastUpdateTime
        val packageSize = calculatePackageSize(appInfo)

        // Values below either come from a live plugin binder or from the metadata persisted by
        // the last successful complete discovery. Text identity survives package updates while
        // version-coupled values require an exact package match.
        // zh-CN: 以下取值来自插件的实时 Binder 或上次完整发现成功时持久化的元数据.
        // 文本类标识信息可跨包更新沿用, 与版本耦合的取值则要求包构建完全一致.
        val cachedExact = cachedEntry?.takeIf { it.matchesInstalledPackage(versionCode, lastUpdateTime) }
        @Suppress("DEPRECATION")
        val nativePageAlignment = PluginNativeAlignmentPolicy.parse(
            appInfo.metaData?.get(org.autojs.plugin.common.api.PluginCapabilityKeys.NATIVE_PAGE_ALIGNMENT),
        ) ?: cachedExact?.nativePageAlignment

        val pluginName = PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.name)
        val pluginDescription = PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.description)
        val pluginInstruction = PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.instruction)
        val pluginAuthor = PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo?.author)
        val manifestAuthor = runCatching {
            PluginResourceResolver.resolveStringValue(
                context,
                packageName,
                appInfo.metaData?.let { BundleCompat.getSerializable(it, KEY_AUTHOR, Serializable::class.java) },
            )
        }.getOrNull()

        val instructionHardCoded = (if (hasLiveInfo) pluginInstruction else cachedEntry?.instruction)
            ?.truncateForSafeBinder()
        val requiresHostVersion = when {
            hasLiveInfo -> PluginCapabilityResolver.resolveRequiresHostVersion(pluginInfo.capabilities)
            else -> cachedExact?.requiresHostVersion
        }
        // The manifest meta-data written by the plugin build describes every ABI the plugin is
        // built for and wins over the INFO binder, whose value some plugins derive from the
        // installed split. The installed set is read from the installed package itself.
        // zh-CN: 构建期写入清单的 meta-data 描述插件构建支持的全部 ABI, 优先于 INFO Binder 的取值
        // (部分插件的 INFO 取值来自已安装分包). 已安装集合直接取自已安装的包.
        val declaredAbis = PluginAbiUtils.resolveSupportedAbisFromValue(
            appInfo.metaData?.getString(org.autojs.plugin.common.api.PluginCapabilityKeys.SUPPORTED_ABIS),
        )
        val supportedAbis = declaredAbis ?: when {
            hasLiveInfo -> pluginInfo.supportedAbis?.toList()?.let(PluginAbiUtils::resolveSupportedAbisFromValue)
            else -> cachedExact?.supportedAbis
        }
        val installedAbis = PluginAbiUtils.parseInstalledApkSupportedAbis(
            listOfNotNull(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty(),
        )

        val resolvedMetadata = when {
            hasLiveInfo -> InstalledPluginMetadataStore.Entry(
                packageName = packageName,
                versionCode = versionCode,
                lastUpdateTime = lastUpdateTime,
                languageTag = Language.getPrefLanguage().locale.toLanguageTag(),
                title = pluginName,
                description = pluginDescription,
                author = pluginAuthor,
                instruction = instructionHardCoded,
                versionDate = pluginInfo.versionDate,
                requiresHostVersion = requiresHostVersion,
                supportedAbis = supportedAbis,
                nativePageAlignment = nativePageAlignment,
                engine = pluginInfo.engine,
                variant = pluginInfo.variant,
                engineId = pluginInfo.id,
            )
            else -> null
        }

        return InstalledPlugin(
            packageName = packageName,
            title = (if (hasLiveInfo) pluginName else cachedEntry?.title) ?: appLabel ?: packageName,
            description = PluginResourceResolver.resolve(
                context,
                packageName,
                "plugin_description" to PluginResourceResolver.ResType.STRING,
                if (hasLiveInfo) pluginDescription else cachedEntry?.description,
            ),
            instructionHardCoded = instructionHardCoded,
            author = (if (hasLiveInfo) pluginAuthor else cachedEntry?.author) ?: manifestAuthor,
            versionName = versionName,
            versionCode = versionCode,
            versionDate = if (hasLiveInfo) pluginInfo.versionDate else cachedExact?.versionDate,
            requiresHostVersion = requiresHostVersion,
            supportedAbis = supportedAbis,
            installedAbis = installedAbis,
            declaredSupportedAbis = declaredAbis,
            nativePageAlignment = nativePageAlignment,
            packageSize = packageSize,
            firstInstallTime = pkgInfo?.firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            icon = icon,
            iconSignature = icon?.let { PluginInstalledIconResolver.signatureOf(packageName, versionCode, lastUpdateTime) },
            pluginInfo = pluginInfo,
            engine = if (hasLiveInfo) pluginInfo.engine else discovery.declaredAgentEngine() ?: cachedEntry?.engine,
            variant = if (hasLiveInfo) pluginInfo.variant else cachedEntry?.variant,
            engineId = if (hasLiveInfo) pluginInfo.id else cachedEntry?.engineId,
            bindError = discovery.error,
            isOcrService = discovery.isOcrService,
            isBarcodeService = discovery.isBarcodeService,
            serviceAction = discovery.serviceAction,
            isStopped = isStopped,
            resolvedMetadata = resolvedMetadata,
        )
    }

    // The first package-manager-only frame must apply Agent's signer-aware policy before INFO binds.
    private fun Discovery.declaredAgentEngine(): String? = ThreeStoveAgentIds.ENGINE.takeIf {
        serviceAction == ThreeStoveAgentActions.SERVICE_ACTION && serviceInfo.packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME
    }

    private fun persistResolvedMetadata(context: Context, plugins: List<InstalledPlugin>) {
        // An empty discovery carries no information about installed packages,
        // so it must not prune previously persisted metadata.
        // zh-CN: 空的发现结果不包含任何已安装包信息, 不得据此清理已持久化的元数据.
        if (plugins.isEmpty()) return
        runCatching {
            val existing = InstalledPluginMetadataStore.readAll(context)
            val merged = InstalledPluginMetadataStore.mergeEntries(
                existing = existing,
                fresh = plugins.mapNotNull { it.resolvedMetadata },
                retainedPackages = plugins.mapTo(hashSetOf()) { it.packageName },
            )
            InstalledPluginMetadataStore.replaceAll(context, merged)
        }.onFailure { error ->
            Log.w(TAG, "Failed to persist installed plugin metadata: ${error.message}")
        }
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
        val category: String? = null,
        val packageName: String? = null,
    )

}
