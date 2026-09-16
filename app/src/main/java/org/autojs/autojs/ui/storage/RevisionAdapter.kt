package org.autojs.autojs.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs6.databinding.ItemRevisionBinding

/**
 * One revision of a tracked file plus whether its content equals the file on disk right now.
 * zh-CN: 已纳管文件的一条 revision, 以及其内容是否与当前磁盘文件一致.
 */
data class RevisionRow(
    val rev: HistoryEntities.Revision,
    val isCurrent: Boolean,
)

/**
 * Revision list with an optional multi-selection mode (entered by long press, left when empty).
 * zh-CN: 带可选多选模式的 revision 列表 (长按进入, 选空后退出).
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
internal class RevisionAdapter(
    private val onClick: (RevisionRow) -> Unit,
    private val onLongClick: (RevisionRow) -> Unit,
    private val onSelectionChanged: (selectedCount: Int, selectionMode: Boolean) -> Unit,
) : RecyclerView.Adapter<RevisionViewHolder>() {

    private var items: List<RevisionRow> = emptyList()
    private val selectedIds = LinkedHashSet<String>()

    var selectionMode: Boolean = false
        private set

    val selectedCount: Int get() = selectedIds.size

    fun submit(newItems: List<RevisionRow>) {
        items = newItems
        val known = newItems.mapTo(HashSet()) { it.rev.revId }
        selectedIds.retainAll(known)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun selectedRows(): List<RevisionRow> = items.filter { it.rev.revId in selectedIds }

    fun enterSelection(row: RevisionRow) {
        selectionMode = true
        toggle(row)
    }

    fun toggle(row: RevisionRow) {
        val id = row.rev.revId
        if (!selectedIds.add(id)) selectedIds.remove(id)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun selectAll() {
        if (items.isEmpty()) return
        selectionMode = true
        items.forEach { selectedIds.add(it.rev.revId) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0, false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RevisionViewHolder {
        val binding = ItemRevisionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RevisionViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: RevisionViewHolder, position: Int) {
        val row = items[position]
        holder.bind(row, selectionMode, row.rev.revId in selectedIds)
    }

    override fun getItemCount(): Int = items.size
}
