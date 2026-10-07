package org.autojs.autojs.theme.app

import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.util.TimeUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtColorHistoryRecyclerViewItemBinding

class ColorHistoryItemViewHolder(itemViewBinding: MtColorHistoryRecyclerViewItemBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

    private val colorView: ImageView = itemViewBinding.color

    private val hexView: TextView = itemViewBinding.colorHex
    private val titleSplitLineView: TextView = itemViewBinding.titleSplitLine
    private val nameView: TextView = itemViewBinding.colorName

    private val lastUsedTimeView: TextView = itemViewBinding.lastUsedTime
    private val subtitleSplitLineView: TextView = itemViewBinding.subtitleSplitLine
    private val colorLibraryIdentifierView: TextView = itemViewBinding.colorLibraryIdentifier

    fun bind(historyItem: ColorHistoryItem) {
        val (_, colorInt, colorName, colorLibraryIdentifier) = historyItem

        ThemeColorHelper.setBackgroundColor(colorView, colorInt)

        hexView.text = String.format("#%06X", 0xFFFFFF and colorInt)
        nameView.text = colorName.also {
            titleSplitLineView.isVisible = !it.isNullOrBlank()
        }
        lastUsedTimeView.text = historyItem.lastUsedTimeString
        val merged = historyItem.mergedCount.takeIf { it > 0 }?.let {
            itemView.resources.getQuantityString(R.plurals.text_n_similar_colors_merged, it, it)
        }
        colorLibraryIdentifierView.text = listOfNotNull(colorLibraryIdentifier?.takeIf { it.isNotBlank() }, merged).joinToString(" ").also {
            subtitleSplitLineView.isVisible = it.isNotBlank()
        }
    }

    companion object {

        data class ColorHistoryItem(
            val lastUsedTime: Long,
            val colorInt: Int,
            val colorName: String? = null,
            val colorLibraryIdentifier: String? = null,
            /** The stored selection ids, when the entry comes from `color_history`. zh-CN: 条目来自 `color_history` 时保存的选中 id. */
            val libraryId: Int? = null,
            val itemId: Int? = null,
            /** Uses recorded for this entry (roadmap P5.2). zh-CN: 该条目记录的使用次数 (Roadmap P5.2). */
            val useCount: Int = 1,
            /** Similar entries merged into this one by "merge similar colors" (roadmap P5.3). zh-CN: "合并相似颜色" 并入此条目的相似条目数 (Roadmap P5.3). */
            val mergedCount: Int = 0,
        ) {
            val lastUsedTimeString = TimeUtils.formatTimestamp(lastUsedTime)
        }

    }

}