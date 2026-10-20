package org.autojs.autojs.theme

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.rhino.extension.IterableExtensions.toHashCode

/**
 * Created by Stardust on Mar 5, 2017.
 * Modified by SuperMonster003 as of Mar 30, 2023.
 * Transformed by SuperMonster003 on Mar 30, 2023.
 */
class ThemeColor(@JvmField var colorPrimary: Int, @JvmField var colorPrimaryDark: Int, @JvmField var colorAccent: Int) {

    @JvmOverloads
    constructor(color: Int = 0) : this(color, color, color)

    @ScriptInterface
    fun getColorPrimary() = colorPrimary

    @ScriptInterface
    fun getColorAccent() = colorAccent

    @ScriptInterface
    fun getColorPrimaryDark() = colorPrimaryDark

    @JvmOverloads
    fun isLuminanceLight(backgroundColorMatters: Boolean = true) = ThemeColorManager.isLuminanceLight(backgroundColorMatters)

    @JvmOverloads
    fun isLuminanceDark(backgroundColorMatters: Boolean = true) = !isLuminanceLight(backgroundColorMatters)

    fun colorPrimary(colorPrimary: Int) = also { this.colorPrimary = colorPrimary }

    fun colorPrimaryDark(colorPrimaryDark: Int) = also { this.colorPrimaryDark = colorPrimaryDark }

    fun colorAccent(colorAccent: Int) = also { this.colorAccent = colorAccent }

    fun saveIn() {
        AppearanceKeys.THEME_COLOR_PRIMARY.set(colorPrimary)
        AppearanceKeys.THEME_COLOR_PRIMARY_DARK.set(colorPrimaryDark)
        AppearanceKeys.THEME_COLOR_ACCENT.set(colorAccent)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other is ThemeColor) {
            return colorPrimary == other.colorPrimary && colorPrimaryDark == other.colorPrimaryDark && colorAccent == other.colorAccent
        }
        return false
    }

    fun readFrom() = also {
        colorPrimary = AppearanceKeys.THEME_COLOR_PRIMARY.get()
        colorAccent = AppearanceKeys.THEME_COLOR_ACCENT.get()
        colorPrimaryDark = AppearanceKeys.THEME_COLOR_PRIMARY_DARK.get()
    }

    override fun hashCode(): Int {
        return listOf(colorPrimary, colorPrimaryDark, colorAccent).toHashCode()
    }

    companion object {

        fun fromPreferences(): ThemeColor? {
            return if (
                AppearanceKeys.THEME_COLOR_PRIMARY.isStored() &&
                AppearanceKeys.THEME_COLOR_PRIMARY_DARK.isStored() &&
                AppearanceKeys.THEME_COLOR_ACCENT.isStored()
            ) ThemeColor().readFrom() else null
        }

        @JvmOverloads
        fun fromColorRes(context: Context?, @ColorRes colorPrimaryRes: Int, @ColorRes colorPrimaryDarkRes: Int = colorPrimaryRes, @ColorRes colorAccentRes: Int = colorPrimaryRes): ThemeColor {
            return ThemeColor()
                .colorPrimary(ContextCompat.getColor(context!!, colorPrimaryRes))
                .colorPrimaryDark(ContextCompat.getColor(context, colorPrimaryDarkRes))
                .colorAccent(ContextCompat.getColor(context, colorAccentRes))
        }

    }

}