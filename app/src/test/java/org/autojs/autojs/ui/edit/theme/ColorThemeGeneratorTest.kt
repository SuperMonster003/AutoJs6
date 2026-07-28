package org.autojs.autojs.ui.edit.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorThemeGeneratorTest {

    @Test
    fun `Gruvbox palette uses the requested 48 percent role separation`() {
        val palette = ColorThemeGenerator.generate(
            backgroundArgb = 0xFF1E1E1E.toInt(),
            titleArgb = 0xFFEBDAB4.toInt(),
            separation = 0.48,
        )

        assertEquals(0xFFFFF3D8.toInt(), palette.a)
        assertEquals(0xFFEBDAB4.toInt(), palette.b)
        assertEquals(0xFFD6CAAF.toInt(), palette.c)
        assertOpaque(palette.a, palette.b, palette.c)
        assertTrue(
            palette.categoryA.contrastAgainstBackground >
                palette.titleB.contrastAgainstBackground,
        )
        assertTrue(
            palette.titleB.contrastAgainstBackground >
                palette.subtitleC.contrastAgainstBackground,
        )
    }

    @Test
    fun `zero separation preserves an already readable title`() {
        val title = 0xFFEBDAB4.toInt()
        val palette = ColorThemeGenerator.generate(
            backgroundArgb = 0xFF1E1E1E.toInt(),
            titleArgb = title,
            separation = 0.0,
        )

        assertEquals(title, palette.a)
        assertEquals(title, palette.b)
        assertEquals(title, palette.c)
    }

    @Test
    fun `gray input produces only opaque gray roles`() {
        val palette = ColorThemeGenerator.generate(
            backgroundArgb = 0xFFF5F5F5.toInt(),
            titleArgb = 0xFF777777.toInt(),
            separation = 0.48,
        )

        listOf(palette.categoryA, palette.titleB, palette.subtitleC).forEach { swatch ->
            assertOpaque(swatch.argb)
            assertEquals(channel(swatch.argb, 16), channel(swatch.argb, 8))
            assertEquals(channel(swatch.argb, 8), channel(swatch.argb, 0))
            assertNull(swatch.hueDegrees)
            assertTrue(swatch.contrastAgainstBackground >= 4.5)
        }
    }

    @Test
    fun `input alpha is ignored consistently`() {
        val translucent = ColorThemeGenerator.generate(
            backgroundArgb = 0x121E1E1E,
            titleArgb = 0x34EBDAB4,
            separation = 0.48,
        )
        val opaque = ColorThemeGenerator.generate(
            backgroundArgb = 0xFF1E1E1E.toInt(),
            titleArgb = 0xFFEBDAB4.toInt(),
            separation = 0.48,
        )

        assertEquals(opaque, translucent)
    }

    private fun assertOpaque(vararg colors: Int) {
        colors.forEach { color ->
            assertEquals(0xFF, color ushr 24)
        }
    }

    private fun channel(argb: Int, shift: Int): Int = argb ushr shift and 0xFF
}
