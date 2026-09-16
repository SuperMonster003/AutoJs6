package org.autojs.autojs.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs6.databinding.ItemTrashBinding

/**
 * Trash list with an optional multi-selection mode (entered by long press, left when empty).
 * zh-CN: 带可选多选模式的回收站列表 (长按进入, 选空后退出).
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
internal class TrashAdapter(
    private val onClick: (TrashEntities.TrashItem) -> Unit,
    private val onLongClick: (TrashEntities.TrashItem) -> Unit,
    private val onSelectionChanged: (selectedCount: Int, selectionMode: Boolean) -> Unit,
) : RecyclerView.Adapter<TrashViewHolder>() {

    private var items: List<TrashEntities.TrashItem> = emptyList()
    private val selectedIds = LinkedHashSet<String>()

    var selectionMode: Boolean = false
        private set

    // Expiry inputs for the countdown column; set by the page before submitting items.
    // zh-CN: 到期倒计时所需输入; 由页面在提交数据前设置.
    var expiryMaxDays: Int = 0
    var now: Long = System.currentTimeMillis()

    val selectedCount: Int get() = selectedIds.size

    fun submit(newItems: List<TrashEntities.TrashItem>) {
        items = newItems
        val known = newItems.mapTo(HashSet()) { it.trashId }
        selectedIds.retainAll(known)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun selectedItems(): List<TrashEntities.TrashItem> = items.filter { it.trashId in selectedIds }

    fun enterSelection(item: TrashEntities.TrashItem) {
        selectionMode = true
        toggle(item)
    }

    fun toggle(item: TrashEntities.TrashItem) {
        val id = item.trashId
        if (!selectedIds.add(id)) selectedIds.remove(id)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun selectAll() {
        if (items.isEmpty()) return
        selectionMode = true
        items.forEach { selectedIds.add(it.trashId) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size, selectionMode)
    }

    fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0, false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrashViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, selectionMode, item.trashId in selectedIds, expiryMaxDays, now)
    }

    override fun getItemCount(): Int = items.size
}
