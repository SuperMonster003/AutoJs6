package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toRoundIntX
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.toRoundIntY
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class MetricsFilter(private val mMetricsProperty: MetricsProperty, private val mValue: Double) : Filter {

    interface MetricsProperty {
        operator fun get(o: UiObject, value: Double): Boolean
    }

    override fun filter(node: UiObject) = mMetricsProperty[node, mValue]

    override fun toString() = "$mMetricsProperty(${formatDouble(mValue)})"

    companion object {

        val LEFT = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.left() == toRoundIntX(value)
            override fun toString() = "left"
        }

        val TOP = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.top() == toRoundIntY(value)
            override fun toString() = "top"
        }

        val RIGHT = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.right() == toRoundIntX(value)
            override fun toString() = "right"
        }

        val BOTTOM = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.bottom() == toRoundIntY(value)
            override fun toString() = "bottom"
        }

        val WIDTH = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.width() == toRoundIntX(value)
            override fun toString() = "width"
        }

        val HEIGHT = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.height() == toRoundIntY(value)
            override fun toString() = "height"
        }

        val CENTER_X = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.centerX() == toRoundIntX(value)
            override fun toString() = "centerX"
        }

        val CENTER_Y = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.centerY() == toRoundIntY(value)
            override fun toString() = "centerY"
        }

        val BOUNDS_LEFT = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsLeft() == toRoundIntX(value)
            override fun toString() = "boundsLeft"
        }

        val BOUNDS_TOP = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsTop() == toRoundIntY(value)
            override fun toString() = "boundsTop"
        }

        val BOUNDS_RIGHT = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsRight() == toRoundIntX(value)
            override fun toString() = "boundsRight"
        }

        val BOUNDS_BOTTOM = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsBottom() == toRoundIntY(value)
            override fun toString() = "boundsBottom"
        }

        val BOUNDS_WIDTH = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsWidth() == toRoundIntX(value)
            override fun toString() = "boundsWidth"
        }

        val BOUNDS_HEIGHT = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsHeight() == toRoundIntY(value)
            override fun toString() = "boundsHeight"
        }

        val BOUNDS_CENTER_X = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsCenterX() == toRoundIntX(value)
            override fun toString() = "boundsCenterX"
        }

        val BOUNDS_CENTER_Y = object : MetricsProperty {
            override fun get(o: UiObject, value: Double) = o.boundsCenterY() == toRoundIntY(value)
            override fun toString() = "boundsCenterY"
        }

    }

}
