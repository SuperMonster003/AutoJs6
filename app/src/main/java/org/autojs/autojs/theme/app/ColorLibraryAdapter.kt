package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.theme.library.ColorLibraryRepository.PlainLibrarySummary
import org.autojs.autojs6.databinding.MtColorLibrariesRecyclerViewItemBinding

/**
 * A row of the color libraries page: a preset library, a virtual library or a user library.
 * zh-CN: 颜色库页面的一行: 预设库, 虚拟库或用户库.
 */
sealed interface LibraryRow {
    /** [description] replaces the default item count when set. zh-CN: 设置 [description] 时替代默认的条目数量描述. */
    data class Preset(val library: PresetColorLibrary, val description: String? = null) : LibraryRow

    /**
     * "Favorites" or "Frequent and recommended" (roadmap P5.1, P5.4), by [libraryId] of `ColorLibraryIds`.
     * zh-CN: "收藏" 或 "常用与推荐" (Roadmap P5.1, P5.4), 由 `ColorLibraryIds` 中的 [libraryId] 区分.
     */
    data class Virtual(val libraryId: Int, val count: Int) : LibraryRow

    data class User(val summary: PlainLibrarySummary) : LibraryRow
}

@SuppressLint("NotifyDataSetChanged")
class ColorLibraryAdapter(
    private var rows: List<LibraryRow>,
    private val onItemClick: (LibraryRow) -> Unit,
    private val onItemLongClick: (LibraryRow) -> Unit = {},
) : RecyclerView.Adapter<ColorLibraryViewHolder>() {

    /**
     * While true, rows do not react to clicks and user rows can be dragged among themselves (roadmap P2.5).
     * zh-CN: 为 true 时各行不响应点击, 用户库行可在彼此之间拖动 (Roadmap P2.5).
     */
    var isReordering = false

    val userRows: List<LibraryRow.User>
        get() = rows.filterIsInstance<LibraryRow.User>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorLibraryViewHolder {
        val binding = MtColorLibrariesRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorLibraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorLibraryViewHolder, position: Int) {
        val row = rows[position]
        holder.bind(row)
        holder.itemView.setOnClickListener { if (!isReordering) onItemClick(row) }
        holder.itemView.setOnLongClickListener { (!isReordering).also { if (it) onItemLongClick(row) } }
    }

    override fun getItemCount() = rows.size

    fun updateData(newRows: List<LibraryRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun isUserRow(position: Int) = rows.getOrNull(position) is LibraryRow.User

    /** Moves a user row; preset rows stay where they are. zh-CN: 移动用户库行; 预设库行保持不动. */
    fun move(from: Int, to: Int): Boolean {
        if (!isUserRow(from) || !isUserRow(to)) return false
        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
        notifyItemMoved(from, to)
        return true
    }

}
