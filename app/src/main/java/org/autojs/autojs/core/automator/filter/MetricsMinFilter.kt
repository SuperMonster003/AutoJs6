package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.DisplayUtils.toRoundIntX
import org.autojs.autojs.util.DisplayUtils.toRoundIntY
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class MetricsMinFilter(private val mIntMinProperty: IntMinProperty, private val mMin: Double) : Filter {

    interface IntMinProperty {
        operator fun get(o: UiObject, min: Double): Boolean
    }

    override fun filter(node: UiObject) = mIntMinProperty[node, mMin]

    override fun toString() = "$mIntMinProperty(${formatDouble(mMin)})"

    companion object {

        val LEFT = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.left() >= toRoundIntX(min)
            override fun toString() = "minLeft"
        }

        val TOP = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.top() >= toRoundIntY(min)
            override fun toString() = "minTop"
        }

        val RIGHT = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.right() >= toRoundIntX(min)
            override fun toString() = "minRight"
        }

        val BOTTOM = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.bottom() >= toRoundIntY(min)
            override fun toString() = "minBottom"
        }

        val WIDTH = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.width() >= toRoundIntX(min)
            override fun toString() = "minWidth"
        }

        val HEIGHT = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.height() >= toRoundIntY(min)
            override fun toString() = "minHeight"
        }

        val CENTER_X = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.centerX() >= toRoundIntX(min)
            override fun toString() = "minCenterX"
        }

        val CENTER_Y = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.centerY() >= toRoundIntY(min)
            override fun toString() = "minCenterY"
        }

        val BOUNDS_LEFT = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsLeft() >= toRoundIntX(min)
            override fun toString() = "boundsMinLeft"
        }

        val BOUNDS_TOP = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsTop() >= toRoundIntY(min)
            override fun toString() = "boundsMinTop"
        }

        val BOUNDS_RIGHT = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsRight() >= toRoundIntX(min)
            override fun toString() = "boundsMinRight"
        }

        val BOUNDS_BOTTOM = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsBottom() >= toRoundIntY(min)
            override fun toString() = "boundsMinBottom"
        }

        val BOUNDS_WIDTH = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsWidth() >= toRoundIntX(min)
            override fun toString() = "boundsMinWidth"
        }

        val BOUNDS_HEIGHT = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsHeight() >= toRoundIntY(min)
            override fun toString() = "boundsMinHeight"
        }

        val BOUNDS_CENTER_X = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsCenterX() >= toRoundIntX(min)
            override fun toString() = "boundsMinCenterX"
        }

        val BOUNDS_CENTER_Y = object : IntMinProperty {
            override fun get(o: UiObject, min: Double) = o.boundsCenterY() >= toRoundIntY(min)
            override fun toString() = "boundsMinCenterY"
        }

    }

}
