package org.autojs.autojs.core.plugin.center

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding

class PluginCenterItemAdapter(
    private val listener: Listener,
) : ListAdapter<PluginCenterItem, PluginCenterItemViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginCenterItemViewHolder {
        val binding = PluginCenterRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PluginCenterItemViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: PluginCenterItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun items(): List<PluginCenterItem> = currentList

    /**
     * Dispatches incremental updates so background refreshes only touch rows whose
     * content actually changed, keeping the visible list calm.
     *
     * zh-CN: 以增量方式派发更新, 使后台刷新只影响内容真正变化的行, 保持列表视觉平稳.
     */
    fun updateData(newItems: List<PluginCenterItem>) = submitList(newItems)

    interface Listener {
        fun onInstall(item: PluginCenterItem)
        fun onToggleEnable(item: PluginCenterItem, enabled: Boolean)
        fun onUninstall(item: PluginCenterItem)
        fun onDetails(item: PluginCenterItem)
        fun onUpdate(item: PluginCenterItem)
        fun onSettings(item: PluginCenterItem)
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PluginCenterItem>() {
            override fun areItemsTheSame(oldItem: PluginCenterItem, newItem: PluginCenterItem): Boolean =
                PluginCenterItemSemantics.areItemsTheSame(oldItem, newItem)

            override fun areContentsTheSame(oldItem: PluginCenterItem, newItem: PluginCenterItem): Boolean =
                PluginCenterItemSemantics.areContentsTheSame(oldItem, newItem)
        }
    }
}
