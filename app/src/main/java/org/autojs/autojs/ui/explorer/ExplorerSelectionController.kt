package org.autojs.autojs.ui.explorer

import org.autojs.autojs.model.explorer.ExplorerItem

/**
 * Owns the ordered selection for an [ExplorerView].
 *
 * Selection used to be represented by a mutable map embedded in the view. Keeping the mutation
 * rules here gives callers immutable snapshots and prevents archive reindexing, change events and
 * row click handlers from each implementing subtly different selection updates.
 */
internal class ExplorerSelectionController(
    private val canSelect: (ExplorerItem) -> Boolean,
) {

    private val selectedByPath = LinkedHashMap<String, ExplorerItem>()

    val isActive: Boolean
        get() = selectedByPath.isNotEmpty()

    val size: Int
        get() = selectedByPath.size

    val items: List<ExplorerItem>
        get() = selectedByPath.values.toList()

    fun contains(item: ExplorerItem): Boolean = selectedByPath.containsKey(item.path)

    /** Returns true when the item is eligible and selection mode was entered. */
    fun enter(item: ExplorerItem): Boolean {
        if (!canSelect(item)) return false
        selectedByPath[item.path] = item
        return true
    }

    /** Returns true when the item was eligible and its selection state was toggled. */
    fun toggle(item: ExplorerItem): Boolean {
        if (!canSelect(item)) return false
        if (selectedByPath.remove(item.path) == null) {
            selectedByPath[item.path] = item
        }
        return true
    }

    fun remove(path: String): Boolean = selectedByPath.remove(path) != null

    /** Replaces stale item instances after an archive index rebuild while retaining order. */
    fun replace(items: Iterable<ExplorerItem>) {
        selectedByPath.clear()
        items.forEach { item ->
            if (canSelect(item)) selectedByPath[item.path] = item
        }
    }

    /** Returns true only when an active selection was actually cleared. */
    fun clear(): Boolean {
        if (selectedByPath.isEmpty()) return false
        selectedByPath.clear()
        return true
    }
}
