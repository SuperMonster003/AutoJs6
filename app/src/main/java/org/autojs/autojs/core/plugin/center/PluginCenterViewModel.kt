package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.autojs.autojs.apkbuilder.template.ApkBuilderTemplatePluginHost
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginHost
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.core.plugin.gallery.GalleryPluginHost
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.autojs.core.plugin.terminal.TerminalPluginHost
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPluginHost
import org.autojs.autojs.core.plugin.installer.InstallerPluginHost
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPluginHost
import org.autojs.plugin.gallery.api.GalleryIds
import org.autojs.plugin.speech.api.SpeechPluginIds
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.autojs.core.plugin.mcp.McpServerPluginHost
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.screencolorpicker.ScreenColorPickerPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.quickjs.runtime.api.QuickJsPluginActions
import org.autojs.plugin.quickjs.runtime.api.QuickJsPluginIds
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import org.autojs.plugin.epub.api.EpubIds
import org.autojs.plugin.terminal.api.TerminalIds
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.autojs.plugin.installer.api.InstallerIds
import org.autojs.plugin.mail.api.MailIds
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
import org.autojs.plugin.yolo.api.YoloContract
import java.util.Locale

/**
 * Loads both index plugins and locally installed plugins,
 * merging them into a PluginCenterItem list.
 *
 * Behavior strategy:
 * - First render a package-manager-only local snapshot together with the cached index.
 *   The snapshot reuses metadata persisted by the last complete discovery, so the first
 *   frame matches the last known real state including icons and descriptions.
 * - Then refresh complete local metadata and the online index independently in the background.
 * - Every publish is gated in the background by PluginCenterItemSemantics, so unchanged
 *   content never re-renders and comparison work never interrupts UI motion.
 * - Isolate discovery failures so one broken plugin never clears the complete list.
 *
 * zh-CN:
 *
 * 统一加载索引插件与本地已安装插件, 合并为 PluginCenterItem 列表.
 *
 * 行为策略:
 * - 先使用仅依赖 PackageManager 的本地快照与缓存索引渲染列表.
 *   快照复用上次完整发现持久化的元数据, 使首帧与上次已知的真实状态一致 (含图标与描述).
 * - 随后在后台分别刷新完整本地元数据与在线索引.
 * - 所有发布均在后台经 PluginCenterItemSemantics 门控, 内容未变化时不会重新渲染,
 *   比较工作也不会打断 UI 动效.
 * - 隔离发现异常, 避免单个异常插件清空整个列表.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Nov 26, 2025.
 * Modified by SuperMonster003 as of Jan 17, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
class PluginCenterViewModel : ViewModel() {

    private val indexRepo = PluginIndexRepository()

    private val installedRepo = InstalledPluginRepository()
    private val legacyRepo = LegacyInstalledPluginRepository()
    private val legacyRefreshState = PluginCenterLegacyRefreshState<LegacyInstalledPluginRepository.LegacyInstalledPlugin>()
    private val installedRefreshState = PluginCenterInstalledRefreshState<InstalledPluginRepository.InstalledPlugin>(
        packageOf = { it.packageName },
        buildOf = { it.versionCode to it.lastUpdateTime },
    )
    private val enableStore = PluginEnableStore
    private val iconRenderModeTracker = PluginIconRenderModeTracker()

    private val _items = MutableStateFlow<List<PluginCenterItem>>(emptyList())
    val items: StateFlow<List<PluginCenterItem>> = _items

    // Whether index loading is completed (includes "success/failure/empty" results, only means "no longer loading").
    // zh-CN: 索引加载是否已完成 (包含 "成功/失败/空" 三种结果, 仅代表 "不再加载").
    private val _indexLoaded = MutableStateFlow(false)
    val indexLoaded: StateFlow<Boolean> = _indexLoaded

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage: SharedFlow<String> = _toastMessage

    // Plugins installed while Plugin Center was closed, found by the comparison of PluginCenterAttentionStore.
    // zh-CN: 插件中心关闭期间装入的插件, 由 PluginCenterAttentionStore 的比对发现.
    private val _freshInstalls = MutableSharedFlow<List<String>>(extraBufferCapacity = 8)
    val freshInstalls: SharedFlow<List<String>> = _freshInstalls

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
        loadJob = viewModelScope.launch load@{
            fun isLatestLoad() = generation == loadGeneration
            suspend fun updateLatestItems(next: List<PluginCenterItem>) {
                if (isLatestLoad()) {
                    val updated = updateItemsIfChanged(
                        next = next,
                        force = forceRender || iconRenderModeTracker.shouldForceRender(iconNightModeEnabled),
                    )
                    if (updated) {
                        iconRenderModeTracker.markRendered(iconNightModeEnabled)
                        PluginInfoDialogManager.refreshIfShowing(context, _items.value)
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

            // Both operations are disk/package-manager only and never bind a plugin process.
            // Render their merged result before starting complete discovery.
            // zh-CN: 两个操作均只访问磁盘或 PackageManager, 不绑定插件进程.
            // 在完整发现开始前优先渲染二者的合并结果.
            val (cachedIndexEntries, installedSnapshotResult) = coroutineScope {
                val cachedIndex = async {
                    runCatching {
                        indexRepo.getCachedIndex(
                            context = context,
                            enabledSources = enabledSources,
                        )
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "getCachedIndex failed: ${error.message}")
                        val message = error.message ?: error.toString()
                        PluginCenterProcessLogger.i("Failed to read cached plugin index: $message.")
                        emitRefreshError(message)
                    }.getOrElse { emptyList() }
                }
                val localSnapshot = async<List<InstalledPluginRepository.InstalledPlugin>?> {
                    runCatching {
                        installedRepo.discoverInstalledSnapshot(context)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "installed snapshot discovery failed: ${error.message}")
                        val message = context.getString(
                            R.string.error_failed_to_load_plugins_with_reason,
                            error.message ?: error.toString(),
                        )
                        PluginCenterProcessLogger.i(
                            "Failed to create installed plugin snapshot: ${error.message ?: error.javaClass.simpleName}.",
                        )
                        emitRefreshError(message)
                    }.getOrNull()
                }
                cachedIndex.await() to localSnapshot.await()
            }
            if (!isLatestLoad()) return@load
            if (installedSnapshotResult != null) {
                // Before the first render, so a plugin installed while Plugin Center was closed is pinned at once.
                // zh-CN: 在首次渲染之前比对, 使插件中心关闭期间装入的插件立即置顶.
                reportFreshInstalls(context, PluginCenterAttentionLedger.SOURCE_AIDL, installedSnapshotResult.associate { it.packageName to it.firstInstallTime })
            }
            val installedSnapshot = installedSnapshotResult.orEmpty()

            PluginCenterProcessLogger.i("Cached plugin index entries loaded: count=${cachedIndexEntries.size}.")
            PluginCenterProcessLogger.i("Installed plugin snapshot created: count=${installedSnapshot.size}.")
            val retainedLegacySnapshot = legacyRefreshState.beginRefresh()
            val renderedInitialSnapshot = _items.value.isEmpty()
            if (renderedInitialSnapshot) {
                updateLatestItems(
                    buildMergedItemsInBackground(
                        context = context,
                        indexEntries = cachedIndexEntries,
                        installed = installedSnapshot,
                        legacyInstalled = retainedLegacySnapshot,
                        allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                        sourceScope = sourceScope,
                    ),
                )
            }

            // Complete AIDL discovery, legacy SDK discovery, and online refresh are independent.
            // Each result updates the currently visible list without waiting for the other stages.
            // zh-CN: 完整 AIDL 发现, 传统 SDK 发现及在线刷新彼此独立.
            // 任一阶段完成后都立即更新当前列表, 无需等待其他阶段.
            coroutineScope {
                val stateMutex = Mutex()
                // A refresh keeps the complete entries already on screen until its own complete discovery finishes.
                // zh-CN: 刷新在自身完整发现完成前沿用屏幕上已有的完整条目.
                var currentInstalled = installedRefreshState.baseline(installedSnapshot)
                var currentLegacyInstalled = retainedLegacySnapshot
                var currentIndexEntries = cachedIndexEntries

                suspend fun publish(
                    installedUpdate: List<InstalledPluginRepository.InstalledPlugin>? = null,
                    legacyUpdate: List<LegacyInstalledPluginRepository.LegacyInstalledPlugin>? = null,
                    indexUpdate: List<PluginIndexEntry>? = null,
                ) {
                    stateMutex.withLock {
                        if (!isLatestLoad()) return@withLock
                        installedUpdate?.let { currentInstalled = it }
                        legacyUpdate?.let {
                            currentLegacyInstalled = legacyRefreshState.completeRefresh(it)
                        }
                        indexUpdate?.let { currentIndexEntries = it }
                        updateLatestItems(
                            buildMergedItemsInBackground(
                                context = context,
                                indexEntries = currentIndexEntries,
                                installed = currentInstalled,
                                legacyInstalled = currentLegacyInstalled,
                                allowGlobalUpdateCheck = autoCheckUpdatesEnabled,
                                sourceScope = sourceScope,
                            ),
                        )
                    }
                }

                val installedJob = launch {
                    PluginCenterProcessLogger.i("Discover installed AIDL plugins in background.")
                    val result = runCatching {
                        installedRepo.discoverInstalled(context)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "installed discovery failed: ${error.message}")
                        val message = context.getString(
                            R.string.error_failed_to_load_plugins_with_reason,
                            error.message ?: error.toString(),
                        )
                        PluginCenterProcessLogger.i(
                            "Failed to discover installed AIDL plugins: ${error.message ?: error.javaClass.simpleName}.",
                        )
                        emitRefreshError(message)
                    }.getOrNull()
                    if (result != null) {
                        PluginCenterProcessLogger.i("Installed AIDL plugins discovered: count=${result.size}.")
                        publish(installedUpdate = installedRefreshState.complete(result))
                    }
                }

                val legacyJob = launch {
                    PluginCenterProcessLogger.i("Discover installed legacy SDK plugins in background.")
                    val result = runCatching {
                        legacyRepo.discoverInstalled(context)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "legacy discovery failed: ${error.message}")
                        val message = error.message ?: error.toString()
                        PluginCenterProcessLogger.i("Failed to discover installed legacy SDK plugins: $message.")
                        emitRefreshError(message)
                    }.getOrNull()
                    if (result != null) {
                        PluginCenterProcessLogger.i("Installed legacy SDK plugins discovered: count=${result.size}.")
                        if (isLatestLoad()) {
                            reportFreshInstalls(context, PluginCenterAttentionLedger.SOURCE_LEGACY, result.associate { it.packageName to it.firstInstallTime })
                        }
                        publish(legacyUpdate = result)
                    }
                }

                val indexJob = launch {
                    val result = runCatching {
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
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "fetchOnlineIndex failed: ${error.message}")
                        val message = error.message ?: error.toString()
                        PluginCenterProcessLogger.i("Failed to fetch online plugin index: $message.")
                        emitRefreshError(
                            context.getString(
                                R.string.error_plugin_index_fetch_failed_with_reason,
                                message,
                            ),
                        )
                    }.getOrElse { cachedIndexEntries }
                    publish(indexUpdate = result)
                }

                joinAll(installedJob, legacyJob, indexJob)
            }
            if (!isLatestLoad()) return@load

            PluginCenterProcessLogger.i("Plugin center load finished: visible entries=${_items.value.size}.")
            _indexLoaded.value = true

        }
        loadJob?.invokeOnCompletion {
            if (generation == loadGeneration && !_indexLoaded.value) {
                _indexLoaded.value = true
            }
        }
    }

    private fun reportFreshInstalls(context: Context, source: String, observed: Map<String, Long?>) {
        val fresh = PluginCenterAttentionStore.reconcile(context, source, observed)
        if (fresh.isNotEmpty()) {
            PluginCenterProcessLogger.i("Plugins installed while Plugin Center was closed: $fresh.")
            _freshInstalls.tryEmit(fresh)
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean, error: PluginError? = null) {
        enableStore.setEnabled(context, packageName, enabled)
        val allowed = enabled && enableStore.isEnabled(context, packageName)
        _items.value = _items.value.map {
            if (it.packageName == packageName) {
                val nextEnabledState = when {
                    error != null && error.code != PluginErrorCode.NOT_AUTHORIZED -> PluginEnabledState.ERROR(error)
                    !allowed -> PluginEnabledState.DISABLED
                    else -> PluginEnabledState.READY
                }
                it.copy(
                    isEnabled = allowed,
                    enabledState = nextEnabledState,
                    lastError = error,
                )
            } else it
        }
        PluginInfoDialogManager.refreshIfShowing(context, _items.value)
    }

    private suspend fun updateItemsIfChanged(next: List<PluginCenterItem>, force: Boolean = false): Boolean {
        if (force) {
            _items.value = next
            return true
        }

        while (true) {
            val comparedItems = _items.value
            val unchanged = withContext(Dispatchers.Default) {
                PluginCenterItemSemantics.isSameList(comparedItems, next)
            }
            // A toggle or another publisher may update the list while comparison is
            // running. Compare again instead of overwriting that newer state.
            // zh-CN: 比较期间可能由开关或其他发布者更新列表.
            // 此时重新比较, 避免覆盖更新的状态.
            if (_items.value !== comparedItems) {
                continue
            }
            if (unchanged) {
                return false
            }
            _items.value = next
            return true
        }
    }

    private fun PluginIndexSource.labelForMessage(context: Context): String = when (this) {
        PluginIndexSource.OFFICIAL -> context.getString(R.string.text_plugin_official)
        PluginIndexSource.THIRD_PARTY -> context.getString(R.string.text_plugin_third_party)
    }

    private fun filterHidden(context: Context, items: List<PluginCenterItem>): List<PluginCenterItem> {
        if (items.isEmpty()) return emptyList()
        return items.filterNot { PluginVisibilityStore.isHidden(context, it.packageName) }
    }

    private suspend fun buildMergedItemsInBackground(
        context: Context,
        indexEntries: List<PluginIndexEntry>,
        installed: List<InstalledPluginRepository.InstalledPlugin>,
        legacyInstalled: List<LegacyInstalledPluginRepository.LegacyInstalledPlugin>,
        allowGlobalUpdateCheck: Boolean,
        sourceScope: PluginIndexSource?,
    ): List<PluginCenterItem> = withContext(Dispatchers.Default) {
        buildMergedItems(
            context = context,
            indexEntries = indexEntries,
            installed = installed,
            legacyInstalled = legacyInstalled,
            allowGlobalUpdateCheck = allowGlobalUpdateCheck,
            sourceScope = sourceScope,
        )
    }

    private fun buildMergedItems(
        context: Context,
        indexEntries: List<PluginIndexEntry>,
        installed: List<InstalledPluginRepository.InstalledPlugin>,
        legacyInstalled: List<LegacyInstalledPluginRepository.LegacyInstalledPlugin>,
        allowGlobalUpdateCheck: Boolean,
        sourceScope: PluginIndexSource?,
    ): List<PluginCenterItem> {
        val installedByPackage = installed.associateBy { it.packageName }
        val installedByNormalizedTitle = installed.groupBy { normalizeTitle(it.title) }
        val legacyInstalledByPackage = legacyInstalled.associateBy { it.packageName }

        val fromIndex = indexEntries.mapNotNull { entry ->
            val legacy = legacyInstalledByPackage[entry.packageName]
            if (legacy != null) {
                return@mapNotNull toLegacyPluginCenterItemSafely(
                    context = context,
                    local = legacy,
                    index = entry,
                    allowGlobalUpdateCheck = allowGlobalUpdateCheck,
                )
            }
            val local = matchLocalPluginForIndex(
                index = entry,
                installedByPkg = installedByPackage,
                installedByNormalizedTitle = installedByNormalizedTitle,
            )
            toPluginCenterItemSafely(
                context = context,
                index = entry,
                local = local,
                allowGlobalUpdateCheck = allowGlobalUpdateCheck,
            )
        }.let(::dedupeByPackagePreferInstalled)

        // Supplement plugins that exist locally but are absent from the selected index.
        // zh-CN: 补充已安装但未包含在所选索引中的插件.
        val fromIndexPackageNames = fromIndex.asSequence().map { it.packageName }.toHashSet()
        val extraLocals = if (sourceScope == null) {
            val extraAidlLocals = installed
                .filter { it.packageName !in fromIndexPackageNames }
                .mapNotNull { local ->
                    toPluginCenterItemSafely(
                        context = context,
                        index = null,
                        local = local,
                        allowGlobalUpdateCheck = allowGlobalUpdateCheck,
                    )
                }
            val extraLegacyLocals = legacyInstalled
                .filter { it.packageName !in fromIndexPackageNames }
                .mapNotNull { legacy ->
                    toLegacyPluginCenterItemSafely(
                        context = context,
                        local = legacy,
                        index = null,
                        allowGlobalUpdateCheck = allowGlobalUpdateCheck,
                    )
                }
            dedupeByPackagePreferInstalled(extraAidlLocals + extraLegacyLocals)
        } else {
            emptyList()
        }

        return filterHidden(context, dedupeByPackagePreferInstalled(fromIndex + extraLocals))
    }

    private fun toPluginCenterItemSafely(
        context: Context,
        index: PluginIndexEntry?,
        local: InstalledPluginRepository.InstalledPlugin?,
        allowGlobalUpdateCheck: Boolean,
    ): PluginCenterItem? = runCatching {
        toPluginCenterItem(context, index, local, allowGlobalUpdateCheck)
    }.getOrElse { error ->
        val packageName = local?.packageName ?: index?.packageName ?: return@getOrElse null
        Log.w(TAG, "Failed to build Plugin Center item for $packageName", error)
        PluginCenterProcessLogger.i(
            "Failed to build Plugin Center item for $packageName: ${error.message ?: error.javaClass.simpleName}.",
        )
        buildFallbackPluginCenterItem(context, index, local, error)
    }

    private fun toLegacyPluginCenterItemSafely(
        context: Context,
        local: LegacyInstalledPluginRepository.LegacyInstalledPlugin,
        index: PluginIndexEntry?,
        allowGlobalUpdateCheck: Boolean,
    ): PluginCenterItem? = runCatching {
        toLegacyPluginCenterItem(context, local, index, allowGlobalUpdateCheck)
    }.getOrElse { error ->
        Log.w(TAG, "Failed to build legacy Plugin Center item for ${local.packageName}", error)
        PluginCenterProcessLogger.i(
            "Failed to build legacy Plugin Center item for ${local.packageName}: ${error.message ?: error.javaClass.simpleName}.",
        )
        // Suspended, not disabled: the next item that builds again restores the user's switch.
        // zh-CN: 挂起而非禁用: 下次条目重新构建成功时恢复用户原来的开关状态.
        enableStore.suspend(context, local.packageName, defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(context, index?.engine, local.packageName))
        val mappedError = PluginErrorMapper.fromThrowable(error)
        val trustInfo = runCatching {
            PluginTrustManager.resolveTrustInfo(context, local.packageName)
        }.getOrNull()
        PluginCenterItem(
            title = local.title.takeIf { it.isNotBlank() } ?: index?.title ?: local.packageName,
            nativePageAlignment = PluginNativeAlignmentPolicy.installedAlignment(null, local.versionCode, index?.releases.orEmpty()),
            packageName = local.packageName,
            versionName = local.versionName,
            versionCode = local.versionCode,
            versionDate = local.versionDate,
            author = local.author ?: index?.author,
            description = local.description ?: index?.description,
            packageSize = local.packageSize,
            icon = local.icon,
            iconSignature = local.iconSignature,
            remoteIconUrl = index?.iconUrl?.toString(),
            remoteNightIconUrl = index?.nightIconUrl?.toString(),
            isEnabled = false,
            isInstalled = true,
            firstInstallTime = local.firstInstallTime,
            lastUpdateTime = local.lastUpdateTime,
            settings = PluginCenterItemSettings(),
            source = index?.source ?: PluginIndexSource.THIRD_PARTY,
            isFeatured = index?.isFeatured ?: true,
            mechanism = PluginMechanism.SDK,
            authorizedState = trustInfo?.authorizedState ?: PluginAuthorizedState.REQUIRED,
            activatedState = PluginActivatedState.NOT_SUPPORTED,
            enabledState = PluginEnabledState.ERROR(mappedError),
            lastError = mappedError,
            signingFingerprintSha256 = trustInfo?.primaryFingerprintSha256,
            isOfficialVerified = trustInfo?.isOfficial == true,
            requiresHostVersion = index?.requiresHostVersion,
            hostVersionCode = currentHostVersionCode(),
        )
    }

    private fun buildFallbackPluginCenterItem(
        context: Context,
        index: PluginIndexEntry?,
        local: InstalledPluginRepository.InstalledPlugin?,
        itemError: Throwable,
    ): PluginCenterItem? {
        val packageName = local?.packageName ?: index?.packageName ?: return null
        val isInstalled = local != null
        val latestRelease = index?.releases?.maxByOrNull { it.versionCode }
        val mappedError = PluginErrorMapper.fromThrowable(local?.bindError ?: itemError)
        val trustInfo = if (isInstalled) {
            runCatching { PluginTrustManager.resolveTrustInfo(context, packageName) }.getOrNull()
        } else null
        val engine = local?.engine ?: index?.engine
        if (isInstalled) {
            // Suspended, not disabled: the next item that builds again restores the user's switch.
            // zh-CN: 挂起而非禁用: 下次条目重新构建成功时恢复用户原来的开关状态.
            val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(engine, trustInfo?.authorizedState ?: PluginAuthorizedState.REQUIRED)
            enableStore.suspend(context, packageName, defaultEnabled)
        }
        return PluginCenterItem(
            title = local?.title?.takeIf { it.isNotBlank() } ?: index?.title ?: packageName,
            nativePageAlignment = if (local != null) PluginNativeAlignmentPolicy.installedAlignment(local.nativePageAlignment, local.versionCode, index?.releases.orEmpty()) else latestRelease?.nativePageAlignment ?: index?.nativePageAlignment,
            packageName = packageName,
            versionName = local?.versionName ?: latestRelease?.versionName ?: context.getString(R.string.text_unknown),
            versionCode = local?.versionCode ?: latestRelease?.versionCode,
            versionDate = local?.versionDate ?: latestRelease?.versionDate,
            author = local?.author ?: index?.author ?: trustInfo?.developer,
            collaborators = index?.collaborators ?: emptyList(),
            description = local?.description ?: index?.description,
            instructionHardCoded = local?.instructionHardCoded ?: index?.instructionHardCoded,
            instructionMarkdownUrl = index?.instructionMarkdownUrl,
            packageSize = local?.packageSize ?: 0L,
            installableApkUrl = latestRelease?.apkUrl,
            installableApkSha256 = latestRelease?.apkSha256,
            installableApkSizeBytes = latestRelease?.apkSizeBytes,
            supportedAbis = PluginAbiUtils.resolveSupportedAbisForDisplay(local?.declaredSupportedAbis, index?.supportedAbis, local?.supportedAbis),
            installedAbis = local?.installedAbis,
            remoteIconUrl = index?.iconUrl?.toString(),
            remoteNightIconUrl = index?.nightIconUrl?.toString(),
            forceIgnoreLocalIcon = index?.forceIgnoreLocalIcon == true,
            iconBackgroundColor = index?.iconBackgroundColor,
            nightIconBackgroundColor = index?.nightIconBackgroundColor,
            icon = if (index?.forceIgnoreLocalIcon == true) null else local?.icon,
            iconSignature = if (index?.forceIgnoreLocalIcon == true) null else local?.iconSignature,
            isEnabled = false,
            isInstalled = isInstalled,
            firstInstallTime = local?.firstInstallTime,
            lastUpdateTime = local?.lastUpdateTime,
            settings = if (isInstalled) PluginCenterItemSettings() else null,
            source = index?.source ?: PluginIndexSource.OFFICIAL,
            isFeatured = index?.isFeatured ?: true,
            engine = engine,
            variant = local?.variant ?: index?.variant,
            engineId = local?.engineId ?: index?.engineId,
            isOcrService = local?.isOcrService == true || engine in OCR_SERVICE_ENGINES,
            isBarcodeService = local?.isBarcodeService == true || engine in BARCODE_SERVICE_ENGINES,
            serviceAction = local?.serviceAction ?: serviceActionFromEngine(engine),
            mechanism = if (isInstalled || index?.source != PluginIndexSource.THIRD_PARTY) {
                PluginMechanism.AIDL
            } else {
                PluginMechanism.SDK
            },
            authorizedState = trustInfo?.authorizedState ?: PluginAuthorizedState.REQUIRED,
            activatedState = PluginActivatedState.NOT_SUPPORTED,
            enabledState = if (isInstalled) PluginEnabledState.ERROR(mappedError) else PluginEnabledState.DISABLED,
            lastError = mappedError.takeIf { isInstalled },
            signingFingerprintSha256 = trustInfo?.primaryFingerprintSha256,
            isOfficialVerified = trustInfo?.isOfficial == true,
            canActivate = false,
            requiresHostVersion = local?.requiresHostVersion ?: index?.requiresHostVersion,
            hostVersionCode = currentHostVersionCode(),
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
        val supportedAbis = PluginAbiUtils.resolveSupportedAbisForDisplay(local?.declaredSupportedAbis, index?.supportedAbis, local?.supportedAbis)
        val engine = local?.engine ?: index?.engine
        val variant = local?.variant ?: index?.variant
        val engineId = local?.engineId ?: index?.engineId
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

        // Normal releases only surface versions newer than the installed one. A compatibility-
        // matrix match may intentionally be lower after the host is downgraded; expose it as a
        // destructive uninstall/reinstall action instead of an Android in-place update.
        // zh-CN: 普通版本仅展示高于已安装版本的更新. 宿主降级后, 兼容矩阵可能命中更低的
        // 配套版本; 此时展示卸载重装动作, 不将其作为 Android 覆盖更新处理.
        val targetAction = PluginReleaseTargetPolicy.resolve(
            releases = index?.releases.orEmpty(),
            installedVersionCode = versionCodeLocal.takeIf { isInstalled },
            allowUpdateCheck = allowUpdateCheck,
            isIgnored = { versionCode -> UpdateIgnoreStore.isIgnored(packageName, versionCode) },
        )
        val targetUpdate = targetAction?.release

        val trustInfo = runCatching { PluginTrustManager.resolveTrustInfo(context, packageName) }.getOrElse {
            PluginTrustManager.TrustInfo(
                authorizedState = PluginAuthorizedState.REQUIRED,
                isOfficial = false,
                isTrusted = false,
                primaryFingerprintSha256 = null,
                fingerprintsSha256 = emptyList(),
            )
        }

        val defaultEnabled = isInstalled && PluginDefaultEnabledPolicy.isEnabledByDefault(engine, trustInfo.authorizedState)
        var enabled = enableStore.isEnabled(context, packageName, defaultEnabled = defaultEnabled)
        if (trustInfo.authorizedState == PluginAuthorizedState.REQUIRED && enabled) {
            enableStore.setEnabled(context, packageName, false)
            enabled = false
        }
        if (!isHostVersionCompatible(hostVersionCode, requiresHostVersion) && enabled) {
            if (engine != ThreeStoveAgentIds.ENGINE) enableStore.setEnabled(context, packageName, false)
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
        val shouldSurfaceDiscoveryError = mappedError != null &&
                trustInfo.authorizedState != PluginAuthorizedState.REQUIRED &&
                hostVersionError == null
        if (shouldSurfaceDiscoveryError && enabled) {
            // A plugin that could not complete discovery must not remain logically enabled, but a
            // transient install/probe failure must not become a permanent opt-out either: the plugin
            // is suspended, and the next discovery that reaches it switches it back on (below).
            // Agent availability checks reject this build without touching the stored state.
            // zh-CN: 无法完成发现的插件不得继续保持逻辑启用状态, 但临时的安装/探测失败也不应变成永久退出:
            // 插件被挂起, 下一次连上它的发现会恢复启用 (见下方). Agent 可用性检查直接拒绝此构建, 不改动存储的状态.
            if (engine != ThreeStoveAgentIds.ENGINE) enableStore.suspend(context, packageName, defaultEnabled)
            enabled = false
        }
        if (!enabled && isInstalled && mappedError == null && authError == null && hostVersionError == null &&
            enableStore.restoreIfSuspended(context, packageName)
        ) {
            enabled = true
        }
        val lastError = authError ?: hostVersionError ?: mappedError
        val enabledState = when {
            hostVersionError != null -> PluginEnabledState.ERROR(hostVersionError)
            trustInfo.authorizedState == PluginAuthorizedState.REQUIRED -> PluginEnabledState.DISABLED
            shouldSurfaceDiscoveryError -> PluginEnabledState.ERROR(mappedError)
            !enabled -> PluginEnabledState.DISABLED
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
            requiresDowngradeReinstall = targetAction?.requiresDowngradeReinstall == true,

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
            installedAbis = local?.installedAbis,
            icon = if (forceIgnoreLocalIcon) null else local?.icon,
            iconSignature = if (forceIgnoreLocalIcon) null else local?.iconSignature,
            remoteIconUrl = index?.iconUrl?.toString(),
            remoteNightIconUrl = index?.nightIconUrl?.toString(),
            forceIgnoreLocalIcon = forceIgnoreLocalIcon,
            iconBackgroundColor = index?.iconBackgroundColor,
            nightIconBackgroundColor = index?.nightIconBackgroundColor,
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
            nativePageAlignment = if (local != null) PluginNativeAlignmentPolicy.installedAlignment(local.nativePageAlignment, local.versionCode, index?.releases.orEmpty()) else latestRelease?.nativePageAlignment ?: index?.nativePageAlignment,
            updatableNativePageAlignment = targetUpdate?.nativePageAlignment,
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
        val targetAction = PluginReleaseTargetPolicy.resolve(
            releases = index?.releases.orEmpty(),
            installedVersionCode = local.versionCode,
            allowUpdateCheck = allowUpdateCheck,
            isIgnored = { versionCode -> UpdateIgnoreStore.isIgnored(packageName, versionCode) },
        )
        val targetUpdate = targetAction?.release
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

        var enabled = enableStore.isEnabled(
            context,
            packageName,
            defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(index?.engine, trustInfo.authorizedState),
        )
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
        // The item built, so a plugin suspended by an earlier failure is reachable again.
        // zh-CN: 条目已构建成功, 之前因故障被挂起的插件已可再次连接.
        if (!enabled && authError == null && hostVersionError == null && enableStore.restoreIfSuspended(context, packageName)) {
            enabled = true
        }
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
            requiresDowngradeReinstall = targetAction?.requiresDowngradeReinstall == true,

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
            iconSignature = if (forceIgnoreLocalIcon) null else local.iconSignature,
            remoteIconUrl = index?.iconUrl?.toString(),
            remoteNightIconUrl = index?.nightIconUrl?.toString(),
            forceIgnoreLocalIcon = forceIgnoreLocalIcon,
            iconBackgroundColor = index?.iconBackgroundColor,
            nightIconBackgroundColor = index?.nightIconBackgroundColor,
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
            nativePageAlignment = PluginNativeAlignmentPolicy.installedAlignment(null, local.versionCode, index?.releases.orEmpty()),
            updatableNativePageAlignment = targetUpdate?.nativePageAlignment,
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

    companion object {
        private const val TAG = "PluginCenterViewModel"
        private val OCR_SERVICE_ENGINES = setOf("mlkit-ocr", "paddle-ocr", "rapid-ocr")
        private val BARCODE_SERVICE_ENGINES = setOf("mlkit-barcode")
        private val SERVICE_ACTION_BY_ENGINE = mapOf(
            "three-stone-ai" to AiProviderProtocol.ACTION_PROVIDER,
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
            ScreenColorPickerContract.ENGINE to ScreenColorPickerPluginHost.ACTION_SCREEN_COLOR_PICKER,
            McpServerIds.ENGINE to McpServerPluginHost.ACTION_MCP_SERVER,
            ThreeStoveAgentIds.ENGINE to ThreeStoveAgentActions.SERVICE_ACTION,
            MailIds.ENGINE to MailPluginHost.ACTION_MAIL,
            InstallerIds.ENGINE to InstallerPluginHost.ACTION_INSTALLER,
            EpubIds.ENGINE to EpubPluginHost.ACTION_EPUB,
            TerminalIds.ENGINE to TerminalPluginHost.ACTION_TERMINAL,
            BatchRenamePluginIds.ENGINE to BatchRenamePluginHost.ACTION_BATCH_RENAME,
            GalleryIds.ENGINE to GalleryPluginHost.ACTION_GALLERY,
            SpeechPluginIds.ENGINE to SpeechPluginHost.ACTION_SPEECH,
            YoloContract.ENGINE_ID to YoloContract.SERVICE_ACTION,
            ApkBuilderTemplatePluginIds.ENGINE to ApkBuilderTemplatePluginHost.ACTION_APK_BUILDER,
            QuickJsPluginIds.ENGINE to QuickJsPluginActions.RUNTIME,
        )

        fun serviceActionFromEngine(engine: String?): String? {
            return engine?.trim()?.lowercase(Locale.ROOT)?.let(SERVICE_ACTION_BY_ENGINE::get)
        }
    }
}
