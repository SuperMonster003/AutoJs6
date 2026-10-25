package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.automator.UiNode

/**
 * The compass walk (`UiObject.compass`, the pickup compass argument): parses a compass string with
 * [CompassParser] and follows its steps over any [UiNode] tree. Free of Android dependencies; the
 * script-facing wrapper in `UiObject` only adds the localized error messages.
 *
 * zh-CN: 罗盘导航 (`UiObject.compass`, pickup 的罗盘参数): 以 [CompassParser] 解析罗盘字符串, 并在任意 [UiNode] 树上
 * 按步骤行走. 不依赖 Android; `UiObject` 中面向脚本的包装只补充本地化的错误信息.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object Compass {

    /** The compass that stands for "the node itself". zh-CN: 表示 "节点自身" 的罗盘. */
    const val PASS_ON = "%"

    /** Whether [s] is a compass string, [PASS_ON] and the empty string included. zh-CN: [s] 是否为罗盘字符串, 含 [PASS_ON] 与空串. */
    fun isCompass(s: Any?): Boolean = s is CharSequence && (s == PASS_ON || CompassParser.isCompass(s))

    /**
     * Walks [compass] from [start]; null, the empty string and [PASS_ON] return [start] itself, an
     * unreachable step returns null.
     * zh-CN: 从 [start] 起按 [compass] 行走; null, 空串与 [PASS_ON] 返回 [start] 自身, 无法到达的步骤返回 null.
     */
    @Throws(CompassSyntaxException::class)
    fun <N : UiNode<N>> navigate(start: N, compass: CharSequence?): N? {
        val steps = compass?.takeUnless { it == PASS_ON }?.let(CompassParser::parse) ?: return start
        return navigate(start, steps)
    }

    /**
     * Walks [steps] from [start]; [visibleOnly]: the step `k` takes only a clickable node visible to the user (`prefer('visible')`,
     * docs/selector/ROADMAP.md P3.4). zh-CN: 从 [start] 起按 [steps] 行走; [visibleOnly]: `k` 步骤只取对用户可见的可点击节点 (`prefer('visible')`, P3.4).
     */
    fun <N : UiNode<N>> navigate(start: N, steps: List<CompassStep>, visibleOnly: Boolean = false): N? {
        var node = start
        for (step in steps) {
            node = when (step) {
                is CompassStep.Up -> up(node, step.levels)
                is CompassStep.Children -> children(node, step.indices)
                is CompassStep.Sibling -> sibling(node, step)
                is CompassStep.Clickable -> clickable(node, step.maxLevels, visibleOnly)
            } ?: return null
        }
        return node
    }

    // p2 : [ .parent().parent() ]
    // p : [ p1 ]
    // ppp : [ p3 ]
    // p4ppp12p : [ p4, pp, p12, p -> 4, 2, 12, 1 -> p19 ]
    private fun <N : UiNode<N>> up(start: N, levels: Int): N? {
        var node = start
        repeat(levels) { node = node.parent() ?: return null }
        return node
    }

    // c0c2c0c1 : [ .child(0).child(2).child(0).child(1) ]
    // c0>2>0>1 : [ .child(0).child(2).child(0).child(1) ]
    // c-3 : [ .child(childCount()-3) ]
    // c-3c2c-1 : [ .child(childCount()-3).child(2).child(childCount()-1) ]
    // c1>2>3>0>-1>1 : [ c1 c2 c3 c0 c-1 c1 ]
    private fun <N : UiNode<N>> children(start: N, indices: List<Int>): N? {
        var node = start
        for (index in indices) {
            val count = node.childCount().takeIf { it > 0 } ?: return null
            val i = index.let { if (it < 0) it + count else it }.takeIf { it in 0 until count } ?: return null
            node = node.child(i) ?: return null
        }
        return node
    }

    // s2 : [ .parent().child(2) ]
    // s-2 : [ .parent().child(childCount() - 2) ]
    // s>2 : [ .parent().child(idxInParent() + 2) ]
    // s<2 : [ .parent().child(idxInParent() - 2) ]
    private fun <N : UiNode<N>> sibling(node: N, step: CompassStep.Sibling): N? {
        val parent = node.parent() ?: return null
        val index = node.indexInParent().takeIf { it >= 0 } ?: return null
        val count = parent.childCount()
        val target = when (step.mode) {
            CompassStep.Sibling.Mode.FORWARD -> index + step.value
            CompassStep.Sibling.Mode.BACKWARD -> index - step.value
            CompassStep.Sibling.Mode.ABSOLUTE -> step.value.let { if (it < 0) it + count else it }
        }
        return target.takeIf { it in 0 until count }?.let { parent.child(it) }
    }

    // k/k1 : [ .clickable() || .parent().clickable() aka pk/p1k ]
    // k2 : [ .clickable() || .parent().clickable() || .p2.clickable() aka p2k ]
    // kn : [ .clickable() || .pn.clickable() aka pnk ]
    // Returns null when no clickable node exists within range (decided on Sep 5, 2026,
    // previously the node itself was returned unless the walk ran past the root).
    // zh-CN: 范围内不存在可点击节点时返回 null (2026-09-05 决定, 此前除非向上越过根节点, 否则返回节点自身).
    private fun <N : UiNode<N>> clickable(start: N, maxLevels: Int, visibleOnly: Boolean): N? {
        var node: N? = start
        repeat(maxLevels + 1) {
            val current = node ?: return null
            if (current.clickable() && (!visibleOnly || current.visibleToUser())) return current
            node = current.parent()
        }
        return null
    }

}
