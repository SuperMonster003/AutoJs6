package org.autojs.autojs.theme.app

import android.content.res.ColorStateList
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
        val color = context.getColor(item.colorRes)
        val colorName = context.getString(item.nameRes)
        val colorDesc = String.format("#%06X", 0xFFFFFF and color)

        ThemeColorHelper.setBackgroundColor(colorView, color)
        nameView.text = colorName
        descView.text = colorDesc
        if (isShowLibraryIdentifier) {
            presetColorLibraries.find { it.id == item.libraryId }?.let {
                colorLibraryIdentifierView.text = context.getString(it.identifierRes)
            }
        }
        subtitleSplitLineView.isVisible = !colorLibraryIdentifierView.text.isNullOrBlank()
        setChecked(selectedLibraryId == item.libraryId && selectedItemId == item.itemId, color)
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