package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toRoundIntX
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toRoundIntY
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class MetricsMaxFilter(private val mIntMaxProperty: IntMaxProperty, private val mMax: Double) : Filter {

    interface IntMaxProperty {
        operator fun get(o: UiObject, max: Double): Boolean
    }

    override fun filter(node: UiObject) = mIntMaxProperty[node, mMax]

    override fun toString() = "$mIntMaxProperty(${formatDouble(mMax)})"

    companion object {

        val LEFT = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.left() <= toRoundIntX(max)
            override fun toString() = "maxLeft"
        }

        val TOP = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.top() <= toRoundIntY(max)
            override fun toString() = "maxTop"
        }

        val RIGHT = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.right() <= toRoundIntX(max)
            override fun toString() = "maxRight"
        }

        val BOTTOM = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.bottom() <= toRoundIntY(max)
            override fun toString() = "maxBottom"
        }

        val WIDTH = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.width() <= toRoundIntX(max)
            override fun toString() = "maxWidth"
        }

        val HEIGHT = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.height() <= toRoundIntY(max)
            override fun toString() = "maxHeight"
        }

        val CENTER_X = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.centerX() <= toRoundIntX(max)
            override fun toString() = "maxCenterX"
        }

        val CENTER_Y = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.centerY() <= toRoundIntY(max)
            override fun toString() = "maxCenterY"
        }

        val BOUNDS_LEFT = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsLeft() <= toRoundIntX(max)
            override fun toString() = "boundsMaxLeft"
        }

        val BOUNDS_TOP = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsTop() <= toRoundIntY(max)
            override fun toString() = "boundsMaxTop"
        }

        val BOUNDS_RIGHT = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsRight() <= toRoundIntX(max)
            override fun toString() = "boundsMaxRight"
        }

        val BOUNDS_BOTTOM = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsBottom() <= toRoundIntY(max)
            override fun toString() = "boundsMaxBottom"
        }

        val BOUNDS_WIDTH = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsWidth() <= toRoundIntX(max)
            override fun toString() = "boundsMaxWidth"
        }

        val BOUNDS_HEIGHT = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsHeight() <= toRoundIntY(max)
            override fun toString() = "boundsMaxHeight"
        }

        val BOUNDS_CENTER_X = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsCenterX() <= toRoundIntX(max)
            override fun toString() = "boundsMaxCenterX"
        }

        val BOUNDS_CENTER_Y = object : IntMaxProperty {
            override fun get(o: UiObject, max: Double) = o.boundsCenterY() <= toRoundIntY(max)
            override fun toString() = "boundsMaxCenterY"
        }

    }

}
