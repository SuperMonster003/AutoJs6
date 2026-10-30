package org.autojs.autojs.core.pref.data

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupState
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.StateColors
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.theme.library.ColorLibraryJson.IntelligentLibrary
import org.autojs.autojs.theme.library.ColorLibraryJson.PlainLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * docs/settings/ROADMAP.md P10.2: the detail pages list the entries of the data files and keep the chosen ones
 * ([SettingsDataEntries]), the shortcuts by their ids, the color libraries by their index in the file.
 *
 * zh-CN: ROADMAP P10.2: 细化页列出数据文件中的条目并保留选中的条目, 快捷方式以 id 标识, 颜色库以其在文件中的下标标识.
 */
class SettingsDataEntriesTest {

    private fun argb(rgb: Long) = (0xFF000000 or rgb).toInt()

    private val shortcuts = ShortcutDataCodec.encode(
        ShortcutBackupState(
            listOf(
                ShortcutBackupEntry("copy-1", "脚本", "Script label", "script", "/sdcard/scripts/a.py", icon = null),
                ShortcutBackupEntry("copy-2", "Settings", "AutoJs6 settings", "settings", icon = null, status = ShortcutBackupEntry.Status.MISSING),
                ShortcutBackupEntry("copy-3", "Docs", "Documentation", "docs", icon = null),
            ),
        ),
        TIME,
    )

    private val libraries = listOf(
        PlainLibrary("我的品牌色", listOf(ColorLibraryJson.Color(argb(0x00695C), "主色", TIME, TIME))),
        IntelligentLibrary(
            "暖冷交替",
            IntelligentConfig(),
            listOf(IntelligentGroup(10L, "橙", position = 0, states = mapOf("day" to StateColors(argb(0xFFB74D))))),
        ),
        PlainLibrary("灰阶", listOf(ColorLibraryJson.Color(argb(0x9E9E9E)))),
    )

    private val app = ColorLibraryJson.AppInfo("6.8.0", 5781)

    private val colors = ColorLibraryDataCodec.encode(libraries, TIME, ZONE, app)

    @Test
    fun theEntriesAreTheShortcutsAndTheLibrariesOfTheFile() {
        assertEquals(
            listOf(
                SettingsDataEntries.Entry("copy-1", "脚本", "/sdcard/scripts/a.py"),
                SettingsDataEntries.Entry("copy-2", "Settings"),
                SettingsDataEntries.Entry("copy-3", "Docs"),
            ),
            SettingsDataEntries.entries("data_shortcuts", shortcuts.bytes),
        )
        assertEquals(
            listOf(SettingsDataEntries.Entry("0", "我的品牌色"), SettingsDataEntries.Entry("1", "暖冷交替"), SettingsDataEntries.Entry("2", "灰阶")),
            SettingsDataEntries.entries("data_color_library", colors.bytes),
        )
        assertThrows(IllegalArgumentException::class.java) { SettingsDataEntries.entries("appearance", shortcuts.bytes) }
        val broken = assertThrows(SettingsArchiveException::class.java) { SettingsDataEntries.entries("data_color_library", "{}".toByteArray()) }
        assertEquals(SettingsArchiveException.Reason.INVALID_FILE, broken.reason)
    }

    @Test
    fun theChosenShortcutsAreKeptInFileOrder() {
        assertSame(shortcuts, SettingsDataEntries.select(shortcuts, null, LATER))
        val selected = SettingsDataEntries.select(shortcuts, listOf("copy-3", "no-such-id", "copy-1"), LATER)!!
        assertEquals("data_shortcuts", selected.id)
        assertEquals(shortcuts.name, selected.name)
        assertEquals(2, selected.entries)
        val decoded = ShortcutDataCodec.decode(selected.bytes).entries
        assertEquals(listOf("copy-1", "copy-3"), decoded.map { it.id })
        assertEquals("/sdcard/scripts/a.py", decoded.first().path)
        // Nothing chosen, or only ids the file does not have: the category is not written.
        assertNull(SettingsDataEntries.select(shortcuts, emptyList(), LATER))
        assertNull(SettingsDataEntries.select(shortcuts, listOf("no-such-id"), LATER))
    }

    @Test
    fun theChosenLibrariesAreKeptWithTheirGroups() {
        val selected = SettingsDataEntries.select(colors, listOf("2", "1"), LATER, ZONE, app)!!
        assertEquals("data_color_library", selected.id)
        assertEquals(2, selected.entries)
        val document = ColorLibraryDataCodec.decode(selected.bytes)
        assertEquals(LATER, document.exportedAt)
        assertEquals(app, document.app)
        assertEquals(listOf("暖冷交替", "灰阶"), document.libraries.map { it.library.name })
        assertEquals(listOf("橙"), (document.libraries[0].library as IntelligentLibrary).groups.map { it.name })
        assertEquals(libraries[2], document.libraries[1].library)
        assertNull(SettingsDataEntries.select(colors, listOf("3"), LATER, ZONE, app))
    }

    @Test
    fun aFileWithoutEntriesIsNotWrittenWhateverWasChosen() {
        val empty = ColorLibraryDataCodec.encode(emptyList(), TIME, ZONE, app)
        assertNull(SettingsDataEntries.select(empty, null, LATER, ZONE, app))
        // A stale choice must not reach the decoder, which refuses a document without libraries.
        assertNull(SettingsDataEntries.select(empty, listOf("0"), LATER, ZONE, app))
    }

    @Test
    fun onlyTheDataCategoriesAreRefinable() {
        assertTrue(SettingsDataEntries.isRefinable(SettingCategory.DATA_SHORTCUTS))
        assertTrue(SettingsDataEntries.isRefinable(SettingCategory.DATA_COLOR_LIBRARY))
        assertFalse(SettingsDataEntries.isRefinable(SettingCategory.APPEARANCE))
        assertFalse(SettingsDataEntries.isRefinable(SettingCategory.CREDENTIALS))
    }

    private companion object {

        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

        /** 2026-10-20T15:30:00+08:00. */
        const val TIME = 1_792_481_400_000L

        /** One hour later, the time of the export the selection is written for. */
        const val LATER = TIME + 3_600_000L
    }
}
