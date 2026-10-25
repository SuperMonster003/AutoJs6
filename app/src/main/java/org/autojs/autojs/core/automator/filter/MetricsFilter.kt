package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.ScreenScale.Axis

/**
 * A [Comparison] of one of the eight bounds metrics of a node, in either spelling of the builders
 * (`left(v)` / `boundsLeft(v)`, `minLeft(v)` / `boundsMinLeft(v)`, ...). Arguments are resolved
 * against the [ScreenScale] at evaluation time: fractions of the screen side, `-1` for the whole
 * side, pixels otherwise.
 * zh-CN: 对节点八个边界度量之一的 [Comparison], 支持 builder 的两种拼写 (`left(v)` / `boundsLeft(v)`, `minLeft(v)` /
 * `boundsMinLeft(v)`, ...). 参数在求值时按 [ScreenScale] 解析: 屏幕边长的比例, `-1` 为整条边, 其余为像素.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class MetricsFilter(
    val property: Property,
    val comparison: Comparison,
    val spelling: Spelling = Spelling.PLAIN,
    private val scale: ScreenScale.Source = ScreenScale.Source.default,
) : Filter {

    enum class Property(val axis: Axis, private val label: String, private val read: (UiNode<*>) -> Int) {
        LEFT(Axis.X, "left", { it.left() }),
        TOP(Axis.Y, "top", { it.top() }),
        RIGHT(Axis.X, "right", { it.right() }),
        BOTTOM(Axis.Y, "bottom", { it.bottom() }),
        WIDTH(Axis.X, "width", { it.width() }),
        HEIGHT(Axis.Y, "height", { it.height() }),
        CENTER_X(Axis.X, "centerX", { it.centerX() }),
        CENTER_Y(Axis.Y, "centerY", { it.centerY() });

        val capitalized: String = label.replaceFirstChar { it.uppercase() }

        operator fun get(node: UiNode<*>): Int = read(node)

        override fun toString() = label
    }

    /** `left(v)` versus `boundsLeft(v)`: the same filter, two builder names. zh-CN: `left(v)` 与 `boundsLeft(v)`: 同一过滤器, 两个 builder 名. */
    enum class Spelling { PLAIN, BOUNDS }

    sealed class Comparison(val prefix: String) {

        abstract fun test(actual: Int, axis: Axis, scale: ScreenScale): Boolean

        abstract fun arguments(): String

        class Equals(val value: Double) : Comparison("") {
            override fun test(actual: Int, axis: Axis, scale: ScreenScale) = actual == scale.round(axis, value)
            override fun arguments() = FilterFormat.number(value)
        }

        class Range(val min: Double, val max: Double) : Comparison("") {
            override fun test(actual: Int, axis: Axis, scale: ScreenScale) = actual in scale.round(axis, min)..scale.round(axis, max)
            override fun arguments() = "${FilterFormat.number(min)}, ${FilterFormat.number(max)}"
        }

        class Min(val min: Double) : Comparison("min") {
            override fun test(actual: Int, axis: Axis, scale: ScreenScale) = actual >= scale.round(axis, min)
            override fun arguments() = FilterFormat.number(min)
        }

        class Max(val max: Double) : Comparison("max") {
            override fun test(actual: Int, axis: Axis, scale: ScreenScale) = actual <= scale.round(axis, max)
            override fun arguments() = FilterFormat.number(max)
        }

    }

    /** The builder name this filter prints as, e.g. `boundsMinLeft`. zh-CN: 本过滤器打印时使用的 builder 名, 如 `boundsMinLeft`. */
    val name: String
        get() = when (spelling) {
            Spelling.PLAIN -> if (comparison.prefix.isEmpty()) property.toString() else comparison.prefix + property.capitalized
            Spelling.BOUNDS -> "bounds" + comparison.prefix.replaceFirstChar { it.uppercase() } + property.capitalized
        }

    override fun filter(node: UiNode<*>) = comparison.test(property[node], property.axis, scale.scale())

    override val cost get() = Filter.COST_BOUNDS

    override fun toString() = "$name(${comparison.arguments()})"

}
