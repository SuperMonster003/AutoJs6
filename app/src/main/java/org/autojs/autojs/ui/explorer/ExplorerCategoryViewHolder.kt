package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.PopupMenu
import androidx.core.widget.ImageViewCompat
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_DATE
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_NAME
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_SIZE
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_TYPE
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.util.ViewUtils.setForceShowIconCompat
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerCategoryBinding

internal interface ExplorerCategoryViewHost {
    val context: Context
    val chevronIconTint: ColorStateList
    val itemManager: ExplorerItemManager
    val pageState: ExplorerView.ExplorerPageState
    var isDirSortMenuShowing: Boolean

    fun sort(sortType: Int, isDir: Boolean, isSortedAscending: Boolean)
    fun canGoUp(): Boolean
    fun goUp()
    fun notifyDataSetChanged()
}

internal class ExplorerCategoryViewHolder(
    itemView: View,
    private val host: ExplorerCategoryViewHost,
) : BindableViewHolder<Any>(itemView) {

    private val binding = ExplorerCategoryBinding.bind(itemView)

    private var mIsDir = false

    init {
        setOnClickListeners()

        ImageViewCompat.setImageTintList(binding.arrowIcon, host.chevronIconTint)
    }

    private fun setOnClickListeners() {
        binding.sortOrder.setOnClickListener {
            if (mIsDir) {
                host.sort(host.itemManager.dirSortType, mIsDir, !host.itemManager.isDirSortedAscending)
                setDirOrderIconWithCurrentState()
            } else {
                host.sort(host.itemManager.fileSortType, mIsDir, !host.itemManager.isFileSortedAscending)
                setFileOrderIconWithCurrentState()
            }
        }
        binding.sortType.setOnClickListener {
            val themedCtx = ContextThemeWrapper(host.context, R.style.PopupMenu_HideIndicator)
            val popupMenu = PopupMenu(themedCtx, binding.sortType).apply {
                inflate(R.menu.menu_sort_options)
                setForceShowIconCompat()
            }

            host.isDirSortMenuShowing = mIsDir

            val currentSortType = if (mIsDir) host.itemManager.dirSortType else host.itemManager.fileSortType
            val initItemId = when (currentSortType) {
                SORT_TYPE_DATE -> R.id.action_sort_by_date
                SORT_TYPE_SIZE -> R.id.action_sort_by_size
                SORT_TYPE_TYPE -> R.id.action_sort_by_type
                else -> R.id.action_sort_by_name
            }
            popupMenu.menu.findItem(initItemId).isChecked = true
            ThemeColorHelper.setThemeColorPrimary(popupMenu.menu, themedCtx, host.context.getColor(R.color.item_background), initItemId)

            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                item.isChecked = true
                when (item.itemId) {
                    R.id.action_sort_by_name -> host.sort(SORT_TYPE_NAME, host.isDirSortMenuShowing, true)
                    R.id.action_sort_by_date -> host.sort(SORT_TYPE_DATE, host.isDirSortMenuShowing, false)
                    R.id.action_sort_by_size -> host.sort(SORT_TYPE_SIZE, host.isDirSortMenuShowing, false)
                    R.id.action_sort_by_type -> host.sort(SORT_TYPE_TYPE, host.isDirSortMenuShowing, true)
                }
                ThemeColorHelper.setThemeColorPrimary(popupMenu.menu, themedCtx, true, item.itemId)
                true
            }
            popupMenu.show()
        }
        binding.goUp.setOnClickListener {
            if (host.canGoUp()) host.goUp()
        }
        binding.titleContainer.setOnClickListener {
            if (mIsDir) {
                host.pageState.dirsCollapsed = !host.pageState.dirsCollapsed
            } else {
                host.pageState.filesCollapsed = !host.pageState.filesCollapsed
            }
            host.notifyDataSetChanged()
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun bind(isDirCategory: Any, position: Int) {
        if (isDirCategory !is Boolean) return
        val titleRes = if (isDirCategory) R.string.text_folder else R.string.text_file
        val count = if (isDirCategory) host.itemManager.groupCount() else host.itemManager.itemCount()
        binding.title.text = "${host.context.getString(titleRes)}  [ $count ]"
        binding.titleContainer.contentDescription = binding.title.text
        mIsDir = isDirCategory
        if (isDirCategory && host.canGoUp()) {
            binding.goUp.visibility = VISIBLE
        } else {
            binding.goUp.visibility = GONE
        }
        if (isDirCategory) {
            binding.arrowIcon.rotation = (if (host.pageState.dirsCollapsed) -90 else 0).toFloat()
            setDirOrderIconWithCurrentState()
        } else {
            binding.arrowIcon.rotation = (if (host.pageState.filesCollapsed) -90 else 0).toFloat()
            setFileOrderIconWithCurrentState()
        }
    }

    private fun setFileOrderIconWithCurrentState() {
        val ascending = host.itemManager.isFileSortedAscending
        binding.sortOrder.setImageResource(if (ascending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
        updateSortOrderContentDescription(ascending)
    }

    private fun setDirOrderIconWithCurrentState() {
        val ascending = host.itemManager.isDirSortedAscending
        binding.sortOrder.setImageResource(if (ascending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
        updateSortOrderContentDescription(ascending)
    }

    private fun updateSortOrderContentDescription(ascending: Boolean) {
        binding.sortOrder.contentDescription = buildString {
            append(host.context.getString(R.string.text_sort_order))
            append(": ")
            append(
                host.context.getString(
                    if (ascending) R.string.text_sort_ascending else R.string.text_sort_descending,
                ),
            )
        }
    }
}
