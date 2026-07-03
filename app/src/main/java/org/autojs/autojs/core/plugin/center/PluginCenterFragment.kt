package org.autojs.autojs.core.plugin.center

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentPluginCenterBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PluginCenterFragment : Fragment(R.layout.fragment_plugin_center) {

    private var _binding: FragmentPluginCenterBinding? = null
    private val binding get() = _binding!!

    private val vm: PluginCenterViewModel by viewModels()
    private lateinit var adapter: PluginCenterItemAdapter
    private lateinit var contextRef: Context

    private var pkgReceiver: BroadcastReceiver? = null

    // Job for delaying the display of empty state hint.
    // zh-CN: 用于延迟显示空态提示的 Job.
    private var emptyHintJob: Job? = null

    // Job for debouncing package change refresh during replacing install/update.
    // zh-CN: 用于在替换安装/更新期间对包变更刷新进行去抖的 Job.
    private var pkgChangeRefreshJob: Job? = null

    private var isFirstEnter: Boolean = true
    private var isFirstListRevealed: Boolean = false
    private var latestIndexLoaded: Boolean = false
    private var lastNightModeEnabled: Boolean? = null
    private var firstListRenderStartedAt: Long = 0L
    private var firstListRevealJob: Job? = null
    private var nightModeRefreshJob: Job? = null

    // Latest full list from ViewModel (unfiltered).
    // zh-CN: 来自 ViewModel 的最新完整列表 (未过滤).
    private var latestFullItems: List<PluginCenterItem> = emptyList()

    // Current query used by UI filtering, null means "no filtering".
    // zh-CN: 当前用于 UI 过滤的查询串, null 表示 "不做过滤".
    private var currentQuery: String? = null

    private var sourceScope: PluginIndexSource? = null
    private val isScopedLibraryMode: Boolean
        get() = sourceScope != null

    private lateinit var currentSort: Sort
    private var currentFilterState = PluginFilterStore.defaultFilterState()
    private val versionDateEpochCache = mutableMapOf<String, Long?>()

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPluginCenterBinding.bind(view)

        val context = requireContext().also { contextRef = it }
        lastNightModeEnabled = isNightModeEnabled()

        currentSort = loadPersistedSort(context)
        currentFilterState = PluginFilterStore.getFilterState(context)
        latestIndexLoaded = false
        isFirstListRevealed = false
        firstListRenderStartedAt = SystemClock.elapsedRealtime()

        adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
            override fun onInstall(item: PluginCenterItem) {
                val url = item.installableApkUrl
                if (url.isNullOrBlank()) {
                    ViewUtils.showToast(contextRef, getString(R.string.text_unavailable), true)
                    return
                }
                PluginAbiCompatibilityPrompt.runBeforeDownload(contextRef, item) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        PluginInstaller.installFromUrlWithPrompt(contextRef, url, item.installableApkSha256)
                    }
                }
            }

            override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) {
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
                    PluginAbiCompatibilityPrompt.runBeforeEnable(contextRef, authorizedItem) {
                        when (authorizedItem.mechanism) {
                            PluginMechanism.SDK -> enableLegacyPlugin(authorizedItem)
                            PluginMechanism.AIDL -> enableAidlPlugin(authorizedItem)
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
                        val uri = "package:${item.packageName}".toUri()
                        val intent = Intent(Intent.ACTION_DELETE, uri)
                        startActivity(intent)
                    }
                    .show()
            }

            override fun onDetails(item: PluginCenterItem) {
                PluginInfoDialogManager.showPluginInfoDialog(contextRef, item)
            }

            override fun onUpdate(item: PluginCenterItem) {
                PluginInfoDialogManager.showUpdatablePluginInfoDialog(contextRef, PluginInfoDialogManager.PluginInfoUpdatable(item))
            }

            override fun onSettings(item: PluginCenterItem) {
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
            itemAnimator = null
            setHasFixedSize(true)
            alpha = 0f
            excludePaddingClippableViewFromBottomNavigationBar()
        }

        // Pull-to-refresh: force refresh index every time (still respects backoff window).
        // zh-CN: 下拉刷新: 每次都强制刷新索引 (仍遵守退避窗口).
        binding.pluginCenterSwipeRefresh.setOnRefreshListener {
            vm.load(contextRef, forceRefreshIndex = true, sourceScope = sourceScope)
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
            }
        }

        // Subscribe to index loading completion status, used to update pull-to-refresh animation and empty state text.
        // zh-CN: 订阅索引加载完成状态, 用于更新下拉刷新动画与空态文案.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.indexLoaded.collectLatest { loaded ->
                latestIndexLoaded = loaded
                binding.pluginCenterSwipeRefresh.isRefreshing = false
                updateEmptyHint(
                    filteredItems = adapter.items(),
                    indexLoaded = loaded,
                    fullItems = latestFullItems,
                    query = currentQuery,
                )
                scheduleFirstListRevealIfNeeded(adapter.items().isNotEmpty())
            }
        }

        // Subscribe to locally discovered fatal exceptions.
        // zh-CN: 订阅本地发现致命异常.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.fatalError.collectLatest { msg ->
                MaterialDialog.Builder(contextRef)
                    .title(R.string.text_error)
                    .content(msg)
                    .positiveText(R.string.dialog_button_dismiss)
                    .onPositive { _, _ -> requireActivity().finish() }
                    .cancelable(false)
                    .show()
            }
        }
    }

    /**
     * Update query for filtering current list.
     * zh-CN: 更新用于过滤当前列表的查询串.
     */
    fun setQuery(query: String?) {
        currentQuery = query?.takeIf { it.isNotBlank() }
        renderList()
    }

    /**
     * Update sort strategy for current list rendering.
     * zh-CN: 更新当前列表渲染的排序策略.
     */
    fun setSort(sort: Sort) {
        currentSort = sort
        renderList()
    }

    /**
     * Update filter strategy for current list rendering.
     * zh-CN: 更新当前列表渲染的筛选策略.
     */
    fun setFilterState(state: PluginFilterStore.FilterState) {
        currentFilterState = state
        renderList()
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

    private fun loadPersistedMechanismSecondarySort(context: Context): Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getMechanismSecondarySort(context)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getMechanismSecondarySort(context)
        null -> PluginSortStore.getMechanismSecondarySort(context)
    }

    private fun loadPersistedSourceSecondarySort(context: Context): Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSourceSecondarySort(context)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSourceSecondarySort(context)
        null -> PluginSortStore.getSourceSecondarySort(context)
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

    private fun buildSortComparator(): Comparator<PluginCenterItem> {
        val primaryAscending = loadPersistedSortAscending(contextRef, currentSort)
        var comparator = comparatorForSortField(currentSort, primaryAscending)

        val usedSorts = linkedSetOf(currentSort)
        if (currentSort.supportsSecondarySort()) {
            val secondary = when (currentSort) {
                Sort.MECHANISM -> loadPersistedMechanismSecondarySort(contextRef)
                Sort.SOURCE -> loadPersistedSourceSecondarySort(contextRef)
                else -> null
            }?.takeUnless { it == currentSort }
                ?: currentSort.defaultSecondarySort()
            val secondaryAscending = loadPersistedSortAscending(contextRef, secondary)
            comparator = comparator.thenComparing(comparatorForSortField(secondary, secondaryAscending))
            usedSorts += secondary
        }

        SORT_FALLBACK_CHAIN.forEach { fallback ->
            if (fallback in usedSorts) return@forEach
            comparator = comparator.thenComparing(comparatorForSortField(fallback, fallback.defaultAscending))
            usedSorts += fallback
        }
        return comparator
    }

    private fun comparatorForSortField(
        sort: Sort,
        ascending: Boolean,
    ): Comparator<PluginCenterItem> {
        val base: Comparator<PluginCenterItem> = when (sort) {
            Sort.TITLE_ASC ->
                compareBy { normalizeText(it.title) }
            Sort.LAST_UPDATE_DESC ->
                compareBy { resolveLastUpdateSortValue(it) }
            Sort.PACKAGE_SIZE_DESC ->
                compareBy { resolvePackageSizeSortValue(it) }
            Sort.MECHANISM ->
                compareBy {
                    when (it.mechanism) {
                        PluginMechanism.AIDL -> 0
                        PluginMechanism.SDK -> 1
                    }
                }
            Sort.VERSION_CODE_DESC ->
                compareBy { resolveVersionSortValue(it) }
            Sort.VERSION_RELEASE_TIME_DESC ->
                compareBy { resolveVersionReleaseTimeSortValue(it) }
            Sort.FIRST_INSTALL_TIME_DESC ->
                compareBy { resolveFirstInstallSortValue(it) }
            Sort.PACKAGE_NAME_ASC ->
                compareBy { normalizeText(it.packageName) }
            Sort.AUTHOR_ASC ->
                compareBy { normalizeText(it.author) }
            Sort.SOURCE ->
                compareBy {
                    when (it.source) {
                        PluginIndexSource.OFFICIAL -> 0
                        PluginIndexSource.THIRD_PARTY -> 1
                    }
                }
        }
        return if (ascending) base else base.reversed()
    }

    private fun resolveVersionSortValue(item: PluginCenterItem): Long =
        item.versionCode?.takeIf { it > 0 } ?: Long.MIN_VALUE

    private fun resolveVersionReleaseTimeSortValue(item: PluginCenterItem): Long =
        parseVersionDateToEpochMillis(item.versionDate) ?: 0L

    private fun resolvePackageSizeSortValue(item: PluginCenterItem): Long = when {
        item.packageSize > 0 -> item.packageSize
        (item.installableApkSizeBytes ?: 0L) > 0L -> item.installableApkSizeBytes ?: 0L
        (item.updatableApkSizeBytes ?: 0L) > 0L -> item.updatableApkSizeBytes ?: 0L
        else -> 0L
    }

    private fun resolveFirstInstallSortValue(item: PluginCenterItem): Long =
        item.firstInstallTime?.takeIf { it > 0L }
            ?: item.lastInstallTime?.takeIf { it > 0L }
            ?: 0L

    private fun resolveLastUpdateSortValue(item: PluginCenterItem): Long =
        item.lastUpdateTime?.takeIf { it > 0L }
            ?: parseVersionDateToEpochMillis(item.versionDate)
            ?: item.versionCode?.takeIf { it > 0L }
            ?: 0L

    private fun normalizeText(value: String?): String =
        value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)
            ?: ""

    private fun parseVersionDateToEpochMillis(versionDate: String?): Long? {
        val normalized = versionDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (versionDateEpochCache.containsKey(normalized)) {
            return versionDateEpochCache[normalized]
        }

        val parsed = DATE_PATTERNS.firstNotNullOfOrNull { pattern ->
            val locale = if (pattern.contains("MMM")) Locale.ENGLISH else Locale.US
            runCatching {
                SimpleDateFormat(pattern, locale).apply { isLenient = false }.parse(normalized)?.time
            }.getOrNull()
        }
        versionDateEpochCache[normalized] = parsed
        return parsed
    }

    private fun renderList() {
        val q = currentQuery
        val visibleItems = latestFullItems.filterNot { PluginVisibilityStore.isHidden(contextRef, it.packageName) }

        val filteredByQuery = if (q.isNullOrBlank()) {
            visibleItems
        } else {
            // @formatter:off
            visibleItems.filter { item ->
                item.title.contains(q, ignoreCase = true) ||
                item.packageName.contains(q, ignoreCase = true) ||
                item.author?.contains(q, ignoreCase = true) == true ||
                item.description?.contains(q, ignoreCase = true) == true
            }
            // @formatter:on
        }

        val filtered = filteredByQuery.filter(::matchesCurrentFilter)

        val sorted = filtered.sortedWith(buildSortComparator())

        adapter.updateData(sorted)
        scheduleFirstListRevealIfNeeded(sorted.isNotEmpty())
        updateEmptyHint(
            filteredItems = sorted,
            indexLoaded = vm.indexLoaded.value,
            fullItems = visibleItems,
            query = currentQuery,
        )
    }

    private fun scheduleFirstListRevealIfNeeded(hasItems: Boolean) {
        if (!hasItems || isFirstListRevealed || _binding == null || !isAdded) {
            return
        }
        firstListRevealJob?.cancel()
        firstListRevealJob = viewLifecycleOwner.lifecycleScope.launch {
            val elapsed = SystemClock.elapsedRealtime() - firstListRenderStartedAt
            val revealDelay = when {
                latestIndexLoaded -> 90L
                elapsed >= FIRST_LIST_REVEAL_TIMEOUT_MS -> 0L
                else -> FIRST_LIST_REVEAL_TIMEOUT_MS - elapsed
            }
            if (revealDelay > 0L) {
                delay(revealDelay)
            }
            if (_binding == null || !isAdded || isFirstListRevealed || adapter.items().isEmpty()) {
                return@launch
            }
            isFirstListRevealed = true
            binding.pluginCenterRecyclerView.animate()
                .alpha(1f)
                .setDuration(120L)
                .start()
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

        viewLifecycleOwner.lifecycleScope.launch {
            val error = runCatching {
                probeAidlPlugin(item)
            }.exceptionOrNull()
            if (error != null && error !is CancellationException) {
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
                            item.activatedState = PluginActivatedState.DONE
                            ViewUtils.showToast(contextRef, getString(R.string.text_activated_successfully), true)
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
            val delays = longArrayOf(300L, 800L, 1500L)
            for (delayMs in delays) {
                delay(delayMs)
                val error = runCatching {
                    probeAidlPlugin(item)
                }.exceptionOrNull()
                if (error == null) {
                    if (isAdded && _binding != null) {
                        vm.setEnabled(contextRef, item.packageName, true)
                        item.isEnabled = true
                        item.enabledState = PluginEnabledState.READY
                        item.lastError = null
                        adapter.notifyDataSetChanged()
                    }
                    return@launch
                }
                if (error is CancellationException) {
                    return@launch
                }
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
        if (item.isOcrService || item.engine in OCR_SERVICE_ENGINES) {
            OcrPluginHost.probe(contextRef, item.packageName)
        } else {
            PluginInfoServiceHost.probe(contextRef, item.packageName)
        }
    }

    private fun scheduleAutoEnableAfterInstall(packageName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(8) { index ->
                delay(if (index == 0) 500L else 700L)
                if (!isAdded || _binding == null) return@launch

                val item = latestFullItems.firstOrNull { it.packageName == packageName && it.isInstalled } ?: return@repeat
                val canAutoEnable = when (item.authorizedState) {
                    PluginAuthorizedState.OFFICIAL,
                    PluginAuthorizedState.TRUSTED,
                    PluginAuthorizedState.USER_GRANTED,
                        -> true
                    else -> false
                }
                if (!canAutoEnable || item.isEnabled) return@launch
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
            PluginFilterStore.registerOnSharedPreferenceChangeListener(contextRef, filterPrefListener)
            PluginOnlineSettingsStore.registerOnSharedPreferenceChangeListener(onlinePrefListener)
            Pref.registerOnSharedPreferenceChangeListener(nightModePrefListener)

            // Sync with latest persisted state (e.g., changed while fragment was stopped).
            // zh-CN: 同步最新的持久化状态 (如在 Fragment 停止期间被修改).
            setSort(loadPersistedSort(contextRef))
            setFilterState(PluginFilterStore.getFilterState(contextRef))
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
        if (::contextRef.isInitialized) {
            unregisterSortPrefListener(contextRef)
            PluginFilterStore.unregisterOnSharedPreferenceChangeListener(contextRef, filterPrefListener)
            PluginOnlineSettingsStore.unregisterOnSharedPreferenceChangeListener(onlinePrefListener)
            Pref.get().unregisterOnSharedPreferenceChangeListener(nightModePrefListener)
        }
        pkgChangeRefreshJob?.cancel()
        pkgChangeRefreshJob = null
        nightModeRefreshJob?.cancel()
        nightModeRefreshJob = null
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
        // Refresh once when returning to page, to cover state after installation/uninstallation.
        // zh-CN: 回到页面时刷新一次, 覆盖安装/卸载后的状态.
        if ((shouldReloadForNightMode || !isFirstEnter) && ::contextRef.isInitialized) {
            vm.load(contextRef, forceRefreshIndex = false, sourceScope = sourceScope, forceRender = shouldReloadForNightMode)
        }
    }

    private fun scheduleNightModeIconRefresh() {
        nightModeRefreshJob?.cancel()
        nightModeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(NIGHT_MODE_ICON_REFRESH_DELAY_MS)
            if (!isAdded || _binding == null || !::contextRef.isInitialized) return@launch
            val currentNightModeEnabled = isNightModeEnabled()
            if (lastNightModeEnabled == currentNightModeEnabled) return@launch
            lastNightModeEnabled = currentNightModeEnabled
            vm.load(contextRef, forceRefreshIndex = false, sourceScope = sourceScope, forceRender = true)
        }
    }

    private fun isNightModeEnabled(): Boolean = ViewUtils.isNightModeEnabled

    override fun onDestroyView() {
        super.onDestroyView()
        emptyHintJob?.cancel()
        emptyHintJob = null
        pkgChangeRefreshJob?.cancel()
        pkgChangeRefreshJob = null
        nightModeRefreshJob?.cancel()
        nightModeRefreshJob = null
        firstListRevealJob?.cancel()
        firstListRevealJob = null
        isFirstListRevealed = false
        latestIndexLoaded = false
        firstListRenderStartedAt = 0L
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
        ;

        fun supportsSecondarySort(): Boolean = this == MECHANISM || this == SOURCE

        fun defaultSecondarySort(): Sort = when (this) {
            MECHANISM, SOURCE -> LAST_UPDATE_DESC
            else -> defaultPrimarySort()
        }

        companion object {
            fun defaultPrimarySort(): Sort = LAST_UPDATE_DESC

            fun primarySortDisplayOrder(): List<Sort> = listOf(
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
        private const val FIRST_LIST_REVEAL_TIMEOUT_MS = 1000L
        private const val NIGHT_MODE_ICON_REFRESH_DELAY_MS = 120L
        private val OCR_SERVICE_ENGINES = setOf("paddle-ocr", "rapid-ocr")
        private val AUTO_ENABLE_RETRYABLE_ERROR_CODES = setOf(
            PluginErrorCode.SERVICE_NOT_FOUND,
            PluginErrorCode.BIND_FAILED,
            PluginErrorCode.HANDSHAKE_TIMEOUT,
            PluginErrorCode.DEAD_OBJECT,
        )
        private val DATE_PATTERNS = arrayOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy.MM.dd",
            "dd.MM.yyyy",
            "yyyyMMdd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "MMM dd, yyyy",
            "dd MMM yyyy",
            "MMM dd yyyy",
        )
        private val SORT_FALLBACK_CHAIN = listOf(
            Sort.LAST_UPDATE_DESC,
            Sort.VERSION_RELEASE_TIME_DESC,
            Sort.VERSION_CODE_DESC,
            Sort.TITLE_ASC,
            Sort.PACKAGE_NAME_ASC,
        )

        fun newInstance(sourceScope: PluginIndexSource?): PluginCenterFragment {
            return PluginCenterFragment().apply {
                arguments = Bundle().apply {
                    if (sourceScope != null) {
                        putString(ARG_SOURCE_SCOPE, sourceScope.name)
                    }
                }
            }
        }
    }

}
