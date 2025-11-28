package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.os.Bundle
import android.view.View
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
    private lateinit var context: Context

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPluginCenterBinding.bind(view)

        val context = requireContext().also { context = it }

        binding.pluginCenterRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PluginCenterItemAdapter().also { this@PluginCenterFragment.adapter = it }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
