package org.autojs.autojs.core.plugin.center

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginHost
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.core.plugin.terminal.TerminalPluginHost
import org.autojs.autojs.core.plugin.terminal.ThreeShellTerminalOfficialPlugin
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPluginHost
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPluginHost
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.autojs.core.plugin.mcp.McpServerPluginHost
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.screencolorpicker.ScreenColorPickerPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.core.plugin.yolo.YoloPluginHost
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentPluginCenterBinding
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.autojs.plugin.yolo.api.YoloContract

class PluginCenterFragment : Fragment(R.layout.fragment_plugin_center) {

    private var _binding: FragmentPluginCenterBinding? = null
    private val binding get() = _binding!!

    private val vm: PluginCenterViewModel by viewModels()
    private lateinit var adapter: PluginCenterItemAdapter
    private lateinit var listSubmitter: PluginCenterFrameChunkedListSubmitter<PluginCenterItem>
    private lateinit var itemViewPreloader: PluginCenterItemViewPreloader
    private lateinit var contextRef: Context

    private var pkgReceiver: BroadcastReceiver? = null

    // Job for delaying the display of empty state hint.
    // zh-CN: 用于延迟显示空态提示的 Job.
    private var emptyHintJob: Job? = null

    // Job for debouncing package change refresh during replacing install/update.
    // zh-CN: 用于在替换安装/更新期间对包变更刷新进行去抖的 Job.
    private var pkgChangeRefreshJob: Job? = null

    private var isFirstEnter: Boolean = true
    private val resumeRefreshPolicy = PluginCenterResumeRefreshPolicy()
    private var latestIndexLoaded: Boolean = false
    private var lastNightModeEnabled: Boolean? = null
    private var nightModeRefreshJob: Job? = null
    private var swipeRefreshIndicatorJob: Job? = null

    // Latest full list from ViewModel (unfiltered).
    // zh-CN: 来自 ViewModel 的最新完整列表 (未过滤).
    private var latestFullItems: List<PluginCenterItem> = emptyList()
    private var pendingInitialRenderItems: List<PluginCenterItem>? = null
    private var isInitialViewportReady = false

    // Current query used by UI filtering, null means "no filtering".
    // zh-CN: 当前用于 UI 过滤的查询串, null 表示 "不做过滤".
    private var currentQuery: String? = null

    private var sourceScope: PluginIndexSource? = null
    private var variantDisplayMode: PluginVariantDisplayMode = PluginVariantDisplayMode.STANDARD
    private val isScopedLibraryMode: Boolean
        get() = sourceScope != null

    private lateinit var currentSort: Sort
    private var currentFilterState = PluginFilterStore.defaultFilterState()
    private val sortPolicy = PluginCenterSortPolicy()
    private val actionPositionPolicy = PluginCenterActionPositionPolicy()
    private var renderedSortSelection: SortSelection? = null

    private val sortPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!isAdded || _binding == null) return@OnSharedPreferenceChangeListener
        val ctx = contextRef
        viewLifecycleOwner.lifecycleScope.launch {
            setSort(loadPersistedSort(ctx))
        }
    }

    private val filterPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!isAdded || _binding == null) return@OnSharedPreferenceChangeListener
        val ctx = contextRef
        viewLifecycleOwner.lifecycleScope.launch {
            setFilterState(PluginFilterStore.getFilterState(ctx))
        }
    }

    private val onlinePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!isAdded || _binding == null) return@OnSharedPreferenceChangeListener
        val ctx = contextRef
        val officialKey = ctx.getString(R.string.key_plugin_center_show_official_plugins)
        val thirdPartyKey = ctx.getString(R.string.key_plugin_center_show_third_party_plugins)
        val autoCheckKey = ctx.getString(R.string.key_plugin_center_auto_check_updates)
        when (key) {
            officialKey, thirdPartyKey -> {
                if (!isScopedLibraryMode) {
                    vm.load(ctx, forceRefreshIndex = true, sourceScope = sourceScope)
                }
            }
            autoCheckKey -> {
                vm.load(ctx, forceRefreshIndex = false, sourceScope = sourceScope)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceScope = arguments?.getString(ARG_SOURCE_SCOPE)
            ?.runCatching { PluginIndexSource.valueOf(this) }
            ?.getOrNull()
        currentQuery = arguments?.getString(ARG_INITIAL_QUERY)?.takeIf { it.isNotBlank() }
        variantDisplayMode = arguments?.getString(ARG_VARIANT_DISPLAY_MODE)
            ?.runCatching { PluginVariantDisplayMode.valueOf(this) }
            ?.getOrNull()
            ?: PluginVariantDisplayMode.STANDARD
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPluginCenterBinding.bind(view)

        val context = requireContext().also { contextRef = it }
        lastNightModeEnabled = isNightModeEnabled()

        currentSort = loadPersistedSort(context)
        currentFilterState = loadFilterState(context)
        latestIndexLoaded = false

        adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
            override fun onInstall(item: PluginCenterItem) {
                val url = item.installableApkUrl
                if (url.isNullOrBlank()) {
                    ViewUtils.showToast(contextRef, getString(R.string.text_unavailable), true)
                    return
                }
                retainActionPosition(item)
                PluginInstallCompatibilityPrompt.runBeforeDownload(contextRef, item) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        PluginInstaller.installFromUrlWithPrompt(contextRef, url, item.installableApkSha256)
                    }
                }
            }

            override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) {
                retainActionPosition(item)
                if (!enabled) {
                    vm.setEnabled(contextRef, item.packageName, false)
                    item.isEnabled = false
                    item.enabledState = PluginEnabledState.DISABLED
                    item.lastError = null
                    adapter.notifyDataSetChanged()
                    return
                }

                if (!item.isInstalled) {
                    vm.setEnabled(contextRef, item.packageName, false)
                    item.isEnabled = false
                    item.enabledState = PluginEnabledState.DISABLED
                    item.lastError = null
                    adapter.notifyDataSetChanged()
                    ViewUtils.showToast(contextRef, getString(R.string.text_unavailable), true)
                    return
                }

                ensureAuthorized(item) { authorizedItem ->
                    if (!PluginLocalNetworkPermission.isGranted(contextRef, authorizedItem.packageName)) {
                        vm.setEnabled(contextRef, authorizedItem.packageName, false)
                        authorizedItem.isEnabled = false
                        authorizedItem.enabledState = PluginEnabledState.DISABLED
                        authorizedItem.lastError = null
                        adapter.notifyDataSetChanged()
                    }
                    PluginLocalNetworkPermission.runBeforeEnable(contextRef, authorizedItem.packageName, authorizedItem.title) {
                        if (isAdded && _binding != null) {
                            PluginAbiCompatibilityPrompt.runBeforeEnable(contextRef, authorizedItem) {
                                when (authorizedItem.mechanism) {
                                    PluginMechanism.SDK -> enableLegacyPlugin(authorizedItem)
                                    PluginMechanism.AIDL -> enableAidlPlugin(authorizedItem)
                                }
                            }
                        }
                    }
                }
            }

            override fun onUninstall(item: PluginCenterItem) {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_confirm_to_uninstall)
                    .negativeText(R.string.dialog_button_cancel)
                    .neutralColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _, _ ->
                        retainActionPosition(item)
                        val uri = "package:${item.packageName}".toUri()
                        val intent = Intent(Intent.ACTION_DELETE, uri)
                        startActivity(intent)
                    }
                    .show()
            }

            override fun onDetails(item: PluginCenterItem) {
                PluginInfoDialogManager.showPluginInfoDialog(contextRef, item, ::retainActionPosition)
            }

            override fun onUpdate(item: PluginCenterItem) {
                PluginInfoDialogManager.showUpdatablePluginInfoDialog(
                    contextRef, PluginInfoDialogManager.PluginInfoUpdatable(item), onUserAction = ::retainActionPosition,
                )
            }

            override fun onSettings(item: PluginCenterItem) {
                if (item.packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME) {
                    org.autojs.autojs.ui.settings.ThreeStoveAgentLauncher.launch(contextRef)
                    return
                }
                if (item.packageName == org.autojs.plugin.mcp.server.api.McpServerIds.DEFAULT_PACKAGE_NAME) {
                    org.autojs.autojs.ui.settings.McpServerSettingsLauncher.launch(contextRef)
                    return
                }
                if (item.packageName == ThreeShellTerminalOfficialPlugin.PACKAGE_NAME) {
                    org.autojs.autojs.ui.settings.TerminalPluginSettingsLauncher.launch(contextRef)
                    return
                }
                val installedCountForEngine = item.engine
                    ?.takeIf { it.isNotBlank() }
                    ?.let { engineName ->
                        latestFullItems.count { current ->
                            current.isInstalled && current.engine == engineName
                        }
                    } ?: 0
                PluginSettingsActivity.launch(contextRef, item, installedCountForEngine)
            }
        })

        binding.pluginCenterRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PluginCenterFragment.adapter
            addItemDecoration(DividerItemDecoration(context, VERTICAL))
            // Data snapshots can arrive exactly when the user starts dragging. RecyclerView's
            // structural animations retain and relayout both old and new holders in that frame,
            // so keep snapshot publication visually stable and leave motion to page/scroll UI.
            // zh-CN: 数据快照可能恰在用户开始拖动时到达. RecyclerView 的结构动画会在该帧
            // 同时保留并重新布局新旧 ViewHolder, 因此列表快照保持静态更新, 动效交由页面与滚动 UI.
            itemAnimator = null
            setHasFixedSize(true)
            excludePaddingClippableViewFromBottomNavigationBar()
        }

        listSubmitter = PluginCenterFrameChunkedListSubmitter(
            submitList = adapter::updateData,
            postNextFrame = binding.pluginCenterRecyclerView::postOnAnimation,
            removeFrameCallback = binding.pluginCenterRecyclerView::removeCallbacks,
        )
        isInitialViewportReady = false
        pendingInitialRenderItems = null
        itemViewPreloader = PluginCenterItemViewPreloader(
            context = context,
            parent = binding.pluginCenterRecyclerView,
            onItemViewReady = adapter::offerPreloadedItemView,
            onViewportReady = {
                if (_binding != null) {
                    isInitialViewportReady = true
                    pendingInitialRenderItems?.let { items ->
                        pendingInitialRenderItems = null
                        listSubmitter.submit(items)
                    }
                }
            },
        ).also(PluginCenterItemViewPreloader::start)

        // Pull-to-refresh: force refresh index every time (still respects backoff window).
        // zh-CN: 下拉刷新: 每次都强制刷新索引 (仍遵守退避窗口).
        binding.pluginCenterSwipeRefresh.setOnRefreshListener {
            actionPositionPolicy.clear()
            renderList()
            scheduleSwipeRefreshIndicatorTimeout()
            vm.load(contextRef, forceRefreshIndex = true, sourceScope = sourceScope, showRefreshErrors = true)
        }

        // First entry: local priority + index async.
        // zh-CN: 首次进入: 本地优先 + 索引异步.
        vm.load(context, forceRefreshIndex = false, sourceScope = sourceScope)

        // Subscribe to list data.
        // zh-CN: 订阅列表数据.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collectLatest { list ->
                latestFullItems = list
                renderList()
                if (list.isNotEmpty() && binding.pluginCenterSwipeRefresh.isRefreshing) {
                    stopSwipeRefreshIndicator()
                }
            }
        }

        // Subscribe to index loading completion status, used to update pull-to-refresh animation and empty state text.
        // zh-CN: 订阅索引加载完成状态, 用于更新下拉刷新动画与空态文案.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.indexLoaded.collectLatest { loaded ->
                latestIndexLoaded = loaded
                if (loaded) {
                    stopSwipeRefreshIndicator()
                }
                updateEmptyHint(
                    filteredItems = adapter.items(),
                    indexLoaded = loaded,
                    fullItems = latestFullItems,
                    query = currentQuery,
                )
            }
        }

        // Subscribe to recoverable loading exceptions for user-initiated pull-to-refresh.
        // zh-CN: 订阅用户手动下拉刷新时的可恢复加载异常.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.toastMessage.collectLatest { msg ->
                ViewUtils.showToast(contextRef, msg, true)
            }
        }
    }

    /**
     * Update query for filtering current list.
     * zh-CN: 更新用于过滤当前列表的查询串.
     */
    fun setQuery(query: String?) {
        actionPositionPolicy.clear()
        currentQuery = query?.takeIf { it.isNotBlank() }
        renderList()
    }

    fun refreshPluginIconsForNightModeChange(force: Boolean = false) {
        scheduleNightModeIconRefresh(force)
    }

    /**
     * Update sort strategy for current list rendering.
     * zh-CN: 更新当前列表渲染的排序策略.
     */
    fun setSort(sort: Sort) {
        actionPositionPolicy.clear()
        currentSort = sort
        renderList()
    }

    /**
     * Update filter strategy for current list rendering.
     * zh-CN: 更新当前列表渲染的筛选策略.
     */
    fun setFilterState(state: PluginFilterStore.FilterState) {
        actionPositionPolicy.clear()
        currentFilterState = state
        renderList()
    }

    private fun loadFilterState(context: Context): PluginFilterStore.FilterState = when {
        isScopedLibraryMode -> PluginFilterStore.defaultFilterState()
        else -> PluginFilterStore.getFilterState(context)
    }

    private fun loadPersistedSort(context: Context): Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSort(context)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSort(context)
        null -> PluginSortStore.getSort(context)
    }

    private fun loadPersistedSortAscending(context: Context, sort: Sort): Boolean = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getAscending(context, sort)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getAscending(context, sort)
        null -> PluginSortStore.getAscending(context, sort)
    }

    private fun loadPersistedSecondarySort(context: Context, primary: Sort): Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSecondarySort(context, primary)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSecondarySort(context, primary)
        null -> PluginSortStore.getSecondarySort(context, primary)
    }

    private fun registerSortPrefListener(context: Context) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.registerOnSharedPreferenceChangeListener(context, sortPrefListener)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.registerOnSharedPreferenceChangeListener(context, sortPrefListener)
            null -> PluginSortStore.registerOnSharedPreferenceChangeListener(context, sortPrefListener)
        }
    }

    private fun unregisterSortPrefListener(context: Context) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.unregisterOnSharedPreferenceChangeListener(context, sortPrefListener)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.unregisterOnSharedPreferenceChangeListener(context, sortPrefListener)
            null -> PluginSortStore.unregisterOnSharedPreferenceChangeListener(context, sortPrefListener)
        }
    }

    private fun retainActionPosition(item: PluginCenterItem) {
        if (_binding == null || !::adapter.isInitialized) return
        actionPositionPolicy.retain(item.packageName, adapter.items())
    }

    private data class SortSelection(
        val primary: Sort,
        val primaryAscending: Boolean,
        val secondary: Sort,
        val secondaryAscending: Boolean,
    )

    private fun buildSortComparator(): Comparator<PluginCenterItem> {
        val secondary = loadPersistedSecondarySort(contextRef, currentSort)
        val selection = SortSelection(
            primary = currentSort,
            primaryAscending = loadPersistedSortAscending(contextRef, currentSort),
            secondary = secondary,
            secondaryAscending = loadPersistedSortAscending(contextRef, secondary),
        )
        if (renderedSortSelection != null && renderedSortSelection != selection) {
            actionPositionPolicy.clear()
        }
        renderedSortSelection = selection
        return sortPolicy.comparator(selection.primary, selection.primaryAscending, selection.secondary, selection.secondaryAscending)
    }

    private fun renderList() {
        val comparator = buildSortComparator()
        val q = currentQuery
        val visibleItems = latestFullItems
            .filterNot { PluginVisibilityStore.isHidden(contextRef, it.packageName) }
            .filter {
                actionPositionPolicy.isRetained(it.packageName) ||
                        PluginVariantDisplayPolicy.shouldDisplay(it.isFeatured, it.isInstalled, variantDisplayMode)
            }

        val filteredByQuery = if (q.isNullOrBlank()) {
            visibleItems
        } else {
            // @formatter:off
            visibleItems.filter { item ->
                actionPositionPolicy.isRetained(item.packageName) ||
                item.title.contains(q, ignoreCase = true) ||
                item.packageName.contains(q, ignoreCase = true) ||
                item.author?.contains(q, ignoreCase = true) == true ||
                item.description?.contains(q, ignoreCase = true) == true
            }
            // @formatter:on
        }

        val filtered = filteredByQuery.filter { actionPositionPolicy.isRetained(it.packageName) || matchesCurrentFilter(it) }

        val sorted = actionPositionPolicy.apply(filtered.sortedWith(comparator))

        if (sorted.isEmpty() || isInitialViewportReady) {
            pendingInitialRenderItems = null
            listSubmitter.submit(sorted)
        } else {
            pendingInitialRenderItems = sorted
        }
        updateEmptyHint(
            filteredItems = sorted,
            indexLoaded = vm.indexLoaded.value,
            fullItems = visibleItems,
            query = currentQuery,
        )
    }

    private fun scheduleSwipeRefreshIndicatorTimeout() {
        binding.pluginCenterSwipeRefresh.isRefreshing = true
        swipeRefreshIndicatorJob?.cancel()
        swipeRefreshIndicatorJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SWIPE_REFRESH_MAX_VISIBLE_MS)
            if (!isAdded || _binding == null) return@launch
            binding.pluginCenterSwipeRefresh.isRefreshing = false
            swipeRefreshIndicatorJob = null
        }
    }

    private fun stopSwipeRefreshIndicator() {
        swipeRefreshIndicatorJob?.cancel()
        swipeRefreshIndicatorJob = null
        if (_binding != null) {
            binding.pluginCenterSwipeRefresh.isRefreshing = false
        }
    }

    private fun matchesCurrentFilter(item: PluginCenterItem): Boolean {
        val filter = currentFilterState

        if (filter.installState == PluginFilterStore.InstallState.INSTALLED && !item.isInstalled) {
            return false
        }
        if (filter.installState == PluginFilterStore.InstallState.NOT_INSTALLED && item.isInstalled) {
            return false
        }

        if (filter.enabledState == PluginFilterStore.EnabledState.ENABLED && !item.isEnabled) {
            return false
        }
        if (filter.enabledState == PluginFilterStore.EnabledState.DISABLED && item.isEnabled) {
            return false
        }

        if (filter.updatableOnly && item.updatableVersionCode == null) {
            return false
        }

        if (filter.mechanismState == PluginFilterStore.MechanismState.AIDL && item.mechanism != PluginMechanism.AIDL) {
            return false
        }
        if (filter.mechanismState == PluginFilterStore.MechanismState.SDK && item.mechanism != PluginMechanism.SDK) {
            return false
        }

        if (filter.sourceState == PluginFilterStore.SourceState.OFFICIAL && item.source != PluginIndexSource.OFFICIAL) {
            return false
        }
        if (filter.sourceState == PluginFilterStore.SourceState.THIRD_PARTY && item.source != PluginIndexSource.THIRD_PARTY) {
            return false
        }

        return true
    }

    private fun updateEmptyHint(
        filteredItems: List<PluginCenterItem>,
        indexLoaded: Boolean,
        fullItems: List<PluginCenterItem>,
        query: String?,
    ) {
        val hintView = binding.pluginCenterEmptyHint

        val hasQuery = !query.isNullOrBlank()

        when {
            filteredItems.isNotEmpty() -> {
                // Has data: hide hint immediately and cancel any waiting tasks.
                // zh-CN: 有数据: 立即隐藏提示, 并取消任何等待任务.
                emptyHintJob?.cancel()
                emptyHintJob = null
                hintView.visibility = View.GONE
                isFirstEnter = false
            }
            hasQuery && fullItems.isNotEmpty() -> {
                // Search filtering produced empty results, do not show misleading "no plugins" hint.
                // zh-CN: 搜索过滤导致结果为空时, 不显示可能误导的 "没有插件" 提示.
                emptyHintJob?.cancel()
                emptyHintJob = null
                hintView.visibility = View.GONE
                isFirstEnter = false
            }
            indexLoaded -> {
                // Local and index stages have ended, list is still empty, immediately show "no plugins" hint.
                // zh-CN: 本地与索引阶段已结束, 列表仍为空, 立即显示 "没有插件" 提示.
                emptyHintJob?.cancel()
                emptyHintJob = null
                hintView.visibility = View.VISIBLE
                when (sourceScope) {
                    PluginIndexSource.OFFICIAL,
                    PluginIndexSource.THIRD_PARTY,
                        -> hintView.setText(R.string.text_no_available_plugins_found_hint)
                    else
                        -> hintView.setText(R.string.text_no_plugins_installed_hint)
                }
                isFirstEnter = false
            }
            else -> {
                // Local is empty, index still being fetched.
                // zh-CN: 本地为空, 索引仍在获取.
                hintView.visibility = View.GONE

                if (isFirstEnter) {
                    // First entry: wait at most 0.5 seconds, if still no data then show "retrieving..." hint.
                    // zh-CN: 首次进入: 最多等待 0.5 秒, 若仍无数据再显示 "正在获取..." 提示.
                    if (emptyHintJob == null || emptyHintJob?.isCancelled == true) {
                        emptyHintJob = viewLifecycleOwner.lifecycleScope.launch {
                            delay(500L)
                            if (isAdded && _binding != null) {
                                val latestItems = adapter.items()
                                val latestIndexLoaded = vm.indexLoaded.value
                                if (latestItems.isEmpty() && !latestIndexLoaded) {
                                    hintView.visibility = View.VISIBLE
                                    hintView.setText(R.string.text_retrieving_plugin_list_data)
                                }
                            }
                        }
                    }
                } else {
                    // Not first time (including pull-to-refresh/auto refresh when returning to page): show loading hint immediately without delay.
                    // zh-CN: 非首次 (包括下拉刷新/返回页面自动刷新): 立即显示加载提示, 不做延迟.
                    emptyHintJob?.cancel()
                    emptyHintJob = null
                    hintView.visibility = View.VISIBLE
                    hintView.setText(R.string.text_retrieving_plugin_list_data)
                }
            }
        }
    }

    private fun ensureAuthorized(item: PluginCenterItem, onAuthorized: (PluginCenterItem) -> Unit) {
        if (item.authorizedState == PluginAuthorizedState.OFFICIAL
            || item.authorizedState == PluginAuthorizedState.TRUSTED
            || item.authorizedState == PluginAuthorizedState.USER_GRANTED
        ) {
            onAuthorized(item)
            return
        }

        val authError = PluginError(PluginErrorCode.NOT_AUTHORIZED)
        vm.setEnabled(contextRef, item.packageName, false, authError)
        item.isEnabled = false
        item.enabledState = PluginEnabledState.DISABLED
        item.lastError = authError
        adapter.notifyDataSetChanged()

        val fingerprint = item.signingFingerprintSha256
        if (fingerprint.isNullOrBlank()) {
            ViewUtils.showToast(contextRef, getString(R.string.text_unavailable), true)
            vm.setEnabled(contextRef, item.packageName, false, authError)
            item.isEnabled = false
            item.enabledState = PluginEnabledState.DISABLED
            item.lastError = authError
            adapter.notifyDataSetChanged()
            return
        }

        MaterialDialog.Builder(contextRef)
            .title(R.string.text_authorize_plugin)
            .content(R.string.text_authorize_plugin_content)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_authorize)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ ->
                PluginAuthorizationStore.grant(contextRef, item.packageName, fingerprint)
                item.authorizedState = PluginAuthorizedState.USER_GRANTED
                item.lastError = null
                d.dismiss()
                adapter.notifyDataSetChanged()
                onAuthorized(item)
            }
            .onNegative { d, _ -> d.dismiss() }
            .cancelable(true)
            .show()
    }

    private fun enableLegacyPlugin(item: PluginCenterItem) {
        val hostVersionError = buildHostVersionErrorIfNeeded(item)
        if (hostVersionError != null) {
            rejectEnableWithError(item, hostVersionError)
            return
        }
        vm.setEnabled(contextRef, item.packageName, true)
        item.isEnabled = true
        item.enabledState = PluginEnabledState.READY
        item.lastError = null
        adapter.notifyDataSetChanged()
    }

    private fun enableAidlPlugin(item: PluginCenterItem) {
        if (!PluginLocalNetworkPermission.isGranted(contextRef, item.packageName)) return
        val hostVersionError = buildHostVersionErrorIfNeeded(item)
        if (hostVersionError != null) {
            rejectEnableWithError(item, hostVersionError)
            return
        }

        // Keep the logical state disabled until the live probe succeeds. This prevents other
        // plugin consumers from selecting a known-bad package during the enable attempt.
        // zh-CN: 实时探测成功前保持逻辑禁用, 避免其他插件调用方在启用过程中选中异常包.
        vm.setEnabled(contextRef, item.packageName, false)
        item.isEnabled = false
        item.enabledState = PluginEnabledState.DISABLED
        item.lastError = null
        adapter.notifyDataSetChanged()

        viewLifecycleOwner.lifecycleScope.launch {
            val error = try {
                probeAidlPlugin(item)
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                error
            }
            if (error != null) {
                val mapped = PluginErrorMapper.fromThrowable(error)
                val shouldRecommend = item.canActivate && PluginErrorMapper.shouldRecommendActivation(mapped)
                if (item.activatedState == PluginActivatedState.UNKNOWN && shouldRecommend) {
                    item.activatedState = PluginActivatedState.RECOMMENDED
                }
                val finalError = if (shouldRecommend) {
                    mapped.copy(
                        code = PluginErrorCode.ROM_FIRST_RUN_RESTRICTED_SUSPECTED,
                        recoverHint = getString(R.string.hint_try_clicking_the_activate_button_to_activate_the_plugin),
                    )
                } else mapped
                showEnableErrorDialog(item, finalError, error)
                vm.setEnabled(contextRef, item.packageName, false, finalError)
                item.isEnabled = false
                item.enabledState = PluginEnabledState.ERROR(finalError)
                item.lastError = finalError
                adapter.notifyDataSetChanged()
                return@launch
            }

            if (!PluginLocalNetworkPermission.isGranted(contextRef, item.packageName)) return@launch
            vm.setEnabled(contextRef, item.packageName, true)
            item.isEnabled = true
            item.enabledState = PluginEnabledState.READY
            item.lastError = null
            adapter.notifyDataSetChanged()
        }
    }

    private fun buildHostVersionErrorIfNeeded(item: PluginCenterItem): PluginError? {
        val requires = item.requiresHostVersion ?: return null
        val hostVersion = item.hostVersionCode ?: BuildConfig.VERSION_CODE.toLong()
        if (hostVersion >= requires) return null
        return PluginError(
            code = PluginErrorCode.HOST_VERSION_TOO_LOW,
            message = "requiresHostVersion=$requires, hostVersionCode=$hostVersion",
            recoverHint = getString(
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requires,
                hostVersion,
            ),
        )
    }

    private fun rejectEnableWithError(item: PluginCenterItem, error: PluginError) {
        val throwable = IllegalStateException(error.message ?: error.recoverHint ?: getString(R.string.text_unknown))
        showEnableErrorDialog(item, error, throwable)
        vm.setEnabled(contextRef, item.packageName, false, error)
        item.isEnabled = false
        item.enabledState = PluginEnabledState.ERROR(error)
        item.lastError = error
        adapter.notifyDataSetChanged()
    }

    private fun showEnableErrorDialog(item: PluginCenterItem, mapped: PluginError, raw: Throwable) {
        if (!isAdded) return

        val pm = contextRef.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(item.packageName)
        val wakeIntent = if (item.canActivate) PluginWakeManager.buildWakeIntent(contextRef, item.packageName) else null
        val shouldShowActivate = shouldShowActivateAction(item, mapped)

        val errorBody = raw.message ?: raw.toString()
        val messageParts = mutableListOf(
            contextRef.getString(R.string.text_exception_info) + contextRef.getString(R.string.symbol_colon_with_blank),
            errorBody,
        )
        val hintText = mapped.recoverHint?.takeIf { it.isNotBlank() }
            ?: when {
                mapped.code == PluginErrorCode.PROTOCOL_MISMATCH -> {
                    getString(R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch)
                }
                mapped.code == PluginErrorCode.HOST_VERSION_TOO_LOW -> {
                    val required = item.requiresHostVersion ?: 0L
                    val host = item.hostVersionCode ?: BuildConfig.VERSION_CODE.toLong()
                    getString(
                        R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                        required,
                        host,
                    )
                }
                mapped.code == PluginErrorCode.PLUGIN_PROCESS_CRASHED -> {
                    getString(R.string.hint_plugin_process_failed_to_start)
                }
                shouldShowActivate -> {
                    getString(R.string.hint_try_clicking_the_activate_button_to_activate_the_plugin)
                }
                else -> null
            }
        if (!hintText.isNullOrBlank()) {
            messageParts += contextRef.getString(R.string.text_hint) + contextRef.getString(R.string.symbol_colon_with_blank)
            messageParts += hintText
        }
        val message = messageParts.joinToString("\n\n")

        MaterialDialog.Builder(contextRef)
            .title(R.string.error_failed_to_enable_the_plugin)
            .content(message)
            .neutralText(R.string.dialog_button_copy)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { d, _ ->
                ClipboardUtils.setClip(contextRef, errorBody)
                ViewUtils.showSnack(d.view, R.string.text_already_copied_to_clip, false)
            }
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .apply {
                if (shouldShowActivate) {
                    positiveText(R.string.dialog_button_activate)

                    val openIntent = wakeIntent ?: launchIntent ?: run {
                        positiveColorRes(R.color.dialog_button_unavailable)
                        onPositive { d, _ ->
                            ViewUtils.showSnack(d.view, R.string.text_unavailable, false)
                        }
                        return@apply
                    }

                    positiveColorRes(R.color.dialog_button_attraction)
                    onPositive { d, _ ->
                        val started = openIntent.startSafely(contextRef, true)
                        d.dismiss()
                        if (started) {
                            item.activatedState = PluginActivatedState.UNKNOWN
                            adapter.notifyDataSetChanged()
                            tryEnableAfterWake(item)
                        }
                    }
                }
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
    }

    private fun shouldShowActivateAction(item: PluginCenterItem, error: PluginError): Boolean {
        if (!item.canActivate) return false
        if (error.code == PluginErrorCode.PROTOCOL_MISMATCH) return false
        if (error.code == PluginErrorCode.HOST_VERSION_TOO_LOW) return false
        return error.code == PluginErrorCode.ROM_FIRST_RUN_RESTRICTED_SUSPECTED ||
                PluginErrorMapper.shouldRecommendActivation(error)
    }

    private fun tryEnableAfterWake(item: PluginCenterItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val delays = longArrayOf(300L, 700L, 1_500L, 3_000L, 5_000L)
            var lastError: Throwable? = null
            for (delayMs in delays) {
                delay(delayMs)
                if (!PluginLocalNetworkPermission.isGranted(contextRef, item.packageName)) return@launch
                val error = runCatching {
                    probeAidlPlugin(item)
                }.exceptionOrNull()
                if (error == null) {
                    if (isAdded && _binding != null) {
                        if (!PluginLocalNetworkPermission.isGranted(contextRef, item.packageName)) return@launch
                        vm.setEnabled(contextRef, item.packageName, true)
                        item.isEnabled = true
                        item.enabledState = PluginEnabledState.READY
                        item.lastError = null
                        item.activatedState = PluginActivatedState.DONE
                        adapter.notifyDataSetChanged()
                        ViewUtils.showToast(contextRef, getString(R.string.text_activated_successfully), true)
                    }
                    return@launch
                }
                if (error is CancellationException) {
                    return@launch
                }
                lastError = error
                if (!PluginErrorMapper.shouldRecommendActivation(PluginErrorMapper.fromThrowable(error))) {
                    break
                }
            }
            val error = lastError ?: return@launch
            if (isAdded && _binding != null) {
                val mapped = PluginErrorMapper.fromThrowable(error)
                vm.setEnabled(contextRef, item.packageName, false, mapped)
                item.isEnabled = false
                item.enabledState = PluginEnabledState.ERROR(mapped)
                item.lastError = mapped
                item.activatedState = PluginActivatedState.RECOMMENDED
                adapter.notifyDataSetChanged()
                showEnableErrorDialog(item, mapped, error)
            }
        }
    }

    private val nightModePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!isAdded || _binding == null) return@OnSharedPreferenceChangeListener
        val ctx = contextRef
        val nightModeKeys = setOf(
            ctx.getString(R.string.key_night_mode),
            ctx.getString(R.string.key_night_mode_enabled),
            ctx.getString(R.string.key_auto_night_mode_enabled),
        )
        if (key in nightModeKeys) {
            scheduleNightModeIconRefresh()
        }
    }

    private suspend fun probeAidlPlugin(item: PluginCenterItem) {
        PluginEnableProbe.run(item.packageName) {
            probeAidlPluginService(item)
        }
    }

    private suspend fun probeAidlPluginService(item: PluginCenterItem) {
        when (val action = item.serviceAction ?: PluginCenterViewModel.serviceActionFromEngine(item.engine)) {
            OcrPluginHost.ACTION_OCR -> {
                OcrPluginHost.probe(contextRef, item.packageName)
            }
            BarcodePluginHost.ACTION_BARCODE -> {
                BarcodePluginHost.probe(contextRef, item.packageName)
            }
            OpenccPluginHost.ACTION_OPENCC -> OpenccPluginHost.probe(contextRef, item.packageName)
            PinyinPluginHost.ACTION_PINYIN -> PinyinPluginHost.probe(contextRef, item.packageName)
            Pinyin4jPluginHost.ACTION_PINYIN4J -> Pinyin4jPluginHost.probe(contextRef, item.packageName)
            MediainfoPluginHost.ACTION_MEDIAINFO -> MediainfoPluginHost.probe(contextRef, item.packageName)
            ExplorerActionPluginHost.ACTION_EXPLORER_ACTION -> ExplorerActionPluginHost.probe(contextRef, item.packageName)
            ScreenColorPickerPluginHost.ACTION_SCREEN_COLOR_PICKER -> ScreenColorPickerPluginHost.probe(contextRef, item.packageName)
            McpServerPluginHost.ACTION_MCP_SERVER -> McpServerPluginHost.probe(contextRef, item.packageName)
            ThreeStoveAgentActions.SERVICE_ACTION -> ThreeStoveAgentPluginHost.probeMetadata(contextRef, item.packageName)
            MailPluginHost.ACTION_MAIL -> MailPluginHost.probe(contextRef, item.packageName)
            EpubPluginHost.ACTION_EPUB -> EpubPluginHost.probe(contextRef, item.packageName)
            TerminalPluginHost.ACTION_TERMINAL -> TerminalPluginHost.probe(contextRef, item.packageName)
            ImageQuantPluginContract.ACTION_RUNTIME -> PluginInfoServiceHost.probe(contextRef, item.packageName)
            OfflineDocsPluginContract.ACTION -> withContext(Dispatchers.IO) {
                OfflineDocsPluginHost.probe(contextRef, item.packageName)
            }
            OpenCvPluginContract.ACTION_RUNTIME -> PluginInfoServiceHost.probe(contextRef, item.packageName)
            YoloContract.SERVICE_ACTION -> {
                YoloPluginHost.probePackage(contextRef, item.packageName)
            }
            else -> {
                when {
                    item.isOcrService || item.engine in OCR_SERVICE_ENGINES -> OcrPluginHost.probe(contextRef, item.packageName)
                    item.isBarcodeService || item.engine in BARCODE_SERVICE_ENGINES -> BarcodePluginHost.probe(contextRef, item.packageName)
                    else -> PluginInfoServiceHost.probe(contextRef, item.packageName)
                }
            }
        }
    }

    private fun scheduleAutoEnableAfterInstall(packageName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(8) { index ->
                delay(if (index == 0) 500L else 700L)
                if (!isAdded || _binding == null) return@launch

                val item = latestFullItems.firstOrNull { it.packageName == packageName && it.isInstalled } ?: return@repeat
                // Official Agent installations are enabled by the shared default policy, including
                // installs completed while this page was stopped. Never override a saved opt-out.
                if (item.engine == ThreeStoveAgentIds.ENGINE && item.authorizedState == PluginAuthorizedState.OFFICIAL) return@launch
                // Default-off engines are auto-enabled only for the host's official signer; the
                // runtime still reads the saved record, so adb sideloads stay disabled.
                // zh-CN: 默认关闭的引擎仅对宿主官方签名自动启用; 运行时仍只读取已保存的记录, adb 侧装保持关闭.
                val canAutoEnable = PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(item.engine, item.authorizedState)
                if (!canAutoEnable || item.isEnabled || PluginNativeAlignmentPolicy.needsWarning(
                        item.nativePageAlignment, org.autojs.autojs.core.plugin.nativealign.NativePageSize.current(),
                    )) return@launch
                if (item.isAwaitingFreshInstallProbe()) {
                    vm.load(contextRef, forceRefreshIndex = false, sourceScope = sourceScope)
                    return@repeat
                }
                val requiresHostVersion = item.requiresHostVersion
                val hostVersion = item.hostVersionCode ?: BuildConfig.VERSION_CODE.toLong()
                if (requiresHostVersion != null && hostVersion < requiresHostVersion) return@launch

                when (item.mechanism) {
                    PluginMechanism.SDK -> enableLegacyPlugin(item)
                    PluginMechanism.AIDL -> enableAidlPlugin(item)
                }
                return@launch
            }
        }
    }

    private fun PluginCenterItem.isAwaitingFreshInstallProbe(): Boolean {
        val error = lastError ?: return false
        return error.code in AUTO_ENABLE_RETRYABLE_ERROR_CODES
    }

    override fun onStart() {
        super.onStart()
        if (::contextRef.isInitialized) {
            registerSortPrefListener(contextRef)
            if (!isScopedLibraryMode) {
                PluginFilterStore.registerOnSharedPreferenceChangeListener(contextRef, filterPrefListener)
            }
            PluginOnlineSettingsStore.registerOnSharedPreferenceChangeListener(onlinePrefListener)
            Pref.registerOnSharedPreferenceChangeListener(nightModePrefListener)

            // Sync with latest persisted state (e.g., changed while fragment was stopped).
            // zh-CN: 同步最新的持久化状态 (如在 Fragment 停止期间被修改).
            // Returning from the installer must keep action positions when preferences are unchanged.
            // zh-CN: 从安装器返回且偏好未变化时, 应继续保留操作位置.
            currentSort = loadPersistedSort(contextRef)
            val filterState = loadFilterState(contextRef)
            if (filterState != currentFilterState) actionPositionPolicy.clear()
            currentFilterState = filterState
            renderList()
        }

        pkgReceiver ?: run registerPackageReceiver@{
            pkgReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val data = intent.data ?: return
                    val packageName = data.schemeSpecificPart ?: return
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    when (intent.action) {
                        Intent.ACTION_PACKAGE_ADDED -> {
                            // Package installation (update) does not record "Recently installed" when replacing, but will refresh.
                            // zh-CN: 替换安装 (更新) 不记录 "最近安装", 但会刷新.
                            if (!replacing) {
                                PluginRecentStore.setLastInstalled(packageName)
                                vm.load(context, forceRefreshIndex = false, sourceScope = sourceScope)
                                scheduleAutoEnableAfterInstall(packageName)
                                return
                            }

                            // Debounce refresh when replacing, to avoid transient "missing pluginInfo" state.
                            // zh-CN: 替换安装时对刷新做去抖, 避免短暂的 "pluginInfo 缺失" 中间态.
                            pkgChangeRefreshJob?.cancel()
                            pkgChangeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
                                delay(1500L)
                                if (isAdded && _binding != null) {
                                    vm.load(context, forceRefreshIndex = false, sourceScope = sourceScope)
                                }
                            }
                        }
                        Intent.ACTION_PACKAGE_REMOVED -> {
                            // Package uninstallation (pre-update phase) does not record "Recently uninstalled" when replacing.
                            // zh-CN: 替换卸载 (更新前阶段) 不记录 "最近卸载".
                            if (!replacing) {
                                PluginRecentStore.setLastUninstalled(packageName)
                                vm.load(context, forceRefreshIndex = false, sourceScope = sourceScope)
                                return
                            }

                            // Debounce refresh when replacing, to avoid transient "missing pluginInfo" state.
                            // zh-CN: 替换卸载时对刷新做去抖, 避免短暂的 "pluginInfo 缺失" 中间态.
                            pkgChangeRefreshJob?.cancel()
                            pkgChangeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
                                delay(1500L)
                                if (isAdded && _binding != null) {
                                    vm.load(context, forceRefreshIndex = false, sourceScope = sourceScope)
                                }
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            requireContext().registerReceiver(pkgReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        resumeRefreshPolicy.onStopped()
        if (::contextRef.isInitialized) {
            unregisterSortPrefListener(contextRef)
            if (!isScopedLibraryMode) {
                PluginFilterStore.unregisterOnSharedPreferenceChangeListener(contextRef, filterPrefListener)
            }
            PluginOnlineSettingsStore.unregisterOnSharedPreferenceChangeListener(onlinePrefListener)
            Pref.get().unregisterOnSharedPreferenceChangeListener(nightModePrefListener)
        }
        pkgChangeRefreshJob?.cancel()
        pkgChangeRefreshJob = null
        nightModeRefreshJob?.cancel()
        nightModeRefreshJob = null
        stopSwipeRefreshIndicator()
        pkgReceiver?.let { runCatching { requireContext().unregisterReceiver(it) } }
        pkgReceiver = null
    }

    override fun onResume() {
        super.onResume()
        var shouldReloadForNightMode = false
        if (::contextRef.isInitialized) {
            val currentNightModeEnabled = isNightModeEnabled()
            val hasNightModeChanged = lastNightModeEnabled != null && lastNightModeEnabled != currentNightModeEnabled
            if (hasNightModeChanged) {
                shouldReloadForNightMode = true
            }
            lastNightModeEnabled = currentNightModeEnabled
        }
        // A ViewPager tab switch does not stop this Fragment, and package broadcasts
        // remain registered while it is STARTED. Only catch up after a real STOPPED
        // interval (such as visiting another Activity), or when icon mode changed.
        // zh-CN: ViewPager 标签切换不会停止此 Fragment, 且处于 STARTED 时
        // 包广播仍保持注册. 仅在真正经历 STOPPED (如访问其他 Activity) 后补刷,
        // 或在图标模式变化时刷新.
        if (resumeRefreshPolicy.consumeRefreshOnResume(shouldReloadForNightMode) && ::contextRef.isInitialized) {
            vm.load(contextRef, forceRefreshIndex = false, sourceScope = sourceScope, forceRender = shouldReloadForNightMode)
        }
    }

    private fun scheduleNightModeIconRefresh(force: Boolean = false) {
        nightModeRefreshJob?.cancel()
        nightModeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(NIGHT_MODE_ICON_REFRESH_DELAY_MS)
            if (!isAdded || _binding == null || !::contextRef.isInitialized) return@launch
            val currentNightModeEnabled = isNightModeEnabled()
            if (!force && lastNightModeEnabled == currentNightModeEnabled) return@launch
            lastNightModeEnabled = currentNightModeEnabled
            vm.load(contextRef, forceRefreshIndex = false, sourceScope = sourceScope, forceRender = true)
        }
    }

    private fun isNightModeEnabled(): Boolean = ViewUtils.isNightModeEnabled

    override fun onDestroyView() {
        super.onDestroyView()
        actionPositionPolicy.clear()
        renderedSortSelection = null
        if (::listSubmitter.isInitialized) {
            listSubmitter.cancel()
        }
        if (::itemViewPreloader.isInitialized) {
            itemViewPreloader.cancel()
        }
        if (::adapter.isInitialized) {
            adapter.clearPreloadedItemViews()
        }
        pendingInitialRenderItems = null
        isInitialViewportReady = false
        emptyHintJob?.cancel()
        emptyHintJob = null
        pkgChangeRefreshJob?.cancel()
        pkgChangeRefreshJob = null
        nightModeRefreshJob?.cancel()
        nightModeRefreshJob = null
        swipeRefreshIndicatorJob?.cancel()
        swipeRefreshIndicatorJob = null
        latestIndexLoaded = false
        _binding = null
    }

    // Sort strategy for rendering list.
    // zh-CN: 用于渲染列表的排序策略.
    enum class Sort(
        val titleRes: Int,
        val defaultAscending: Boolean,
    ) {
        TITLE_ASC(R.string.text_sort_strategy_name, true),
        LAST_UPDATE_DESC(R.string.text_sort_strategy_last_update_time, false),
        PACKAGE_SIZE_DESC(R.string.text_sort_strategy_package_size, false),
        MECHANISM(R.string.text_sort_strategy_mechanism, true),
        VERSION_CODE_DESC(R.string.text_sort_strategy_version_code, false),
        FIRST_INSTALL_TIME_DESC(R.string.text_sort_strategy_first_install_time, false),
        PACKAGE_NAME_ASC(R.string.text_sort_strategy_package_name, true),
        AUTHOR_ASC(R.string.text_sort_strategy_author, true),
        SOURCE(R.string.text_sort_strategy_source, true),
        VERSION_RELEASE_TIME_DESC(R.string.text_sort_strategy_version_release_time, false),
        // Append new values to preserve saved sort ordinals.
        // zh-CN: 新值追加在末尾, 保留已存储的排序序号.
        STATUS(R.string.text_sort_strategy_status, true),
        ;

        fun defaultSecondarySort(): Sort = when (this) {
            LAST_UPDATE_DESC -> TITLE_ASC
            else -> LAST_UPDATE_DESC
        }

        companion object {
            fun defaultPrimarySort(): Sort = STATUS

            fun primarySortDisplayOrder(): List<Sort> = listOf(
                STATUS,
                TITLE_ASC,
                LAST_UPDATE_DESC,
                PACKAGE_SIZE_DESC,
                VERSION_RELEASE_TIME_DESC,
                VERSION_CODE_DESC,
                MECHANISM,
                SOURCE,
                FIRST_INSTALL_TIME_DESC,
                AUTHOR_ASC,
                PACKAGE_NAME_ASC,
            )

            fun secondaryCandidatesFor(primary: Sort): List<Sort> = primarySortDisplayOrder()
                .filter { it != primary }
        }
    }

    // zh-CN: 用于渲染列表的筛选策略.
    companion object {
        private const val ARG_SOURCE_SCOPE = "arg_source_scope"
        private const val ARG_VARIANT_DISPLAY_MODE = "arg_variant_display_mode"
        private const val ARG_INITIAL_QUERY = "arg_initial_query"
        private const val NIGHT_MODE_ICON_REFRESH_DELAY_MS = 120L
        private const val SWIPE_REFRESH_MAX_VISIBLE_MS = 8_000L
        private val OCR_SERVICE_ENGINES = setOf("mlkit-ocr", "paddle-ocr", "rapid-ocr")
        private val BARCODE_SERVICE_ENGINES = setOf("mlkit-barcode")
        private val AUTO_ENABLE_RETRYABLE_ERROR_CODES = setOf(
            PluginErrorCode.SERVICE_NOT_FOUND,
            PluginErrorCode.BIND_FAILED,
            PluginErrorCode.HANDSHAKE_TIMEOUT,
            PluginErrorCode.DEAD_OBJECT,
        )
        fun newInstance(
            sourceScope: PluginIndexSource?,
            variantDisplayMode: PluginVariantDisplayMode = PluginVariantDisplayMode.STANDARD,
            initialQuery: String? = null,
        ): PluginCenterFragment {
            return PluginCenterFragment().apply {
                arguments = Bundle().apply {
                    if (sourceScope != null) {
                        putString(ARG_SOURCE_SCOPE, sourceScope.name)
                    }
                    putString(ARG_VARIANT_DISPLAY_MODE, variantDisplayMode.name)
                    initialQuery?.takeIf { it.isNotBlank() }?.let {
                        putString(ARG_INITIAL_QUERY, it)
                    }
                }
            }
        }
    }

}
