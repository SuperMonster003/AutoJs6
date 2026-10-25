package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Oct 25, 2026.
 */
interface Filter {

    fun filter(node: UiNode<*>): Boolean

    /**
     * The relative cost of [filter] on one node (docs/selector/ROADMAP.md P2.2): a [Selector] evaluates its filters
     * cheapest first, so that a cheap rejection spares the costly ones; equal costs keep the declared order. Filters
     * that do not say keep [COST_UNKNOWN] and so come after the known ones, in their declared order.
     * zh-CN: 在一个节点上执行 [filter] 的相对开销 (P2.2): [Selector] 按开销从低到高求值其过滤器, 使廉价的否决省去昂贵的过滤器; 开销相同者保持声明顺序.
     * 未声明开销的过滤器为 [COST_UNKNOWN], 因此排在已知过滤器之后, 彼此保持声明顺序.
     */
    val cost: Int get() = COST_UNKNOWN

    companion object {

        /** A field of the node: a boolean, an int (depth, child count ...), the class name compared exactly. zh-CN: 节点的字段: 布尔值, 整数 (深度, 子节点数等), 精确比较的类名. */
        const val COST_PROPERTY = 10

        /** A string compared as a literal: equality, prefix, suffix, substring. zh-CN: 按字面比较的字符串: 相等, 前缀, 后缀, 子串. */
        const val COST_STRING = 20

        /** The bounds and their metrics, against the screen scale. zh-CN: 边界及其度量, 对照屏幕比例. */
        const val COST_BOUNDS = 30

        /** A regular expression. zh-CN: 正则表达式. */
        const val COST_REGEX = 40

        /** A lookup in the resources of the node's package (`idHex`). zh-CN: 在节点所属包的资源中查找 (`idHex`). */
        const val COST_RESOURCE = 45

        /** A script function (`filter(fn)`). zh-CN: 脚本函数 (`filter(fn)`). */
        const val COST_SCRIPT = 50

        /** The action list of the node, mapped to names (`action(...)`). zh-CN: 节点的动作列表, 映射为名称 (`action(...)`). */
        const val COST_ACTIONS = 60

        /** A filter that does not state its cost. zh-CN: 未声明开销的过滤器. */
        const val COST_UNKNOWN = 100

    }

}
