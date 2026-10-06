package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.library.ColorLibraryRepository.PlainLibrarySummary
import org.autojs.autojs6.databinding.MtColorLibrariesRecyclerViewItemBinding

/**
 * A row of the color libraries page: a preset library or a user library.
 * zh-CN: 颜色库页面的一行: 预设库或用户库.
 */
sealed interface LibraryRow {
    data class Preset(val library: PresetColorLibrary) : LibraryRow
    data class User(val summary: PlainLibrarySummary) : LibraryRow
}

@SuppressLint("NotifyDataSetChanged")
class ColorLibraryAdapter(
    private var rows: List<LibraryRow>,
    private val onItemClick: (LibraryRow) -> Unit,
    private val onItemLongClick: (LibraryRow) -> Unit = {},
) : RecyclerView.Adapter<ColorLibraryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorLibraryViewHolder {
        val binding = MtColorLibrariesRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorLibraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorLibraryViewHolder, position: Int) {
        val row = rows[position]
        holder.bind(row)
        holder.itemView.setOnClickListener { onItemClick(row) }
        holder.itemView.setOnLongClickListener { true.also { onItemLongClick(row) } }
    }

    override fun getItemCount() = rows.size

    fun updateData(newRows: List<LibraryRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

}
