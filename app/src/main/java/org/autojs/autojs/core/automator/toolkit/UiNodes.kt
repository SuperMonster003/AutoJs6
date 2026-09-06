package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.UiObjectSummary
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassStep

/**
 * Small tree walks the tools share, over any [UiNode] and free of Android: bounded pre-order
 * traversal, the first / all descendants passing a predicate, the largest scrollable node, and
 * the clickable-ancestor walk of the compass `k` step. Nothing here recycles nodes: the tools
 * hold few nodes for a short time, and the search algorithms' release-on-the-way semantics would
 * invalidate nodes a tool still uses (a fake tree even refuses a second recycle).
 * zh-CN: 各工具共用的小型树遍历, 面向任意 [UiNode] 且不依赖 Android: 有界先序遍历, 通过谓词的首个 / 全部后代, 面积最大的可滚动节点,
 * 以及罗盘 `k` 步骤的可点击祖先行走. 这里不回收节点: 工具短时间只持有少量节点, 而搜索算法的沿途释放语义会使工具仍在使用的节点失效
 * (假节点树甚至拒绝二次回收).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
object UiNodes {

    /** The node budget of one walk over a window, generous for any real screen. zh-CN: 一次窗口遍历的节点预算, 对任何真实屏幕都足够. */
    const val DEFAULT_MAX_NODES = 2000

    /**
     * Visits [root] (when [includeSelf]) and its descendants in pre-order until [visit] returns
     * `false` or [maxNodes] nodes have been visited; returns whether the walk ran to completion.
     * zh-CN: 先序访问 [root] ([includeSelf] 时) 及其后代, 直到 [visit] 返回 `false` 或已访问 [maxNodes] 个节点; 返回遍历是否走完.
     */
    fun <N : UiNode<N>> walk(root: N, includeSelf: Boolean = true, maxNodes: Int = DEFAULT_MAX_NODES, visit: (N) -> Boolean): Boolean {
        val stack = ArrayDeque<N>()
        if (includeSelf) stack.addLast(root) else pushChildren(root, stack)
        var visited = 0
        while (stack.isNotEmpty()) {
            if (visited >= maxNodes) return false
            val node = stack.removeLast()
            visited += 1
            if (!visit(node)) return false
            pushChildren(node, stack)
        }
        return true
    }

    private fun <N : UiNode<N>> pushChildren(node: N, stack: ArrayDeque<N>) {
        for (i in node.childCount() - 1 downTo 0) {
            stack.addLast(node.child(i) ?: continue)
        }
    }

    /** The first node in pre-order passing [predicate], or `null`. zh-CN: 先序中首个通过 [predicate] 的节点, 或 `null`. */
    fun <N : UiNode<N>> firstDescendant(root: N, includeSelf: Boolean = true, maxNodes: Int = DEFAULT_MAX_NODES, predicate: (N) -> Boolean): N? {
        var found: N? = null
        walk(root, includeSelf, maxNodes) { node ->
            if (predicate(node)) found = node
            found == null
        }
        return found
    }

    /** Every node in pre-order passing [predicate]. zh-CN: 先序中通过 [predicate] 的全部节点. */
    fun <N : UiNode<N>> descendants(root: N, includeSelf: Boolean = true, maxNodes: Int = DEFAULT_MAX_NODES, predicate: (N) -> Boolean): List<N> {
        val result = ArrayList<N>()
        walk(root, includeSelf, maxNodes) { node ->
            if (predicate(node)) result += node
            true
        }
        return result
    }

    /**
     * The scrollable node under [root] (itself included) covering the largest area, the first in
     * pre-order on a tie; the usual main list of a screen. `null` when nothing scrolls.
     * zh-CN: [root] 下 (含自身) 面积最大的可滚动节点, 面积相同时取先序在前者; 通常即页面的主列表. 没有可滚动节点时为 `null`.
     */
    fun <N : UiNode<N>> largestScrollable(root: N, maxNodes: Int = DEFAULT_MAX_NODES): N? {
        var best: N? = null
        var bestArea = -1L
        walk(root, maxNodes = maxNodes) { node ->
            if (node.scrollable()) {
                val area = node.width().toLong() * node.height().toLong()
                if (area > bestArea) {
                    best = node
                    bestArea = area
                }
            }
            true
        }
        return best
    }

    /**
     * [node] itself when clickable, else the nearest clickable ancestor within [maxLevels] levels,
     * else `null`: the compass `k` step (D6), reused by the smart click.
     * zh-CN: [node] 可点击时为其自身, 否则为 [maxLevels] 级内最近的可点击祖先, 都没有则为 `null`: 即罗盘的 `k` 步骤 (D6), 供智能点击复用.
     */
    fun <N : UiNode<N>> clickableAncestorOrSelf(node: N, maxLevels: Int): N? = Compass.navigate(node, listOf(CompassStep.Clickable(maxLevels)))

}

/**
 * Hashes of a whole subtree, for the two questions the scrolling tools ask: "did the scroll
 * change anything" ([subtree], geometry included) and "have I collected this item before"
 * ([content], geometry excluded, since a list item keeps its content while its position moves).
 * Both walk in pre-order within a node budget and fold the depth in, so the same nodes in a
 * different arrangement hash differently. Free of Android.
 * zh-CN: 整棵子树的哈希, 回答滚动工具的两个问题: "这次滚动有没有改变什么" ([subtree], 含几何信息) 与 "这一项是否已采集过" ([content],
 * 不含几何信息, 因为列表项位置移动时内容不变). 两者都在节点预算内先序遍历并混入深度, 因此相同节点的不同排列得到不同哈希. 不依赖 Android.
 */
object NodeFingerprints {

    /** Geometry, text and state of every node of the subtree ([UiObjectSummary.fingerprint] each). zh-CN: 子树每个节点的几何, 文本与状态 ([UiObjectSummary.fingerprint] 逐个). */
    fun subtree(node: UiNode<*>, maxNodes: Int = UiNodes.DEFAULT_MAX_NODES): Int = fold(node, maxNodes) { n, depth ->
        31 * UiObjectSummary.fingerprint(n) + depth
    }

    /** Class, text, description and the checked / selected / enabled states of every node, no geometry. zh-CN: 每个节点的类名, 文本, 描述与 checked / selected / enabled 状态, 不含几何信息. */
    fun content(node: UiNode<*>, maxNodes: Int = UiNodes.DEFAULT_MAX_NODES): Int = fold(node, maxNodes) { n, depth ->
        var hash = depth
        hash = 31 * hash + (n.className()?.hashCode() ?: 0)
        hash = 31 * hash + n.text().hashCode()
        hash = 31 * hash + (n.desc()?.hashCode() ?: 0)
        hash = 31 * hash + n.checked().hashCode()
        hash = 31 * hash + n.selected().hashCode()
        hash = 31 * hash + n.enabled().hashCode()
        hash = 31 * hash + n.childCount()
        hash
    }

    private inline fun fold(root: UiNode<*>, maxNodes: Int, nodeHash: (UiNode<*>, Int) -> Int): Int {
        var hash = 17
        var visited = 0
        val stack = ArrayDeque<Pair<UiNode<*>, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = stack.removeLast()
            visited += 1
            hash = 31 * hash + nodeHash(node, depth)
            for (i in node.childCount() - 1 downTo 0) {
                stack.addLast((node.child(i) ?: continue) to depth + 1)
            }
        }
        return hash
    }

}
