package org.autojs.autojs.theme.color

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMetricsTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val gray77 = 0xFF777777.toInt()

    @Test
    fun relativeLuminanceFollowsWcag() {
        assertEquals(1.0, ColorMetrics.relativeLuminance(white), 1e-9)
        assertEquals(0.0, ColorMetrics.relativeLuminance(black), 1e-9)
        assertEquals(0.18447, ColorMetrics.relativeLuminance(gray77), 1e-4)
        // Window backgrounds (md_gray_50 / md_gray_900).
        assertEquals(0.9560, ColorMetrics.relativeLuminance(0xFFFAFAFA.toInt()), 1e-3)
        assertEquals(0.0152, ColorMetrics.relativeLuminance(0xFF212121.toInt()), 1e-3)
    }

    @Test
    fun contrastRatioMatchesKnownPairsInEitherOrder() {
        assertEquals(21.0, ColorMetrics.contrastRatio(white, black), 1e-9)
        assertEquals(21.0, ColorMetrics.contrastRatio(black, white), 1e-9)
        assertEquals(4.478, ColorMetrics.contrastRatio(gray77, white), 1e-3)
        assertEquals(1.0, ColorMetrics.contrastRatio(gray77, gray77), 1e-12)
    }

    @Test
    fun deltaEOkIsAMetric() {
        val teal = 0xFF00695C.toInt()
        val orange = 0xFFFFB74D.toInt()
        assertEquals(0.0, ColorMetrics.deltaEOk(teal, teal), 0.0)
        assertEquals(1.0, ColorMetrics.deltaEOk(black, white), 1e-6)
        assertEquals(ColorMetrics.deltaEOk(teal, orange), ColorMetrics.deltaEOk(orange, teal), 1e-12)
        // Neighbouring 8-bit grays are below the just-noticeable difference.
        assertEquals(true, ColorMetrics.deltaEOk(0xFF808080.toInt(), 0xFF818181.toInt()) < 0.02)
    }

    @Test
    fun hueDistanceTakesTheShortWayAround() {
        assertEquals(20.0, ColorMetrics.hueDistance(350.0, 10.0), 1e-9)
        assertEquals(180.0, ColorMetrics.hueDistance(0.0, 180.0), 1e-9)
        assertEquals(0.0, ColorMetrics.hueDistance(90.0, 450.0), 1e-9)
    }

}
