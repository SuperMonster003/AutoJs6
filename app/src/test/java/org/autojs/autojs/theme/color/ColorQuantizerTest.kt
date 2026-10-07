package org.autojs.autojs.theme.color

import org.autojs.autojs.theme.color.ColorQuantizer.Swatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorQuantizerTest {

    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()

    private fun pixels(vararg runs: Pair<Int, Int>) = runs.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    @Test
    fun twoColorsComeBackExactlyWithTheirShares() {
        val result = ColorQuantizer.quantize(pixels(blue to 30, red to 70), 5)
        assertEquals(listOf(Swatch(red, 70), Swatch(blue, 30)), result)
    }

    @Test
    fun wellSeparatedColorsAreRecovered() {
        val colors = listOf(0xFFF44336, 0xFF4CAF50, 0xFF2196F3, 0xFFFFEB3B, 0xFF9C27B0, 0xFF212121).map { it.toInt() }
        val input = pixels(*colors.mapIndexed { index, color -> color to (60 - index * 5) }.toTypedArray())
        val result = ColorQuantizer.quantize(input, 6)
        assertEquals(colors, result.map { it.color })
        assertEquals(colors.indices.map { 60 - it * 5 }, result.map { it.population })
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val input = pixels(red to 10, (blue and 0x7FFFFFFF) to 50, (0x80000000.toInt() or (blue and 0xFFFFFF)) to 5)
        // Alpha 0x7F is dropped, alpha 0x80 counts as the opaque color. zh-CN: alpha 0x7F 被忽略, alpha 0x80 计为不透明颜色.
        assertEquals(listOf(Swatch(red, 10), Swatch(blue, 5)), ColorQuantizer.quantize(input, 3))
        assertTrue(ColorQuantizer.quantize(intArrayOf(0x00FFFFFF, 0x10000000), 3).isEmpty())
    }

    @Test
    fun gradientsQuantizeDeterministically() {
        val gray = IntArray(256 * 4) { 0xFF000000.toInt() or ((it / 4) * 0x010101) }
        val first = ColorQuantizer.quantize(gray, 4)
        assertEquals(4, first.size)
        assertEquals(gray.size, first.sumOf { it.population })
        assertEquals(first.sortedByDescending { it.population }.map { it.population }, first.map { it.population })
        assertEquals(first, ColorQuantizer.quantize(gray, 4))
        // Four levels of a gray ramp land roughly at its quarters. zh-CN: 灰阶的四个代表大致位于四等分处.
        val levels = first.map { it.color and 0xFF }.sorted()
        assertTrue("levels $levels", levels.zip(listOf(32, 96, 160, 224)).all { (level, expected) -> kotlin.math.abs(level - expected) <= 24 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun countIsLimited() {
        ColorQuantizer.quantize(intArrayOf(red), ColorQuantizer.MAX_COLORS + 1)
    }

}
