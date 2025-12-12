package org.autojs.autojs.core.plugin.center

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private var isFirstEnter: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPluginCenterBinding.bind(view)

        val context = requireContext().also { contextRef = it }

        adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
            override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) {
                vm.setEnabled(contextRef, item.packageName, enabled)
                item.isEnabled = enabled
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
                val url = item.installableApkUrl
                when {
                    url.isNullOrBlank() -> MaterialDialog.Builder(contextRef)
                        .title(R.string.text_failed_to_update)
                        .content(R.string.error_no_available_url_provided_for_current_plugin)
                        .positiveText(R.string.dialog_button_dismiss)
                        .show()
                    else -> viewLifecycleOwner.lifecycleScope.launch {
                        PluginInstaller.installFromUrlWithPrompt(
                            context = contextRef,
                            url = url,
                            expectedSha256 = item.installableApkSha256,
                        )
                    }
                }
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
                adapter.updateData(list)
                updateEmptyHint(list, vm.indexLoaded.value)
            }
        }

        // Subscribe to index loading completion status, used to update pull-to-refresh animation and empty state text.
        // zh-CN: 订阅索引加载完成状态, 用于更新下拉刷新动画与空态文案.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.indexLoaded.collectLatest { loaded ->
                binding.pluginCenterSwipeRefresh.isRefreshing = false
                updateEmptyHint(adapter.items(), loaded)
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

    private fun updateEmptyHint(items: List<PluginCenterItem>, indexLoaded: Boolean) {
        val hintView = binding.pluginCenterEmptyHint

        when {
            items.isNotEmpty() -> {
                // Has data: hide hint immediately and cancel any waiting tasks.
                // zh-CN: 有数据: 立即隐藏提示, 并取消任何等待任务.
                emptyHintJob?.cancel()
                emptyHintJob = null
                hintView.visibility = View.GONE
                isFirstEnter = false
            }
            indexLoaded -> {
                // Local and index stages have ended, list is still empty, immediately show "no plugins" hint.
                // zh-CN: 本地与索引阶段已结束, 列表仍为空, 立即显示"没有插件"提示.
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

    override fun onStart() {
        super.onStart()
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
                            if (!replacing) PluginRecentStore.setLastInstalled(packageName)
                            vm.load(context, forceRefreshIndex = false)
                        }
                        Intent.ACTION_PACKAGE_REMOVED -> {
                            // Package uninstallation (pre-update phase) does not record "Recently uninstalled" when replacing.
                            // zh-CN: 替换卸载 (更新前阶段) 不记录 "最近卸载".
                            if (!replacing) PluginRecentStore.setLastUninstalled(packageName)
                            vm.load(context, forceRefreshIndex = false)
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
        _binding = null
    }

}
