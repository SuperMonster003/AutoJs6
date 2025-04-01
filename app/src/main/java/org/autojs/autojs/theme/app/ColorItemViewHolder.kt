package org.autojs.autojs.theme.app

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class ColorItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val context = itemView.context

    private val colorView: ImageView = itemView.findViewById(R.id.color)
    private val nameView: TextView = itemView.findViewById(R.id.name)
    private val descView: TextView = itemView.findViewById(R.id.description)

    fun bind(item: PresetColorItem, selectedLibraryId: Int, selectedItemId: Int, isLibraryIdentifierAppendedToDesc: Boolean) {
        val color = context.getColor(item.colorRes)
        val colorName = context.getString(item.nameRes)
        val colorDesc = String.format("#%06X", 0xFFFFFF and color)

        ThemeColorHelper.setBackgroundColor(colorView, color)
        nameView.text = colorName
        descView.text = when {
            isLibraryIdentifierAppendedToDesc -> {
                presetColorLibraries.find { it.id == item.libraryId }?.let {
                    "${context.getString(it.identifierRes)} | $colorDesc"
                } ?: colorDesc
            }
            else -> colorDesc
        }
        setChecked(color, selectedLibraryId == item.libraryId && selectedItemId == item.itemId)
    }

    fun setChecked(@ColorInt color: Int, checked: Boolean) {
        if (checked) {
            colorView.setImageResource(R.drawable.mt_ic_check_white_36dp)
            val tintRes = if (ViewUtils.isLuminanceLight(color)) R.color.day else R.color.night
            colorView.imageTintList = ColorStateList.valueOf(context.getColor(tintRes))
        } else {
            colorView.setImageDrawable(null)
        }
    }

}