package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtColorLibrariesRecyclerViewItemBinding

class ColorLibraryViewHolder(itemViewBinding: MtColorLibrariesRecyclerViewItemBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

    private val root = itemViewBinding.root
    private val context = root.context
    private val resources = root.resources

    private val colorItemView: ImageView = itemViewBinding.libraryItem
    private val libraryName: TextView = itemViewBinding.name
    private val description: TextView = itemViewBinding.description

    fun bind(library: PresetColorLibrary) {
        libraryName.text = context.getString(library.nameRes)

        description.text = when {
            library.isIntelligent -> {
                @SuppressLint("SetTextI18n")
                "[ ${context.getString(R.string.text_under_development)} ]"
            }
            else -> {
                val size = library.colors.size
                resources.getQuantityString(R.plurals.text_items_total_sum, size, size)
            }
        }

        val adjustedContrastColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
        when {
            library.isDefault -> colorItemView.setImageResource(R.drawable.ic_color_library_default)
            library.isIntelligent -> colorItemView.setImageResource(R.drawable.ic_color_library_intelligent)
            // library.isCreated -> colorItemView.setImageResource(R.drawable.ic_color_library_created)
            // library.isImported -> colorItemView.setImageResource(R.drawable.ic_color_library_imported)
            // library.isCloned -> colorItemView.setImageResource(R.drawable.ic_color_library_cloned)
            else -> colorItemView.setImageResource(R.drawable.ic_color_library_preset)
        }
        colorItemView.imageTintList = ColorStateList.valueOf(adjustedContrastColor)
    }

}