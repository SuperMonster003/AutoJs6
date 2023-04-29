package org.autojs.autojs.util

import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R
import kotlin.math.roundToInt

class ColorUtils {

    companion object {

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
            //  ! When alpha is 0, toString() returns #RRGGBB without alpha.

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
        fun get(@ColorRes colorRes: Int) = globalAppContext.getColor(colorRes)

        @JvmStatic
        @JvmOverloads
        fun toUnit8(component: Double, takeNumOneAsPercent: Boolean = false) = when {
            component < 1 -> (component * 255).roundToInt()
            else -> when {
                component == 1.0 && takeNumOneAsPercent -> 255
                else -> 255.coerceAtMost(component.roundToInt())
            }
        }

    }

}