package org.autojs.autojs.core.automator.filter

import android.graphics.Rect
import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoHelper
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toCeilIntX
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toCeilIntY
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toFloorIntX
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toFloorIntY
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by Stardust on 2017/3/9.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class BoundsFilter(private val mLeft: Double, private val mTop: Double, private val mRight: Double, private val mBottom: Double, private val mType: Int) : Filter {

    override fun filter(node: UiObject) = AccessibilityNodeInfoHelper.getBoundsInScreen(node).let {
        when (mType) {
            TYPE_INSIDE -> Rect(toFloorIntX(mLeft), toFloorIntY(mTop), toCeilIntX(mRight), toCeilIntY(mBottom)).contains(it)
            TYPE_CONTAINS -> it.contains(Rect(toCeilIntX(mLeft), toCeilIntY(mTop), toFloorIntX(mRight), toFloorIntY(mBottom)))
            else -> it.left in toFloorIntX(mLeft)..toCeilIntX(mLeft) &&
                    it.top in toFloorIntY(mTop)..toCeilIntY(mTop) &&
                    it.right in toFloorIntX(mRight)..toCeilIntX(mRight) &&
                    it.bottom in toFloorIntY(mBottom)..toCeilIntY(mBottom)
        }
    }

    override fun toString(): String {
        val name = when (mType) {
            TYPE_INSIDE -> "boundsInside"
            TYPE_CONTAINS -> "boundsContains"
            else -> "bounds"
        }
        return "$name(${formatDouble(mLeft)}, ${formatDouble(mTop)}, ${formatDouble(mRight)}, ${formatDouble(mBottom)})"
    }

    companion object {

        const val TYPE_EQUALS = 0
        const val TYPE_INSIDE = 1
        const val TYPE_CONTAINS = 2

    }

}
