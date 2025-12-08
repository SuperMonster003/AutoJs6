package org.autojs.autojs.core.plugin.center

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.load(requireContext())
    }

    private var pkgReceiver: BroadcastReceiver? = null

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
                val uri = Uri.parse("package:${item.packageName}")
                val intent = Intent(Intent.ACTION_DELETE, uri)
                uninstallLauncher.launch(intent)
            }

            override fun onDetails(item: PluginCenterItem) {
                PluginInfoDialogManager.showPluginInfoDialog(contextRef, item)
            }
        })

        binding.pluginCenterRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PluginCenterFragment.adapter
            addItemDecoration(DividerItemDecoration(context, VERTICAL))
            excludePaddingClippableViewFromBottomNavigationBar()
        }

        // Load data.
        // zh-CN: 加载数据.
        vm.load(context)

        // Subscribe data updates.
        // zh-CN: 订阅数据更新.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collectLatest { list ->
                adapter.updateData(list)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        run registerPackageReceiver@{
            pkgReceiver ?: return@registerPackageReceiver
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
                            vm.load(context)
                        }
                        Intent.ACTION_PACKAGE_REMOVED -> {
                            // Package uninstallation (pre-update phase) does not record "Recently uninstalled" when replacing.
                            // zh-CN: 替换卸载 (更新前阶段) 不记录 "最近卸载".
                            if (!replacing) PluginRecentStore.setLastUninstalled(packageName)
                            vm.load(context)
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
        // Refresh once when returning to the page to update install/uninstall status.
        // zh-CN: 回到页面时刷新一次, 覆盖安装/卸载后的状态.
        if (::contextRef.isInitialized) {
            vm.load(contextRef)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
