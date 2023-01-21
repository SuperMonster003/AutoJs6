package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.deviceScreenHeight
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.deviceScreenWidth
import org.autojs.autojs.util.StringUtils.formatDouble
import kotlin.math.abs

/**
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class ToleranceFilter(private val mToleranceSupplier: ToleranceSupplier, private val mExceptedValue: Boolean? = null, private val mTolerance: Double? = null) : Filter {

    interface ToleranceSupplier {
        operator fun get(node: UiObject, tolerance: Double): Boolean
    }

    override fun filter(node: UiObject) = mToleranceSupplier[node, mTolerance ?: DEFAULT_TOLERANCE] == (mExceptedValue ?: DEFAULT_EXPECTED)

    override fun toString() = when (mExceptedValue != null) {
        true -> "$mToleranceSupplier($mExceptedValue${mTolerance?.let { ", ${formatDouble(mTolerance)}" } ?: ""})"
        else -> "$mToleranceSupplier(${mTolerance?.let { formatDouble(it) } ?: ""})"
    }

    companion object {

        const val DEFAULT_EXPECTED = true

        const val DEFAULT_TOLERANCE = 0.016

        val SCREEN_CENTER_X = object : ToleranceSupplier {
            override fun get(node: UiObject, tolerance: Double): Boolean {
                val w = deviceScreenWidth
                val x = node.centerX().toDouble()
                return abs(x - w / 2) / w <= tolerance
            }

            override fun toString() = "screenCenterX"
        }

        val SCREEN_CENTER_Y = object : ToleranceSupplier {
            override fun get(node: UiObject, tolerance: Double): Boolean {
                val h = deviceScreenHeight
                val y = node.centerY().toDouble()
                return abs(y - h / 2) / h <= tolerance
            }

            override fun toString() = "screenCenterY"
        }

    }

}
