package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.app.ColorHistoryItemViewHolder.Companion.ColorHistoryItem
import org.autojs.autojs6.databinding.MtColorHistoryRecyclerViewItemBinding

@SuppressLint("NotifyDataSetChanged")
class ColorHistoryItemAdapter(
    private var items: List<ColorHistoryItem>,
    private val onItemClickListener: (selectedHistoryItem: ColorHistoryItem) -> Unit,
) : RecyclerView.Adapter<ColorHistoryItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorHistoryItemViewHolder {
        val binding = MtColorHistoryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorHistoryItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorHistoryItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClickListener(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ColorHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

}