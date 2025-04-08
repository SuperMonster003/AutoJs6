package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.app.ColorEntities.PaletteHistory
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

@SuppressLint("NotifyDataSetChanged")
class PaletteHistoryItemAdapter(
    var items: List<PaletteHistory>,
    private val onItemClick: ((selectedColor: Int) -> Unit),
) : RecyclerView.Adapter<PaletteHistoryItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaletteHistoryItemViewHolder {
        val binding = MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PaletteHistoryItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PaletteHistoryItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick(item.colorInfo.colorInt)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newData: List<PaletteHistory>) {
        items = newData
        notifyDataSetChanged()
    }

}