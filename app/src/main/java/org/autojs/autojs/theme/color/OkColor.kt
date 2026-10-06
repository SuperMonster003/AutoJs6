package org.autojs.autojs.theme.color

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A color in the OKLab space. [l] is perceptual lightness in `0..1`, [a] / [b] are the opponent axes.
 * zh-CN: OKLab 空间中的颜色. [l] 为 0..1 的感知亮度, [a] / [b] 为对立色轴.
 */
data class OkLab(val l: Double, val a: Double, val b: Double)

/**
 * A color in the OKLCH space (the polar form of OKLab). [h] is the hue in degrees `[0, 360)`;
 * it carries no meaning when [c] is close to zero.
 * zh-CN: OKLCH 空间中的颜色 (OKLab 的极坐标形式). [h] 为 [0, 360) 的色相角度; [c] 接近 0 时色相没有意义.
 */
data class OkLch(val l: Double, val c: Double, val h: Double)

/**
 * Conversions between opaque ARGB colors and OKLab / OKLCH (Bjorn Ottosson, 2020).
 * zh-CN: 不透明 ARGB 颜色与 OKLab / OKLCH 之间的转换 (Bjorn Ottosson, 2020).
 *
 * The alpha channel of the input is ignored; outputs carry the requested alpha (opaque by default).
 * Conversions from OKLCH map out-of-gamut colors back into sRGB by reducing chroma while keeping
 * lightness and hue, so derived colors never drift in hue because of channel clipping.
 * zh-CN: 输入的 alpha 通道被忽略, 输出使用指定的 alpha (默认不透明).
 * 从 OKLCH 转换时, 超出 sRGB 色域的颜色通过降低彩度映射回色域, 亮度与色相保持不变, 避免通道截断导致色相漂移.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object OkColor {

    /** Chroma below which a color is treated as achromatic. zh-CN: 彩度低于此值时视为无彩色. */
    const val ACHROMATIC_CHROMA = 1e-4

    private const val GAMUT_EPSILON = 1e-7
    private const val GAMUT_SEARCH_STEPS = 32

    fun srgbToLinear(channel: Double): Double = when {
        channel <= 0.04045 -> channel / 12.92
        else -> ((channel + 0.055) / 1.055).pow(2.4)
    }

    fun linearToSrgb(linear: Double): Double = when {
        linear <= 0.0031308 -> linear * 12.92
        else -> 1.055 * linear.pow(1.0 / 2.4) - 0.055
    }

    fun toOkLab(argb: Int): OkLab {
        val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = srgbToLinear((argb and 0xFF) / 255.0)

        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        return OkLab(
            l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun toOkLch(argb: Int): OkLch = toOkLch(toOkLab(argb))

    fun toOkLch(lab: OkLab): OkLch {
        val c = sqrt(lab.a * lab.a + lab.b * lab.b)
        val h = normalizeHue(Math.toDegrees(atan2(lab.b, lab.a)))
        return OkLch(lab.l, c, if (c < ACHROMATIC_CHROMA) 0.0 else h)
    }

    fun toOkLab(lch: OkLch): OkLab {
        val radians = Math.toRadians(lch.h)
        return OkLab(lch.l, lch.c * cos(radians), lch.c * sin(radians))
    }

    /**
     * Linear sRGB channels of [lab], possibly outside `0..1`.
     * zh-CN: [lab] 对应的线性 sRGB 通道值, 可能超出 0..1.
     */
    fun toLinearRgb(lab: OkLab): DoubleArray {
        val l = (lab.l + 0.3963377774 * lab.a + 0.2158037573 * lab.b).let { it * it * it }
        val m = (lab.l - 0.1055613458 * lab.a - 0.0638541728 * lab.b).let { it * it * it }
        val s = (lab.l - 0.0894841775 * lab.a - 1.2914855480 * lab.b).let { it * it * it }
        return doubleArrayOf(
            +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    fun isInGamut(lab: OkLab): Boolean = toLinearRgb(lab).all { it >= -GAMUT_EPSILON && it <= 1.0 + GAMUT_EPSILON }

    fun isInGamut(lch: OkLch): Boolean = isInGamut(toOkLab(lch))

    /**
     * Converts [lab] to ARGB, clipping each channel into range.
     * Prefer [fromOkLch] for computed colors, which preserves hue.
     * zh-CN: 将 [lab] 转换为 ARGB, 逐通道截断到合法范围. 计算得到的颜色优先使用保持色相的 [fromOkLch].
     */
    fun fromOkLab(lab: OkLab, alpha: Int = 0xFF): Int {
        val (r, g, b) = toLinearRgb(lab).map { to8Bit(it) }
        return (alpha.coerceIn(0, 0xFF) shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Converts [lch] to ARGB after mapping it into the sRGB gamut (see [gamutMap]).
     * zh-CN: 先将 [lch] 映射进 sRGB 色域 (见 [gamutMap]), 再转换为 ARGB.
     */
    fun fromOkLch(lch: OkLch, alpha: Int = 0xFF): Int = fromOkLab(toOkLab(gamutMap(lch)), alpha)

    /**
     * Brings [lch] into the sRGB gamut by lowering chroma only; lightness is clamped to `0..1`
     * and hue is kept. The returned chroma is the largest in-gamut value found by bisection.
     * zh-CN: 只降低彩度把 [lch] 带回 sRGB 色域; 亮度截断到 0..1, 色相保持不变. 返回的彩度为二分查找得到的最大色域内取值.
     */
    fun gamutMap(lch: OkLch): OkLch {
        val l = lch.l
        val c = lch.c.coerceAtLeast(0.0)
        val h = normalizeHue(lch.h)
        if (l >= 1.0) return OkLch(1.0, 0.0, h)
        if (l <= 0.0) return OkLch(0.0, 0.0, h)
        val candidate = OkLch(l, c, h)
        if (isInGamut(candidate)) return candidate
        var low = 0.0
        var high = c
        repeat(GAMUT_SEARCH_STEPS) {
            val mid = (low + high) / 2
            if (isInGamut(OkLch(l, mid, h))) low = mid else high = mid
        }
        return OkLch(l, low, h)
    }

    fun normalizeHue(degrees: Double): Double {
        val wrapped = (degrees % 360.0).let { if (it < 0) it + 360.0 else it }
        // A tiny negative remainder rounds up to exactly 360.0 after the shift.
        // zh-CN: 极小的负余数加 360 后会舍入为 360.0.
        return if (wrapped >= 360.0) 0.0 else wrapped
    }

    private fun to8Bit(linear: Double): Int = (linearToSrgb(linear.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 0xFF)

}
