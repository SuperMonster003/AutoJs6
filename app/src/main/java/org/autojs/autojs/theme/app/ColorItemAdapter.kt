package org.autojs.autojs.theme.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.SELECT_NONE
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.saveDatabaseForColorHistories
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.savePrefsForLegacy
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.savePrefsForLibraries
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

@SuppressLint("NotifyDataSetChanged")
class ColorItemAdapter(
    internal var items: List<PresetColorItem>,
    private var isShowLibraryIdentifier: Boolean = false,
    private val onItemClick: ((positionOfCurrentSelection: Int) -> Unit)? = null,
) : RecyclerView.Adapter<ColorItemViewHolder>() {

    var selectedItemId = SELECT_NONE
    var selectedLibraryId = SELECT_NONE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
        val binding = MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorItemViewHolder(binding)
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

        holder.bind(item, selectedLibraryId, selectedItemId, isShowLibraryIdentifier)

        itemView.setOnClickListener {
            onItemConfirmed(context, holder.bindingAdapterPosition)
        }
    }

    private fun updateSelectedPosition(item: PresetColorItem, positionOfCurrentSelection: Int) {
        when (selectedItemId) {
            SELECT_NONE -> {
                selectedLibraryId = item.libraryId
                selectedItemId = item.itemId
                notifyItemChanged(positionOfCurrentSelection)
            }
            else -> {
                val positionBeforeSelection = items.indexOfFirst {
                    it.libraryId == selectedLibraryId && it.itemId == selectedItemId
                }
                if (positionBeforeSelection != positionOfCurrentSelection && positionBeforeSelection >= 0) {
                    notifyItemChanged(positionBeforeSelection)
                }
                selectedLibraryId = item.libraryId
                selectedItemId = item.itemId
                notifyItemChanged(positionOfCurrentSelection)
            }
        }
    }

    private fun savePrefsAndDatabase(context: Context, item: PresetColorItem) {
        savePrefsForLibraries(item)
        savePrefsForLegacy(context, item)
        saveDatabaseForColorHistories(
            applicationContext = context.applicationContext,
            libraryId = item.libraryId,
            itemId = item.itemId,
        )
    }

    private fun applyThemeColor(context: Context, item: PresetColorItem) {
        ThemeColorManager.setThemeColor(context.getColor(item.colorRes))
        ThemeChangeNotifier.notifyThemeChanged()
    }

    fun onItemConfirmed(context: Context, position: Int) {
        val item: PresetColorItem = items[position]
        savePrefsAndDatabase(context, item)
        updateSelectedPosition(item, position)
        applyThemeColor(context, item)
        onItemClick?.invoke(position)
    }

    override fun getItemCount() = items.size

    fun items() = items

    fun updateData(newItems: List<PresetColorItem>) {
        items = newItems
        notifyDataSetChanged()
    }

}