package org.autojs.autojs.core.ui.inflater.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.InflateException
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.attribute.ViewAttributes
import org.autojs.autojs6.R
import java.util.regex.Pattern
import kotlin.math.roundToInt

/**
 * Created by nick on 8/10/15.
 * Taken from http://stackoverflow.com/questions/8343971/how-to-parse-a-dimension-string-and-convert-it-to-a-dimension-value
 * Transformed by SuperMonster003 on May 21, 2023.
 */
object Dimensions {

    private val UNITS = ValueMapper<Int>("unit")
        .map("px", TypedValue.COMPLEX_UNIT_PX)
        .map("dip", TypedValue.COMPLEX_UNIT_DIP)
        .map("dp", TypedValue.COMPLEX_UNIT_DIP)
        .map("sp", TypedValue.COMPLEX_UNIT_SP)
        .map("pt", TypedValue.COMPLEX_UNIT_PT)
        .map("in", TypedValue.COMPLEX_UNIT_IN)
        .map("mm", TypedValue.COMPLEX_UNIT_MM)

    private val DIMENSION_PATTERN = Pattern.compile("([+-]?[0-9.]+)([a-zA-Z]*)")

    fun parseToPixel(dimension: String, view: View, parent: ViewGroup?, horizontal: Boolean): Int {
        if (dimension.endsWith("%") && parent != null) {
            val pct = dimension.substring(0, dimension.length - 1).toFloat() / 100.0f
            return (pct * if (horizontal) parent.measuredWidth else parent.measuredHeight).toInt()
        }
        return parseToIntPixel(dimension, view.context)
    }

    fun parseToPixel(dimension: String, view: View): Float {
        return parseToPixel(dimension, view.context)
    }

    fun parseToPixel(view: View, dimension: String): Float {
        return parseToPixel(dimension, view.context)
    }

    @SuppressLint("DiscouragedApi")
    fun parseToPixel(dimension: String, context: Context): Float {
        if (dimension.startsWith("?")) {
            val attr = intArrayOf(
                context.resources.getIdentifier(
                    dimension.substring(1), "attr",
                    context.packageName,
                ),
            )
            val ta = context.obtainStyledAttributes(attr)
            val d = ta.getDimension(0, 0f)
            ta.recycle()
            return d
        }
        val m = DIMENSION_PATTERN.matcher(dimension)
        if (!m.matches()) {
            throw InflateException(context.getString(R.string.error_illegal_argument, "dimension", dimension))
        }
        val unit = if (m.groupCount() == 2) UNITS[m.group(2), TypedValue.COMPLEX_UNIT_DIP] else TypedValue.COMPLEX_UNIT_DIP
        runCatching {
            return m.group(1)?.toFloat()?.let {
                TypedValue.applyDimension(unit, it, context.resources.displayMetrics)
            } ?: 0f
        }.getOrElse { e -> throw RuntimeException(e) }
    }

    fun parseToIntPixel(value: String, view: View): Int {
        return parseToPixel(value, view).roundToInt()
    }

    fun parseToIntPixel(value: String, context: Context): Int {
        return parseToPixel(value, context).roundToInt()
    }

    fun parseToIntPixelArray(view: View, value: String): IntArray {
        val split: Array<String> = ViewAttributes.parseAttrValue(value).toTypedArray()
        val pixels = IntArray(4)
        for (i in split.indices) {
            pixels[i] = parseToIntPixel(split[i], view)
        }
        if (split.size == 1) {
            pixels[3] = pixels[0]
            pixels[2] = pixels[3]
            pixels[1] = pixels[2]
        } else if (split.size == 2) {
            pixels[2] = pixels[0]
            pixels[3] = pixels[1]
        }
        return pixels
    }
}