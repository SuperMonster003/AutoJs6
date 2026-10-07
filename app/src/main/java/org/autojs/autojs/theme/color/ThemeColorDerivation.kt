package org.autojs.autojs.theme.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Derives related theme colors from a single primary color, working in OKLCH so hue is preserved.
 * zh-CN: 从单个主色派生相关主题色. 在 OKLCH 中计算, 保持色相不变.
 *
 * - [deriveDark] / [deriveAccent]: the optional `colorPrimaryDark` / `colorAccent` of a theme color.
 * - [nightVariant] / [dayVariant]: fill the other state of a day / night group from one known color.
 * - [ensureContrast] / [ensureContrastAgainstBoth]: the nearest color along the lightness axis that meets a contrast ratio.
 * - [pickDayShade] / [pickNightShade]: pick existing shades of a tonal family by contrast instead of deriving new colors.
 * zh-CN:
 * - [deriveDark] / [deriveAccent]: 主题色可选的 `colorPrimaryDark` / `colorAccent`.
 * - [nightVariant] / [dayVariant]: 由日 / 夜组中已知的一个颜色补全另一个状态.
 * - [ensureContrast] / [ensureContrastAgainstBoth]: 沿亮度轴寻找满足对比度要求的最近颜色.
 * - [pickDayShade] / [pickNightShade]: 按对比度从色系的既有色阶中挑选, 而非派生新颜色.
 *
 * Inputs are treated as opaque; outputs are opaque.
 * zh-CN: 输入按不透明处理, 输出均为不透明颜色.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object ThemeColorDerivation {

    /** `R.color.window_background_light` (md_gray_50). */
    @JvmField
    val WINDOW_BACKGROUND_LIGHT = 0xFFFAFAFA.toInt()

    /** `R.color.window_background_night` (md_gray_900). */
    @JvmField
    val WINDOW_BACKGROUND_NIGHT = 0xFF212121.toInt()

    /** WCAG 1.4.11 minimum for non-text UI components. zh-CN: WCAG 1.4.11 对非文本界面元素的最低对比度. */
    const val UI_COMPONENT_CONTRAST = 3.0

    /** Roughly the lightness gap between Material 500 and 700 shades. zh-CN: 约为 Material 500 与 700 色阶之间的亮度差. */
    private const val DARK_LIGHTNESS_DROP = 0.08

    private const val ACCENT_CHROMA_GAIN = 1.25
    private const val ACCENT_MIN_CHROMA = 0.12

    /** Below this chroma a color is a tinted gray and keeps its chroma when varied. zh-CN: 低于此彩度视为带色调的灰色, 变换时保持原彩度. */
    private const val NEAR_ACHROMATIC_CHROMA = 0.015

    private const val NIGHT_CHROMA_GAIN = 4.0
    private const val NIGHT_CHROMA_CAP = 0.10
    private const val DAY_CHROMA_FACTOR = 0.35

    private const val LIGHTNESS_STEP = 0.01
    private const val REFINE_STEPS = 20

    fun deriveDark(primary: Int): Int {
        val lch = OkColor.toOkLch(primary)
        return OkColor.fromOkLch(lch.copy(l = (lch.l - DARK_LIGHTNESS_DROP).coerceAtLeast(0.0)))
    }

    /**
     * A more vivid color of the same hue that keeps at least [ratio] against both window backgrounds,
     * suitable for floating action buttons and other accents shown in day and night mode.
     * Near-gray primaries yield a gray accent.
     * zh-CN: 同色相, 彩度更高, 且与日 / 夜两种窗口背景的对比度都不低于 [ratio] 的颜色, 适用于悬浮按钮等日夜模式都会出现的强调元素.
     * 接近灰色的主色得到灰色的强调色.
     */
    @JvmOverloads
    fun deriveAccent(
        primary: Int,
        lightBackground: Int = WINDOW_BACKGROUND_LIGHT,
        darkBackground: Int = WINDOW_BACKGROUND_NIGHT,
        ratio: Double = UI_COMPONENT_CONTRAST,
    ): Int {
        val lch = OkColor.toOkLch(primary)
        val chroma = when {
            lch.c < NEAR_ACHROMATIC_CHROMA -> lch.c
            else -> max(lch.c * ACCENT_CHROMA_GAIN, ACCENT_MIN_CHROMA)
        }
        return ensureContrastAgainstBoth(OkColor.fromOkLch(lch.copy(c = chroma)), lightBackground, darkBackground, ratio)
    }

    /**
     * The night-state counterpart of a day color: lightness is pulled into roughly `0.25..0.5`
     * (never lighter than the input) and pale colors gain chroma so the hue stays recognizable.
     * For example md_teal_50 (#E0F2F1) maps close to md_teal_800 (#00695C).
     * zh-CN: 日间颜色在夜间状态下的对应色: 亮度收拢到约 0.25..0.5 (不会比输入更亮), 浅色提高彩度以保持色相可辨.
     * 例如 md_teal_50 (#E0F2F1) 映射到接近 md_teal_800 (#00695C) 的颜色.
     */
    fun nightVariant(day: Int): Int {
        val lch = OkColor.toOkLch(day)
        val lightness = min(lch.l, 0.25 + 0.25 * lch.l)
        val chroma = when {
            lch.c < NEAR_ACHROMATIC_CHROMA -> lch.c
            else -> max(lch.c, min(lch.c * NIGHT_CHROMA_GAIN, NIGHT_CHROMA_CAP))
        }
        return OkColor.fromOkLch(OkLch(lightness, chroma, lch.h))
    }

    /**
     * The day-state counterpart of a night color: lightness is raised to at least `0.85`
     * (never darker than the input) and chroma is softened toward a pastel.
     * zh-CN: 夜间颜色在日间状态下的对应色: 亮度提高到至少 0.85 (不会比输入更暗), 彩度降低为柔和的浅色.
     */
    fun dayVariant(night: Int): Int {
        val lch = OkColor.toOkLch(night)
        val lightness = max(lch.l, 0.85 + 0.10 * lch.l)
        val chroma = when {
            lch.c < NEAR_ACHROMATIC_CHROMA -> lch.c
            else -> lch.c * DAY_CHROMA_FACTOR
        }
        return OkColor.fromOkLch(OkLch(lightness, chroma, lch.h))
    }

    /**
     * The day shade of a tonal family: the lightest of [shades] that meets [ratio] against [background],
     * or the most contrasting shade when none does (roadmap D24). The order of [shades] does not matter.
     * zh-CN: 色系的日间色阶: [shades] 中与 [background] 对比度满足 [ratio] 的最浅者; 均不满足时取对比度最高者 (Roadmap D24).
     * [shades] 的顺序不影响结果.
     */
    @JvmOverloads
    fun pickDayShade(shades: List<Int>, background: Int = WINDOW_BACKGROUND_LIGHT, ratio: Double = UI_COMPONENT_CONTRAST): Int =
        pickShade(shades.sortedByDescending { ColorMetrics.relativeLuminance(it) }, background, ratio)

    /**
     * The night shade of a tonal family: the darkest of [shades] that meets [ratio] against [background],
     * or the most contrasting shade when none does (roadmap D24). The order of [shades] does not matter.
     * zh-CN: 色系的夜间色阶: [shades] 中与 [background] 对比度满足 [ratio] 的最深者; 均不满足时取对比度最高者 (Roadmap D24).
     * [shades] 的顺序不影响结果.
     */
    @JvmOverloads
    fun pickNightShade(shades: List<Int>, background: Int = WINDOW_BACKGROUND_NIGHT, ratio: Double = UI_COMPONENT_CONTRAST): Int =
        pickShade(shades.sortedBy { ColorMetrics.relativeLuminance(it) }, background, ratio)

    /**
     * Day and night colors for [seed]: the nearest colors with at least [ratio] against the day and night window
     * backgrounds (roadmap P4.2, consistent with D24).
     * zh-CN: [seed] 的日间与夜间颜色: 与日间 / 夜间窗口背景对比度不低于 [ratio] 的最近颜色 (Roadmap P4.2, 与 D24 一致).
     */
    @JvmOverloads
    fun dayNightPair(seed: Int, ratio: Double = UI_COMPONENT_CONTRAST): Pair<Int, Int> =
        ensureContrast(seed, WINDOW_BACKGROUND_LIGHT, ratio) to ensureContrast(seed, WINDOW_BACKGROUND_NIGHT, ratio)

    private fun pickShade(preferred: List<Int>, background: Int, ratio: Double): Int {
        require(preferred.isNotEmpty()) { "No shades to pick from" }
        return preferred.firstOrNull { ColorMetrics.contrastRatio(it, background) >= ratio }
            ?: preferred.maxBy { ColorMetrics.contrastRatio(it, background) }
    }

    /**
     * Returns [color] if it already meets [ratio] against [background]; otherwise the color of the same
     * hue and chroma (gamut permitting) whose lightness is closest to the original and meets the ratio.
     * When no lightness can meet it, returns black or white, whichever contrasts more.
     * zh-CN: [color] 与 [background] 的对比度已满足 [ratio] 时原样返回; 否则返回色相与彩度 (色域允许时) 不变,
     * 亮度最接近原色且满足要求的颜色. 任何亮度都无法满足时, 返回对比度更高的黑色或白色.
     */
    fun ensureContrast(color: Int, background: Int, ratio: Double): Int {
        val backgroundLuminance = ColorMetrics.relativeLuminance(background)
        return nearestByLightness(color) { candidate ->
            ColorMetrics.contrastRatioOfLuminance(ColorMetrics.relativeLuminance(candidate), backgroundLuminance) >= ratio
        } ?: listOf(BLACK, WHITE).maxBy { ColorMetrics.contrastRatio(it, background) }
    }

    /**
     * Like [ensureContrast], but the result must meet [ratio] against both backgrounds. When that is impossible,
     * returns the lightness that maximizes the weaker of the two contrasts.
     * zh-CN: 与 [ensureContrast] 相同, 但结果需同时满足与两个背景的对比度. 无法满足时, 返回使两者中较低对比度最大的亮度.
     */
    fun ensureContrastAgainstBoth(color: Int, lightBackground: Int, darkBackground: Int, ratio: Double): Int {
        val lightLuminance = ColorMetrics.relativeLuminance(lightBackground)
        val darkLuminance = ColorMetrics.relativeLuminance(darkBackground)
        fun weakerContrast(candidate: Int): Double {
            val luminance = ColorMetrics.relativeLuminance(candidate)
            return min(
                ColorMetrics.contrastRatioOfLuminance(luminance, lightLuminance),
                ColorMetrics.contrastRatioOfLuminance(luminance, darkLuminance),
            )
        }
        return nearestByLightness(color) { weakerContrast(it) >= ratio }
            ?: lightnessSweep(OkColor.toOkLch(color)).maxBy { weakerContrast(it) }
    }

    /**
     * Walks the lightness axis away from [color] in both directions and returns the passing color whose
     * lightness is closest to the original, refined by bisection; null when nothing passes.
     * Candidates are evaluated after 8-bit quantization, so the returned color itself passes.
     * zh-CN: 从 [color] 出发沿亮度轴向两个方向搜索, 返回亮度最接近原色的通过者 (经二分细化); 没有通过者时返回 null.
     * 候选色在 8 位量化后再判定, 因此返回的颜色本身一定通过.
     */
    private fun nearestByLightness(color: Int, accepts: (Int) -> Boolean): Int? {
        val opaque = color or OPAQUE
        if (accepts(opaque)) return opaque
        val lch = OkColor.toOkLch(opaque)
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        for (direction in intArrayOf(-1, 1)) {
            var failing = lch.l
            for (lightness in lightnessSteps(lch.l, direction)) {
                if (abs(lightness - lch.l) >= bestDistance) break
                if (!accepts(OkColor.fromOkLch(lch.copy(l = lightness)))) {
                    failing = lightness
                    continue
                }
                var passing = lightness
                repeat(REFINE_STEPS) {
                    val middle = (failing + passing) / 2
                    if (accepts(OkColor.fromOkLch(lch.copy(l = middle)))) passing = middle else failing = middle
                }
                best = OkColor.fromOkLch(lch.copy(l = passing))
                bestDistance = abs(passing - lch.l)
                break
            }
        }
        return best
    }

    private fun lightnessSteps(start: Double, direction: Int): Sequence<Double> = sequence {
        var lightness = start
        while (true) {
            lightness += direction * LIGHTNESS_STEP
            if (lightness <= 0.0) {
                yield(0.0)
                return@sequence
            }
            if (lightness >= 1.0) {
                yield(1.0)
                return@sequence
            }
            yield(lightness)
        }
    }

    private fun lightnessSweep(lch: OkLch): List<Int> = (0..100).map { OkColor.fromOkLch(lch.copy(l = it / 100.0)) }

    private const val OPAQUE = 0xFF shl 24

    private val BLACK = 0xFF000000.toInt()

    private val WHITE = 0xFFFFFFFF.toInt()

}
