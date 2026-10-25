package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * `screenCoverage(min?)`: the share of the screen the node's on-screen part covers is at least
 * `min` (default [DEFAULT]).
 * zh-CN: `screenCoverage(min?)`: 节点在屏幕内的部分所占屏幕面积的比例不小于 `min` (默认 [DEFAULT]).
 *
 * Created by SuperMonster003 on Nov 19, 2022.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class DoubleMinFilter(
    private val mDoubleMinProperty: DoubleMinProperty,
    private val mMin: Double? = null,
    private val scale: ScreenScale.Source = ScreenScale.Source.default,
) : Filter {

    interface DoubleMinProperty {
        operator fun get(o: UiNode<*>, scale: ScreenScale): Double
    }

    override fun filter(node: UiNode<*>) = mDoubleMinProperty[node, scale.scale()] >= (mMin ?: DEFAULT)

    override val cost get() = Filter.COST_BOUNDS

    override fun toString() = "$mDoubleMinProperty(${mMin?.let { FilterFormat.number(it) } ?: ""})"

    companion object {

        const val DEFAULT = 0.948

        val SCREEN_COVERAGE = object : DoubleMinProperty {
            override fun get(o: UiNode<*>, scale: ScreenScale): Double {
                val w = scale.width
                val h = scale.height
                val validLeft = o.left().coerceIn(0..w)
                val validTop = o.top().coerceIn(0..h)
                val validRight = o.right().coerceIn(0..w)
                val validBottom = o.bottom().coerceIn(0..h)
                val objectArea = (validRight - validLeft) * (validBottom - validTop)
                val screenArea = w * h
                return objectArea.toDouble() / screenArea.toDouble()
            }

            override fun toString() = "screenCoverage"
        }

    }

}
