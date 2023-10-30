package org.autojs.autojs.util

import android.os.Build
import android.util.DisplayMetrics
import android.util.DisplayMetrics.DENSITY_DEFAULT
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.util.TypedValue.COMPLEX_UNIT_SP
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.runtime.api.ScreenMetrics
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

@Suppress("unused")
object DisplayUtils {

    @JvmStatic
    val displayMetrics: DisplayMetrics
        get() = ScreenMetrics.resources?.displayMetrics ?: GlobalAppContext.get().resources.displayMetrics

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @return A float value to represent px equivalent to dp depending on device density
     */
    @JvmStatic
    fun dpToPx(dp: Float) = TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp, displayMetrics)

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @return A float value to represent dp equivalent to px value
     */
    @JvmStatic
    fun pxToDp(px: Float): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return TypedValue.deriveDimension(COMPLEX_UNIT_DIP, px, displayMetrics)
        }
        return px / (displayMetrics.densityDpi.toFloat() / DENSITY_DEFAULT)
    }

    @JvmStatic
    fun pxToSp(px: Float): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return TypedValue.deriveDimension(COMPLEX_UNIT_SP, px, displayMetrics)
        }
        @Suppress("DEPRECATION")
        return px / displayMetrics.scaledDensity
    }

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