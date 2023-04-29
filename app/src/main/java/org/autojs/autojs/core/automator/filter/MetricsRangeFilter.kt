package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.util.DisplayUtils.toRoundIntX
import org.autojs.autojs.util.DisplayUtils.toRoundIntY
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class MetricsRangeFilter(private val mIntRangeProperty: IntRangeProperty, private val mMin: Double, private val mMax: Double) : Filter {

    interface IntRangeProperty {
        operator fun get(o: UiObject, min: Double, max: Double): Boolean
    }

    override fun filter(node: UiObject) = mIntRangeProperty[node, mMin, mMax]

    override fun toString() = "$mIntRangeProperty(${formatDouble(mMin)}, ${formatDouble(mMax)})"

    companion object {

        val LEFT = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.left() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "left"
        }

        val TOP = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.top() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "top"
        }

        val RIGHT = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.right() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "right"
        }

        val BOTTOM = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.bottom() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "bottom"
        }

        val WIDTH = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.width() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "width"
        }

        val HEIGHT = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.height() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "height"
        }

        val CENTER_X = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.centerX() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "centerX"
        }

        val CENTER_Y = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.centerY() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "centerY"
        }

        val BOUNDS_LEFT = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsLeft() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "boundsLeft"
        }

        val BOUNDS_TOP = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsTop() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "boundsTop"
        }

        val BOUNDS_RIGHT = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsRight() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "boundsRight"
        }

        val BOUNDS_BOTTOM = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsBottom() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "boundsBottom"
        }

        val BOUNDS_WIDTH = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsWidth() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "boundsWidth"
        }

        val BOUNDS_HEIGHT = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsHeight() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "boundsHeight"
        }

        val BOUNDS_CENTER_X = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsCenterX() in toRoundIntX(min)..toRoundIntX(max)
            override fun toString() = "boundsCenterX"
        }

        val BOUNDS_CENTER_Y = object : IntRangeProperty {
            override fun get(o: UiObject, min: Double, max: Double) = o.boundsCenterY() in toRoundIntY(min)..toRoundIntY(max)
            override fun toString() = "boundsCenterY"
        }

    }

}
