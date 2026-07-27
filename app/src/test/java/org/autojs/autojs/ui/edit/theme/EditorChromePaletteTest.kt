package org.autojs.autojs.ui.edit.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorChromePaletteTest {

    @Test
    fun `palette updates background and foreground atomically`() {
        val background = 0xFF1E1E1E.toInt()
        val foreground = 0xFFEBDAB4.toInt()

        EditorChromePalette.update(background, foreground)

        assertEquals(EditorChromePalette.Colors(background, foreground), EditorChromePalette.current())
        assertEquals(foreground, EditorChromePalette.foreground(enabled = true))
        assertEquals(0x6DEBDAB4, EditorChromePalette.foreground(enabled = false))
    }
}
