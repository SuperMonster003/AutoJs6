package org.autojs.autojs.util

import android.content.Context
import android.util.DisplayMetrics.DENSITY_DEFAULT

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

}