package org.autojs.autojs.ui.doc

import org.autojs.autojs.theme.color.ColorMetrics
import org.autojs.autojs.theme.color.ThemeColorDerivation
import org.autojs.autojs.ui.doc.OfflineDocsUnavailablePalette.Companion.PRIMARY_CONTAINER_CONTRAST_ON_DARK
import org.autojs.autojs.ui.doc.OfflineDocsUnavailablePalette.Companion.TEXT_CONTRAST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDocsUnavailablePaletteTest {

    private data class Surface(val name: String, val background: Int, val foreground: Int)

    private val surfaces = listOf(
        // R.color.window_background with R.color.text_color_primary (day / dusk).
        Surface("app day", 0xFFFAFAFA.toInt(), 0xDE212121.toInt()),
        Surface("app night", 0xFF212121.toInt(), 0xDEBFBFBF.toInt()),
        // Typical editor palettes.
        Surface("editor dark", 0xFF282A36.toInt(), 0xFFF8F8F2.toInt()),
        Surface("editor light", 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
        Surface("editor mid gray", 0xFF808080.toInt(), 0xFF000000.toInt()),
    )

    private val accents: List<Int> = buildList {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            add((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
        // Theme default (web_navajo_white), md_blue_gray_900, md_light_blue_500, md_yellow_500, md_pink_500.
        addAll(listOf(0xFFFFDEAD, 0xFF263238, 0xFF03A9F4, 0xFFFFEB3B, 0xFFE91E63).map { it.toInt() })
    }

    private fun assertContrast(label: String, first: Int, second: Int, ratio: Double) {
        val actual = ColorMetrics.contrastRatio(first, second)
        assertTrue(
            "%s: #%06X on #%06X has %.2f, expected >= %.2f".format(label, first and 0xFFFFFF, second and 0xFFFFFF, actual, ratio),
            actual >= ratio,
        )
    }

    @Test
    fun everyTextAndOutlineIsReadable() {
        for (surface in surfaces) for (accent in accents) {
            val palette = OfflineDocsUnavailablePalette.derive(surface.background, surface.foreground, accent)
            val label = "%s, accent #%06X".format(surface.name, accent and 0xFFFFFF)
            assertContrast("$label, title", palette.title, palette.background, TEXT_CONTRAST)
            assertContrast("$label, body", palette.body, palette.background, TEXT_CONTRAST)
            assertContrast("$label, icon", palette.icon, palette.iconContainer, TEXT_CONTRAST)
            assertContrast("$label, primary action", palette.onPrimaryContainer, palette.primaryContainer, TEXT_CONTRAST)
            assertContrast("$label, secondary action", palette.secondaryContent, palette.background, TEXT_CONTRAST)
            assertContrast("$label, secondary outline", palette.secondaryOutline, palette.background, ThemeColorDerivation.UI_COMPONENT_CONTRAST)
        }
    }

    @Test
    fun everyColorIsOpaque() {
        val palette = OfflineDocsUnavailablePalette.derive(0x00FAFAFA, 0x80212121.toInt(), 0x00FFDEAD)
        listOf(
            palette.background, palette.title, palette.body, palette.iconContainer, palette.icon,
            palette.primaryContainer, palette.onPrimaryContainer, palette.secondaryContent, palette.secondaryOutline,
        ).forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test
    fun defaultThemeColorKeepsItsFillOnTheDaySurface() {
        val palette = OfflineDocsUnavailablePalette.derive(0xFFFAFAFA.toInt(), 0xDE212121.toInt(), 0xFFFFDEAD.toInt())
        assertEquals(0xFFFFDEAD.toInt(), palette.primaryContainer)
        assertEquals(0xFF212121.toInt(), palette.onPrimaryContainer)
    }

    @Test
    fun darkThemeColorIsLiftedOnTheNightSurface() {
        val background = 0xFF212121.toInt()
        val palette = OfflineDocsUnavailablePalette.derive(background, 0xDEBFBFBF.toInt(), 0xFF263238.toInt())
        assertContrast("primary container", palette.primaryContainer, background, PRIMARY_CONTAINER_CONTRAST_ON_DARK)
    }

}
