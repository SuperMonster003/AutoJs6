package org.autojs.autojs.core.plugin.center

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding

class PluginCenterItemAdapter(
    private val listener: Listener,
) : RecyclerView.Adapter<PluginCenterItemViewHolder>() {

    internal var items = emptyList<PluginCenterItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginCenterItemViewHolder {
        val binding = PluginCenterRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PluginCenterItemViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: PluginCenterItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun items() = items

    /**
     * Dispatches incremental updates so background refreshes only touch rows whose
     * content actually changed, keeping the visible list calm.
     *
     * zh-CN: 以增量方式派发更新, 使后台刷新只影响内容真正变化的行, 保持列表视觉平稳.
     */
    fun updateData(newItems: List<PluginCenterItem>) {
        val oldItems = items
        val diffResult = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    PluginCenterItemSemantics.areItemsTheSame(oldItems[oldItemPosition], newItems[newItemPosition])

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    PluginCenterItemSemantics.areContentsTheSame(oldItems[oldItemPosition], newItems[newItemPosition])
            },
            true,
        )
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    interface Listener {
        fun onInstall(item: PluginCenterItem)
        fun onToggleEnable(item: PluginCenterItem, enabled: Boolean)
        fun onUninstall(item: PluginCenterItem)
        fun onDetails(item: PluginCenterItem)
        fun onUpdate(item: PluginCenterItem)
        fun onSettings(item: PluginCenterItem)
    }
}
