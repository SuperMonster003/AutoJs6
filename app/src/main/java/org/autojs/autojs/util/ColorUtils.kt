@file:Suppress("unused")

package org.autojs.autojs.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs6.R
import kotlin.math.pow
import kotlin.math.roundToInt

object ColorUtils {

    private val globalAppContext = GlobalAppContext.get()

    @JvmField
    val MATERIAL_COLOR_ITEMS: List<Pair</* colorRes */ Int, /* colorNameRes */ Int>> = listOf(

        /* reds */

        Pair(R.color.md_red_50, R.string.md_red_50),
        Pair(R.color.md_red_100, R.string.md_red_100),
        Pair(R.color.md_red_200, R.string.md_red_200),
        Pair(R.color.md_red_300, R.string.md_red_300),
        Pair(R.color.md_red_400, R.string.md_red_400),
        Pair(R.color.md_red_500, R.string.md_red_500),
        Pair(R.color.md_red_600, R.string.md_red_600),
        Pair(R.color.md_red_700, R.string.md_red_700),
        Pair(R.color.md_red_800, R.string.md_red_800),
        Pair(R.color.md_red_900, R.string.md_red_900),
        Pair(R.color.md_red_a100, R.string.md_red_a100),
        Pair(R.color.md_red_a200, R.string.md_red_a200),
        Pair(R.color.md_red_a400, R.string.md_red_a400),
        Pair(R.color.md_red_a700, R.string.md_red_a700),

        /* pinks */

        Pair(R.color.md_pink_50, R.string.md_pink_50),
        Pair(R.color.md_pink_100, R.string.md_pink_100),
        Pair(R.color.md_pink_200, R.string.md_pink_200),
        Pair(R.color.md_pink_300, R.string.md_pink_300),
        Pair(R.color.md_pink_400, R.string.md_pink_400),
        Pair(R.color.md_pink_500, R.string.md_pink_500),
        Pair(R.color.md_pink_600, R.string.md_pink_600),
        Pair(R.color.md_pink_700, R.string.md_pink_700),
        Pair(R.color.md_pink_800, R.string.md_pink_800),
        Pair(R.color.md_pink_900, R.string.md_pink_900),
        Pair(R.color.md_pink_a100, R.string.md_pink_a100),
        Pair(R.color.md_pink_a200, R.string.md_pink_a200),
        Pair(R.color.md_pink_a400, R.string.md_pink_a400),
        Pair(R.color.md_pink_a700, R.string.md_pink_a700),

        /* purples */

        Pair(R.color.md_purple_50, R.string.md_purple_50),
        Pair(R.color.md_purple_100, R.string.md_purple_100),
        Pair(R.color.md_purple_200, R.string.md_purple_200),
        Pair(R.color.md_purple_300, R.string.md_purple_300),
        Pair(R.color.md_purple_400, R.string.md_purple_400),
        Pair(R.color.md_purple_500, R.string.md_purple_500),
        Pair(R.color.md_purple_600, R.string.md_purple_600),
        Pair(R.color.md_purple_700, R.string.md_purple_700),
        Pair(R.color.md_purple_800, R.string.md_purple_800),
        Pair(R.color.md_purple_900, R.string.md_purple_900),
        Pair(R.color.md_purple_a100, R.string.md_purple_a100),
        Pair(R.color.md_purple_a200, R.string.md_purple_a200),
        Pair(R.color.md_purple_a400, R.string.md_purple_a400),
        Pair(R.color.md_purple_a700, R.string.md_purple_a700),

        /* deep purples */

        Pair(R.color.md_deep_purple_50, R.string.md_deep_purple_50),
        Pair(R.color.md_deep_purple_100, R.string.md_deep_purple_100),
        Pair(R.color.md_deep_purple_200, R.string.md_deep_purple_200),
        Pair(R.color.md_deep_purple_300, R.string.md_deep_purple_300),
        Pair(R.color.md_deep_purple_400, R.string.md_deep_purple_400),
        Pair(R.color.md_deep_purple_500, R.string.md_deep_purple_500),
        Pair(R.color.md_deep_purple_600, R.string.md_deep_purple_600),
        Pair(R.color.md_deep_purple_700, R.string.md_deep_purple_700),
        Pair(R.color.md_deep_purple_800, R.string.md_deep_purple_800),
        Pair(R.color.md_deep_purple_900, R.string.md_deep_purple_900),
        Pair(R.color.md_deep_purple_a100, R.string.md_deep_purple_a100),
        Pair(R.color.md_deep_purple_a200, R.string.md_deep_purple_a200),
        Pair(R.color.md_deep_purple_a400, R.string.md_deep_purple_a400),
        Pair(R.color.md_deep_purple_a700, R.string.md_deep_purple_a700),

        /* indigo */

        Pair(R.color.md_indigo_50, R.string.md_indigo_50),
        Pair(R.color.md_indigo_100, R.string.md_indigo_100),
        Pair(R.color.md_indigo_200, R.string.md_indigo_200),
        Pair(R.color.md_indigo_300, R.string.md_indigo_300),
        Pair(R.color.md_indigo_400, R.string.md_indigo_400),
        Pair(R.color.md_indigo_500, R.string.md_indigo_500),
        Pair(R.color.md_indigo_600, R.string.md_indigo_600),
        Pair(R.color.md_indigo_700, R.string.md_indigo_700),
        Pair(R.color.md_indigo_800, R.string.md_indigo_800),
        Pair(R.color.md_indigo_900, R.string.md_indigo_900),
        Pair(R.color.md_indigo_a100, R.string.md_indigo_a100),
        Pair(R.color.md_indigo_a200, R.string.md_indigo_a200),
        Pair(R.color.md_indigo_a400, R.string.md_indigo_a400),
        Pair(R.color.md_indigo_a700, R.string.md_indigo_a700),

        /* blue */

        Pair(R.color.md_blue_50, R.string.md_blue_50),
        Pair(R.color.md_blue_100, R.string.md_blue_100),
        Pair(R.color.md_blue_200, R.string.md_blue_200),
        Pair(R.color.md_blue_300, R.string.md_blue_300),
        Pair(R.color.md_blue_400, R.string.md_blue_400),
        Pair(R.color.md_blue_500, R.string.md_blue_500),
        Pair(R.color.md_blue_600, R.string.md_blue_600),
        Pair(R.color.md_blue_700, R.string.md_blue_700),
        Pair(R.color.md_blue_800, R.string.md_blue_800),
        Pair(R.color.md_blue_900, R.string.md_blue_900),
        Pair(R.color.md_blue_a100, R.string.md_blue_a100),
        Pair(R.color.md_blue_a200, R.string.md_blue_a200),
        Pair(R.color.md_blue_a400, R.string.md_blue_a400),
        Pair(R.color.md_blue_a700, R.string.md_blue_a700),

        /* light blue */

        Pair(R.color.md_light_blue_50, R.string.md_light_blue_50),
        Pair(R.color.md_light_blue_100, R.string.md_light_blue_100),
        Pair(R.color.md_light_blue_200, R.string.md_light_blue_200),
        Pair(R.color.md_light_blue_300, R.string.md_light_blue_300),
        Pair(R.color.md_light_blue_400, R.string.md_light_blue_400),
        Pair(R.color.md_light_blue_500, R.string.md_light_blue_500),
        Pair(R.color.md_light_blue_600, R.string.md_light_blue_600),
        Pair(R.color.md_light_blue_700, R.string.md_light_blue_700),
        Pair(R.color.md_light_blue_800, R.string.md_light_blue_800),
        Pair(R.color.md_light_blue_900, R.string.md_light_blue_900),
        Pair(R.color.md_light_blue_a100, R.string.md_light_blue_a100),
        Pair(R.color.md_light_blue_a200, R.string.md_light_blue_a200),
        Pair(R.color.md_light_blue_a400, R.string.md_light_blue_a400),
        Pair(R.color.md_light_blue_a700, R.string.md_light_blue_a700),

        /* cyan */

        Pair(R.color.md_cyan_50, R.string.md_cyan_50),
        Pair(R.color.md_cyan_100, R.string.md_cyan_100),
        Pair(R.color.md_cyan_200, R.string.md_cyan_200),
        Pair(R.color.md_cyan_300, R.string.md_cyan_300),
        Pair(R.color.md_cyan_400, R.string.md_cyan_400),
        Pair(R.color.md_cyan_500, R.string.md_cyan_500),
        Pair(R.color.md_cyan_600, R.string.md_cyan_600),
        Pair(R.color.md_cyan_700, R.string.md_cyan_700),
        Pair(R.color.md_cyan_800, R.string.md_cyan_800),
        Pair(R.color.md_cyan_900, R.string.md_cyan_900),
        Pair(R.color.md_cyan_a100, R.string.md_cyan_a100),
        Pair(R.color.md_cyan_a200, R.string.md_cyan_a200),
        Pair(R.color.md_cyan_a400, R.string.md_cyan_a400),
        Pair(R.color.md_cyan_a700, R.string.md_cyan_a700),

        /* teal */

        Pair(R.color.md_teal_50, R.string.md_teal_50),
        Pair(R.color.md_teal_100, R.string.md_teal_100),
        Pair(R.color.md_teal_200, R.string.md_teal_200),
        Pair(R.color.md_teal_300, R.string.md_teal_300),
        Pair(R.color.md_teal_400, R.string.md_teal_400),
        Pair(R.color.md_teal_500, R.string.md_teal_500),
        Pair(R.color.md_teal_600, R.string.md_teal_600),
        Pair(R.color.md_teal_700, R.string.md_teal_700),
        Pair(R.color.md_teal_800, R.string.md_teal_800),
        Pair(R.color.md_teal_900, R.string.md_teal_900),
        Pair(R.color.md_teal_a100, R.string.md_teal_a100),
        Pair(R.color.md_teal_a200, R.string.md_teal_a200),
        Pair(R.color.md_teal_a400, R.string.md_teal_a400),
        Pair(R.color.md_teal_a700, R.string.md_teal_a700),

        /* green */

        Pair(R.color.md_green_50, R.string.md_green_50),
        Pair(R.color.md_green_100, R.string.md_green_100),
        Pair(R.color.md_green_200, R.string.md_green_200),
        Pair(R.color.md_green_300, R.string.md_green_300),
        Pair(R.color.md_green_400, R.string.md_green_400),
        Pair(R.color.md_green_500, R.string.md_green_500),
        Pair(R.color.md_green_600, R.string.md_green_600),
        Pair(R.color.md_green_700, R.string.md_green_700),
        Pair(R.color.md_green_800, R.string.md_green_800),
        Pair(R.color.md_green_900, R.string.md_green_900),
        Pair(R.color.md_green_a100, R.string.md_green_a100),
        Pair(R.color.md_green_a200, R.string.md_green_a200),
        Pair(R.color.md_green_a400, R.string.md_green_a400),
        Pair(R.color.md_green_a700, R.string.md_green_a700),

        /* light green */

        Pair(R.color.md_light_green_50, R.string.md_light_green_50),
        Pair(R.color.md_light_green_100, R.string.md_light_green_100),
        Pair(R.color.md_light_green_200, R.string.md_light_green_200),
        Pair(R.color.md_light_green_300, R.string.md_light_green_300),
        Pair(R.color.md_light_green_400, R.string.md_light_green_400),
        Pair(R.color.md_light_green_500, R.string.md_light_green_500),
        Pair(R.color.md_light_green_600, R.string.md_light_green_600),
        Pair(R.color.md_light_green_700, R.string.md_light_green_700),
        Pair(R.color.md_light_green_800, R.string.md_light_green_800),
        Pair(R.color.md_light_green_900, R.string.md_light_green_900),
        Pair(R.color.md_light_green_a100, R.string.md_light_green_a100),
        Pair(R.color.md_light_green_a200, R.string.md_light_green_a200),
        Pair(R.color.md_light_green_a400, R.string.md_light_green_a400),
        Pair(R.color.md_light_green_a700, R.string.md_light_green_a700),

        /* lime */

        Pair(R.color.md_lime_50, R.string.md_lime_50),
        Pair(R.color.md_lime_100, R.string.md_lime_100),
        Pair(R.color.md_lime_200, R.string.md_lime_200),
        Pair(R.color.md_lime_300, R.string.md_lime_300),
        Pair(R.color.md_lime_400, R.string.md_lime_400),
        Pair(R.color.md_lime_500, R.string.md_lime_500),
        Pair(R.color.md_lime_600, R.string.md_lime_600),
        Pair(R.color.md_lime_700, R.string.md_lime_700),
        Pair(R.color.md_lime_800, R.string.md_lime_800),
        Pair(R.color.md_lime_900, R.string.md_lime_900),
        Pair(R.color.md_lime_a100, R.string.md_lime_a100),
        Pair(R.color.md_lime_a200, R.string.md_lime_a200),
        Pair(R.color.md_lime_a400, R.string.md_lime_a400),
        Pair(R.color.md_lime_a700, R.string.md_lime_a700),

        /* yellow */

        Pair(R.color.md_yellow_50, R.string.md_yellow_50),
        Pair(R.color.md_yellow_100, R.string.md_yellow_100),
        Pair(R.color.md_yellow_200, R.string.md_yellow_200),
        Pair(R.color.md_yellow_300, R.string.md_yellow_300),
        Pair(R.color.md_yellow_400, R.string.md_yellow_400),
        Pair(R.color.md_yellow_500, R.string.md_yellow_500),
        Pair(R.color.md_yellow_600, R.string.md_yellow_600),
        Pair(R.color.md_yellow_700, R.string.md_yellow_700),
        Pair(R.color.md_yellow_800, R.string.md_yellow_800),
        Pair(R.color.md_yellow_900, R.string.md_yellow_900),
        Pair(R.color.md_yellow_a100, R.string.md_yellow_a100),
        Pair(R.color.md_yellow_a200, R.string.md_yellow_a200),
        Pair(R.color.md_yellow_a400, R.string.md_yellow_a400),
        Pair(R.color.md_yellow_a700, R.string.md_yellow_a700),

        /* amber */

        Pair(R.color.md_amber_50, R.string.md_amber_50),
        Pair(R.color.md_amber_100, R.string.md_amber_100),
        Pair(R.color.md_amber_200, R.string.md_amber_200),
        Pair(R.color.md_amber_300, R.string.md_amber_300),
        Pair(R.color.md_amber_400, R.string.md_amber_400),
        Pair(R.color.md_amber_500, R.string.md_amber_500),
        Pair(R.color.md_amber_600, R.string.md_amber_600),
        Pair(R.color.md_amber_700, R.string.md_amber_700),
        Pair(R.color.md_amber_800, R.string.md_amber_800),
        Pair(R.color.md_amber_900, R.string.md_amber_900),
        Pair(R.color.md_amber_a100, R.string.md_amber_a100),
        Pair(R.color.md_amber_a200, R.string.md_amber_a200),
        Pair(R.color.md_amber_a400, R.string.md_amber_a400),
        Pair(R.color.md_amber_a700, R.string.md_amber_a700),

        /* orange */

        Pair(R.color.md_orange_50, R.string.md_orange_50),
        Pair(R.color.md_orange_100, R.string.md_orange_100),
        Pair(R.color.md_orange_200, R.string.md_orange_200),
        Pair(R.color.md_orange_300, R.string.md_orange_300),
        Pair(R.color.md_orange_400, R.string.md_orange_400),
        Pair(R.color.md_orange_500, R.string.md_orange_500),
        Pair(R.color.md_orange_600, R.string.md_orange_600),
        Pair(R.color.md_orange_700, R.string.md_orange_700),
        Pair(R.color.md_orange_800, R.string.md_orange_800),
        Pair(R.color.md_orange_900, R.string.md_orange_900),
        Pair(R.color.md_orange_a100, R.string.md_orange_a100),
        Pair(R.color.md_orange_a200, R.string.md_orange_a200),
        Pair(R.color.md_orange_a400, R.string.md_orange_a400),
        Pair(R.color.md_orange_a700, R.string.md_orange_a700),

        /* deep orange */

        Pair(R.color.md_deep_orange_50, R.string.md_deep_orange_50),
        Pair(R.color.md_deep_orange_100, R.string.md_deep_orange_100),
        Pair(R.color.md_deep_orange_200, R.string.md_deep_orange_200),
        Pair(R.color.md_deep_orange_300, R.string.md_deep_orange_300),
        Pair(R.color.md_deep_orange_400, R.string.md_deep_orange_400),
        Pair(R.color.md_deep_orange_500, R.string.md_deep_orange_500),
        Pair(R.color.md_deep_orange_600, R.string.md_deep_orange_600),
        Pair(R.color.md_deep_orange_700, R.string.md_deep_orange_700),
        Pair(R.color.md_deep_orange_800, R.string.md_deep_orange_800),
        Pair(R.color.md_deep_orange_900, R.string.md_deep_orange_900),
        Pair(R.color.md_deep_orange_a100, R.string.md_deep_orange_a100),
        Pair(R.color.md_deep_orange_a200, R.string.md_deep_orange_a200),
        Pair(R.color.md_deep_orange_a400, R.string.md_deep_orange_a400),
        Pair(R.color.md_deep_orange_a700, R.string.md_deep_orange_a700),

        /* brown */

        Pair(R.color.md_brown_50, R.string.md_brown_50),
        Pair(R.color.md_brown_100, R.string.md_brown_100),
        Pair(R.color.md_brown_200, R.string.md_brown_200),
        Pair(R.color.md_brown_300, R.string.md_brown_300),
        Pair(R.color.md_brown_400, R.string.md_brown_400),
        Pair(R.color.md_brown_500, R.string.md_brown_500),
        Pair(R.color.md_brown_600, R.string.md_brown_600),
        Pair(R.color.md_brown_700, R.string.md_brown_700),
        Pair(R.color.md_brown_800, R.string.md_brown_800),
        Pair(R.color.md_brown_900, R.string.md_brown_900),

        /* grey */

        Pair(R.color.md_grey_50, R.string.md_grey_50),
        Pair(R.color.md_grey_100, R.string.md_grey_100),
        Pair(R.color.md_grey_200, R.string.md_grey_200),
        Pair(R.color.md_grey_300, R.string.md_grey_300),
        Pair(R.color.md_grey_400, R.string.md_grey_400),
        Pair(R.color.md_grey_500, R.string.md_grey_500),
        Pair(R.color.md_grey_600, R.string.md_grey_600),
        Pair(R.color.md_grey_700, R.string.md_grey_700),
        Pair(R.color.md_grey_800, R.string.md_grey_800),
        Pair(R.color.md_grey_900, R.string.md_grey_900),
        Pair(R.color.md_black_1000, R.string.md_black_1000),
        Pair(R.color.md_white_1000, R.string.md_white_1000),

        /* blue grey */

        Pair(R.color.md_blue_grey_50, R.string.md_blue_grey_50),
        Pair(R.color.md_blue_grey_100, R.string.md_blue_grey_100),
        Pair(R.color.md_blue_grey_200, R.string.md_blue_grey_200),
        Pair(R.color.md_blue_grey_300, R.string.md_blue_grey_300),
        Pair(R.color.md_blue_grey_400, R.string.md_blue_grey_400),
        Pair(R.color.md_blue_grey_500, R.string.md_blue_grey_500),
        Pair(R.color.md_blue_grey_600, R.string.md_blue_grey_600),
        Pair(R.color.md_blue_grey_700, R.string.md_blue_grey_700),
        Pair(R.color.md_blue_grey_800, R.string.md_blue_grey_800),
        Pair(R.color.md_blue_grey_900, R.string.md_blue_grey_900),
    )

    @JvmStatic
    fun toString(color: Int): String {

        // @Comment by SuperMonster003 on Dec 23, 2022.
        //  ! When alpha is 0, code below returns #RRGGBB without alpha,
        //  ! which doesn't behave as expected.

        // val c = StringBuilder(Integer.toHexString(color))
        // while (c.length < 6) {
        //     c.insert(0, "0")
        // }

        val c = String.format("%08X", 0xFFFFFFFF and color.toLong())
        if (c.length == 8 && c.startsWith("FF", true)) {
            return "#${c.substring(2)}"
        }
        return "#$c"
    }

    @ColorInt
    fun fromResources(@ColorRes colorRes: Int) = globalAppContext.getColor(colorRes)

    @JvmStatic
    @JvmOverloads
    fun toUnit8(component: Double, takeNumOneAsPercent: Boolean = false) = when {
        component < 1 -> (component * 255).roundToInt()
        else -> when {
            component == 1.0 && takeNumOneAsPercent -> 255
            else -> 255.coerceAtMost(component.roundToInt())
        }
    }

    @JvmStatic
    fun toInt(num: Long) = toInt(toHex(num))

    @JvmStatic
    fun toInt(themeColor: ThemeColor) = toInt(toHex(themeColor))

    @JvmStatic
    fun toInt(colorString: String): Int {
        try {
            return Color.parseColor(toHex(colorString))
        } catch (e: Exception) {
            AutoJs.instance.runtime.console.error("Passed color: $colorString")
            throw Exception(e.message + "\n" + e.stackTrace)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun toHex(num: Long, alpha: String = "auto"): String {
        return toHex(toString(toJavaIntegerRange(num)), alpha)
    }

    @JvmStatic
    fun toHex(num: Long, hasAlpha: Boolean): String {
        return toHex(toString(toJavaIntegerRange(num)), hasAlpha)
    }

    @JvmStatic
    fun toHex(num: Long, resultLength: Int): String {
        return toHex(toString(toJavaIntegerRange(num)), resultLength)
    }

    @JvmStatic
    @JvmOverloads
    fun toHex(themeColor: ThemeColor, alpha: String = "auto"): String {
        return toHex(themeColor.colorPrimary.toLong(), alpha)
    }

    @JvmStatic
    fun toHex(themeColor: ThemeColor, hasAlpha: Boolean): String {
        return toHex(themeColor.colorPrimary.toLong(), hasAlpha)
    }

    @JvmStatic
    fun toHex(themeColor: ThemeColor, resultLength: Int): String {
        return toHex(themeColor.colorPrimary.toLong(), resultLength)
    }

    @JvmStatic
    @JvmOverloads
    fun toHex(colorString: String, alpha: String = "auto"): String {
        var color = colorString
        if (color.startsWith('#')) {
            if (color.length == 4) {
                color = color.replace(Regex("(#)(\\w)(\\w)(\\w)"), "$1$2$2$3$3$4$4")
            }
        } else {
            if (color.matches(Regex("[+-]?\\d+"))) {
                return toHex(color.toLong(), alpha)
            }
            val colorByName = ColorTable.getColorByName(color, true)
            if (colorByName !== null) {
                return toHex(colorByName.toLong(), alpha)
            }
        }
        if (!Regex("#[A-F\\d]{3}([A-F\\d]{3}([A-F\\d]{2})?)?", RegexOption.IGNORE_CASE).matches(color)) {
            throw Exception("Invalid color string format: $color")
        }
        if (alpha == "keep") {
            if (color.length == 7) {
                color = "#FF${color.substring(1)}"
            }
            return color.uppercase()
        }
        if (alpha == "none") {
            return "#${color.substring(color.length - 6)}".uppercase()
        }
        if (alpha == "auto") {
            return if (Regex("#FF([A-F\\d]){6}", RegexOption.IGNORE_CASE).matches(color)) {
                "#${color.substring(3)}".uppercase()
            } else {
                color.uppercase()
            }
        }
        throw Exception("Unknown alpha \"$alpha\" for converting a color into ColorHex")
    }

    @JvmStatic
    fun toHex(colorString: String, hasAlpha: Boolean): String {
        return if (hasAlpha) toHex(colorString, 8) else toHex(colorString, 6)
    }

    @JvmStatic
    fun toHex(colorString: String, resultLength: Int): String {
        if (resultLength == 3) {
            if (!Regex("#(?:([A-F\\d]){2})?([A-F\\d])\\2([A-F\\d])\\3([A-F\\d])\\4", RegexOption.IGNORE_CASE).matches(colorString)) {
                throw Exception("Can't convert color $colorString to #RGB with unexpected color format.")
            }
            val r = colorString.substring(colorString.length - 6, colorString.length - 5)
            val g = colorString.substring(colorString.length - 4, colorString.length - 3)
            val b = colorString.substring(colorString.length - 2, colorString.length - 1)
            return "#$r$g$b"
        }
        if (resultLength == 6) {
            return toHex(colorString, "none")
        }
        if (resultLength == 8) {
            return toHex(colorString, "keep")
        }
        throw Exception("Unknown length \"$resultLength\" for converting a color into ColorHex")
    }

    @JvmStatic
    fun toFullHex(num: Long) = toHex(num, 8)

    @JvmStatic
    fun toFullHex(themeColor: ThemeColor) = toHex(themeColor, 8)

    @JvmStatic
    fun toFullHex(colorString: String) = toHex(colorString, 8)

    @JvmStatic
    fun parse(view: View, color: String) = parse(view.context, color)

    @Suppress("DEPRECATION")
    @SuppressLint("DiscouragedApi")
    @JvmStatic
    fun parse(context: Context, color: String): Int {
        val resources = context.resources
        if (color.startsWith("@color/")) {
            return resources.getColor(resources.getIdentifier(color.substring("@color/".length), "color", context.packageName))
        }
        return if (color.startsWith("@android:color/")) {
            Color.parseColor(color.substring("@android:color/".length))
        } else toInt(color)
    }

    private fun toJavaIntegerRange(x: Long): Int {
        val t = 2f.pow(32).toLong()
        val min = (-2f).pow(31).toLong()
        val max = 2f.pow(31 - 1).toLong()
        var tmp = x
        while (tmp < min) tmp += t
        while (tmp > max) tmp -= t
        return tmp.toInt()
    }

    fun toColorStateList(color: String): ColorStateList {
        return ColorStateList.valueOf(toInt(color))
    }

    fun toColorStateList(view: View, color: String): ColorStateList {
        return ColorStateList.valueOf(parse(view, color))
    }

}