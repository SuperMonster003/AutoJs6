package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.theme.color.ColorMetrics
import org.autojs.autojs.theme.color.OkColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Argument handling and results of the perceptual `colors` functions (roadmap P6.1). The functions are called past
 * the color conversion of `toIntRhino`, which relies on `android.graphics.Color`.
 * zh-CN: 感知色彩相关 `colors` 函数的参数处理与结果 (Roadmap P6.1). 测试绕过依赖 `android.graphics.Color` 的 `toIntRhino` 颜色转换.
 */
class ColorsPerceptualTest {

    private val teal = 0xFF00897B.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val red = 0xFFF44336.toInt()

    private fun fails(block: () -> Unit) {
        assertThrows(RuntimeException::class.java) { block() }
    }

    @Test
    fun oklabAndOklchBuildersRoundTrip() {
        listOf(teal, red, 0xFFFFEB3B.toInt(), black, white).forEach { color ->
            val lab = OkColor.toOkLab(color)
            assertEquals(color, Colors.oklabRhino(lab.l, lab.a, lab.b))
            val lch = OkColor.toOkLch(color)
            assertEquals(color, Colors.oklchRhino(lch.l, lch.c, lch.h))
        }
        // Alpha as a fraction or 0..255; percentages for lightness. zh-CN: alpha 可为小数或 0..255; 亮度可写作百分比.
        assertEquals(0x80, Colors.oklchRhino(0.5, 0.1, 270, 0.5) ushr 24)
        assertEquals(0x40, Colors.oklabRhino(0.5, 0.0, 0.0, 64) ushr 24)
        assertEquals(Colors.oklchRhino(0.5, 0.1, 270), Colors.oklchRhino("50%", 0.1, 270))
        assertEquals(Colors.oklchRhino(0.5, 0.1, 270), Colors.oklchRhino(0.5, 0.1, -90))
        // Out of gamut chroma is reduced, hue kept. zh-CN: 超出色域时降低彩度, 保持色相.
        val vivid = Colors.oklchRhino(0.7, 0.9, 140)
        assertEquals(140.0, OkColor.toOkLch(vivid).h, 2.0)
        assertEquals(0.7, OkColor.toOkLch(Colors.oklabRhino(0.7, 0.5, 0.5)).l, 0.02)
        fails { Colors.oklchRhino(1.2, 0.1, 0) }
        fails { Colors.oklchRhino(0.5, -0.1, 0) }
        fails { Colors.oklabRhino(0.5, Double.NaN, 0) }
        fails { Colors.oklabRhino(0.5, "x", 0) }
    }

    @Test
    fun ensureContrastReachesTheRatioAndKeepsAlpha() {
        val result = Colors.ensureContrastOf(teal, white)
        assertTrue(ColorMetrics.contrastRatio(result, white) >= 4.5)
        assertTrue(ColorMetrics.contrastRatio(Colors.ensureContrastOf(teal, black, 7), black) >= 7.0)
        assertEquals(0x80, Colors.ensureContrastOf(teal and 0x80FFFFFF.toInt(), white) ushr 24)
        // Already enough: unchanged. zh-CN: 已经达标时保持不变.
        assertEquals(black, Colors.ensureContrastOf(black, white, 3))
        fails { Colors.ensureContrastOf(teal, white and 0x80FFFFFF.toInt()) }
        fails { Colors.ensureContrastOf(teal, white, 22) }
        fails { Colors.ensureContrastOf(teal, white, "x") }
    }

    @Test
    fun harmoniesTonesAndSimulation() {
        val triadic = Colors.harmonyOf(teal, "triadic")
        assertEquals(3, triadic.size)
        assertEquals(teal, triadic.first())
        assertEquals(2, Colors.harmonyOf(teal, "complementary").size)
        assertEquals(4, Colors.harmonyOf(teal, "tetradic").size)
        fails { Colors.harmonyOf(teal, "square") }
        fails { Colors.harmonyOf(teal, null) }

        assertEquals(10, Colors.tonesOf(teal).size)
        assertEquals(20, Colors.tonesOf(teal, 20).size)
        assertEquals(2, Colors.tonesOf(teal, "2").size)
        fails { Colors.tonesOf(teal, 1) }
        fails { Colors.tonesOf(teal, 21) }
        fails { Colors.tonesOf(teal, 2.5) }

        assertEquals(white, Colors.simulateOf(white, "deuteranopia"))
        assertTrue(Colors.simulateOf(red, "protanopia") != red)
        fails { Colors.simulateOf(teal, "achromatopsia") }
    }

    @Test
    fun oklabDetectorUsesTheThresholdScale() {
        fun distance(a: Int, b: Int) = ColorDetector.get(a, "oklab", 0).distance(b shr 16 and 0xFF, b shr 8 and 0xFF, b and 0xFF)
        assertEquals(255.0, distance(black, white), 1e-4)
        assertEquals(0.0, distance(teal, teal), 1e-9)
        assertEquals(ColorMetrics.deltaEOk(teal, red) * 255, distance(teal, red), 1e-6)
        // Shared with images.findColor; names are case-insensitive. zh-CN: 与 images.findColor 共用; 名称不区分大小写.
        assertTrue(ColorDetector.get(teal, "OKLab", 4).detectColor(0x00, 0x89, 0x7B))
        assertTrue(!ColorDetector.get(teal, "oklab", 4).detectColor(0x00, 0x96, 0x88))
    }

}
