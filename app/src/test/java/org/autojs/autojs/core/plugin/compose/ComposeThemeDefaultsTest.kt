package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.ComposeUiThemeColors as Colors
import org.autojs.plugin.compose.api.model.ThemeSpec
import org.junit.Assert.*
import org.junit.Test

class ComposeThemeDefaultsTest {
    @Test fun absentScriptThemeKeepsTheExactLiveHostSnapshot() {
        val host = ThemeSpec(0xff123456.toInt(), mapOf(Colors.PRIMARY to 0xff456789.toInt()),
            dark = true, dynamicColor = true, fontFamily = "sans-serif", fontScale = 1.25)
        assertSame(host, resolveComposeTheme(host, null))
    }

    @Test fun partialColorOverridesFollowLaterHostSeedNightModeAndTypographyChanges() {
        val override = ThemeSpec(colorOverrides = mapOf(Colors.PRIMARY to 0xffabcdef.toInt()))
        val first = ThemeSpec(0xff123456.toInt(), mapOf(Colors.PRIMARY to 1, Colors.SURFACE to 2),
            dark = false, fontFamily = "sans-serif", fontScale = 1.0)
        val later = ThemeSpec(0xff654321.toInt(), mapOf(Colors.PRIMARY to 3, Colors.SURFACE to 4),
            dark = true, fontFamily = "serif", fontScale = 1.5)
        val before = resolveComposeTheme(first, override)
        val after = resolveComposeTheme(later, override)
        assertEquals(first.seedArgb, before.seedArgb)
        assertEquals(later.seedArgb, after.seedArgb)
        assertEquals(false, before.dark)
        assertEquals(true, after.dark)
        assertEquals("serif", after.fontFamily)
        assertEquals(1.5, requireNotNull(after.fontScale), 0.0)
        assertEquals(0xffabcdef.toInt(), after.colorOverrides[Colors.PRIMARY])
        assertEquals(4, after.colorOverrides[Colors.SURFACE])
        assertEquals(2, before.colorOverrides[Colors.SURFACE])
        assertEquals(3, later.colorOverrides[Colors.PRIMARY])
        assertEquals(setOf(Colors.PRIMARY), override.colorOverrides.keys)
    }

    @Test fun explicitSeedNightAndTypographyStayFixedWhileUnspecifiedHostColorsStillUpdate() {
        val override = ThemeSpec(0xff998877.toInt(), mapOf(Colors.ON_PRIMARY to 0xff010203.toInt()),
            dark = false, dynamicColor = true, fontFamily = "monospace", fontScale = 0.85)
        for (host in listOf(
            ThemeSpec(0xff123456.toInt(), mapOf(Colors.SURFACE to 1), dark = true, fontFamily = "sans-serif", fontScale = 1.0),
            ThemeSpec(0xff654321.toInt(), mapOf(Colors.SURFACE to 2), dark = null, fontFamily = "serif", fontScale = 1.5),
        )) {
            val resolved = resolveComposeTheme(host, override)
            assertEquals(override.seedArgb, resolved.seedArgb)
            assertEquals(false, resolved.dark)
            assertTrue(resolved.dynamicColor)
            assertEquals("monospace", resolved.fontFamily)
            assertEquals(0.85, requireNotNull(resolved.fontScale), 0.0)
            assertEquals(override.colorOverrides[Colors.ON_PRIMARY], resolved.colorOverrides[Colors.ON_PRIMARY])
            assertEquals(host.colorOverrides[Colors.SURFACE], resolved.colorOverrides[Colors.SURFACE])
        }
    }
}
