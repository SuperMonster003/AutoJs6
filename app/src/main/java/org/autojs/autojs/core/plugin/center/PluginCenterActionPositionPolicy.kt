package org.autojs.autojs.core.plugin.center

/**
 * Keeps explicitly operated rows at their displayed positions for the current view session.
 * Only positions are retained: every render still uses fresh items, and unretained rows keep
 * their normal sort order. Missing items are never recreated from stale snapshots.
 *
 * zh-CN: 在当前视图会话中保留主动操作条目的显示位置. 只保留位置, 每次渲染仍使用最新条目,
 * 未操作条目继续正常排序, 不从旧快照重建已经消失的条目.
 */
internal class PluginCenterActionPositionPolicy {

    private val positions = linkedMapOf<String, Int>()

    fun retain(packageName: String, displayedItems: List<PluginCenterItem>) {
        val displayedPositions = displayedItems.withIndex().associate { it.value.packageName to it.index }
        val position = displayedPositions[packageName] ?: return
        // Another row may have disappeared since the previous action. Capture what the user
        // actually sees now so consecutive actions cannot reserve the same displayed slot.
        // zh-CN: 上次操作后其他条目可能已消失. 按当前实际显示位置重新记录, 避免连续操作占用同一位置.
        positions.replaceAll { name, oldPosition -> displayedPositions[name] ?: oldPosition }
        positions[packageName] = position
    }

    fun isRetained(packageName: String): Boolean = packageName in positions

    fun apply(sortedItems: List<PluginCenterItem>): List<PluginCenterItem> {
        if (positions.isEmpty() || sortedItems.isEmpty()) return sortedItems
        val retained = sortedItems.filter { isRetained(it.packageName) }
            .sortedBy { positions.getValue(it.packageName) }
        if (retained.isEmpty()) return sortedItems
        val result = sortedItems.filterNot { isRetained(it.packageName) }.toMutableList()
        retained.forEach { item ->
            result.add(positions.getValue(item.packageName).coerceAtMost(result.size), item)
        }
        return result
    }

    fun clear() {
        positions.clear()
    }
}
