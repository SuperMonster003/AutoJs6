package org.autojs.autojs.theme.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Perceptual and accessibility measurements between colors. Alpha channels are ignored.
 * zh-CN: 颜色之间的感知与可访问性度量. alpha 通道被忽略.
 *
 * [relativeLuminance] and [contrastRatio] follow WCAG 2.x, the same formula used by
 * `ColorUtils.luminance` (androidx `calculateLuminance`), so results stay consistent with existing callers.
 * zh-CN: [relativeLuminance] 与 [contrastRatio] 遵循 WCAG 2.x, 与 `ColorUtils.luminance` (androidx `calculateLuminance`)
 * 的公式一致, 因此与既有调用方的结果保持一致.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object ColorMetrics {

    fun relativeLuminance(argb: Int): Double {
        val r = OkColor.srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = OkColor.srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = OkColor.srgbToLinear((argb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * WCAG contrast ratio in `1..21`, independent of argument order.
     * zh-CN: WCAG 对比度, 取值 1..21, 与参数顺序无关.
     */
    fun contrastRatio(first: Int, second: Int): Double = contrastRatioOfLuminance(relativeLuminance(first), relativeLuminance(second))

    fun contrastRatioOfLuminance(first: Double, second: Double): Double = (max(first, second) + 0.05) / (min(first, second) + 0.05)

    /**
     * Euclidean distance in OKLab (dE_ok). About 0.02 is a just-noticeable difference; black to white is 1.
     * zh-CN: OKLab 欧氏距离 (dE_ok). 约 0.02 为刚好可察觉的差异; 黑到白为 1.
     */
    fun deltaEOk(first: Int, second: Int): Double = deltaEOk(OkColor.toOkLab(first), OkColor.toOkLab(second))

    fun deltaEOk(first: OkLab, second: OkLab): Double {
        val dl = first.l - second.l
        val da = first.a - second.a
        val db = first.b - second.b
        return sqrt(dl * dl + da * da + db * db)
    }

    /**
     * Shortest angular distance between two hues, in `0..180` degrees.
     * zh-CN: 两个色相之间的最短角距离, 取值 0..180 度.
     */
    fun hueDistance(first: Double, second: Double): Double {
        val diff = abs(OkColor.normalizeHue(first) - OkColor.normalizeHue(second))
        return if (diff > 180.0) 360.0 - diff else diff
    }

}
