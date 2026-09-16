package org.autojs.autojs.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.storage.history.TrashExpiry
import org.autojs.autojs6.databinding.ItemTrashBinding

/**
 * Display row: the item plus its derived state, so [DiffUtil] notices countdown / broken-state changes.
 * zh-CN: 显示行: 条目加上派生状态, 使 [DiffUtil] 能感知倒计时 / 失效状态的变化.
 */
internal data class TrashRow(
    val item: TrashEntities.TrashItem,
    val missing: Boolean,
    val remaining: TrashExpiry.Remaining?,
)

/**
 * Trash list backed by [ListAdapter] with an optional multi-selection mode (entered by long press,
 * left when empty). Selection lives in the adapter and is refreshed with targeted notifications.
 * zh-CN: 基于 [ListAdapter] 的回收站列表, 带可选多选模式 (长按进入, 选空后退出). 选择状态保存在 adapter 内, 以定向通知刷新.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
internal class TrashAdapter(
    private val onClick: (TrashEntities.TrashItem) -> Unit,
    private val onLongClick: (TrashEntities.TrashItem) -> Unit,
    private val onSelectionChanged: (selectedCount: Int, selectionMode: Boolean) -> Unit,
) : ListAdapter<TrashRow, TrashViewHolder>(DIFF) {

    private val selectedIds = LinkedHashSet<String>()

    var selectionMode: Boolean = false
        private set

    val selectedCount: Int get() = selectedIds.size

    fun submit(rows: List<TrashRow>) {
        val known = rows.mapTo(HashSet()) { it.item.trashId }
        val wasSelecting = selectionMode
        selectedIds.retainAll(known)
        if (selectedIds.isEmpty()) selectionMode = false
        val modeChanged = wasSelecting != selectionMode
        submitList(rows) {
            // Rows unchanged by the diff still need the checkbox column toggled.
            // zh-CN: diff 认为未变化的行仍需切换复选框列.
            if (modeChanged) notifyItemRangeChanged(0, itemCount)
        }
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun selectedItems(): List<TrashEntities.TrashItem> = currentList.filter { it.item.trashId in selectedIds }.map { it.item }

    fun enterSelection(item: TrashEntities.TrashItem) {
        val wasSelecting = selectionMode
        selectionMode = true
        applyToggle(item, wasSelecting)
    }

    fun toggle(item: TrashEntities.TrashItem) {
        applyToggle(item, selectionMode)
    }

    private fun applyToggle(item: TrashEntities.TrashItem, wasSelecting: Boolean) {
        val id = item.trashId
        if (!selectedIds.add(id)) selectedIds.remove(id)
        if (selectedIds.isEmpty()) selectionMode = false
        if (wasSelecting != selectionMode) {
            notifyItemRangeChanged(0, itemCount)
        } else {
            val index = currentList.indexOfFirst { it.item.trashId == id }
            if (index >= 0) notifyItemChanged(index)
        }
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun selectAll() {
        if (currentList.isEmpty()) return
        selectionMode = true
        currentList.forEach { selectedIds.add(it.item.trashId) }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(0, false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrashViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val row = getItem(position)
        holder.bind(row, selectionMode, row.item.trashId in selectedIds)
    }

    companion object {

        private val DIFF = object : DiffUtil.ItemCallback<TrashRow>() {

            override fun areItemsTheSame(oldItem: TrashRow, newItem: TrashRow): Boolean {
                return oldItem.item.trashId == newItem.item.trashId
            }

            override fun areContentsTheSame(oldItem: TrashRow, newItem: TrashRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
