@file:Suppress("unused")

package org.autojs.autojs.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs6.R
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.text.RegexOption.IGNORE_CASE

object ColorUtils {

    @JvmField
    val MATERIAL_COLOR_ITEMS: List<Pair</* colorRes */ Int, /* colorNameRes */ Int>> = listOf(

        /* reds */

        R.color.md_red_50 to R.string.md_red_50,
        R.color.md_red_100 to R.string.md_red_100,
        R.color.md_red_200 to R.string.md_red_200,
        R.color.md_red_300 to R.string.md_red_300,
        R.color.md_red_400 to R.string.md_red_400,
        R.color.md_red_500 to R.string.md_red_500,
        R.color.md_red_600 to R.string.md_red_600,
        R.color.md_red_700 to R.string.md_red_700,
        R.color.md_red_800 to R.string.md_red_800,
        R.color.md_red_900 to R.string.md_red_900,
        R.color.md_red_a100 to R.string.md_red_a100,
        R.color.md_red_a200 to R.string.md_red_a200,
        R.color.md_red_a400 to R.string.md_red_a400,
        R.color.md_red_a700 to R.string.md_red_a700,

        /* pinks */

        R.color.md_pink_50 to R.string.md_pink_50,
        R.color.md_pink_100 to R.string.md_pink_100,
        R.color.md_pink_200 to R.string.md_pink_200,
        R.color.md_pink_300 to R.string.md_pink_300,
        R.color.md_pink_400 to R.string.md_pink_400,
        R.color.md_pink_500 to R.string.md_pink_500,
        R.color.md_pink_600 to R.string.md_pink_600,
        R.color.md_pink_700 to R.string.md_pink_700,
        R.color.md_pink_800 to R.string.md_pink_800,
        R.color.md_pink_900 to R.string.md_pink_900,
        R.color.md_pink_a100 to R.string.md_pink_a100,
        R.color.md_pink_a200 to R.string.md_pink_a200,
        R.color.md_pink_a400 to R.string.md_pink_a400,
        R.color.md_pink_a700 to R.string.md_pink_a700,

        /* purples */

        R.color.md_purple_50 to R.string.md_purple_50,
        R.color.md_purple_100 to R.string.md_purple_100,
        R.color.md_purple_200 to R.string.md_purple_200,
        R.color.md_purple_300 to R.string.md_purple_300,
        R.color.md_purple_400 to R.string.md_purple_400,
        R.color.md_purple_500 to R.string.md_purple_500,
        R.color.md_purple_600 to R.string.md_purple_600,
        R.color.md_purple_700 to R.string.md_purple_700,
        R.color.md_purple_800 to R.string.md_purple_800,
        R.color.md_purple_900 to R.string.md_purple_900,
        R.color.md_purple_a100 to R.string.md_purple_a100,
        R.color.md_purple_a200 to R.string.md_purple_a200,
        R.color.md_purple_a400 to R.string.md_purple_a400,
        R.color.md_purple_a700 to R.string.md_purple_a700,

        /* deep purples */

        R.color.md_deep_purple_50 to R.string.md_deep_purple_50,
        R.color.md_deep_purple_100 to R.string.md_deep_purple_100,
        R.color.md_deep_purple_200 to R.string.md_deep_purple_200,
        R.color.md_deep_purple_300 to R.string.md_deep_purple_300,
        R.color.md_deep_purple_400 to R.string.md_deep_purple_400,
        R.color.md_deep_purple_500 to R.string.md_deep_purple_500,
        R.color.md_deep_purple_600 to R.string.md_deep_purple_600,
        R.color.md_deep_purple_700 to R.string.md_deep_purple_700,
        R.color.md_deep_purple_800 to R.string.md_deep_purple_800,
        R.color.md_deep_purple_900 to R.string.md_deep_purple_900,
        R.color.md_deep_purple_a100 to R.string.md_deep_purple_a100,
        R.color.md_deep_purple_a200 to R.string.md_deep_purple_a200,
        R.color.md_deep_purple_a400 to R.string.md_deep_purple_a400,
        R.color.md_deep_purple_a700 to R.string.md_deep_purple_a700,

        /* indigo */

        R.color.md_indigo_50 to R.string.md_indigo_50,
        R.color.md_indigo_100 to R.string.md_indigo_100,
        R.color.md_indigo_200 to R.string.md_indigo_200,
        R.color.md_indigo_300 to R.string.md_indigo_300,
        R.color.md_indigo_400 to R.string.md_indigo_400,
        R.color.md_indigo_500 to R.string.md_indigo_500,
        R.color.md_indigo_600 to R.string.md_indigo_600,
        R.color.md_indigo_700 to R.string.md_indigo_700,
        R.color.md_indigo_800 to R.string.md_indigo_800,
        R.color.md_indigo_900 to R.string.md_indigo_900,
        R.color.md_indigo_a100 to R.string.md_indigo_a100,
        R.color.md_indigo_a200 to R.string.md_indigo_a200,
        R.color.md_indigo_a400 to R.string.md_indigo_a400,
        R.color.md_indigo_a700 to R.string.md_indigo_a700,

        /* blue */

        R.color.md_blue_50 to R.string.md_blue_50,
        R.color.md_blue_100 to R.string.md_blue_100,
        R.color.md_blue_200 to R.string.md_blue_200,
        R.color.md_blue_300 to R.string.md_blue_300,
        R.color.md_blue_400 to R.string.md_blue_400,
        R.color.md_blue_500 to R.string.md_blue_500,
        R.color.md_blue_600 to R.string.md_blue_600,
        R.color.md_blue_700 to R.string.md_blue_700,
        R.color.md_blue_800 to R.string.md_blue_800,
        R.color.md_blue_900 to R.string.md_blue_900,
        R.color.md_blue_a100 to R.string.md_blue_a100,
        R.color.md_blue_a200 to R.string.md_blue_a200,
        R.color.md_blue_a400 to R.string.md_blue_a400,
        R.color.md_blue_a700 to R.string.md_blue_a700,

        /* light blue */

        R.color.md_light_blue_50 to R.string.md_light_blue_50,
        R.color.md_light_blue_100 to R.string.md_light_blue_100,
        R.color.md_light_blue_200 to R.string.md_light_blue_200,
        R.color.md_light_blue_300 to R.string.md_light_blue_300,
        R.color.md_light_blue_400 to R.string.md_light_blue_400,
        R.color.md_light_blue_500 to R.string.md_light_blue_500,
        R.color.md_light_blue_600 to R.string.md_light_blue_600,
        R.color.md_light_blue_700 to R.string.md_light_blue_700,
        R.color.md_light_blue_800 to R.string.md_light_blue_800,
        R.color.md_light_blue_900 to R.string.md_light_blue_900,
        R.color.md_light_blue_a100 to R.string.md_light_blue_a100,
        R.color.md_light_blue_a200 to R.string.md_light_blue_a200,
        R.color.md_light_blue_a400 to R.string.md_light_blue_a400,
        R.color.md_light_blue_a700 to R.string.md_light_blue_a700,

        /* cyan */

        R.color.md_cyan_50 to R.string.md_cyan_50,
        R.color.md_cyan_100 to R.string.md_cyan_100,
        R.color.md_cyan_200 to R.string.md_cyan_200,
        R.color.md_cyan_300 to R.string.md_cyan_300,
        R.color.md_cyan_400 to R.string.md_cyan_400,
        R.color.md_cyan_500 to R.string.md_cyan_500,
        R.color.md_cyan_600 to R.string.md_cyan_600,
        R.color.md_cyan_700 to R.string.md_cyan_700,
        R.color.md_cyan_800 to R.string.md_cyan_800,
        R.color.md_cyan_900 to R.string.md_cyan_900,
        R.color.md_cyan_a100 to R.string.md_cyan_a100,
        R.color.md_cyan_a200 to R.string.md_cyan_a200,
        R.color.md_cyan_a400 to R.string.md_cyan_a400,
        R.color.md_cyan_a700 to R.string.md_cyan_a700,

        /* teal */

        R.color.md_teal_50 to R.string.md_teal_50,
        R.color.md_teal_100 to R.string.md_teal_100,
        R.color.md_teal_200 to R.string.md_teal_200,
        R.color.md_teal_300 to R.string.md_teal_300,
        R.color.md_teal_400 to R.string.md_teal_400,
        R.color.md_teal_500 to R.string.md_teal_500,
        R.color.md_teal_600 to R.string.md_teal_600,
        R.color.md_teal_700 to R.string.md_teal_700,
        R.color.md_teal_800 to R.string.md_teal_800,
        R.color.md_teal_900 to R.string.md_teal_900,
        R.color.md_teal_a100 to R.string.md_teal_a100,
        R.color.md_teal_a200 to R.string.md_teal_a200,
        R.color.md_teal_a400 to R.string.md_teal_a400,
        R.color.md_teal_a700 to R.string.md_teal_a700,

        /* green */

        R.color.md_green_50 to R.string.md_green_50,
        R.color.md_green_100 to R.string.md_green_100,
        R.color.md_green_200 to R.string.md_green_200,
        R.color.md_green_300 to R.string.md_green_300,
        R.color.md_green_400 to R.string.md_green_400,
        R.color.md_green_500 to R.string.md_green_500,
        R.color.md_green_600 to R.string.md_green_600,
        R.color.md_green_700 to R.string.md_green_700,
        R.color.md_green_800 to R.string.md_green_800,
        R.color.md_green_900 to R.string.md_green_900,
        R.color.md_green_a100 to R.string.md_green_a100,
        R.color.md_green_a200 to R.string.md_green_a200,
        R.color.md_green_a400 to R.string.md_green_a400,
        R.color.md_green_a700 to R.string.md_green_a700,

        /* light green */

        R.color.md_light_green_50 to R.string.md_light_green_50,
        R.color.md_light_green_100 to R.string.md_light_green_100,
        R.color.md_light_green_200 to R.string.md_light_green_200,
        R.color.md_light_green_300 to R.string.md_light_green_300,
        R.color.md_light_green_400 to R.string.md_light_green_400,
        R.color.md_light_green_500 to R.string.md_light_green_500,
        R.color.md_light_green_600 to R.string.md_light_green_600,
        R.color.md_light_green_700 to R.string.md_light_green_700,
        R.color.md_light_green_800 to R.string.md_light_green_800,
        R.color.md_light_green_900 to R.string.md_light_green_900,
        R.color.md_light_green_a100 to R.string.md_light_green_a100,
        R.color.md_light_green_a200 to R.string.md_light_green_a200,
        R.color.md_light_green_a400 to R.string.md_light_green_a400,
        R.color.md_light_green_a700 to R.string.md_light_green_a700,

        /* lime */

        R.color.md_lime_50 to R.string.md_lime_50,
        R.color.md_lime_100 to R.string.md_lime_100,
        R.color.md_lime_200 to R.string.md_lime_200,
        R.color.md_lime_300 to R.string.md_lime_300,
        R.color.md_lime_400 to R.string.md_lime_400,
        R.color.md_lime_500 to R.string.md_lime_500,
        R.color.md_lime_600 to R.string.md_lime_600,
        R.color.md_lime_700 to R.string.md_lime_700,
        R.color.md_lime_800 to R.string.md_lime_800,
        R.color.md_lime_900 to R.string.md_lime_900,
        R.color.md_lime_a100 to R.string.md_lime_a100,
        R.color.md_lime_a200 to R.string.md_lime_a200,
        R.color.md_lime_a400 to R.string.md_lime_a400,
        R.color.md_lime_a700 to R.string.md_lime_a700,

        /* yellow */

        R.color.md_yellow_50 to R.string.md_yellow_50,
        R.color.md_yellow_100 to R.string.md_yellow_100,
        R.color.md_yellow_200 to R.string.md_yellow_200,
        R.color.md_yellow_300 to R.string.md_yellow_300,
        R.color.md_yellow_400 to R.string.md_yellow_400,
        R.color.md_yellow_500 to R.string.md_yellow_500,
        R.color.md_yellow_600 to R.string.md_yellow_600,
        R.color.md_yellow_700 to R.string.md_yellow_700,
        R.color.md_yellow_800 to R.string.md_yellow_800,
        R.color.md_yellow_900 to R.string.md_yellow_900,
        R.color.md_yellow_a100 to R.string.md_yellow_a100,
        R.color.md_yellow_a200 to R.string.md_yellow_a200,
        R.color.md_yellow_a400 to R.string.md_yellow_a400,
        R.color.md_yellow_a700 to R.string.md_yellow_a700,

        /* amber */

        R.color.md_amber_50 to R.string.md_amber_50,
        R.color.md_amber_100 to R.string.md_amber_100,
        R.color.md_amber_200 to R.string.md_amber_200,
        R.color.md_amber_300 to R.string.md_amber_300,
        R.color.md_amber_400 to R.string.md_amber_400,
        R.color.md_amber_500 to R.string.md_amber_500,
        R.color.md_amber_600 to R.string.md_amber_600,
        R.color.md_amber_700 to R.string.md_amber_700,
        R.color.md_amber_800 to R.string.md_amber_800,
        R.color.md_amber_900 to R.string.md_amber_900,
        R.color.md_amber_a100 to R.string.md_amber_a100,
        R.color.md_amber_a200 to R.string.md_amber_a200,
        R.color.md_amber_a400 to R.string.md_amber_a400,
        R.color.md_amber_a700 to R.string.md_amber_a700,

        /* orange */

        R.color.md_orange_50 to R.string.md_orange_50,
        R.color.md_orange_100 to R.string.md_orange_100,
        R.color.md_orange_200 to R.string.md_orange_200,
        R.color.md_orange_300 to R.string.md_orange_300,
        R.color.md_orange_400 to R.string.md_orange_400,
        R.color.md_orange_500 to R.string.md_orange_500,
        R.color.md_orange_600 to R.string.md_orange_600,
        R.color.md_orange_700 to R.string.md_orange_700,
        R.color.md_orange_800 to R.string.md_orange_800,
        R.color.md_orange_900 to R.string.md_orange_900,
        R.color.md_orange_a100 to R.string.md_orange_a100,
        R.color.md_orange_a200 to R.string.md_orange_a200,
        R.color.md_orange_a400 to R.string.md_orange_a400,
        R.color.md_orange_a700 to R.string.md_orange_a700,

        /* deep orange */

        R.color.md_deep_orange_50 to R.string.md_deep_orange_50,
        R.color.md_deep_orange_100 to R.string.md_deep_orange_100,
        R.color.md_deep_orange_200 to R.string.md_deep_orange_200,
        R.color.md_deep_orange_300 to R.string.md_deep_orange_300,
        R.color.md_deep_orange_400 to R.string.md_deep_orange_400,
        R.color.md_deep_orange_500 to R.string.md_deep_orange_500,
        R.color.md_deep_orange_600 to R.string.md_deep_orange_600,
        R.color.md_deep_orange_700 to R.string.md_deep_orange_700,
        R.color.md_deep_orange_800 to R.string.md_deep_orange_800,
        R.color.md_deep_orange_900 to R.string.md_deep_orange_900,
        R.color.md_deep_orange_a100 to R.string.md_deep_orange_a100,
        R.color.md_deep_orange_a200 to R.string.md_deep_orange_a200,
        R.color.md_deep_orange_a400 to R.string.md_deep_orange_a400,
        R.color.md_deep_orange_a700 to R.string.md_deep_orange_a700,

        /* brown */

        R.color.md_brown_50 to R.string.md_brown_50,
        R.color.md_brown_100 to R.string.md_brown_100,
        R.color.md_brown_200 to R.string.md_brown_200,
        R.color.md_brown_300 to R.string.md_brown_300,
        R.color.md_brown_400 to R.string.md_brown_400,
        R.color.md_brown_500 to R.string.md_brown_500,
        R.color.md_brown_600 to R.string.md_brown_600,
        R.color.md_brown_700 to R.string.md_brown_700,
        R.color.md_brown_800 to R.string.md_brown_800,
        R.color.md_brown_900 to R.string.md_brown_900,

        /* grey */

        R.color.md_grey_50 to R.string.md_grey_50,
        R.color.md_grey_100 to R.string.md_grey_100,
        R.color.md_grey_200 to R.string.md_grey_200,
        R.color.md_grey_300 to R.string.md_grey_300,
        R.color.md_grey_400 to R.string.md_grey_400,
        R.color.md_grey_500 to R.string.md_grey_500,
        R.color.md_grey_600 to R.string.md_grey_600,
        R.color.md_grey_700 to R.string.md_grey_700,
        R.color.md_grey_800 to R.string.md_grey_800,
        R.color.md_grey_900 to R.string.md_grey_900,
        R.color.md_black_1000 to R.string.md_black_1000,
        R.color.md_white_1000 to R.string.md_white_1000,

        /* blue grey */

        R.color.md_blue_grey_50 to R.string.md_blue_grey_50,
        R.color.md_blue_grey_100 to R.string.md_blue_grey_100,
        R.color.md_blue_grey_200 to R.string.md_blue_grey_200,
        R.color.md_blue_grey_300 to R.string.md_blue_grey_300,
        R.color.md_blue_grey_400 to R.string.md_blue_grey_400,
        R.color.md_blue_grey_500 to R.string.md_blue_grey_500,
        R.color.md_blue_grey_600 to R.string.md_blue_grey_600,
        R.color.md_blue_grey_700 to R.string.md_blue_grey_700,
        R.color.md_blue_grey_800 to R.string.md_blue_grey_800,
        R.color.md_blue_grey_900 to R.string.md_blue_grey_900,
    )

    @JvmStatic
    fun toString(color: Int): String {

        // @Comment by SuperMonster003 on Dec 23, 2022.
        //  ! When alpha is 0, code below returns #RRGGBB without alpha info,
        //  ! which doesn't behave as expected.
        //  ! zh-CN:
        //  ! 当 alpha 通道为 0 时, 以下代码返回 #RRGGBB 格式, 不含 alpha 信息,
        //  ! 这个结果与预期不符.
        //  !
        //  # val c = StringBuilder(Integer.toHexString(color))
        //  # while (c.length < 6) {
        //  #     c.insert(0, "0")
        //  # }

        val c = String.format("%08X", 0xFFFFFFFF and color.toLong())
        return when {
            c.length == 8 && c.startsWith("FF", true) -> {
                "#${c.substring(2)}"
            }
            else -> "#$c"
        }
    }

    @ColorInt
    fun fromResources(@ColorRes colorRes: Int) = GlobalAppContext.get().getColor(colorRes)

    @JvmStatic
    @JvmOverloads
    fun toUnit8(component: Double, takeNumOneAsPercent: Boolean = false) = when {
        component < 1 -> (component * 255).roundToInt()
        component == 1.0 && takeNumOneAsPercent -> 255
        else -> 255.coerceAtMost(component.roundToInt())
    }

    @JvmStatic
    fun toInt(num: Long) = toInt(toHex(num))

    @JvmStatic
    fun toInt(themeColor: ThemeColor) = toInt(toHex(themeColor))

    @JvmStatic
    fun toInt(colorString: String): Int = try {
        Color.parseColor(toHex(colorString))
    } catch (e: Exception) {
        // AutoJs.instance.globalConsole.warn("Passed color: $colorString")
        throw Exception(e.message + "\n" + e.stackTrace)
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
        when {
            color.startsWith('#') -> {
                if (color.length == 4) {
                    color = color.replace(Regex("(#)(\\w)(\\w)(\\w)"), "$1$2$2$3$3$4$4")
                }
            }
            color.matches(Regex("[+-]?\\d+")) -> {
                return toHex(color.toLong(), alpha)
            }
            else -> ColorTable.getColorByName(color, true)?.let { colorByName ->
                return toHex(colorByName.toLong(), alpha)
            }
        }
        require(Regex("#[A-F\\d]{3}([A-F\\d]{3}([A-F\\d]{2})?)?", IGNORE_CASE).matches(color)) {
            "Invalid color string format: $color"
        }
        return when (alpha) {
            "keep" -> {
                if (color.length == 7) {
                    color = "#FF${color.substring(1)}"
                }
                color.uppercase()
            }
            "none" -> "#${color.substring(color.length - 6)}".uppercase()
            "auto" -> when {
                Regex("#FF([A-F\\d]){6}", IGNORE_CASE).matches(color) -> {
                    "#${color.substring(3)}".uppercase()
                }
                else -> color.uppercase()
            }
            else -> throw IllegalArgumentException("Unknown alpha \"$alpha\" for converting a color into ColorHex")
        }
    }

    @JvmStatic
    fun toHex(colorString: String, hasAlpha: Boolean): String = when {
        hasAlpha -> toHex(colorString, 8)
        else -> toHex(colorString, 6)
    }

    @JvmStatic
    fun toHex(colorString: String, resultLength: Int): String = when (resultLength) {
        3 -> {
            require(Regex("#(?:([A-F\\d]){2})?([A-F\\d])\\2([A-F\\d])\\3([A-F\\d])\\4", IGNORE_CASE).matches(colorString)) {
                "Cannot convert color $colorString to #RGB with unexpected color format."
            }
            val r = colorString.substring(colorString.length - 6, colorString.length - 5)
            val g = colorString.substring(colorString.length - 4, colorString.length - 3)
            val b = colorString.substring(colorString.length - 2, colorString.length - 1)
            "#$r$g$b"
        }
        6 -> toHex(colorString, "none")
        8 -> toHex(colorString, "keep")
        else -> throw IllegalArgumentException("Unknown length \"$resultLength\" for converting a color into ColorHex")
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
    fun parse(context: Context, color: String): Int = when {
        color.startsWith("@color/") -> {
            context.resources.getIdentifier(
                color.substring("@color/".length), "color", context.packageName,
            ).let { identifier -> context.resources.getColor(identifier) }
        }
        color.startsWith("@android:color/") -> {
            Color.parseColor(color.substring("@android:color/".length))
        }
        else -> toInt(color)
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