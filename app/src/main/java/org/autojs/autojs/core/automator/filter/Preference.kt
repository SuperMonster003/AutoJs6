package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * The preference among the candidates of a single result, `prefer(keys...)` (docs/selector/ROADMAP.md D4, P3.4,
 * appendix C Q3; off unless given): the single-result queries of a selector with a preference collect every node it
 * matches instead of stopping at the first one, and take the best by [keys] compared one after another (the order of
 * the keys is their priority; ties go to the node found first, in the order of `find()`). `visible`, `clickable` and
 * `enabled` prefer the nodes for which they hold, `center` the node whose centre is nearest the centre of the screen,
 * `area` the largest node, `top` the highest one. Under `visible`, the compass step `k` passes over the clickable
 * ancestors that are not visible to the user.
 *
 * zh-CN: 单个结果的候选之间的偏好 `prefer(keys...)` (D4, P3.4, 附录 C Q3; 默认不启用): 带偏好的选择器的单结果查询收集它匹配的全部节点, 而不是停在首个,
 * 然后按 [keys] 逐个比较取最优 (键的顺序即优先级; 相同时取先找到的节点, 即 `find()` 中的顺序). `visible`, `clickable`, `enabled` 偏好该项成立的节点,
 * `center` 偏好中心离屏幕中心最近的节点, `area` 偏好面积最大的节点, `top` 偏好最靠上的节点. 含 `visible` 时, 罗盘的 `k` 步骤越过对用户不可见的可点击祖先.
 *
 * Created by SuperMonster003 on Oct 26, 2026.
 */
class Preference(val keys: List<Key>, private val scale: ScreenScale.Source? = null) {

    /** A key of `prefer`. zh-CN: `prefer` 的一个键. */
    enum class Key(val label: String) {
        VISIBLE("visible"),
        CLICKABLE("clickable"),
        ENABLED("enabled"),
        CENTER("center"),
        AREA("area"),
        TOP("top");

        override fun toString() = label

        companion object {

            /** The key named [label], or null. zh-CN: 名为 [label] 的键, 或 null. */
            fun of(label: String): Key? = entries.firstOrNull { it.label == label }

            /** The labels, quoted for messages. zh-CN: 各键名, 加引号供消息使用. */
            val quoted: String get() = entries.joinToString(", ") { "\"${it.label}\"" }

        }

    }

    /** Whether the compass step `k` passes over invisible clickable ancestors. zh-CN: 罗盘 `k` 步骤是否越过不可见的可点击祖先. */
    val visibleOnly: Boolean get() = Key.VISIBLE in keys

    /** The best of [nodes] (in the order found), or null when there is none. zh-CN: [nodes] (按找到的顺序) 中的最优者, 没有节点时为 null. */
    fun <N : UiNode<N>> best(nodes: List<N?>): N? {
        val candidates = nodes.filterNotNull()
        if (candidates.size < 2 || keys.isEmpty()) return candidates.firstOrNull()
        val comparator = comparator<N>()
        return candidates.minWithOrNull(comparator)
    }

    private fun <N : UiNode<N>> comparator(): Comparator<N> {
        // The centre of the screen is read once per choice. zh-CN: 每次选择只读一次屏幕中心.
        val screen by lazy { (scale ?: ScreenScale.Source.default).scale().let { it.width.toLong() to it.height.toLong() } }
        var comparator: Comparator<N> = Comparator { _, _ -> 0 }
        for (key in keys) {
            comparator = comparator.then(
                when (key) {
                    Key.VISIBLE -> compareBy<N> { !it.visibleToUser() }
                    Key.CLICKABLE -> compareBy<N> { !it.clickable() }
                    Key.ENABLED -> compareBy<N> { !it.enabled() }
                    Key.CENTER -> compareBy<N> { node ->
                        // Twice the distances, so that the centre of an odd side stays an integer. zh-CN: 取两倍距离, 使奇数边长的中心仍为整数.
                        val dx = node.left().toLong() + node.right() - screen.first
                        val dy = node.top().toLong() + node.bottom() - screen.second
                        dx * dx + dy * dy
                    }
                    Key.AREA -> compareByDescending<N> { it.width().toLong() * it.height() }
                    Key.TOP -> compareBy<N> { it.top() }
                }
            )
        }
        return comparator
    }

    /** As the selector string renders it, `prefer("visible", "center")`. zh-CN: 选择器字符串中的写法. */
    override fun toString() = "prefer(" + keys.joinToString(", ") { JsLiteral.quote(it.label) } + ")"

}

/**
 * `prefer(...)` among the filters of a selector: passes every node, and tells the single-result queries of the selector
 * which candidate to take ([Selector] applies the last one of them). zh-CN: 选择器过滤器中的 `prefer(...)`: 接受每个节点, 并告诉该选择器的单结果查询
 * 取哪个候选 ([Selector] 采用其中最后一个).
 */
class PreferFilter(val preference: Preference) : Filter {

    override fun filter(node: UiNode<*>) = true

    override val cost: Int get() = Filter.COST_PROPERTY

    override fun toString() = preference.toString()

}
