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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.ocr.PaddleOcrPluginHost
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentPluginCenterBinding

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

    // Latest full list from ViewModel (unfiltered).
    // zh-CN: 来自 ViewModel 的最新完整列表 (未过滤).
    private var latestFullItems: List<PluginCenterItem> = emptyList()

    // Current query used by UI filtering, null means "no filtering".
    // zh-CN: 当前用于 UI 过滤的查询串, null 表示 "不做过滤".
    private var currentQuery: String? = null

    private lateinit var currentSort: Sort
    private lateinit var currentFilter: Filter

    private val sortPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!isAdded || _binding == null) return@OnSharedPreferenceChangeListener
        val ctx = contextRef
        viewLifecycleOwner.lifecycleScope.launch {
            setSort(PluginSortStore.getSort(ctx))
        }
    }

    private val filterPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!isAdded || _binding == null) return@OnSharedPreferenceChangeListener
        val ctx = contextRef
        viewLifecycleOwner.lifecycleScope.launch {
            setFilter(PluginFilterStore.getFilter(ctx))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPluginCenterBinding.bind(view)

        val context = requireContext().also { contextRef = it }

        currentSort = PluginSortStore.getSort(context)
        currentFilter = PluginFilterStore.getFilter(context)

        adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
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
                    when (authorizedItem.mechanism) {
                        PluginMechanism.SDK -> enableLegacyPlugin(authorizedItem)
                        PluginMechanism.AIDL -> enableAidlPlugin(authorizedItem)
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
        })

        binding.pluginCenterRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PluginCenterFragment.adapter
            addItemDecoration(DividerItemDecoration(context, VERTICAL))
            excludePaddingClippableViewFromBottomNavigationBar()
        }

        // Pull-to-refresh: force refresh index every time (still respects backoff window).
        // zh-CN: 下拉刷新: 每次都强制刷新索引 (仍遵守退避窗口).
        binding.pluginCenterSwipeRefresh.setOnRefreshListener {
            vm.load(contextRef, forceRefreshIndex = true)
        }

        // First entry: local priority + index async.
        // zh-CN: 首次进入: 本地优先 + 索引异步.
        vm.load(context, forceRefreshIndex = false)

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
                binding.pluginCenterSwipeRefresh.isRefreshing = false
                updateEmptyHint(
                    filteredItems = adapter.items(),
                    indexLoaded = loaded,
                    fullItems = latestFullItems,
                    query = currentQuery,
                )
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
    fun setFilter(filter: Filter) {
        currentFilter = filter
        renderList()
    }

    private fun renderList() {
        val q = currentQuery

        val filteredByQuery = if (q.isNullOrBlank()) {
            latestFullItems
        } else {
            // @formatter:off
            latestFullItems.filter { item ->
                item.title.contains(q, ignoreCase = true) ||
                item.packageName.contains(q, ignoreCase = true) ||
                item.author?.contains(q, ignoreCase = true) == true ||
                item.description?.contains(q, ignoreCase = true) == true
            }
            // @formatter:on
        }

        val filtered = filteredByQuery.filter { item ->
            when (currentFilter) {
                Filter.ALL -> true
                Filter.INSTALLED -> item.isInstalled
                Filter.NOT_INSTALLED -> !item.isInstalled
                Filter.ENABLED -> item.isEnabled
                Filter.DISABLED -> !item.isEnabled
                Filter.UPDATABLE -> item.updatableVersionCode != null
                Filter.AIDL -> item.mechanism == PluginMechanism.AIDL
                Filter.SDK -> item.mechanism == PluginMechanism.SDK
            }
        }

        val sorted = when (currentSort) {
            Sort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            Sort.LAST_UPDATE_DESC -> filtered.sortedWith(
                compareByDescending<PluginCenterItem> { it.isInstalled }
                    .thenByDescending { it.lastUpdateTime ?: 0L }
                    .thenBy { it.title.lowercase() }
            )
            Sort.PACKAGE_SIZE_DESC -> filtered.sortedWith(
                compareByDescending<PluginCenterItem> { it.isInstalled }
                    .thenByDescending { it.packageSize }
                    .thenBy { it.title.lowercase() }
            )
        }

        adapter.updateData(sorted)
        updateEmptyHint(
            filteredItems = sorted,
            indexLoaded = vm.indexLoaded.value,
            fullItems = latestFullItems,
            query = currentQuery,
        )
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
                hintView.setText(R.string.text_no_plugins_installed_hint)
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
        if (item.authorizedState != PluginAuthorizedState.REQUIRED) {
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
        vm.setEnabled(contextRef, item.packageName, true)
        item.isEnabled = true
        item.enabledState = PluginEnabledState.READY
        item.lastError = null
        adapter.notifyDataSetChanged()
    }

    private fun enableAidlPlugin(item: PluginCenterItem) {
        vm.setEnabled(contextRef, item.packageName, true)
        item.isEnabled = true
        item.enabledState = PluginEnabledState.READY
        item.lastError = null
        adapter.notifyDataSetChanged()

        viewLifecycleOwner.lifecycleScope.launch {
            val error = runCatching {
                PaddleOcrPluginHost.probe(contextRef, item.packageName)
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

    private fun showEnableErrorDialog(item: PluginCenterItem, mapped: PluginError, raw: Throwable) {
        if (!isAdded) return

        val pm = contextRef.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(item.packageName)
        val wakeIntent = if (item.canActivate) PluginWakeManager.buildWakeIntent(contextRef, item.packageName) else null

        val errorBody = raw.message ?: raw.toString()
        val messageParts = mutableListOf(
            contextRef.getString(R.string.text_exception_info) + contextRef.getString(R.string.symbol_colon_with_blank),
            errorBody,
        )
        val hintText = mapped.recoverHint?.takeIf { it.isNotBlank() }
            ?: if (item.canActivate && PluginErrorMapper.shouldRecommendActivation(mapped)) {
                getString(R.string.hint_try_clicking_the_activate_button_to_activate_the_plugin)
            } else null
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
            .apply positive@{
                positiveText(R.string.dialog_button_activate)

                val openIntent = wakeIntent ?: launchIntent ?: run {
                    positiveColorRes(R.color.dialog_button_unavailable)
                    onPositive { d, _ ->
                        ViewUtils.showSnack(d.view, R.string.text_unavailable, false)
                    }
                    return@positive
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
            .cancelable(false)
            .autoDismiss(false)
            .show()
    }

    private fun tryEnableAfterWake(item: PluginCenterItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val delays = longArrayOf(300L, 800L, 1500L)
            for (delayMs in delays) {
                delay(delayMs)
                val error = runCatching {
                    PaddleOcrPluginHost.probe(contextRef, item.packageName)
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

    override fun onStart() {
        super.onStart()
        if (::contextRef.isInitialized) {
            PluginSortStore.registerOnSharedPreferenceChangeListener(contextRef, sortPrefListener)
            PluginFilterStore.registerOnSharedPreferenceChangeListener(contextRef, filterPrefListener)

            // Sync with latest persisted state (e.g., changed while fragment was stopped).
            // zh-CN: 同步最新的持久化状态 (如在 Fragment 停止期间被修改).
            setSort(PluginSortStore.getSort(contextRef))
            setFilter(PluginFilterStore.getFilter(contextRef))
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
                                vm.load(context, forceRefreshIndex = false)
                                return
                            }

                            // Debounce refresh when replacing, to avoid transient "missing pluginInfo" state.
                            // zh-CN: 替换安装时对刷新做去抖, 避免短暂的 "pluginInfo 缺失" 中间态.
                            pkgChangeRefreshJob?.cancel()
                            pkgChangeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
                                delay(1500L)
                                if (isAdded && _binding != null) {
                                    vm.load(context, forceRefreshIndex = false)
                                }
                            }
                        }
                        Intent.ACTION_PACKAGE_REMOVED -> {
                            // Package uninstallation (pre-update phase) does not record "Recently uninstalled" when replacing.
                            // zh-CN: 替换卸载 (更新前阶段) 不记录 "最近卸载".
                            if (!replacing) {
                                PluginRecentStore.setLastUninstalled(packageName)
                                vm.load(context, forceRefreshIndex = false)
                                return
                            }

                            // Debounce refresh when replacing, to avoid transient "missing pluginInfo" state.
                            // zh-CN: 替换卸载时对刷新做去抖, 避免短暂的 "pluginInfo 缺失" 中间态.
                            pkgChangeRefreshJob?.cancel()
                            pkgChangeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
                                delay(1500L)
                                if (isAdded && _binding != null) {
                                    vm.load(context, forceRefreshIndex = false)
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
            PluginSortStore.unregisterOnSharedPreferenceChangeListener(contextRef, sortPrefListener)
            PluginFilterStore.unregisterOnSharedPreferenceChangeListener(contextRef, filterPrefListener)
        }
        pkgChangeRefreshJob?.cancel()
        pkgChangeRefreshJob = null
        pkgReceiver?.let { runCatching { requireContext().unregisterReceiver(it) } }
        pkgReceiver = null
    }

    override fun onResume() {
        super.onResume()
        // Refresh once when returning to page, to cover state after installation/uninstallation.
        // zh-CN: 回到页面时刷新一次, 覆盖安装/卸载后的状态.
        if (!isFirstEnter && ::contextRef.isInitialized) {
            vm.load(contextRef, forceRefreshIndex = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        emptyHintJob?.cancel()
        emptyHintJob = null
        pkgChangeRefreshJob?.cancel()
        pkgChangeRefreshJob = null
        _binding = null
    }

    // Sort strategy for rendering list.
    // zh-CN: 用于渲染列表的排序策略.
    enum class Sort(val titleRes: Int) {
        TITLE_ASC(R.string.text_sort_by_name),
        LAST_UPDATE_DESC(R.string.text_sort_by_last_update_time),
        PACKAGE_SIZE_DESC(R.string.text_sort_by_package_size),
    }

    // Filter strategy for rendering list.
    // zh-CN: 用于渲染列表的筛选策略.
    enum class Filter(val titleRes: Int) {
        ALL(R.string.text_all),
        INSTALLED(R.string.text_installed),
        NOT_INSTALLED(R.string.text_not_installed),
        ENABLED(R.string.text_enabled),
        DISABLED(R.string.text_disabled),
        UPDATABLE(R.string.text_updatable),
        AIDL(R.string.text_plugin_mechanism_aidl),
        SDK(R.string.text_plugin_mechanism_sdk),
    }

}
