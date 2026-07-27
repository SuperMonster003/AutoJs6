package org.autojs.autojs.ui.edit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEditorInteractionPaletteTest {

    @Test
    fun `dark editor keeps its contrasting foreground for controls`() {
        val colors = NativeEditorInteractionPalette.resolve(
            background = 0xFF1E1E1E.toInt(),
            foreground = 0xFFE8DBB8.toInt(),
        )

        assertEquals(0xFFE8DBB8.toInt(), colors.controlColor)
        assertEquals(0x4DE8DBB8, colors.selectionColor)
        assertTrue(
            NativeEditorInteractionPalette.contrastRatio(colors.background, colors.controlColor) >=
                NativeEditorInteractionPalette.MINIMUM_CONTROL_CONTRAST,
        )
    }

    @Test
    fun `light editor keeps its contrasting foreground for controls`() {
        val colors = NativeEditorInteractionPalette.resolve(
            background = 0xFFF3F2F3.toInt(),
            foreground = 0xFF202124.toInt(),
        )

        assertEquals(0xFF202124.toInt(), colors.controlColor)
        assertEquals(0x4D202124, colors.selectionColor)
        assertTrue(
            NativeEditorInteractionPalette.contrastRatio(colors.background, colors.controlColor) >=
                NativeEditorInteractionPalette.MINIMUM_CONTROL_CONTRAST,
        )
    }

    @Test
    fun `low contrast theme foreground falls back to the strongest neutral`() {
        val colors = NativeEditorInteractionPalette.resolve(
            background = 0xFF777777.toInt(),
            foreground = 0xFF808080.toInt(),
        )

        assertEquals(0xFF000000.toInt(), colors.controlColor)
        assertTrue(
            NativeEditorInteractionPalette.contrastRatio(colors.background, colors.controlColor) >=
                NativeEditorInteractionPalette.MINIMUM_CONTROL_CONTRAST,
        )
    }

    @Test
    fun `updates replace all interaction colors atomically`() {
        val updated = NativeEditorInteractionPalette.update(
            background = 0xFF101216.toInt(),
            foreground = 0xFFFAFAFA.toInt(),
        )

        assertEquals(updated, NativeEditorInteractionPalette.current())
        assertEquals(updated.controlColor and 0x00FFFFFF, updated.selectionColor and 0x00FFFFFF)
        assertEquals(0x4D, updated.selectionColor ushr 24)
    }
}
