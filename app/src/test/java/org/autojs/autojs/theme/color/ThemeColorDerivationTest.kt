package org.autojs.autojs.theme.color

import org.autojs.autojs.theme.color.ThemeColorDerivation.WINDOW_BACKGROUND_LIGHT
import org.autojs.autojs.theme.color.ThemeColorDerivation.WINDOW_BACKGROUND_NIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeColorDerivationTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private val samples: List<Int> = buildList {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            add((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
        // md_teal_50, md_teal_800, md_blue_500, md_yellow_500, md_blue_gray_50, md_gray_50, md_gray_900, theme default.
        addAll(listOf(0xFFE0F2F1, 0xFF00695C, 0xFF2196F3, 0xFFFFEB3B, 0xFFECEFF1, 0xFFFAFAFA, 0xFF212121, 0xFFFFDEAD).map { it.toInt() })
    }

    private fun lch(color: Int) = OkColor.toOkLch(color)

    /**
     * Hue is only meaningful, and 8-bit quantization only precise enough, for colors that are neither dark nor grayish.
     * zh-CN: 只有既不暗也不接近灰色的颜色, 色相才有意义, 8 位量化的精度也才足够.
     */
    private fun assertHueKept(original: Int, derived: Int, tolerance: Double) {
        val from = lch(original)
        val to = lch(derived)
        if (from.c > 0.06 && to.c > 0.06 && from.l > 0.3 && to.l > 0.3) {
            val drift = ColorMetrics.hueDistance(from.h, to.h)
            assertTrue("hue drift $drift for #%06X -> #%06X".format(original and 0xFFFFFF, derived and 0xFFFFFF), drift < tolerance)
        }
    }

    @Test
    fun darkLowersLightnessAndKeepsHue() {
        for (color in samples) {
            val dark = ThemeColorDerivation.deriveDark(color)
            val expected = max(0.0, lch(color).l - 0.08)
            assertEquals(expected, lch(dark).l, 0.006)
            assertHueKept(color, dark, 3.0)
        }
        assertEquals(black, ThemeColorDerivation.deriveDark(black))
    }

    @Test
    fun accentContrastsWithBothWindowBackgrounds() {
        for (color in samples) {
            val accent = ThemeColorDerivation.deriveAccent(color)
            val onLight = ColorMetrics.contrastRatio(accent, WINDOW_BACKGROUND_LIGHT)
            val onNight = ColorMetrics.contrastRatio(accent, WINDOW_BACKGROUND_NIGHT)
            assertTrue("#%06X light %.2f night %.2f".format(accent and 0xFFFFFF, onLight, onNight), min(onLight, onNight) >= 3.0)
            assertHueKept(color, accent, 5.0)
        }
    }

    @Test
    fun accentOfGrayStaysGray() {
        for (gray in listOf(0xFF777777, 0xFFFAFAFA, 0xFF212121).map { it.toInt() }) {
            assertTrue(lch(ThemeColorDerivation.deriveAccent(gray)).c < 0.02)
        }
    }

    @Test
    fun nightVariantOfTeal50IsCloseToTeal800() {
        val night = ThemeColorDerivation.nightVariant(0xFFE0F2F1.toInt())
        assertTrue(ColorMetrics.deltaEOk(night, 0xFF00695C.toInt()) < 0.05)
    }

    @Test
    fun nightVariantIsDarkAndKeepsHue() {
        for (color in samples) {
            val night = ThemeColorDerivation.nightVariant(color)
            val dayL = lch(color).l
            val nightL = lch(night).l
            assertTrue(nightL <= dayL + 0.006)
            assertTrue(nightL <= 0.5 + 0.006)
            assertHueKept(color, night, 3.0)
        }
    }

    @Test
    fun dayVariantIsLightAndKeepsHue() {
        for (color in samples) {
            val day = ThemeColorDerivation.dayVariant(color)
            val nightL = lch(color).l
            val dayL = lch(day).l
            assertTrue(dayL >= max(nightL, 0.85) - 0.006)
            assertHueKept(color, day, 5.0)
        }
    }

    @Test
    fun ensureContrastKeepsPassingColorsUnchanged() {
        assertEquals(black, ThemeColorDerivation.ensureContrast(black, white, 4.5))
        assertEquals(0xFF336699.toInt(), ThemeColorDerivation.ensureContrast(0x00336699, white, 4.5))
    }

    @Test
    fun ensureContrastMeetsEveryFeasibleTarget() {
        val backgrounds = listOf(WINDOW_BACKGROUND_LIGHT, WINDOW_BACKGROUND_NIGHT, white, black, 0xFF2196F3.toInt(), 0xFF777777.toInt())
        for (background in backgrounds) for (ratio in listOf(3.0, 4.5)) {
            val feasible = max(ColorMetrics.contrastRatio(black, background), ColorMetrics.contrastRatio(white, background)) >= ratio
            if (!feasible) continue
            for (color in samples) {
                val adjusted = ThemeColorDerivation.ensureContrast(color, background, ratio)
                assertTrue(ColorMetrics.contrastRatio(adjusted, background) >= ratio)
                assertHueKept(color, adjusted, 5.0)
            }
        }
    }

    @Test
    fun ensureContrastMovesOnlyAsFarAsNeeded() {
        val adjusted = ThemeColorDerivation.ensureContrast(0xFF777777.toInt(), white, 4.5)
        val ratio = ColorMetrics.contrastRatio(adjusted, white)
        assertTrue(ratio >= 4.5 && ratio < 4.7)
    }

    @Test
    fun ensureContrastFallsBackToBlackOrWhite() {
        // Against mid gray, black reaches 4.69 and white 4.48; 7:1 is impossible.
        assertEquals(black, ThemeColorDerivation.ensureContrast(0xFFFF0000.toInt(), 0xFF777777.toInt(), 7.0))
    }

    @Test
    fun ensureContrastAgainstBothMaximizesTheWeakerSideWhenImpossible() {
        val result = ThemeColorDerivation.ensureContrastAgainstBoth(0xFF2196F3.toInt(), WINDOW_BACKGROUND_LIGHT, WINDOW_BACKGROUND_NIGHT, 10.0)
        val weaker = min(
            ColorMetrics.contrastRatio(result, WINDOW_BACKGROUND_LIGHT),
            ColorMetrics.contrastRatio(result, WINDOW_BACKGROUND_NIGHT),
        )
        assertTrue("weaker contrast $weaker", weaker >= 3.5)
    }

    // md_teal_50..900 and md_yellow_50..900.
    private val teal = listOf(0xFFE0F2F1, 0xFFB2DFDB, 0xFF80CBC4, 0xFF4DB6AC, 0xFF26A69A, 0xFF009688, 0xFF00897B, 0xFF00796B, 0xFF00695C, 0xFF004D40).map { it.toInt() }
    private val yellow = listOf(0xFFFFFDE7, 0xFFFFF9C4, 0xFFFFF59D, 0xFFFFF176, 0xFFFFEE58, 0xFFFFEB3B, 0xFFFDD835, 0xFFFBC02D, 0xFFF9A825, 0xFFF57F17).map { it.toInt() }

    @Test
    fun shadesArePickedByContrastAgainstWindowBackgrounds() {
        // Teal 400 has 2.87:1 against the light background, so day is 500; teal 800 has 2.27:1 against the night one, so night is 700.
        assertEquals(0xFF009688.toInt(), ThemeColorDerivation.pickDayShade(teal))
        assertEquals(0xFF00796B.toInt(), ThemeColorDerivation.pickNightShade(teal))
        assertTrue(ColorMetrics.contrastRatio(ThemeColorDerivation.pickDayShade(teal), WINDOW_BACKGROUND_LIGHT) >= 3.0)
        assertTrue(ColorMetrics.contrastRatio(ThemeColorDerivation.pickNightShade(teal), WINDOW_BACKGROUND_NIGHT) >= 3.0)
        // The order of the input does not matter.
        assertEquals(ThemeColorDerivation.pickDayShade(teal), ThemeColorDerivation.pickDayShade(teal.shuffled(java.util.Random(7))))
        assertEquals(ThemeColorDerivation.pickNightShade(teal), ThemeColorDerivation.pickNightShade(teal.reversed()))
    }

    @Test
    fun dayNightPairsMeetContrastAgainstBothBackgrounds() {
        // A light wallpaper color gets darker for day and stays for night; a dark one the other way round.
        listOf(0xFFF8BBD0.toInt(), 0xFF1A237E.toInt(), 0xFF00897B.toInt(), 0xFFFFEB3B.toInt()).forEach { seed ->
            val (day, night) = ThemeColorDerivation.dayNightPair(seed)
            assertTrue("day of #%06X".format(seed and 0xFFFFFF), ColorMetrics.contrastRatio(day, WINDOW_BACKGROUND_LIGHT) >= 3.0)
            assertTrue("night of #%06X".format(seed and 0xFFFFFF), ColorMetrics.contrastRatio(night, WINDOW_BACKGROUND_NIGHT) >= 3.0)
        }
        val pink = 0xFFF8BBD0.toInt()
        assertEquals(pink, ThemeColorDerivation.dayNightPair(pink).second)
        assertTrue(lch(ThemeColorDerivation.dayNightPair(pink).first).l < lch(pink).l)
        val navy = 0xFF1A237E.toInt()
        assertEquals(navy, ThemeColorDerivation.dayNightPair(navy).first)
        assertTrue(lch(ThemeColorDerivation.dayNightPair(navy).second).l > lch(navy).l)
    }

    @Test
    fun shadeFallsBackToTheMostContrastingOne() {
        // No yellow reaches 3:1 against the light background; 900 contrasts most. At night 900 is the darkest passing shade.
        assertEquals(0xFFF57F17.toInt(), ThemeColorDerivation.pickDayShade(yellow))
        assertEquals(0xFFF57F17.toInt(), ThemeColorDerivation.pickNightShade(yellow))
        assertEquals(white, ThemeColorDerivation.pickDayShade(listOf(white), ratio = 21.0))
    }

}
