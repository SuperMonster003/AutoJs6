package org.autojs.autojs.theme.library

import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_ANDROID
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_CSS
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_INTELLIGENT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_WEB
import org.autojs.autojs.theme.library.ColorLibraryIds.SELECT_NONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSelectionCodecTest {

    private val materialCount = 256

    private fun decode(libraryId: Int, itemId: Int = SELECT_NONE, legacyIndex: Int = SELECT_NONE) =
        ColorSelectionCodec.decode(libraryId, itemId, legacyIndex, materialCount)

    @Test
    fun absentLibraryKeyFallsBackToTheLegacyIndex() {
        assertEquals(ColorSelection.Preset(COLOR_LIBRARY_ID_DEFAULT, null), decode(SELECT_NONE))
        assertEquals(ColorSelection.Palette, decode(SELECT_NONE, legacyIndex = 0))
        assertEquals(ColorSelection.Preset(COLOR_LIBRARY_ID_DEFAULT, null), decode(SELECT_NONE, legacyIndex = 1))
        assertEquals(ColorSelection.Preset(COLOR_LIBRARY_ID_MATERIAL, 0), decode(SELECT_NONE, legacyIndex = 2))
        assertEquals(ColorSelection.Preset(COLOR_LIBRARY_ID_MATERIAL, materialCount - 1), decode(SELECT_NONE, legacyIndex = materialCount + 1))
        assertEquals(ColorSelection.Unknown(SELECT_NONE, materialCount + 2), decode(SELECT_NONE, legacyIndex = materialCount + 2))
        // The legacy index is ignored once the library key is present. zh-CN: 库键存在时忽略 legacy 索引.
        assertEquals(ColorSelection.Preset(COLOR_LIBRARY_ID_CSS, 4), decode(COLOR_LIBRARY_ID_CSS, 4, legacyIndex = 0))
    }

    @Test
    fun presetLibrariesAndThePalette() {
        for (id in listOf(COLOR_LIBRARY_ID_DEFAULT, COLOR_LIBRARY_ID_MATERIAL, COLOR_LIBRARY_ID_ANDROID, COLOR_LIBRARY_ID_CSS, COLOR_LIBRARY_ID_WEB)) {
            assertEquals(ColorSelection.Preset(id, 3), decode(id, 3))
            assertEquals(ColorSelection.Preset(id, null), decode(id))
        }
        assertEquals(ColorSelection.Palette, decode(COLOR_LIBRARY_ID_PALETTE, 0))
        assertEquals(ColorSelection.Unknown(COLOR_LIBRARY_ID_INTELLIGENT, 0), decode(COLOR_LIBRARY_ID_INTELLIGENT, 0))
        assertEquals(ColorSelection.Unknown(0x06, 1), decode(0x06, 1))
        assertEquals(ColorSelection.Unknown(-5, 1), decode(-5, 1))
    }

    @Test
    fun userAndIntelligentSegments() {
        val plain = ColorLibraryIds.ofUserLibrary(5, intelligent = false)
        val intelligent = ColorLibraryIds.ofUserLibrary(5, intelligent = true)
        assertEquals(ColorSelection.UserColor(5, 7), decode(plain, 7))
        assertEquals(ColorSelection.UserColor(5, null), decode(plain))

        val user = decode(intelligent, 3) as ColorSelection.Intelligent
        assertEquals(3L, user.groupId)
        assertEquals(5L, user.userRowId)
        assertFalse(user.isPreset)

        val preset = decode(0x101, 2) as ColorSelection.Intelligent
        assertTrue(preset.isPreset)
        assertNull(preset.userRowId)
        assertEquals(ColorSelection.Intelligent(0x1FF, null), decode(0x1FF))
    }

    @Test
    fun everySelectionRoundTrips() {
        val selections = listOf(
            ColorSelection.Preset(COLOR_LIBRARY_ID_WEB, 12),
            ColorSelection.Preset(COLOR_LIBRARY_ID_DEFAULT, null),
            ColorSelection.Palette,
            ColorSelection.UserColor(1, 42),
            ColorSelection.UserColor(ColorLibraryIds.USER_ROW_ID_LIMIT - 1, null),
            ColorSelection.Intelligent(0x100, 0),
            ColorSelection.Intelligent(ColorLibraryIds.ofUserLibrary(9, intelligent = true), 4),
            ColorSelection.Unknown(0x06, 1),
        )
        for (selection in selections) {
            val (libraryId, itemId) = ColorSelectionCodec.encode(selection)
            assertEquals(selection, decode(libraryId, itemId))
        }
        assertEquals(COLOR_LIBRARY_ID_PALETTE to 0, ColorSelectionCodec.encode(ColorSelection.Palette))
    }

    @Test
    fun segmentsDoNotOverlap() {
        val last = ColorLibraryIds.USER_ROW_ID_LIMIT - 1
        assertTrue(ColorLibraryIds.isUserPlain(ColorLibraryIds.ofUserLibrary(last, intelligent = false)))
        assertFalse(ColorLibraryIds.isUserIntelligent(ColorLibraryIds.ofUserLibrary(last, intelligent = false)))
        assertTrue(ColorLibraryIds.isUserIntelligent(ColorLibraryIds.ofUserLibrary(last, intelligent = true)))
        assertFalse(ColorLibraryIds.isPreset(ColorLibraryIds.PRESET_INTELLIGENT_FIRST))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rowIdsBeyondTheSegmentAreRejected() {
        ColorLibraryIds.ofUserLibrary(ColorLibraryIds.USER_ROW_ID_LIMIT, intelligent = false)
    }

}
