package org.autojs.autojs.core.automator.filter

import android.graphics.Rect
import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoHelper
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.util.DisplayUtils.toCeilIntX
import org.autojs.autojs.util.DisplayUtils.toCeilIntY
import org.autojs.autojs.util.DisplayUtils.toFloorIntX
import org.autojs.autojs.util.DisplayUtils.toFloorIntY
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class BoundsFilter(private val left: Double, private val top: Double, private val right: Double, private val bottom: Double, private val type: Int) : Filter {

    override fun filter(node: UiObject) = AccessibilityNodeInfoHelper.getBoundsInScreen(node).let {
        when (type) {
            TYPE_INSIDE -> DesirableRect(it).inside(left, top, right, bottom)
            TYPE_CONTAINS -> DesirableRect(it).contains(left, top, right, bottom)
            else /* EQUALS */ -> DesirableRect(it).equals(left, top, right, bottom)
        }
    }

    override fun toString(): String {
        val name = when (type) {
            TYPE_INSIDE -> "boundsInside"
            TYPE_CONTAINS -> "boundsContains"
            else /* EQUALS */ -> "bounds"
        }
        return "$name(${formatDouble(left)}, ${formatDouble(top)}, ${formatDouble(right)}, ${formatDouble(bottom)})"
    }

    companion object {

        const val TYPE_EQUALS = 0
        const val TYPE_INSIDE = 1
        const val TYPE_CONTAINS = 2

        private class DesirableRect(left: Double, top: Double, right: Double, bottom: Double) {

            constructor(rect: Rect) : this(rect.left.toDouble(), rect.top.toDouble(), rect.right.toDouble(), rect.bottom.toDouble())

            val intLeft = toFloorIntX(left)
            val intTop = toFloorIntY(top)
            val intRight = toCeilIntX(right)
            val intBottom = toCeilIntY(bottom)

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

            fun contains(left: Double, top: Double, right: Double, bottom: Double) = contains(DesirableRect(left, top, right, bottom))

            fun inside(left: Double, top: Double, right: Double, bottom: Double) = DesirableRect(left, top, right, bottom).contains(this)

            fun equals(left: Double, top: Double, right: Double, bottom: Double): Boolean {
                return this.niceLeft in toFloorIntX(left)..toCeilIntX(left) &&
                       this.niceTop in toFloorIntY(top)..toCeilIntY(top) &&
                       this.niceRight in toFloorIntX(right)..toCeilIntX(right) &&
                       this.niceBottom in toFloorIntY(bottom)..toCeilIntY(bottom)
            }

        }

    }

}
