package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding

@SuppressLint("NotifyDataSetChanged")
class PluginCenterItemAdapter : RecyclerView.Adapter<PluginCenterItemViewHolder>() {

    internal var items = emptyList<PluginCenterItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginCenterItemViewHolder {
        val binding = PluginCenterRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PluginCenterItemViewHolder(binding)
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

}