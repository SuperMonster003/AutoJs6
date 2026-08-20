package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.autojs.autojs.apkbuilder.template.ApkBuilderTemplatePluginHost
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPluginHost
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import java.util.Locale

/**
 * Loads both index plugins and locally installed plugins,
 * merging them into a PluginCenterItem list.
 *
 * Behavior strategy:
 * - First load local plugins and immediately push the list (local priority).
 * - Then load the index in the background, merge with local plugins and push again after success.
 * - If local discovery fails, notify UI to show a dialog and exit Activity through fatalError.
 *
 * zh-CN:
 *
 * 统一加载索引插件与本地已安装插件, 合并为 PluginCenterItem 列表.
 *
 * 行为策略:
 * - 先加载本地插件并立即推送列表 (本地优先).
 * - 紧接着在后台加载索引, 成功后与本地合并再推送一次.
 * - 若本地发现失败, 通过 fatalError 通知 UI 弹窗并退出 Activity.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Nov 26, 2025.
 * Modified by SuperMonster003 as of Jan 17, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
class PluginCenterViewModel : ViewModel() {

    private val indexRepo = PluginIndexRepository()

    private val installedRepo = InstalledPluginRepository()
    private val legacyRepo = LegacyInstalledPluginRepository()
    private val enableStore = PluginEnableStore
    private val iconRenderModeTracker = PluginIconRenderModeTracker()

    private val _items = MutableStateFlow<List<PluginCenterItem>>(emptyList())
    val items: StateFlow<List<PluginCenterItem>> = _items

    // Whether index loading is completed (includes "success/failure/empty" results, only means "no longer loading").
    // zh-CN: 索引加载是否已完成 (包含 "成功/失败/空" 三种结果, 仅代表 "不再加载").
    private val _indexLoaded = MutableStateFlow(false)
    val indexLoaded: StateFlow<Boolean> = _indexLoaded

    // Fatal exception discovered locally (requires user notification and Activity exit).
    // zh-CN: 本地发现的致命异常 (需要提示用户并退出 Activity).
    private val _fatalError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val fatalError: SharedFlow<String> = _fatalError

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage: SharedFlow<String> = _toastMessage

    private var loadGeneration: Long = 0L
    private var loadJob: Job? = null

    /**
     * Load plugin list.
     *
     * zh-CN: 加载插件列表.
     *
     * @param forceRefreshIndex Whether to force refresh index.
     * - false: Use 1 minute throttling + ETag/backoff (normal page entry).
     * - true: Attempt to refresh index every time (still respects backoff window, for pull-to-refresh usage).
     *
     * zh-CN: 是否强制刷新索引.
     * - false: 使用 1 分钟节流 + ETag/退避 (普通进入页面).
     * - true: 每次都尝试刷新索引 (仍遵守退避窗口, 供下拉刷新使用).
     */
    fun load(
        context: Context,
        forceRefreshIndex: Boolean = false,
        sourceScope: PluginIndexSource? = null,
        forceRender: Boolean = false,
        showRefreshErrors: Boolean = false,
    ) {
        Log.d(TAG, "load: forceRefreshIndex = $forceRefreshIndex, sourceScope = $sourceScope")
        PluginCenterProcessLogger.i("Start plugin center load: forceRefreshIndex=$forceRefreshIndex, sourceScope=${sourceScope?.name ?: "ALL"}.")
        val iconNightModeEnabled = ViewUtils.isNightModeEnabled
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            fun isLatestLoad() = generation == loadGeneration
            fun updateLatestItems(next: List<PluginCenterItem>) {
                if (isLatestLoad()) {
                    val updated = updateItemsIfChanged(
                        next = next,
                        force = forceRender || iconRenderModeTracker.shouldForceRender(iconNightModeEnabled),
                    )
                    if (updated) {
                        iconRenderModeTracker.markRendered(iconNightModeEnabled)
                    }
                }
            }
            fun emitRefreshError(message: String) {
                if (showRefreshErrors && isLatestLoad()) {
                    _toastMessage.tryEmit(message)
                }
            }

            _indexLoaded.value = false
            val autoCheckUpdatesEnabled = PluginOnlineSettingsStore.isAutoCheckUpdatesEnabled()
            val enabledSources = sourceScope?.let { setOf(it) } ?: PluginOnlineSettingsStore.selectedSources()
            val shouldForceByAutoCheck = autoCheckUpdatesEnabled && !forceRefreshIndex && PluginOnlineSettingsStore.shouldAutoCheckNow()
            val shouldForceFetchIndex = forceRefreshIndex || shouldForceByAutoCheck

            if (autoCheckUpdatesEnabled && shouldForceFetchIndex && enabledSources.isNotEmpty()) {
                PluginOnlineSettingsStore.markCheckedNow()
            }

            // First load local plugins (local priority).
            // zh-CN: 先加载本地插件 (本地优先).
            PluginCenterProcessLogger.i("Discover installed AIDL plugins.")
            val installed = runCatching {
                installedRepo.discoverInstalled(context)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (!isLatestLoad()) return@onFailure
                // Treat "local discovery" failure as a serious exception.
                // zh-CN: 将 "本地发现" 失败视为严重异常.
                val message = context.getString(
                    R.string.error_failed_to_load_plugins_with_reason,
                    e.message ?: e.toString(),
                )
                PluginCenterProcessLogger.i("Failed to discover installed AIDL plugins: ${e.message ?: e.javaClass.simpleName}.")
                emitRefreshError(message)
                _fatalError.tryEmit(message)
            }.getOrNull() ?: run {
                // Do not continue subsequent logic when unable to obtain local list.
                // zh-CN: 无法获取本地列表时不再继续后续逻辑.
                if (!isLatestLoad()) return@launch
                updateLatestItems(emptyList())
                _indexLoaded.value = true
                return@launch
            }
            if (!isLatestLoad()) return@launch
            PluginCenterProcessLogger.i("Installed AIDL plugins discovered: count=${installed.size}.")

            PluginCenterProcessLogger.i("Discover installed legacy SDK plugins.")
            val legacyInstalled = runCatching {
                legacyRepo.discoverInstalled(context)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "legacy discovery failed: ${e.message}")
                val message = e.message ?: e.toString()
                PluginCenterProcessLogger.i("Failed to discover installed legacy SDK plugins: $message.")
                emitRefreshError(message)
            }.getOrElse { emptyList() }
            PluginCenterProcessLogger.i("Installed legacy SDK plugins discovered: count=${legacyInstalled.size}.")
            // Render list using "local only".
            // zh-CN: 使用 "仅本地" 渲染列表.
            val localAidlItems = installed.mapNotNull { local ->
                toPluginCenterItem(
                    context,
                    index = null,
                    local = local,
                    allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                )
            }
            val localLegacyItems = legacyInstalled.mapNotNull { local ->
                toLegacyPluginCenterItem(
                    context = context,
                    local = local,
                    index = null,
                    allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                )
            }
            val onlyLocalItems = filterHidden(context, localAidlItems + localLegacyItems)

            val installedByPkg = installed.associateBy { it.packageName }
            val installedByNormalizedTitle = installed.groupBy { normalizeTitle(it.title) }
            val legacyInstalledByPkg = legacyInstalled.associateBy { it.packageName }

            fun buildMergedItems(indexEntries: List<PluginIndexEntry>): List<PluginCenterItem> {
                val fromIndex = indexEntries.mapNotNull { entry ->
                    val legacy = legacyInstalledByPkg[entry.packageName]
                    if (legacy != null) {
                        return@mapNotNull toLegacyPluginCenterItem(
                            context = context,
                            local = legacy,
                            index = entry,
                            allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                        )
                    }
                    val local = matchLocalPluginForIndex(
                        index = entry,
                        installedByPkg = installedByPkg,
                        installedByNormalizedTitle = installedByNormalizedTitle,
                    )
                    toPluginCenterItem(
                        context = context,
                        index = entry,
                        local = local,
                        allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                    )
                }.let(::dedupeByPackagePreferInstalled)

                // Supplement plugins that "exist locally but not in index" (third-party/not yet in index).
                // zh-CN: 补充 "本地有但索引没有" 的插件 (第三方/暂未入索引).
                val fromIndexPackageNames = fromIndex.asSequence().map { it.packageName }.toHashSet()
                val includeUnindexedLocals = sourceScope == null
                val extraLocals = if (includeUnindexedLocals) {
                    val extraAidlLocals = installed
                        .filter { ins -> ins.packageName !in fromIndexPackageNames }
                        .mapNotNull { local ->
                            toPluginCenterItem(
                                context = context,
                                index = null,
                                local = local,
                                allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                            )
                        }
                    val extraLegacyLocals = legacyInstalled
                        .filter { it.packageName !in fromIndexPackageNames }
                        .mapNotNull { legacy ->
                            toLegacyPluginCenterItem(
                                context = context,
                                local = legacy,
                                index = null,
                                allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                            )
                        }
                    dedupeByPackagePreferInstalled(extraAidlLocals + extraLegacyLocals)
                } else {
                    localLegacyItems.filter { it.packageName in fromIndexPackageNames }
                }

                val merged = dedupeByPackagePreferInstalled(fromIndex + extraLocals)
                return filterHidden(context, merged)
            }

            val cachedIndexEntries = runCatching {
                indexRepo.getCachedIndex(
                    context = context,
                    enabledSources = enabledSources,
                )
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "getCachedIndex failed: ${it.message}")
                val message = it.message ?: it.toString()
                PluginCenterProcessLogger.i("Failed to read cached plugin index: $message.")
                emitRefreshError(message)
            }.getOrElse { emptyList() }
            PluginCenterProcessLogger.i("Cached plugin index entries loaded: count=${cachedIndexEntries.size}.")

            when {
                cachedIndexEntries.isNotEmpty() -> updateLatestItems(buildMergedItems(cachedIndexEntries))
                sourceScope == null -> updateLatestItems(onlyLocalItems)
            }
            if (!isLatestLoad()) return@launch

            // Asynchronously load index and merge.
            val indexEntries = runCatching {
                indexRepo.fetchOnlineIndex(
                    context = context,
                    forceRefresh = shouldForceFetchIndex,
                    enabledSources = enabledSources,
                    onIssue = { issue ->
                        emitRefreshError(
                            context.getString(
                                R.string.error_plugin_index_source_fetch_failed_with_reason,
                                issue.source.labelForMessage(context),
                                issue.message,
                            ),
                        )
                    },
                )
            }.onFailure {
                if (it is CancellationException) throw it
                // Index fetch exception is not fatal, just log it.
                Log.w(TAG, "fetchOnlineIndex failed: ${it.message}")
                val message = it.message ?: it.toString()
                PluginCenterProcessLogger.i("Failed to fetch online plugin index: $message.")
                emitRefreshError(
                    context.getString(
                        R.string.error_plugin_index_fetch_failed_with_reason,
                        message,
                    ),
                )
            }.getOrElse { cachedIndexEntries }
            if (!isLatestLoad()) return@launch

            updateLatestItems(buildMergedItems(indexEntries))
            PluginCenterProcessLogger.i("Plugin center load finished: visible entries=${_items.value.size}.")
            _indexLoaded.value = true

            // When returning to the page, refresh if the details dialog is being displayed.
            // zh-CN: 返回页面时, 若详情对话框正在显示则刷新.
            PluginInfoDialogManager.refreshIfShowing(context, _items.value)
        }
        loadJob?.invokeOnCompletion {
            if (generation == loadGeneration && !_indexLoaded.value) {
                _indexLoaded.value = true
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean, error: PluginError? = null) {
        enableStore.setEnabled(context, packageName, enabled)
        _items.value = _items.value.map {
            if (it.packageName == packageName) {
                val nextEnabledState = when {
                    !enabled -> PluginEnabledState.DISABLED
                    error != null -> PluginEnabledState.ERROR(error)
                    else -> PluginEnabledState.READY
                }
                it.copy(
                    isEnabled = enabled,
                    enabledState = nextEnabledState,
                    lastError = error,
                )
            } else it
        }
        PluginInfoDialogManager.refreshIfShowing(context, _items.value)
    }

    private fun updateItemsIfChanged(next: List<PluginCenterItem>, force: Boolean = false): Boolean {
        val shouldUpdate = force || !isSemanticallySameList(_items.value, next)
        if (shouldUpdate) {
            _items.value = next
        }
        return shouldUpdate
    }

    private fun PluginIndexSource.labelForMessage(context: Context): String = when (this) {
        PluginIndexSource.OFFICIAL -> context.getString(R.string.text_plugin_official)
        PluginIndexSource.THIRD_PARTY -> context.getString(R.string.text_plugin_third_party)
    }

    private fun filterHidden(context: Context, items: List<PluginCenterItem>): List<PluginCenterItem> {
        if (items.isEmpty()) return emptyList()
        return items.filterNot { PluginVisibilityStore.isHidden(context, it.packageName) }
    }

    private fun isSemanticallySameList(
        current: List<PluginCenterItem>,
        next: List<PluginCenterItem>,
    ): Boolean {
        if (current.size != next.size) {
            return false
        }
        return current.map(::snapshotOf) == next.map(::snapshotOf)
    }

    private fun snapshotOf(item: PluginCenterItem): ItemSnapshot {
        val enabledStateKey = when (val state = item.enabledState) {
            PluginEnabledState.DISABLED -> "disabled"
            PluginEnabledState.READY -> "ready"
            is PluginEnabledState.ERROR -> "error:${state.error.code.name}:${state.error.recoverHint.orEmpty()}"
        }
        return ItemSnapshot(
            packageName = item.packageName,
            title = item.title,
            versionName = item.versionName,
            versionCode = item.versionCode,
            updatableVersionName = item.updatableVersionName,
            updatableVersionCode = item.updatableVersionCode,
            updatableChangelogUrl = item.updatableChangelogUrl,
            updatableChangelogText = item.updatableChangelogText,
            installableApkUrl = item.installableApkUrl,
            updatableApkUrl = item.updatableApkUrl,
            author = item.author,
            description = item.description,
            instructionHardCoded = item.instructionHardCoded,
            instructionMarkdownUrl = item.instructionMarkdownUrl,
            remoteIconUrl = item.remoteIconUrl,
            remoteNightIconUrl = item.remoteNightIconUrl,
            forceIgnoreLocalIcon = item.forceIgnoreLocalIcon,
            packageSize = item.packageSize,
            supportedAbis = item.supportedAbis,
            isEnabled = item.isEnabled,
            isInstalled = item.isInstalled,
            firstInstallTime = item.firstInstallTime,
            lastUpdateTime = item.lastUpdateTime,
            source = item.source,
            isFeatured = item.isFeatured,
            mechanism = item.mechanism,
            authorizedState = item.authorizedState,
            activatedState = item.activatedState,
            enabledStateKey = enabledStateKey,
            engine = item.engine,
            variant = item.variant,
            engineId = item.engineId,
            isOcrService = item.isOcrService,
            isBarcodeService = item.isBarcodeService,
            serviceAction = item.serviceAction,
            requiresHostVersion = item.requiresHostVersion,
            hostVersionCode = item.hostVersionCode,
        )
    }

    fun ignoreUpdatableVersion(item: PluginCenterItem) {
        val v = item.updatableVersionCode ?: return
        UpdateIgnoreStore.ignoreVersion(item.packageName, v, item.versionName)
    }

    private fun matchLocalPluginForIndex(
        index: PluginIndexEntry,
        installedByPkg: Map<String, InstalledPluginRepository.InstalledPlugin>,
        installedByNormalizedTitle: Map<String, List<InstalledPluginRepository.InstalledPlugin>>,
    ): InstalledPluginRepository.InstalledPlugin? {
        installedByPkg[index.packageName]?.let { return it }

        if (!index.packageName.startsWith("unknown.")) {
            return null
        }

        val normalizedTitle = normalizeTitle(index.title)
        if (normalizedTitle.isBlank()) {
            return null
        }

        val candidates = installedByNormalizedTitle[normalizedTitle].orEmpty()
        return candidates.singleOrNull()
    }

    private fun dedupeByPackagePreferInstalled(items: List<PluginCenterItem>): List<PluginCenterItem> {
        if (items.isEmpty()) return emptyList()

        val merged = linkedMapOf<String, PluginCenterItem>()
        items.forEach { item ->
            val existing = merged[item.packageName]
            if (existing == null) {
                merged[item.packageName] = item
                return@forEach
            }
            merged[item.packageName] = pickPreferredItem(existing, item)
        }
        return merged.values.toList()
    }

    private fun pickPreferredItem(existing: PluginCenterItem, candidate: PluginCenterItem): PluginCenterItem {
        if (existing.isInstalled != candidate.isInstalled) {
            return if (candidate.isInstalled) candidate else existing
        }

        val existingVersion = existing.versionCode ?: Long.MIN_VALUE
        val candidateVersion = candidate.versionCode ?: Long.MIN_VALUE
        if (existingVersion != candidateVersion) {
            return if (candidateVersion > existingVersion) candidate else existing
        }

        val existingHasInstallable = !existing.installableApkUrl.isNullOrBlank()
        val candidateHasInstallable = !candidate.installableApkUrl.isNullOrBlank()
        if (existingHasInstallable != candidateHasInstallable) {
            return if (candidateHasInstallable) candidate else existing
        }

        return existing
    }

    private fun normalizeTitle(title: String?): String = title?.trim()?.lowercase(Locale.ROOT).orEmpty()

    private fun toPluginCenterItem(
        context: Context,
        index: PluginIndexEntry?,
        local: InstalledPluginRepository.InstalledPlugin?,
        allowGlobalUpdateCheck: Boolean,
    ): PluginCenterItem? {
        val packageName = local?.packageName ?: index?.packageName
        if (packageName.isNullOrBlank()) return null
        val allowUpdateCheck = allowGlobalUpdateCheck &&
                PluginUpdateCheckStore.isEnabled(packageName, defaultEnabled = true)

        val title = local?.title ?: index?.title ?: packageName
        val description = local?.description ?: index?.description
        val instructionHardCoded = local?.instructionHardCoded ?: index?.instructionHardCoded
        val instructionMarkdownUrl = index?.instructionMarkdownUrl
        val author = local?.author ?: index?.author
        val collaborators = index?.collaborators ?: emptyList()
        val source = index?.source ?: PluginIndexSource.OFFICIAL
        val supportedAbis = local?.supportedAbis ?: index?.supportedAbis
        val engine = local?.pluginInfo?.engine ?: index?.engine
        val variant = local?.pluginInfo?.variant ?: index?.variant
        val engineId = local?.pluginInfo?.id ?: index?.engineId
        val isOcrService = local?.isOcrService == true || engine in OCR_SERVICE_ENGINES
        val isBarcodeService = local?.isBarcodeService == true || engine in BARCODE_SERVICE_ENGINES
        val serviceAction = local?.serviceAction ?: serviceActionFromEngine(engine)
        val requiresHostVersion = local?.requiresHostVersion ?: index?.requiresHostVersion
        val hostVersionCode = currentHostVersionCode()

        val versionNameLocal = local?.versionName ?: index?.releases?.firstOrNull()?.versionName ?: context.getString(R.string.text_unknown)
        val versionCodeLocal = local?.versionCode
        val versionDateLocal = local?.versionDate
            ?: resolveVersionDateFromIndex(
                index = index,
                localVersionCode = versionCodeLocal,
                localVersionName = versionNameLocal,
            )
        val isInstalled = local != null
        val forceIgnoreLocalIcon = index?.forceIgnoreLocalIcon == true

        // When not installed: installable uses the latest release.
        // zh-CN: 未安装时: installable 取 releases 最新 (第一个).
        val latestRelease = index?.releases?.maxByOrNull { it.versionCode }

        // When installed: updatable uses highest release greater than local and not ignored.
        // zh-CN: 已安装时: updatable 取 "大于 installed 且未忽略" 的最高版本.
        val targetUpdate = run {
            if (!allowUpdateCheck || !isInstalled || versionCodeLocal == null) return@run null
            val candidates = index?.releases
                ?.filter { it.versionCode > versionCodeLocal }
                ?.sortedByDescending { it.versionCode }
                ?: emptyList()
            candidates.firstOrNull { !UpdateIgnoreStore.isIgnored(packageName, it.versionCode) }
        }

        val trustInfo = runCatching { PluginTrustManager.resolveTrustInfo(context, packageName) }.getOrElse {
            PluginTrustManager.TrustInfo(
                authorizedState = PluginAuthorizedState.REQUIRED,
                isOfficial = false,
                isTrusted = false,
                primaryFingerprintSha256 = null,
                fingerprintsSha256 = emptyList(),
            )
        }

        var enabled = enableStore.isEnabled(context, packageName, defaultEnabled = isInstalled)
        if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }
        if (!isHostVersionCompatible(hostVersionCode, requiresHostVersion) && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }

        val canActivate = isInstalled && PluginWakeManager.buildWakeIntent(context, packageName) != null
        var activatedState = when {
            !canActivate -> PluginActivatedState.NOT_SUPPORTED
            else -> PluginActivatedState.UNKNOWN
        }

        val mappedError = local?.bindError?.let { PluginErrorMapper.fromThrowable(it) }
        if (mappedError != null && canActivate && activatedState == PluginActivatedState.UNKNOWN && PluginErrorMapper.shouldRecommendActivation(mappedError)) {
            activatedState = PluginActivatedState.RECOMMENDED
        }
        val hostVersionError = if (!isHostVersionCompatible(hostVersionCode, requiresHostVersion)) {
            PluginError(
                code = PluginErrorCode.HOST_VERSION_TOO_LOW,
                message = "requiresHostVersion=$requiresHostVersion, hostVersionCode=$hostVersionCode",
                recoverHint = context.getString(
                    R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                    requiresHostVersion ?: 0L,
                    hostVersionCode,
                ),
            )
        } else null
        val authError = if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED) PluginError(PluginErrorCode.NOT_AUTHORIZED) else null
        val lastError = authError ?: hostVersionError ?: mappedError
        val enabledState = when {
            hostVersionError != null -> PluginEnabledState.ERROR(hostVersionError)
            !enabled -> PluginEnabledState.DISABLED
            mappedError != null -> PluginEnabledState.ERROR(mappedError)
            else -> PluginEnabledState.READY
        }
        val readyEnabled = enabled && enabledState is PluginEnabledState.READY
        val mechanism = when {
            local != null -> PluginMechanism.AIDL
            index?.source == PluginIndexSource.THIRD_PARTY -> PluginMechanism.SDK
            else -> PluginMechanism.AIDL
        }

        return PluginCenterItem(
            title = title,
            packageName = packageName,
            versionName = versionNameLocal,
            versionCode = versionCodeLocal ?: latestRelease?.versionCode,
            versionDate = versionDateLocal ?: latestRelease?.versionDate,

            updatableVersionName = targetUpdate?.versionName,
            updatableVersionCode = targetUpdate?.versionCode,
            updatableVersionDate = targetUpdate?.versionDate,
            updatableApkUrl = targetUpdate?.apkUrl,
            updatableApkSha256 = targetUpdate?.apkSha256,
            updatableApkSizeBytes = targetUpdate?.apkSizeBytes,
            updatableChangelogUrl = targetUpdate?.changelogUrl,
            updatableChangelogText = targetUpdate?.changelogText,

            author = author ?: trustInfo.developer,
            collaborators = collaborators,
            description = description,
            instructionHardCoded = instructionHardCoded,
            instructionMarkdownUrl = instructionMarkdownUrl,

            packageSize = local?.packageSize ?: 0L,

            installableApkUrl = latestRelease?.apkUrl,
            installableApkSha256 = latestRelease?.apkSha256,
            installableApkSizeBytes = latestRelease?.apkSizeBytes,
            supportedAbis = supportedAbis,
            icon = if (forceIgnoreLocalIcon) null else local?.icon,
            remoteIconUrl = index?.iconUrl?.toString(),
            remoteNightIconUrl = index?.nightIconUrl?.toString(),
            forceIgnoreLocalIcon = forceIgnoreLocalIcon,
            isEnabled = readyEnabled,
            isInstalled = isInstalled,
            firstInstallTime = local?.firstInstallTime,
            lastUpdateTime = local?.lastUpdateTime,
            settings = if (isInstalled) PluginCenterItemSettings() else null,
            source = source,
            isFeatured = index?.isFeatured ?: true,
            engine = engine,
            variant = variant,
            engineId = engineId,
            isOcrService = isOcrService,
            isBarcodeService = isBarcodeService,
            serviceAction = serviceAction,
            mechanism = mechanism,
            authorizedState = trustInfo.authorizedState,
            activatedState = activatedState,
            enabledState = enabledState,
            lastError = lastError,
            signingFingerprintSha256 = trustInfo.primaryFingerprintSha256,
            isOfficialVerified = trustInfo.isOfficial,
            canActivate = canActivate,
            requiresHostVersion = requiresHostVersion,
            hostVersionCode = hostVersionCode,
        )
    }

    private fun toLegacyPluginCenterItem(
        context: Context,
        local: LegacyInstalledPluginRepository.LegacyInstalledPlugin,
        index: PluginIndexEntry?,
        allowGlobalUpdateCheck: Boolean,
    ): PluginCenterItem? {
        val packageName = local.packageName
        if (packageName.isBlank()) return null
        val allowUpdateCheck = allowGlobalUpdateCheck &&
                PluginUpdateCheckStore.isEnabled(packageName, defaultEnabled = true)
        val versionDateLocal = local.versionDate
            ?: resolveVersionDateFromIndex(
                index = index,
                localVersionCode = local.versionCode,
                localVersionName = local.versionName,
            )
        val latestRelease = index?.releases?.maxByOrNull { it.versionCode }
        val targetUpdate = run {
            val localVersionCode = local.versionCode
            if (!allowUpdateCheck || localVersionCode == null) return@run null
            val candidates = index?.releases
                ?.filter { it.versionCode > localVersionCode }
                ?.sortedByDescending { it.versionCode }
                ?: emptyList()
            candidates.firstOrNull { !UpdateIgnoreStore.isIgnored(packageName, it.versionCode) }
        }
        val requiresHostVersion = index?.requiresHostVersion
        val hostVersionCode = currentHostVersionCode()
        val forceIgnoreLocalIcon = index?.forceIgnoreLocalIcon == true
        val supportedAbis = index?.supportedAbis

        val trustInfo = runCatching { PluginTrustManager.resolveTrustInfo(context, packageName) }.getOrElse {
            PluginTrustManager.TrustInfo(
                authorizedState = PluginAuthorizedState.REQUIRED,
                isOfficial = false,
                isTrusted = false,
                primaryFingerprintSha256 = null,
                fingerprintsSha256 = emptyList(),
            )
        }

        var enabled = enableStore.isEnabled(context, packageName, defaultEnabled = true)
        if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }
        if (!isHostVersionCompatible(hostVersionCode, requiresHostVersion) && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }

        val hostVersionError = if (!isHostVersionCompatible(hostVersionCode, requiresHostVersion)) {
            PluginError(
                code = PluginErrorCode.HOST_VERSION_TOO_LOW,
                message = "requiresHostVersion=$requiresHostVersion, hostVersionCode=$hostVersionCode",
                recoverHint = context.getString(
                    R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                    requiresHostVersion ?: 0L,
                    hostVersionCode,
                ),
            )
        } else null
        val authError = if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED) PluginError(PluginErrorCode.NOT_AUTHORIZED) else null
        val enabledState = when {
            hostVersionError != null -> PluginEnabledState.ERROR(hostVersionError)
            enabled -> PluginEnabledState.READY
            else -> PluginEnabledState.DISABLED
        }
        val lastError = authError ?: hostVersionError
        val readyEnabled = enabled && enabledState is PluginEnabledState.READY

        return PluginCenterItem(
            title = local.title.takeIf { it.isNotBlank() } ?: index?.title ?: packageName,
            packageName = packageName,
            versionName = local.versionName,
            versionCode = local.versionCode,
            versionDate = versionDateLocal,

            updatableVersionName = targetUpdate?.versionName,
            updatableVersionCode = targetUpdate?.versionCode,
            updatableVersionDate = targetUpdate?.versionDate,
            updatableApkUrl = targetUpdate?.apkUrl,
            updatableApkSha256 = targetUpdate?.apkSha256,
            updatableApkSizeBytes = targetUpdate?.apkSizeBytes,
            updatableChangelogUrl = targetUpdate?.changelogUrl,
            updatableChangelogText = targetUpdate?.changelogText,

            author = local.author ?: index?.author ?: trustInfo.developer,
            collaborators = index?.collaborators ?: emptyList(),
            description = local.description ?: index?.description,
            instructionHardCoded = local.instructionHardCoded ?: index?.instructionHardCoded,
            instructionMarkdownUrl = index?.instructionMarkdownUrl,

            packageSize = local.packageSize,

            installableApkUrl = latestRelease?.apkUrl,
            installableApkSha256 = latestRelease?.apkSha256,
            installableApkSizeBytes = latestRelease?.apkSizeBytes,
            supportedAbis = supportedAbis,

            icon = if (forceIgnoreLocalIcon) null else local.icon,
            remoteIconUrl = index?.iconUrl?.toString(),
            remoteNightIconUrl = index?.nightIconUrl?.toString(),
            forceIgnoreLocalIcon = forceIgnoreLocalIcon,
            isEnabled = readyEnabled,
            isInstalled = true,
            firstInstallTime = local.firstInstallTime,
            lastUpdateTime = local.lastUpdateTime,
            settings = PluginCenterItemSettings(),
            source = index?.source ?: PluginIndexSource.THIRD_PARTY,
            isFeatured = index?.isFeatured ?: true,
            engine = null,
            variant = null,
            engineId = null,
            mechanism = PluginMechanism.SDK,
            authorizedState = trustInfo.authorizedState,
            activatedState = PluginActivatedState.NOT_SUPPORTED,
            enabledState = enabledState,
            lastError = lastError,
            signingFingerprintSha256 = trustInfo.primaryFingerprintSha256,
            isOfficialVerified = trustInfo.isOfficial,
            canActivate = false,
            requiresHostVersion = requiresHostVersion,
            hostVersionCode = hostVersionCode,
        )
    }

    private fun currentHostVersionCode(): Long = BuildConfig.VERSION_CODE.toLong()

    private fun isHostVersionCompatible(hostVersionCode: Long, requiresHostVersion: Long?): Boolean {
        return PluginCapabilityResolver.isHostVersionCompatible(hostVersionCode, requiresHostVersion)
    }

    private fun resolveVersionDateFromIndex(
        index: PluginIndexEntry?,
        localVersionCode: Long?,
        localVersionName: String?,
    ): String? {
        val releases = index?.releases.orEmpty()
        if (releases.isEmpty()) return null

        localVersionCode?.let { code ->
            releases.firstOrNull { it.versionCode == code }
                ?.versionDate
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        localVersionName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { name ->
                releases.firstOrNull { it.versionName.equals(name, ignoreCase = true) }
                    ?.versionDate
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

        return releases.maxByOrNull { it.versionCode }?.versionDate?.takeIf { it.isNotBlank() }
    }

    private data class ItemSnapshot(
        val packageName: String,
        val title: String,
        val versionName: String,
        val versionCode: Long?,
        val updatableVersionName: String?,
        val updatableVersionCode: Long?,
        val updatableChangelogUrl: String?,
        val updatableChangelogText: String?,
        val installableApkUrl: String?,
        val updatableApkUrl: String?,
        val author: String?,
        val description: String?,
        val instructionHardCoded: String?,
        val instructionMarkdownUrl: String?,
        val remoteIconUrl: String?,
        val remoteNightIconUrl: String?,
        val forceIgnoreLocalIcon: Boolean,
        val packageSize: Long,
        val supportedAbis: List<String>?,
        val isEnabled: Boolean,
        val isInstalled: Boolean,
        val firstInstallTime: Long?,
        val lastUpdateTime: Long?,
        val source: PluginIndexSource,
        val isFeatured: Boolean,
        val mechanism: PluginMechanism,
        val authorizedState: PluginAuthorizedState,
        val activatedState: PluginActivatedState,
        val enabledStateKey: String,
        val engine: String?,
        val variant: String?,
        val engineId: String?,
        val isOcrService: Boolean,
        val isBarcodeService: Boolean,
        val serviceAction: String?,
        val requiresHostVersion: Long?,
        val hostVersionCode: Long?,
    )

    companion object {
        private const val TAG = "PluginCenterViewModel"
        private val OCR_SERVICE_ENGINES = setOf("mlkit-ocr", "paddle-ocr", "rapid-ocr")
        private val BARCODE_SERVICE_ENGINES = setOf("mlkit-barcode")
        private val SERVICE_ACTION_BY_ENGINE = mapOf(
            "on-device-ai" to OnDeviceAiProtocol.ACTION_PROVIDER,
            "mlkit-ocr" to OcrPluginHost.ACTION_OCR,
            "paddle-ocr" to OcrPluginHost.ACTION_OCR,
            "rapid-ocr" to OcrPluginHost.ACTION_OCR,
            "mlkit-barcode" to BarcodePluginHost.ACTION_BARCODE,
            "opencc" to OpenccPluginHost.ACTION_OPENCC,
            "pinyin" to PinyinPluginHost.ACTION_PINYIN,
            "pinyin4j" to Pinyin4jPluginHost.ACTION_PINYIN4J,
            "mediainfo" to MediainfoPluginHost.ACTION_MEDIAINFO,
            ExplorerActionPluginIds.ENGINE to ExplorerActionPluginHost.ACTION_EXPLORER_ACTION,
            ImageQuantPluginContract.ENGINE to ImageQuantPluginContract.ACTION_RUNTIME,
            OfflineDocsPluginContract.ENGINE to OfflineDocsPluginContract.ACTION,
            OpenCvPluginContract.ENGINE to OpenCvPluginContract.ACTION_RUNTIME,
            ApkBuilderTemplatePluginIds.ENGINE to ApkBuilderTemplatePluginHost.ACTION_APK_BUILDER,
        )

        fun serviceActionFromEngine(engine: String?): String? {
            return engine?.trim()?.lowercase(Locale.ROOT)?.let(SERVICE_ACTION_BY_ENGINE::get)
        }
    }
}
