package org.autojs.autojs.theme.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.KEY_LEGACY_SELECTED_COLOR_INDEX
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.KEY_SELECTED_COLOR_LIBRARY_ID
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.KEY_SELECTED_COLOR_LIBRARY_ITEM_ID
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.SELECT_NONE
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.customColorPosition
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.defaultColorPosition
import org.autojs.autojs6.R

@SuppressLint("NotifyDataSetChanged")
class ColorItemAdapter(
    var items: List<PresetColorItem>,
    var isLibraryIdentifierAppendedToDesc: Boolean = false,
    private val onItemClick: ((PresetColorItem, View) -> Unit)? = null,
) : RecyclerView.Adapter<ColorItemViewHolder>() {

    var selectedItemId = SELECT_NONE
    var selectedLibraryId = SELECT_NONE

    fun updateData(newItems: List<PresetColorItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun items() = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.mt_color_library_recycler_view_item, parent, false)
        return ColorItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
        val itemView = holder.itemView
        val context = itemView.context
        val item = items[position]

        when (val tag = itemView.tag) {
            is ValueAnimator -> {
                tag.cancel()
                itemView.tag = null
                itemView.setBackgroundColor(context.getColor(R.color.window_background))
            }
        }

        holder.bind(item, selectedLibraryId, selectedItemId, isLibraryIdentifierAppendedToDesc)

        itemView.setOnClickListener {
            savePrefsForLibraries(item)
            savePrefsForLegacy(context, item)

            when (selectedItemId) {
                SELECT_NONE -> {
                    selectedLibraryId = item.libraryId
                    selectedItemId = item.itemId
                    notifyItemChanged(holder.bindingAdapterPosition)
                }
                else -> {
                    val positionBeforeSelection = items().indexOfFirst {
                        it.libraryId == selectedLibraryId && it.itemId == selectedItemId
                    }
                    val positionOfCurrentSelection = holder.bindingAdapterPosition
                    if (positionBeforeSelection != positionOfCurrentSelection && positionBeforeSelection >= 0) {
                        notifyItemChanged(positionBeforeSelection)
                    }
                    selectedLibraryId = item.libraryId
                    selectedItemId = item.itemId
                    notifyItemChanged(positionOfCurrentSelection)
                }
            }

            ThemeColorManager.setThemeColor(context.getColor(item.colorRes))
            ThemeChangeNotifier.notifyThemeChanged()

            onItemClick?.let { it(item, itemView) }
        }
    }

    override fun getItemCount() = items.size

    private fun savePrefsForLibraries(colorItem: PresetColorItem) {
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, colorItem.libraryId)
        Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ITEM_ID, colorItem.itemId)
    }

    private fun savePrefsForLegacy(context: Context, colorItem: PresetColorItem) {
        when (colorItem.libraryId) {
            COLOR_LIBRARY_ID_DEFAULT -> {
                if (context.getColor(colorItem.colorRes) == context.getColor(R.color.theme_color_default)) {
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, defaultColorPosition)
                } else {
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                }
            }
            COLOR_LIBRARY_ID_MATERIAL -> {
                val index = colorItem.itemId
                if (index !in ColorItems.MATERIAL_COLORS.indices) {
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
                } else {
                    val fixedIndex = maxOf(
                        defaultColorPosition,
                        customColorPosition,
                    ) + 1 + index
                    Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, fixedIndex)
                }
            }
            else -> {
                Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
            }
        }
    }

}