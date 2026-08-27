package org.autojs.autojs.core.plugin.center

/**
 * Content-level equality for [PluginCenterItem], shared by the ViewModel publish gate
 * and the RecyclerView diff so both agree on what counts as a visible change.
 * Drawable instances differ on every discovery pass and are therefore excluded;
 * icon changes are tracked through [PluginCenterItem.iconSignature] instead.
 *
 * zh-CN: [PluginCenterItem] 的内容级等价判断, 由 ViewModel 发布门控与 RecyclerView
 * 差分共用, 确保二者对 "可见变化" 的认定一致. Drawable 实例在每轮发现中都会变化,
 * 因此被排除在比较之外; 图标变化改由 [PluginCenterItem.iconSignature] 跟踪.
 */
object PluginCenterItemSemantics {

    fun areItemsTheSame(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return a.packageName == b.packageName
    }

    fun areContentsTheSame(a: PluginCenterItem, b: PluginCenterItem): Boolean {
        return normalize(a) == normalize(b)
    }

    fun isSameList(current: List<PluginCenterItem>, next: List<PluginCenterItem>): Boolean {
        if (current.size != next.size) {
            return false
        }
        return current.indices.all { areContentsTheSame(current[it], next[it]) }
    }

    private fun normalize(item: PluginCenterItem): PluginCenterItem = item.copy(icon = null)

}
