package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter

/**
 * The result of evaluating the inspector filters over a node tree (roadmap P0.3): the [matches] in
 * pre-order, the nodes to keep when the tree is pruned (matches plus their ancestors, roadmap D3),
 * and cyclic next / previous navigation over the matches. [unfiltered] stands for "no query": every
 * node is kept and matched, [count] is 0 and navigation yields nothing.
 *
 * Membership is by the node's own `equals`; [InspectorNode] compares by the wrapped [org.autojs.autojs.core.accessibility.NodeInfo],
 * so a freshly wrapped node can be probed.
 *
 * zh-CN: 布局分析过滤器对节点树的求值结果 (Roadmap P0.3): 先序排列的 [matches], 裁剪树时需保留的节点 (匹配节点及其祖先,
 * Roadmap D3), 以及在匹配项间循环的上一个 / 下一个导航. [unfiltered] 表示 "没有查询": 所有节点均保留且匹配, [count] 为 0,
 * 导航不返回任何节点. 成员资格按节点自身的 `equals` 判定; [InspectorNode] 以所包装的 NodeInfo 比较, 因此可用新建的包装对象探查.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class TreeMatch<N : UiNode<N>> private constructor(
    val matches: List<N>,
    private val matched: Set<N>,
    private val kept: Set<N>,
    val isUnfiltered: Boolean,
) {

    val count: Int get() = matches.size

    val isEmpty: Boolean get() = !isUnfiltered && matches.isEmpty()

    fun isMatched(node: N): Boolean = isUnfiltered || node in matched

    /** Whether [node] stays in a pruned tree: it matches or an item below it does. zh-CN: [node] 是否留在裁剪后的树中: 自身匹配或其下有匹配项. */
    fun isKept(node: N): Boolean = isUnfiltered || node in kept

    /** Position of [node] among [matches], -1 when it is not one. zh-CN: [node] 在 [matches] 中的序号, 不是匹配项时为 -1. */
    fun indexOf(node: N?): Int = if (node == null) -1 else matches.indexOf(node)

    /** The match after [current] (wrapping), the first one when [current] is null or not a match; null when there are none. zh-CN: [current] 之后的匹配项 (循环), [current] 为 null 或不是匹配项时取第一个; 没有匹配项时为 null. */
    fun next(current: N?): N? = step(current, 1)

    /** The match before [current] (wrapping), the last one when [current] is null or not a match; null when there are none. zh-CN: [current] 之前的匹配项 (循环), [current] 为 null 或不是匹配项时取最后一个; 没有匹配项时为 null. */
    fun previous(current: N?): N? = step(current, -1)

    private fun step(current: N?, delta: Int): N? {
        if (matches.isEmpty()) return null
        val index = indexOf(current)
        if (index < 0) return if (delta > 0) matches.first() else matches.last()
        return matches[Math.floorMod(index + delta, matches.size)]
    }

    companion object {

        /**
         * Evaluates [filters] (all must pass) over the subtree rooted at [root] in pre-order; an
         * empty list gives [unfiltered].
         * zh-CN: 以先序对以 [root] 为根的子树求值 [filters] (须全部通过); 空列表给出 [unfiltered].
         */
        fun <N : UiNode<N>> evaluate(root: N, filters: List<Filter>): TreeMatch<N> {
            if (filters.isEmpty()) return unfiltered()
            val matches = ArrayList<N>()
            val matched = HashSet<N>()
            val kept = HashSet<N>()
            for (node in InspectorNodes.preOrder(root)) {
                if (!filters.all { it.filter(node) }) continue
                matches += node
                matched += node
                var ancestor: N? = node
                while (ancestor != null && kept.add(ancestor)) {
                    ancestor = ancestor.parent()
                }
            }
            return TreeMatch(matches, matched, kept, isUnfiltered = false)
        }

        fun <N : UiNode<N>> unfiltered(): TreeMatch<N> = TreeMatch(emptyList(), emptySet(), emptySet(), isUnfiltered = true)

    }

}

/** Tree walks over [UiNode]s that the inspector shares with its tests. zh-CN: 布局分析与其测试共用的 [UiNode] 树遍历. */
object InspectorNodes {

    /** The subtree rooted at [root] in pre-order (parents before children, siblings in index order). zh-CN: 以 [root] 为根的子树, 先序 (父在子前, 兄弟按索引). */
    fun <N : UiNode<N>> preOrder(root: N): List<N> {
        val result = ArrayList<N>()
        val stack = ArrayDeque<N>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            result += node
            for (i in node.childCount() - 1 downTo 0) {
                node.child(i)?.let(stack::addLast)
            }
        }
        return result
    }

}
