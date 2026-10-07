package org.autojs.autojs.theme.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

class ColorItemViewHolder(itemViewBinding: MtColorLibraryRecyclerViewItemBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

    private val context = itemViewBinding.root.context

    private val colorView: ImageView = itemViewBinding.color
    private val nameView: TextView = itemViewBinding.name
    private val descView: TextView = itemViewBinding.description
    private val subtitleSplitLineView: TextView = itemViewBinding.subtitleSplitLine
    private val colorLibraryIdentifierView: TextView = itemViewBinding.colorLibraryIdentifier

    fun bind(item: PresetColorItem, selectedLibraryId: Int, selectedItemId: Int, isShowLibraryIdentifier: Boolean) {
        val identifier = when {
            isShowLibraryIdentifier -> presetColorLibraries.find { it.id == item.libraryId }?.let { context.getString(it.identifierRes) }
            else -> null
        }
        bind(
            colors = listOfNotNull(context.getColor(item.colorRes), item.mergedColorRes?.let { context.getColor(it) }),
            name = context.getString(item.nameRes),
            identifier = identifier,
            checked = selectedLibraryId == item.libraryId && selectedItemId == item.itemId,
        )
    }

    /**
     * Binds raw values; the description shows the color as `#RRGGBB`.
     * zh-CN: 按原始值绑定; 描述显示为 `#RRGGBB` 形式的色值.
     */
    fun bind(@ColorInt color: Int, name: String, identifier: String?, checked: Boolean) = bind(listOf(color), name, identifier, checked)

    /**
     * Binds one or more colors; several colors (e.g. day / night) share a segmented circle.
     * zh-CN: 绑定一种或多种颜色; 多种颜色 (例如日 / 夜) 共用一个分段圆形.
     */
    fun bind(colors: List<Int>, name: String, identifier: String?, checked: Boolean) {
        when {
            colors.size > 1 -> colorView.background = SegmentedCircleDrawable(colors)
            else -> {
                // A recycled view may still carry a segmented circle. zh-CN: 复用的视图可能仍带有分段圆形.
                if (colorView.background !is GradientDrawable) {
                    colorView.setBackgroundResource(R.drawable.mt_circle)
                    colorView.background = colorView.background.mutate()
                }
                ThemeColorHelper.setBackgroundColor(colorView, colors.first())
            }
        }
        nameView.text = name
        descView.text = colors.joinToString(" / ") { String.format("#%06X", 0xFFFFFF and it) }
        colorLibraryIdentifierView.text = identifier
        subtitleSplitLineView.isVisible = !identifier.isNullOrBlank()
        setChecked(checked, colors.first())
    }

    fun setChecked(checked: Boolean, @ColorInt refColor: Int? = null) {
        if (checked) {
            val niceRefColor = refColor ?: ThemeColorManager.colorPrimary
            val tintRes = when (ViewUtils.isLuminanceLight(niceRefColor)) {
                true -> R.color.day
                else -> R.color.night
            }
            colorView.setImageResource(R.drawable.mt_ic_check_white_36dp)
            colorView.imageTintList = ColorStateList.valueOf(context.getColor(tintRes))
        } else {
            colorView.setImageDrawable(null)
        }
    }

}