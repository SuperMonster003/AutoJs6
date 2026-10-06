package org.autojs.autojs.theme.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OkColorTest {

    private fun assertLch(expectedL: Double, expectedC: Double, expectedH: Double, actual: OkLch) {
        assertEquals("L", expectedL, actual.l, 1e-3)
        assertEquals("C", expectedC, actual.c, 1e-3)
        assertEquals("h", expectedH, actual.h, 0.1)
    }

    @Test
    fun matchesCssColor4ReferenceValues() {
        assertLch(0.62796, 0.25768, 29.2339, OkColor.toOkLch(0xFFFF0000.toInt()))
        assertLch(0.86644, 0.29483, 142.4953, OkColor.toOkLch(0xFF00FF00.toInt()))
        assertLch(0.45201, 0.31321, 264.052, OkColor.toOkLch(0xFF0000FF.toInt()))

        val red = OkColor.toOkLab(0xFFFF0000.toInt())
        assertEquals(0.22486, red.a, 1e-3)
        assertEquals(0.12585, red.b, 1e-3)
    }

    @Test
    fun achromaticColorsHaveZeroChromaAndHue() {
        val white = OkColor.toOkLch(0xFFFFFFFF.toInt())
        assertEquals(1.0, white.l, 1e-6)
        assertEquals(0.0, white.c, 1e-6)
        assertEquals(0.0, white.h, 0.0)

        val black = OkColor.toOkLch(0xFF000000.toInt())
        assertEquals(0.0, black.l, 1e-9)

        val gray = OkColor.toOkLch(0xFF777777.toInt())
        assertTrue(gray.c < OkColor.ACHROMATIC_CHROMA)
        assertEquals(0.0, gray.h, 0.0)
    }

    @Test
    fun alphaIsIgnoredOnInputAndAppliedOnOutput() {
        assertEquals(OkColor.toOkLab(0xFF336699.toInt()), OkColor.toOkLab(0x00336699))
        val lab = OkColor.toOkLab(0xFF336699.toInt())
        assertEquals(0x80336699.toInt(), OkColor.fromOkLab(lab, alpha = 0x80))
    }

    @Test
    fun eightBitRoundTripIsLossless() {
        var maxError = 0
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val back = OkColor.fromOkLch(OkColor.toOkLch(argb))
            maxError = maxOf(
                maxError,
                abs(((back shr 16) and 0xFF) - r),
                abs(((back shr 8) and 0xFF) - g),
                abs((back and 0xFF) - b),
            )
        }
        assertTrue("max channel error $maxError", maxError <= 1)
    }

    @Test
    fun gamutMappingKeepsLightnessAndHueAndMaximizesChroma() {
        for (step in 1..19) {
            val l = step * 0.05
            for (h in 0 until 360 step 10) {
                val requested = OkLch(l, 0.4, h.toDouble())
                val mapped = OkColor.gamutMap(requested)
                assertTrue("in gamut at L=$l h=$h", OkColor.isInGamut(mapped))
                assertEquals(l, mapped.l, 0.0)
                assertEquals(h.toDouble(), mapped.h, 1e-9)
                assertTrue(mapped.c <= requested.c)
                assertFalse("chroma not maximal at L=$l h=$h", OkColor.isInGamut(mapped.copy(c = mapped.c + 2e-3)))
            }
        }
    }

    @Test
    fun gamutMappingClampsLightnessExtremes() {
        assertEquals(OkLch(1.0, 0.0, 120.0), OkColor.gamutMap(OkLch(1.2, 0.3, 120.0)))
        assertEquals(OkLch(0.0, 0.0, 120.0), OkColor.gamutMap(OkLch(-0.1, 0.3, 120.0)))
        assertEquals(0xFFFFFFFF.toInt(), OkColor.fromOkLch(OkLch(1.2, 0.3, 120.0)))
        assertEquals(0xFF000000.toInt(), OkColor.fromOkLch(OkLch(-0.1, 0.3, 120.0)))
    }

    @Test
    fun mappedColorsKeepTheirHueAfterQuantization() {
        for (h in 0 until 360 step 15) {
            val argb = OkColor.fromOkLch(OkLch(0.65, 0.4, h.toDouble()))
            val back = OkColor.toOkLch(argb)
            assertTrue("hue drift at h=$h: ${back.h}", ColorMetrics.hueDistance(back.h, h.toDouble()) < 2.0)
        }
    }

    @Test
    fun hueIsNormalizedIntoHalfOpenRange() {
        assertEquals(0.0, OkColor.normalizeHue(360.0), 0.0)
        assertEquals(350.0, OkColor.normalizeHue(-10.0), 1e-9)
        assertEquals(10.0, OkColor.normalizeHue(730.0), 1e-9)
        assertEquals(0.0, OkColor.normalizeHue(-1e-15), 0.0)
    }

}
