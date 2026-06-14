package org.autojs.autojs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

object ContextUtils {

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun Context.requireActivity(): Activity {
        return this.findActivity() ?: throw IllegalArgumentException("Context cannot be cast to Activity")
    }

    @JvmStatic
    fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @ColorInt
    fun Context.getColorInNightMode(
        @ColorRes colorRes: Int,
        nightMode: Int,
    ): Int {
        require(
            nightMode == Configuration.UI_MODE_NIGHT_YES ||
                    nightMode == Configuration.UI_MODE_NIGHT_NO
        ) {
            "nightMode must be UI_MODE_NIGHT_YES or UI_MODE_NIGHT_NO"
        }

        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }

        val themedContext = createConfigurationContext(config)
        return ContextCompat.getColor(themedContext, colorRes)
    }

    @ColorInt
    fun Context.getLightModeColor(@ColorRes colorRes: Int): Int =
        getColorInNightMode(colorRes, Configuration.UI_MODE_NIGHT_NO)

    @ColorInt
    fun Context.getDarkModeColor(@ColorRes colorRes: Int): Int =
        getColorInNightMode(colorRes, Configuration.UI_MODE_NIGHT_YES)

    @ColorInt
    fun Context.getColorForMode(
        @ColorRes colorRes: Int,
        darkMode: Boolean,
    ): Int {
        return getColorInNightMode(
            colorRes,
            if (darkMode) Configuration.UI_MODE_NIGHT_YES
            else Configuration.UI_MODE_NIGHT_NO
        )
    }

    fun Context.getColorsForMode(
        darkMode: Boolean,
        @ColorRes colorResArray: Array<Int>,
    ): Array<Int> {
        return colorResArray.map { getColorForMode(it, darkMode) }.toTypedArray()
    }
}