package org.autojs.autojs.theme.color

import kotlin.math.max

/**
 * Color harmonies and tonal scales in OKLCH (roadmap P4.3): harmonies rotate the hue and keep lightness and chroma;
 * tonal scales keep the hue and step the lightness. Results are mapped into the sRGB gamut by lowering chroma.
 * zh-CN: 在 OKLCH 中计算和谐色与色阶 (Roadmap P4.3): 和谐色旋转色相, 保持亮度与彩度; 色阶保持色相, 逐级改变亮度.
 * 结果通过降低彩度映射到 sRGB 色域内.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorHarmony {

    /** Hue offsets in degrees; the first is always the seed itself. zh-CN: 色相偏移 (度); 第一个总是种子色本身. */
    enum class Type(val key: String, val offsets: List<Double>) {
        COMPLEMENTARY("complementary", listOf(0.0, 180.0)),
        ANALOGOUS("analogous", listOf(0.0, -30.0, 30.0)),
        TRIADIC("triadic", listOf(0.0, 120.0, 240.0)),
        SPLIT_COMPLEMENTARY("splitComplementary", listOf(0.0, 150.0, 210.0)),
        TETRADIC("tetradic", listOf(0.0, 90.0, 180.0, 270.0));

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key }
        }
    }

    /** Lightness of the default ten tones, light to dark, near Material 50 to 900. zh-CN: 默认十级色阶的亮度, 由浅到深, 接近 Material 50 至 900. */
    private val TONE_LIGHTNESS = doubleArrayOf(0.97, 0.92, 0.85, 0.77, 0.69, 0.62, 0.55, 0.48, 0.40, 0.32)

    private const val OPAQUE = 0xFF shl 24

    /**
     * The colors of [type] for [seed]: the seed first, then the rotated hues in the order of [Type.offsets].
     * Near-gray seeds have no meaningful hue, so their harmonies are the seed repeated.
     * zh-CN: [seed] 在 [type] 下的和谐色: 先是种子色本身, 然后按 [Type.offsets] 的顺序给出旋转后的色相.
     * 接近灰色的种子色没有有意义的色相, 其和谐色为种子色本身的重复.
     */
    fun harmony(seed: Int, type: Type): List<Int> {
        val opaque = seed or OPAQUE
        val lch = OkColor.toOkLch(opaque)
        return type.offsets.map { offset ->
            if (offset == 0.0 || lch.c < NEAR_ACHROMATIC_CHROMA) opaque
            else OkColor.fromOkLch(lch.copy(h = OkColor.normalizeHue(lch.h + offset)))
        }
    }

    /**
     * A tonal scale of [seed] from light to dark ([count] tones, 2 to 10 by default spacing or evenly spread for
     * other counts). Chroma tapers toward the light end, so light tones read as tints like Material 50 to 100.
     * zh-CN: [seed] 由浅到深的色阶 ([count] 级). 越浅彩度越低, 使浅色阶接近 Material 50 至 100 那样的浅色调.
     */
    fun tones(seed: Int, count: Int = TONE_LIGHTNESS.size): List<Int> {
        require(count >= 2) { "At least two tones" }
        val lch = OkColor.toOkLch(seed or OPAQUE)
        val lightness = when (count) {
            TONE_LIGHTNESS.size -> TONE_LIGHTNESS.toList()
            else -> List(count) { index -> TONE_LIGHTNESS.first() + (TONE_LIGHTNESS.last() - TONE_LIGHTNESS.first()) * index / (count - 1) }
        }
        return lightness.map { l ->
            val chroma = when {
                lch.c < NEAR_ACHROMATIC_CHROMA -> lch.c
                l > lch.l -> lch.c * max(LIGHT_END_CHROMA, 1.0 - (l - lch.l) / max(1.0 - lch.l, 1e-6) * (1.0 - LIGHT_END_CHROMA))
                else -> lch.c * max(DARK_END_CHROMA, 1.0 - (lch.l - l) / max(lch.l, 1e-6) * (1.0 - DARK_END_CHROMA))
            }
            OkColor.fromOkLch(OkLch(l, chroma, lch.h))
        }
    }

    /** Below this chroma a color counts as gray. zh-CN: 低于此彩度视为灰色. */
    private const val NEAR_ACHROMATIC_CHROMA = 0.015

    /** Chroma share kept at the lightest and darkest tones. zh-CN: 最浅与最深色阶保留的彩度比例. */
    private const val LIGHT_END_CHROMA = 0.25
    private const val DARK_END_CHROMA = 0.7

}
