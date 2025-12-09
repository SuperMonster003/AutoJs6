package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding

@SuppressLint("NotifyDataSetChanged")
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

    fun updateData(newItems: List<PluginCenterItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    interface Listener {
        fun onToggleEnable(item: PluginCenterItem, enabled: Boolean)
        fun onUninstall(item: PluginCenterItem)
        fun onDetails(item: PluginCenterItem)
        fun onUpdate(item: PluginCenterItem)
    }

}