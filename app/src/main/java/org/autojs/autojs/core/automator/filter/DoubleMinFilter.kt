package org.autojs.autojs.core.automator.filter

import androidx.core.util.rangeTo
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.util.StringUtils.formatDouble

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class DoubleMinFilter(private val mDoubleMinProperty: DoubleMinProperty, private val mMin: Double? = null) : Filter {

    interface DoubleMinProperty {
        operator fun get(o: UiObject): Double
    }

    override fun filter(node: UiObject) = mDoubleMinProperty[node] >= (mMin ?: DEFAULT)

    override fun toString() = "$mDoubleMinProperty(${mMin?.let { formatDouble(it) } ?: ""})"

    companion object {

        const val DEFAULT = 0.948

        val SCREEN_COVERAGE = object : DoubleMinProperty {
            override fun get(o: UiObject): Double {
                val w = ScreenMetrics.deviceScreenWidth
                val h = ScreenMetrics.deviceScreenHeight
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
