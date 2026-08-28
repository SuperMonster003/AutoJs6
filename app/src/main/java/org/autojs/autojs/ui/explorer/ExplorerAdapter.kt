package org.autojs.autojs.ui.explorer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager
import org.autojs.autojs.ui.widget.BindableViewHolder

/** Shared row type contract used by [ExplorerView] and its file-chooser subclass. */
internal object ExplorerRowViewType {
    const val ITEM = 0
    const val PAGE = 1
    const val CATEGORY = 2
}

/**
 * Maps the two logical Explorer groups to the flattened RecyclerView layout.
 *
 * The adapter owns category offsets and collapsed-group accounting. The view no longer repeats
 * those calculations while dispatching change events, which keeps row positioning consistent.
 */
internal class ExplorerAdapter(
    private val itemManager: () -> ExplorerItemManager,
    private val pageState: () -> ExplorerView.ExplorerPageState,
    private val viewHolderFactory: (
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int,
    ) -> BindableViewHolder<Any>,
) : RecyclerView.Adapter<BindableViewHolder<Any>>() {

    val positionOfDirectoryCategory: Int
        get() = layout().directoryCategoryPosition

    val positionOfFileCategory: Int
        get() = layout().fileCategoryPosition

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<Any> {
        return viewHolderFactory(LayoutInflater.from(parent.context), parent, viewType)
    }

    override fun onBindViewHolder(holder: BindableViewHolder<Any>, position: Int) {
        val manager = itemManager()
        when (val row = layout().rowAt(position)) {
            is ExplorerListRow.Category -> holder.bind(row.directories, position)
            is ExplorerListRow.Entry -> holder.bind(
                if (row.directories) manager.getDirItem(row.index) else manager.getFileItem(row.index),
                position,
            )
        }
    }

    override fun onViewRecycled(holder: BindableViewHolder<Any>) {
        super.onViewRecycled(holder)
        holder.onViewRecycled()
    }

    override fun getItemViewType(position: Int): Int {
        return when (val row = layout().rowAt(position)) {
            is ExplorerListRow.Category -> ExplorerRowViewType.CATEGORY
            is ExplorerListRow.Entry -> if (row.directories) {
                ExplorerRowViewType.PAGE
            } else {
                ExplorerRowViewType.ITEM
            }
        }
    }

    fun getItemPosition(item: ExplorerItem?, index: Int): Int {
        return layout().positionOf(directories = item is ExplorerPage, index = index)
    }

    fun notifyItemChanged(item: ExplorerItem?, index: Int) {
        notifyItemChanged(getItemPosition(item, index))
    }

    fun notifyItemRemoved(item: ExplorerItem?, index: Int) {
        notifyItemRemoved(getItemPosition(item, index))
    }

    fun notifyItemInserted(item: ExplorerItem?, index: Int) {
        notifyItemInserted(getItemPosition(item, index))
    }

    override fun getItemCount(): Int {
        return layout().itemCount
    }

    private fun layout(): ExplorerListLayout {
        val manager = itemManager()
        val state = pageState()
        return ExplorerListLayout(
            directoryCount = manager.groupCount(),
            fileCount = manager.itemCount(),
            directoriesCollapsed = state.dirsCollapsed,
            filesCollapsed = state.filesCollapsed,
        )
    }
}
