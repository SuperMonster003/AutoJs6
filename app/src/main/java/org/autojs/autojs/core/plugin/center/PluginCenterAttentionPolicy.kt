package org.autojs.autojs.core.plugin.center

/**
 * Places pinned plugins ([PluginCenterAttentionLedger]) before the sorted list and labels them (maintainer decisions
 * of 2026-10-16). Only installed rows that passed the current search and filter are pinned, the newest pin first; the
 * other rows keep their sorted order. A row the user operated on keeps the position the action policy retained for
 * it, so a plugin installed or enabled from its own row is labeled without moving.
 *
 * zh-CN: 将置顶的插件 ([PluginCenterAttentionLedger]) 排在已排序列表之前并加上标签 (维护者 2026-10-16 的决定).
 * 只置顶通过当前搜索与筛选的已安装条目, 最新的置顶在前; 其余条目保持排序结果. 用户操作过的条目保持操作位置策略
 * 为其保留的位置, 因此在自身条目上安装或启用的插件只加标签, 不移动.
 */
internal object PluginCenterAttentionPolicy {

    fun attentionOf(item: PluginCenterItem, pin: PluginCenterAttentionLedger.Pin): PluginCenterAttention {
        val failed = item.enabledState is PluginEnabledState.ERROR ||
                (!item.isEnabled && (pin.enableFailed || item.lastError != null))
        return if (failed) PluginCenterAttention.ENABLE_FAILED else PluginCenterAttention.NEW_INSTALL
    }

    fun apply(
        sorted: List<PluginCenterItem>,
        pins: Map<String, PluginCenterAttentionLedger.Pin>,
        isRetained: (String) -> Boolean,
    ): List<PluginCenterItem> {
        if (pins.isEmpty()) return sorted
        val hoisted = mutableListOf<Pair<PluginCenterItem, Long>>()
        val rest = ArrayList<PluginCenterItem>(sorted.size)
        sorted.forEach { item ->
            val pin = pins[item.packageName]?.takeIf { item.isInstalled }
            if (pin == null) {
                rest += item
                return@forEach
            }
            val attention = attentionOf(item, pin)
            val labeled = if (item.attention == attention) item else item.copy(attention = attention)
            if (isRetained(item.packageName)) rest += labeled else hoisted += labeled to pin.createdAt
        }
        if (hoisted.isEmpty()) return rest
        return hoisted.sortedByDescending { it.second }.map { it.first } + rest
    }

    /** Packages of the pins present in [items]. zh-CN: [items] 中存在的置顶包名. */
    fun presentPins(items: List<PluginCenterItem>, pins: Map<String, PluginCenterAttentionLedger.Pin>): List<String> =
        if (pins.isEmpty()) emptyList() else items.filter { it.isInstalled && it.packageName in pins }.map { it.packageName }
}
