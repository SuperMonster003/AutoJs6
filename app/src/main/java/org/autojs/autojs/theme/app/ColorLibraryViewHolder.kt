package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
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

    fun bind(row: LibraryRow) {
        when (row) {
            is LibraryRow.Preset -> bindPreset(row)
            is LibraryRow.User -> bindUser(row)
        }
        val adjustedContrastColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
        colorItemView.imageTintList = ColorStateList.valueOf(adjustedContrastColor)
    }

    private fun bindPreset(row: LibraryRow.Preset) {
        val library = row.library
        libraryName.text = context.getString(library.nameRes)

        description.text = when {
            library.isIntelligent -> {
                @SuppressLint("SetTextI18n")
                "[ ${context.getString(R.string.text_under_development)} ]"
            }
            else -> totalSum(library.colors.size)
        }

        when {
            library.isDefault -> colorItemView.setImageResource(R.drawable.ic_color_library_default)
            library.isIntelligent -> colorItemView.setImageResource(R.drawable.ic_color_library_intelligent)
            else -> colorItemView.setImageResource(R.drawable.ic_color_library_preset)
        }
    }

    private fun bindUser(row: LibraryRow.User) {
        val library = row.summary.library
        libraryName.text = library.name
        description.text = totalSum(row.summary.colorCount)
        colorItemView.setImageResource(
            when (library.kind) {
                ColorLibrary.Kind.CLONED -> R.drawable.ic_color_library_cloned
                ColorLibrary.Kind.IMPORTED -> R.drawable.ic_color_library_imported
                else -> R.drawable.ic_color_library_created
            }
        )
    }

    private fun totalSum(size: Int) = resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, size, size)

}
