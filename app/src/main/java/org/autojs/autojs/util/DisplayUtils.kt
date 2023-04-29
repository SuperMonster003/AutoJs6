package org.autojs.autojs.util

import android.content.Context
import android.util.DisplayMetrics.DENSITY_DEFAULT
import org.autojs.autojs.runtime.api.ScreenMetrics
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

object DisplayUtils {
    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param ctx Context to get resources and device specific display metrics
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @return A float value to represent px equivalent to dp depending on device density
     */
    @JvmStatic
    fun dpToPx(ctx: Context, dp: Float) = dp * (ctx.resources.displayMetrics.densityDpi.toFloat() / DENSITY_DEFAULT)

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param ctx Context to get resources and device specific display metrics
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @return A float value to represent dp equivalent to px value
     */
    @JvmStatic
    fun pxToDp(ctx: Context, px: Float) = px / (ctx.resources.displayMetrics.densityDpi.toFloat() / DENSITY_DEFAULT)

    @JvmStatic
    fun pxToSp(ctx: Context, px: Float) = px / ctx.resources.displayMetrics.scaledDensity

    @JvmOverloads
    @JvmStatic
    fun toRoundIntX(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenWidth
        -1.0 < value && value < 1.0 -> (value * ScreenMetrics.deviceScreenWidth).roundToInt()
        else -> value.roundToInt()
    }

    @JvmOverloads
    @JvmStatic
    fun toRoundDoubleX(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenWidth.toDouble()
        -1.0 < value && value < 1.0 -> round(value * ScreenMetrics.deviceScreenWidth)
        else -> value
    }

    @JvmOverloads
    @JvmStatic
    fun toRoundIntY(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenHeight
        -1.0 < value && value < 1.0 -> (value * ScreenMetrics.deviceScreenHeight).roundToInt()
        else -> value.roundToInt()
    }

    @JvmOverloads
    @JvmStatic
    fun toRoundDoubleY(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenHeight.toDouble()
        -1.0 < value && value < 1.0 -> round(value * ScreenMetrics.deviceScreenHeight)
        else -> value
    }

    @JvmOverloads
    @JvmStatic
    fun toFloorIntX(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenWidth
        -1.0 < value && value < 1.0 -> floor(value * ScreenMetrics.deviceScreenWidth).toInt()
        else -> floor(value).toInt()
    }

    @JvmOverloads
    @JvmStatic
    fun toFloorIntY(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenHeight
        -1.0 < value && value < 1.0 -> floor(value * ScreenMetrics.deviceScreenHeight).toInt()
        else -> floor(value).toInt()
    }

    @JvmOverloads
    @JvmStatic
    fun toCeilIntX(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenWidth
        -1.0 < value && value < 1.0 -> ceil(value * ScreenMetrics.deviceScreenWidth).toInt()
        else -> ceil(value).toInt()
    }

    @JvmOverloads
    @JvmStatic
    fun toCeilIntY(value: Double, enableMinusOneMetric: Boolean = true) = when {
        value == -1.0 && enableMinusOneMetric -> ScreenMetrics.deviceScreenHeight
        -1.0 < value && value < 1.0 -> ceil(value * ScreenMetrics.deviceScreenHeight).toInt()
        else -> ceil(value).toInt()
    }

}