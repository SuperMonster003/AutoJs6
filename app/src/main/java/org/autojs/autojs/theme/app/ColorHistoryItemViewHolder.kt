package org.autojs.autojs.theme.app

import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.util.TimeUtils
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
        colorLibraryIdentifierView.text = colorLibraryIdentifier.also {
            subtitleSplitLineView.isVisible = !it.isNullOrBlank()
        }
    }

    companion object {

        data class ColorHistoryItem(
            val lastUsedTime: Long,
            val colorInt: Int,
            val colorName: String? = null,
            val colorLibraryIdentifier: String? = null,
        ) {
            val lastUsedTimeString = TimeUtils.formatTimestamp(lastUsedTime)
        }

    }

}