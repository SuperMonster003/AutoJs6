package org.autojs.autojs.ui.storage

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.storage.history.HistoryEntities

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 */
internal class VersionHistoryFileAdapter(
    private val onClick: (HistoryEntities.FileEntry) -> Unit,
    private val onLongClick: (HistoryEntities.FileEntry) -> Unit,
    private val getSizeBytesOrNull: (fileId: String) -> Long?,
    private val getRevisionCountOrNull: (fileId: String) -> Long?,
) : RecyclerView.Adapter<VersionHistoryFileViewHolder>() {

    private var items: List<HistoryEntities.FileEntry> = emptyList()

    fun submit(newItems: List<HistoryEntities.FileEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionHistoryFileViewHolder {
        return VersionHistoryFileViewHolder.create(parent, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: VersionHistoryFileViewHolder, position: Int) {
        val item = items[position]
        holder.bind(
            item = item,
            totalSizeBytesOrNull = getSizeBytesOrNull(item.fileId),
            revisionCountOrNull = getRevisionCountOrNull(item.fileId),
        )
    }

    override fun getItemCount(): Int = items.size
}