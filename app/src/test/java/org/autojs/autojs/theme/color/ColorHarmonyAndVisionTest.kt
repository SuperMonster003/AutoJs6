package org.autojs.autojs.theme.color

import org.autojs.autojs.theme.color.ColorVisionSimulation.Deficiency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorHarmonyAndVisionTest {

    private val teal = 0xFF00897B.toInt()
    private val red = 0xFFE53935.toInt()
    private val green = 0xFF43A047.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val yellow = 0xFFFDD835.toInt()
    private val gray = 0xFF808080.toInt()

    private fun hue(color: Int) = OkColor.toOkLch(color).h

    @Test
    fun harmoniesRotateTheHueAndStartWithTheSeed() {
        ColorHarmony.Type.entries.forEach { type ->
            val colors = ColorHarmony.harmony(teal, type)
            assertEquals(type.name, type.offsets.size, colors.size)
            assertEquals(type.name, teal, colors.first())
            colors.zip(type.offsets).drop(1).forEach { (color, offset) ->
                val expected = OkColor.normalizeHue(hue(teal) + offset)
                val drift = ColorMetrics.hueDistance(expected, hue(color))
                assertTrue("${type.name} offset $offset drifted $drift", drift < 3.0)
                // Lightness is kept; chroma may only shrink to fit the gamut.
                assertEquals(OkColor.toOkLch(teal).l, OkColor.toOkLch(color).l, 0.01)
                assertTrue(OkColor.toOkLch(color).c <= OkColor.toOkLch(teal).c + 0.005)
            }
        }
        assertEquals(listOf(gray, gray, gray), ColorHarmony.harmony(gray, ColorHarmony.Type.TRIADIC))
        assertEquals(ColorHarmony.Type.SPLIT_COMPLEMENTARY, ColorHarmony.Type.of("splitComplementary"))
    }

    @Test
    fun tonesGoFromLightToDarkAndKeepTheHue() {
        listOf(teal, red, blue, yellow, gray).forEach { seed ->
            val tones = ColorHarmony.tones(seed)
            assertEquals(10, tones.size)
            val lightness = tones.map { OkColor.toOkLch(it).l }
            lightness.zipWithNext().forEach { (a, b) -> assertTrue("lightness must fall for #%06X: $lightness".format(seed and 0xFFFFFF), a > b) }
            assertTrue(tones.all { it ushr 24 == 0xFF })
            if (seed != gray) {
                tones.filter { OkColor.toOkLch(it).c > 0.04 }.forEach { tone ->
                    assertTrue(ColorMetrics.hueDistance(hue(seed), hue(tone)) < 6.0)
                }
            }
        }
        // The lightest tone is a tint: much less chroma than the seed.
        assertTrue(OkColor.toOkLch(ColorHarmony.tones(red).first()).c < OkColor.toOkLch(red).c * 0.5)
        assertEquals(5, ColorHarmony.tones(teal, 5).size)
    }

    @Test
    fun grayIsUnchangedByEveryDeficiency() {
        listOf(0xFF000000.toInt(), gray, 0xFFFFFFFF.toInt(), 0xFF3C3C3C.toInt()).forEach { color ->
            Deficiency.entries.forEach { deficiency ->
                val simulated = ColorVisionSimulation.simulate(color, deficiency)
                assertTrue("${deficiency.name} #%06X".format(color and 0xFFFFFF), ColorMetrics.deltaEOk(color, simulated) < 0.01)
            }
        }
        assertEquals(0x80, ColorVisionSimulation.simulate(0x80E53935.toInt(), Deficiency.PROTANOPIA) ushr 24)
    }

    @Test
    fun confusionPairsMoveCloser() {
        val original = ColorMetrics.deltaEOk(red, green)
        listOf(Deficiency.PROTANOPIA, Deficiency.DEUTERANOPIA).forEach { deficiency ->
            val simulated = ColorMetrics.deltaEOk(ColorVisionSimulation.simulate(red, deficiency), ColorVisionSimulation.simulate(green, deficiency))
            // The pair also differs in lightness, which no deficiency removes. zh-CN: 这对颜色的亮度也不同, 任何色觉缺陷都不会消除这一差异.
            assertTrue("${deficiency.name}: $simulated vs $original", simulated < original * 0.6)
        }
        // Tritanopia confuses blue with green rather than red with green.
        val blueGreen = ColorMetrics.deltaEOk(blue, green)
        val tritan = ColorMetrics.deltaEOk(ColorVisionSimulation.simulate(blue, Deficiency.TRITANOPIA), ColorVisionSimulation.simulate(green, Deficiency.TRITANOPIA))
        assertTrue("tritanopia: $tritan vs $blueGreen", tritan < blueGreen * 0.6)
        val redGreenTritan = ColorMetrics.deltaEOk(ColorVisionSimulation.simulate(red, Deficiency.TRITANOPIA), ColorVisionSimulation.simulate(green, Deficiency.TRITANOPIA))
        assertTrue(redGreenTritan > original * 0.6)
    }

}
