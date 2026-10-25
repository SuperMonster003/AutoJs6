package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.ScreenScale.Axis

/**
 * `bounds(l, t, r, b)` / `boundsInside(...)` / `boundsContains(...)`: the node's bounds equal, lie
 * inside, or contain the given rectangle. Both rectangles are normalised (left <= right, top <= bottom)
 * with the left / top floored and the right / bottom ceiled, so fractional and inverted arguments
 * behave (issue #49); `-1` and fractions are screen-relative, see [ScreenScale].
 * zh-CN: `bounds(l, t, r, b)` / `boundsInside(...)` / `boundsContains(...)`: 节点边界等于, 位于, 或包含给定矩形. 两个矩形都会
 * 规范化 (left <= right, top <= bottom), left / top 向下取整, right / bottom 向上取整, 因此小数与颠倒的参数也能工作
 * (issue #49); `-1` 与比例值相对于屏幕, 见 [ScreenScale].
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class BoundsFilter(
    private val left: Double,
    private val top: Double,
    private val right: Double,
    private val bottom: Double,
    private val type: Int,
    private val scale: ScreenScale.Source = ScreenScale.Source.default,
) : Filter {

    override val cost get() = Filter.COST_BOUNDS

    override fun filter(node: UiNode<*>): Boolean {
        val s = scale.scale()
        val actual = DesirableRect(s, node.left().toDouble(), node.top().toDouble(), node.right().toDouble(), node.bottom().toDouble())
        return when (type) {
            TYPE_INSIDE -> actual.inside(s, left, top, right, bottom)
            TYPE_CONTAINS -> actual.contains(s, left, top, right, bottom)
            else /* EQUALS */ -> actual.equals(s, left, top, right, bottom)
        }
    }

    override fun toString(): String {
        val name = when (type) {
            TYPE_INSIDE -> "boundsInside"
            TYPE_CONTAINS -> "boundsContains"
            else /* EQUALS */ -> "bounds"
        }
        return "$name(${FilterFormat.number(left)}, ${FilterFormat.number(top)}, ${FilterFormat.number(right)}, ${FilterFormat.number(bottom)})"
    }

    companion object {

        const val TYPE_EQUALS = 0
        const val TYPE_INSIDE = 1
        const val TYPE_CONTAINS = 2

        private class DesirableRect(scale: ScreenScale, left: Double, top: Double, right: Double, bottom: Double) {

            val intLeft = scale.floor(Axis.X, left)
            val intTop = scale.floor(Axis.Y, top)
            val intRight = scale.ceil(Axis.X, right)
            val intBottom = scale.ceil(Axis.Y, bottom)

            val niceLeft = minOf(intLeft, intRight)
            val niceTop = minOf(intTop, intBottom)
            val niceRight = maxOf(intLeft, intRight)
            val niceBottom = maxOf(intTop, intBottom)

            fun contains(r: DesirableRect): Boolean {
                return niceLeft <= r.niceLeft &&
                       niceTop <= r.niceTop &&
                       niceRight >= r.niceRight &&
                       niceBottom >= r.niceBottom
            }

            fun contains(scale: ScreenScale, left: Double, top: Double, right: Double, bottom: Double) = contains(DesirableRect(scale, left, top, right, bottom))

            fun inside(scale: ScreenScale, left: Double, top: Double, right: Double, bottom: Double) = DesirableRect(scale, left, top, right, bottom).contains(this)

            fun equals(scale: ScreenScale, left: Double, top: Double, right: Double, bottom: Double): Boolean {
                return this.niceLeft in scale.floor(Axis.X, left)..scale.ceil(Axis.X, left) &&
                       this.niceTop in scale.floor(Axis.Y, top)..scale.ceil(Axis.Y, top) &&
                       this.niceRight in scale.floor(Axis.X, right)..scale.ceil(Axis.X, right) &&
                       this.niceBottom in scale.floor(Axis.Y, bottom)..scale.ceil(Axis.Y, bottom)
            }

        }

    }

}
