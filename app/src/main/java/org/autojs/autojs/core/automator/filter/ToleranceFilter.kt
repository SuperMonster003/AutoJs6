package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode
import kotlin.math.abs

/**
 * `screenCenterX(b?, tolerance?)` / `screenCenterY(...)`: whether the node's centre lies within
 * `tolerance` (a fraction of the screen side, default 1.6 %) of the screen centre, expected to be
 * [DEFAULT_EXPECTED] unless said otherwise.
 * zh-CN: `screenCenterX(b?, tolerance?)` / `screenCenterY(...)`: 节点中心是否落在屏幕中心的 `tolerance` (屏幕边长的比例,
 * 默认 1.6 %) 范围内, 未指明时期望值为 [DEFAULT_EXPECTED].
 *
 * Created by SuperMonster003 on Nov 19, 2022.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class ToleranceFilter(
    private val mToleranceSupplier: ToleranceSupplier,
    private val mExceptedValue: Boolean? = null,
    private val mTolerance: Double? = null,
    private val scale: ScreenScale.Source = ScreenScale.Source.default,
) : Filter {

    interface ToleranceSupplier {
        operator fun get(node: UiNode<*>, tolerance: Double, scale: ScreenScale): Boolean
    }

    override val cost get() = Filter.COST_BOUNDS

    override fun filter(node: UiNode<*>) = mToleranceSupplier[node, mTolerance ?: DEFAULT_TOLERANCE, scale.scale()] == (mExceptedValue ?: DEFAULT_EXPECTED)

    override fun toString() = when (mExceptedValue != null) {
        true -> "$mToleranceSupplier($mExceptedValue${mTolerance?.let { ", ${FilterFormat.number(it)}" } ?: ""})"
        else -> "$mToleranceSupplier(${mTolerance?.let { FilterFormat.number(it) } ?: ""})"
    }

    companion object {

        const val DEFAULT_EXPECTED = true
        const val DEFAULT_TOLERANCE = 0.016

        val SCREEN_CENTER_X = object : ToleranceSupplier {
            override fun get(node: UiNode<*>, tolerance: Double, scale: ScreenScale): Boolean {
                val w = scale.width
                val x = node.centerX().toDouble()
                return abs(x - w / 2) / w <= tolerance
            }

            override fun toString() = "screenCenterX"
        }

        val SCREEN_CENTER_Y = object : ToleranceSupplier {
            override fun get(node: UiNode<*>, tolerance: Double, scale: ScreenScale): Boolean {
                val h = scale.height
                val y = node.centerY().toDouble()
                return abs(y - h / 2) / h <= tolerance
            }

            override fun toString() = "screenCenterY"
        }

    }

}
