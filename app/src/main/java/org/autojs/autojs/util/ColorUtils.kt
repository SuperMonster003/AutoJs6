@file:Suppress("unused")

package org.autojs.autojs.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs6.R
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.text.RegexOption.IGNORE_CASE

@Suppress("FunctionName")
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

    fun toColorStateList(color: String) = ColorStateList.valueOf(toInt(color))

    fun toColorStateList(view: View, color: String) = ColorStateList.valueOf(parse(view, color))

    @JvmStatic
    fun rgb(red: Int, green: Int, blue: Int): Int = Color.rgb(red, green, blue)

    @JvmStatic
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = Color.argb(alpha, red, green, blue)

    /**
     * Calculates the relative luminance of a color based on the W3C WCAG 2.0 definition.
     * This method employs a perceptual brightness calculation, which considers how human eyes perceive light
     * and visibly similar changes in brightness. It differs from a simple brightness calculation by employing
     * gamma correction and converting RGB color information to the CIE XYZ color space.
     *
     * zh-CN:
     *
     * 计算颜色相对亮度 (luminance), 基于 W3C WCAG 2.0 的定义.
     * 本方法采用了一种符合人眼感知的亮度计算方式, 考虑到人眼对不同颜色的光敏感性的非线性特性以及视网膜对亮度变化的感知效果.
     * 与简单的线性明度计算不同, 本方法通过加入伽马矫正 (Gamma Correction) 和将 RGB 颜色转化到 CIE XYZ 色彩空间来实现更真实的感官亮度评估.
     *
     * Differences from basic brightness calculation:
     * 1. Gamma Correction: Adjusts the linear RGB color channels to account for human vision's non-linear
     * response to light intensity. This step improves the representation of perceptual brightness.
     * 2. CIE XYZ Color Space: To better model human color perception, color information is converted
     * from RGB to the XYZ color space, which defines brightness, chromaticity, and hue more accurately.
     *
     * zh-CN:
     *
     * 与基本明度计算的区别:
     * 1. 伽马矫正 (Gamma Correction): 对 RGB 通道值进行非线性调整, 以匹配人眼对亮度强度的非线性响应. 此步骤可提高感知亮度的准确性.
     * 2. CIE XYZ 色彩空间: 将颜色从 RGB 转换到 XYZ 色彩空间, 该空间定义了更符合人类视觉感知的 [亮度/色度/色调] 信息.
     *
     * Usage:
     * This method adheres to the W3C Web Content Accessibility Guidelines (WCAG 2.0) definition of relative
     * luminance, suitable for evaluating color contrast ratios to ensure text readability and accessibility.
     * For example, it is commonly used in validating color combinations for web accessibility or UI design.
     *
     * zh-CN:
     *
     * 用法:
     * 本方法遵循 W3C <<网络内容无障碍指南2.0>> (WCAG 2.0) 中定义的 "相对亮度" 概念,
     * 适用于评估颜色对比度, 从而确保文本可读性和无障碍性设计.
     * 例如, 在验证网页颜色组合或 UI 设计中的颜色对比是否满足无障碍标准中, 广泛使用此方法.
     *
     * Implementation notes:
     * Internally, this method uses [androidx.core.graphics.ColorUtils.calculateLuminance], which follows
     * the WCAG standard. On `Android O` or higher, alternative system solutions such as [Color.luminance]
     * may also be used.
     *
     * zh-CN:
     *
     * 实现说明:
     * 此方法对内调用了 [androidx.core.graphics.ColorUtils.calculateLuminance], 其实现遵循 WCAG 标准.
     * 对于 `Android O` 及其以上版本, 也可以替代性地使用系统方法如 [Color.luminance].
     *
     * @param color The input color, in ARGB format. (zh-CN: 输入颜色, 以 ARGB 格式表示).
     * @return The relative luminance of the color, following WCAG 2.0 standards. (zh-CN: 返回该颜色的相对亮度, 符合 WCAG 2.0 标准).
     *
     * @see <a href="https://www.w3.org/TR/WCAG20/#relativeluminancedef">W3C Recommendation</a>
     */
    @JvmStatic
    fun luminance(@ColorInt color: Int): Double {
        // @Hint by SuperMonster003 on Jan 21, 2023.
        //  ! Compatibility solution.
        //  ! zh-CN: 兼容方案.
        //  # return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        //  #         ? Color.luminance(Color.pack(color))
        //  #         : Color.luminance(color);
        return ColorUtils.calculateLuminance(color)
    }

    /**
     * @see <a href=https://stackoverflow.com/
     * questions/3942878/how-to-decide-font-color-in-white-or-black-depending-on-background-color>
     * How to decide font color in white or black depending on background color?
     * </a>
     */
    @JvmStatic
    fun isLuminanceLight(@ColorInt color: Int) = luminance(color) > 0.179

    @JvmStatic
    fun isLuminanceDark(@ColorInt color: Int) = !isLuminanceLight(color)

    @JvmStatic
    fun parseColor(colorString: String?): Int = Color.parseColor(colorString)

    @JvmStatic
    fun RGBToHSV(red: Int, green: Int, blue: Int, hsv: FloatArray?) {
        Color.RGBToHSV(red, green, blue, hsv)
    }

    @JvmStatic
    fun colorToHSV(color: Int, hsv: FloatArray?) {
        Color.colorToHSV(color, hsv)
    }

    @JvmStatic
    fun HSVToColor(hsv: FloatArray?) = Color.HSVToColor(hsv)

    @JvmStatic
    fun HSVToColor(alpha: Int, hsv: FloatArray?) = Color.HSVToColor(alpha, hsv)

    @JvmStatic
    fun equals(c1: Int, c2: Int) = (c1 and 0xffffff) == (c2 and 0xffffff)

    @JvmStatic
    fun equals(c1: Int, c2: String?) = equals(c1, parseColor(c2))

    @JvmStatic
    fun equals(c1: String?, c2: Int) = equals(parseColor(c1), c2)

    @JvmStatic
    fun equals(c1: String?, c2: String?) = equals(parseColor(c1), parseColor(c2))

    /**
     * Adjust the reference color to achieve better contrast on the background color, while remaining as close as possible to the reference color.
     *
     * zh-CN: 调整参考色, 使其在背景色上具有更好的对比度, 同时尽量接近参考色.
     *
     * @param background The background color. (zh-CN: 背景颜色.)
     * @param reference The reference color. (zh-CN: 参考颜色.)
     * @param minimumContrast The minimum contrast ratio, typically between 1.0 and 21.0 (1 being the lowest contrast, 21 being the highest contrast).
     * (zh-CN: 最小对比度, 通常值为 1.0 - 21.0 (1 为最低对比度, 21 为最高对比度).)
     * - 4.5: Minimum contrast ratio for normal use cases (Standard AA)
     * - 7.0: High contrast ratio for better readability (Standard AAA)
     * - < 4.5 (e.g. 3.0): Lower contrast ratio (< 4.5, e.g., 3.0) is acceptable for decorative or less significant elements
     * - zh-CN:
     * - 4.5: 普通场景的最低对比度标准 (AA 标准)
     * - 7.0: 高对比度场景标准 (AAA 标准)
     * - < 4.5 (如 3.0): 装饰性或次要元素可用的较低标准
     * @return The adjusted color. (zh-CN: 调整后的颜色.)
     */
    @JvmStatic
    fun adjustColorForContrast(
        @ColorInt background: Int,
        @ColorInt reference: Int,
        minimumContrast: Double = 4.5,
    ): Int {
        // 如果已经满足对比度要求, 直接返回参考色
        if (calculateContrast(background, reference) >= minimumContrast) {
            return reference
        }

        // 转为 HSL 以便调整亮度
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(reference, hsl)

        // 提高亮度, 尝试达到目标对比度
        val lighterColor = findAdjustableHSLColor(hsl, background, minimumContrast, lighten = true)
        if (lighterColor != null) {
            return lighterColor
        }

        // 降低亮度, 尝试达到目标对比度
        val darkerColor = findAdjustableHSLColor(hsl, background, minimumContrast, lighten = false)
        if (darkerColor != null) {
            return darkerColor
        }

        // 如果两种情况下都无法满足, 返回原始颜色 (极端情况)
        return reference
    }

    /**
     * Calculate the contrast ratio between two colors using the W3C WCAG formula.
     *
     * zh-CN: 计算两种颜色的对比度, 使用 W3C WCAG 公式.
     */
    private fun calculateContrast(color1: Int, color2: Int): Double {
        return ColorUtils.calculateContrast(color1, color2)
    }

    /**
     * Adjust the lightness of a color in HSL format to meet the minimum contrast ratio.
     *
     * zh-CN: 调整颜色的亮度, 尝试找到满足最小对比度的颜色.
     *
     * @param hsl The color value in HSL format, including Hue, Saturation, and Lightness.
     * (zh-CN: HSL 格式颜色值, 包括色相 (Hue), 饱和度 (Saturation), 亮度 (Lightness).)
     * @param background The background color. (zh-CN: 背景颜色.)
     * @param minimumContrast minimum contrast ratio. (zh-CN: 最小对比度值.)
     * @param lighten Whether to increase brightness (true) or decrease brightness (false).
     * (zh-CN: 是否提高亮度 (true 为提高亮度, false 为降低亮度).)
     * @return The adjusted color, or null if no suitable color can be found.
     * (zh-CN: 调整后的颜色, 若无法找到满足条件的颜色, 返回 null.)
     */
    @ColorInt
    private fun findAdjustableHSLColor(
        hsl: FloatArray,
        @ColorInt background: Int,
        minimumContrast: Double,
        lighten: Boolean,
    ): Int? {
        val step = 0.02f // 每次调整亮度的步长
        val maxSteps = 50 // 最大调整次数 (防止死循环)

        val adjustedHSL = hsl.copyOf()
        for (i in 0 until maxSteps) {
            // 根据 lighten 参数, 增加或减少亮度
            adjustedHSL[2] = (adjustedHSL[2] + (if (lighten) step else -step))
                .coerceIn(0f, 1f) // 防止超出合法范围 [0, 1]

            // 将调整后的 HSL 转回 RGB
            val adjustedColor = ColorUtils.HSLToColor(adjustedHSL)

            // 重新计算对比度, 如果满足条件即可返回
            if (calculateContrast(background, adjustedColor) >= minimumContrast) {
                return adjustedColor
            }
        }

        // 无法找到合适的亮度调整
        return null
    }

}