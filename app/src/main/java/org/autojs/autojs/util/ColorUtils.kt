@file:Suppress("unused")

package org.autojs.autojs.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.graphics.ColorUtils
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import java.math.RoundingMode
import kotlin.math.roundToInt
import kotlin.text.RegexOption.IGNORE_CASE

@Suppress("FunctionName")
object ColorUtils {

    @JvmStatic
    @JvmOverloads
    fun toString(color: Int, uppercase: Boolean = true): String {

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
        val hex = when {
            c.length == 8 && c.startsWith("FF", true) -> {
                "#${c.substring(2)}"
            }
            else -> "#$c"
        }
        return if (uppercase) hex.uppercase() else hex.lowercase()
    }

    @JvmStatic
    @JvmOverloads
    fun toUnit8(component: Double, takeNumOneAsPercent: Boolean = false) = when {
        component < 1 -> (component * 255).roundToInt()
        component == 1.0 && takeNumOneAsPercent -> 255
        else -> 255.coerceAtMost(component.roundToInt())
    }

    @JvmStatic
    fun toInt(num: Number) = toInt(toHex(num))

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
    fun toHex(num: Number, alpha: String = "auto"): String {
        return toHex(toString(toJavaIntegerRange(num)), alpha)
    }

    @JvmStatic
    fun toHex(num: Number, hasAlpha: Boolean): String {
        return toHex(toString(toJavaIntegerRange(num)), hasAlpha)
    }

    @JvmStatic
    fun toHex(num: Number, resultLength: Int): String {
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
    fun toFullHex(num: Number) = toHex(num, 8)

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

    private fun toJavaIntegerRange(x: Number): Int {
        // @Commented by SuperMonster003 on Apr 1, 2025.
        //  # val t = 2f.pow(32).toLong()
        //  # val min = (-2f).pow(31).toLong()
        //  # val max = 2f.pow(31 - 1).toLong()
        //  # var tmp = x.toLong()
        //  # while (tmp < min) tmp += t
        //  # while (tmp > max) tmp -= t
        //  # return tmp.toInt()
        return x.toLong().toInt()
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
    @FloatRange(0.0, 1.0)
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
    fun isLuminanceLight(@ColorInt color: Int) = luminance(color) >= 0.179

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
    @JvmOverloads
    fun adjustColorForContrast(
        @ColorInt background: Int,
        @ColorInt reference: Int,
        minimumContrast: Double = 4.5,
        alpha: Double = 1.0,
    ): Int {

        /* 原色已满足要求, 直接返回. */
        if (calculateContrast(background, reference) >= minimumContrast) {
            return applyAlpha(reference, alpha)
        }

        /* 把参考色转成 HSL, 方便按亮度通道调整. */
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(reference, it) }

        /* 先尝试变亮, 再尝试变暗, 找到即可返回. */
        listOf(true, false).forEach { lighten ->
            findAdjustableHSLColor(hsl, background, minimumContrast, lighten)?.let {
                return applyAlpha(it, alpha)
            }
        }

        /* 极端情况下两次尝试都失败, 退回原色. */
        return applyAlpha(reference, alpha)
    }

    @JvmStatic
    @JvmOverloads
    fun adjustThemeColorForContrast(
        @ColorInt background: Int,
        minimumContrast: Double = 4.5,
        alpha: Double = 1.0,
    ): Int = adjustColorForContrast(
        background,
        ThemeColorManager.colorPrimary,
        minimumContrast,
        alpha,
    )

    @JvmStatic
    fun applyAlpha(@ColorInt color: Int, alpha: Double): Int = when (alpha) {
        1.0 -> color
        else -> ColorUtils.setAlphaComponent(color, (alpha * 255).roundToInt())
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

    @JvmStatic
    @JvmOverloads
    fun Double.roundToAlphaString(scale: Int = 1, keepTrailingZeroForFullAlpha: Boolean = true): String {
        return toBigDecimal()
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .let { if (keepTrailingZeroForFullAlpha && it == "1") "1.0" else it }
    }

    @JvmStatic
    @JvmOverloads
    fun Double.roundToHueString(scale: Int = 0): String {
        return toBigDecimal()
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    @JvmStatic
    @JvmOverloads
    fun Double.roundToSaturationString(scale: Int = 1): String {
        return (this * 100.0).toBigDecimal()
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + "%"
    }

    @JvmStatic
    @JvmOverloads
    fun Double.roundToValueString(scale: Int = 1): String {
        return (this * 100.0).toBigDecimal()
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + "%"
    }

}