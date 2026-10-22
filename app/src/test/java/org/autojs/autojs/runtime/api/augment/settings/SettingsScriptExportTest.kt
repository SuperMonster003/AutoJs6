package org.autojs.autojs.runtime.api.augment.settings

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.runtime.api.augment.settings.SettingsScriptExport.Skip
import org.autojs.autojs.runtime.api.augment.settings.SettingsScriptExport.SkipReason
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import java.io.File
import java.io.IOException
import java.time.ZoneOffset

/**
 * docs/settings/ROADMAP.md P12.1: the arguments of `settings.export(path?, categories?)`, the file it writes and the
 * archive, which is the archive of the export wizard without credentials (T15 / R6).
 * zh-CN: ROADMAP P12.1: `settings.export(path?, categories?)` 的参数, 写入的文件与归档; 归档与导出向导相同, 但不含凭据 (T15 / R6).
 */
class SettingsScriptExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")

    private val device = SettingsArchiveManifest.Device("OPD2413", 35)

    @Test
    fun pathIsNullishOrANonBlankString() {
        assertNull(SettingsScriptExport.pathOf(null))
        assertNull(SettingsScriptExport.pathOf(Undefined.instance))
        assertEquals("out/a.zip", SettingsScriptExport.pathOf("out/a.zip"))
        assertEquals("out/", SettingsScriptExport.pathOf(StringBuilder("out/")))
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.pathOf("") }
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.pathOf("  ") }
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.pathOf(1) }
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.pathOf(true) }
    }

    @Test
    fun categoriesAreNullishAStringOrAnArrayOfStrings() {
        assertNull(SettingsScriptExport.categoryIds(null))
        assertNull(SettingsScriptExport.categoryIds(Undefined.instance))
        assertEquals(listOf("appearance"), SettingsScriptExport.categoryIds("appearance"))
        assertEquals(listOf("appearance", "general"), SettingsScriptExport.categoryIds(NativeArray(arrayOf<Any?>("appearance", "general"))))
        assertEquals(listOf("appearance", "general"), SettingsScriptExport.categoryIds(listOf("appearance", "general")))
        assertEquals(listOf("appearance"), SettingsScriptExport.categoryIds(arrayOf("appearance")))
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.categoryIds(NativeArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.categoryIds(emptyList<String>()) }
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.categoryIds(1) }
        val element = assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.categoryIds(listOf("appearance", 1)) }
        assertTrue(element.message!!, element.message!!.contains("categories[1]"))
    }

    @Test
    fun theDefaultCategoriesAreThoseOfTheWizardAndTheAutomaticBackups() {
        val selection = SettingsScriptExport.select(null)
        assertEquals(SettingsScriptExport.DEFAULT_CATEGORIES, selection.categories)
        assertEquals(SettingsAutoBackup.CATEGORIES, selection.categories)
        assertTrue(selection.skipped.isEmpty())
        assertTrue(SettingCategory.DATA_SHORTCUTS in selection.categories)
        assertTrue(SettingCategory.DATA_COLOR_LIBRARY in selection.categories)
        listOf(SettingCategory.CREDENTIALS, SettingCategory.DEVELOPER, SettingCategory.OTHER).forEach { assertFalse(it.id, it in selection.categories) }
    }

    @Test
    fun selectionKeepsPageOrderOnceEachAndSkipsCredentials() {
        val selection = SettingsScriptExport.select(listOf("other", "credentials", "appearance", "appearance"))
        assertEquals(listOf(SettingCategory.APPEARANCE, SettingCategory.OTHER), selection.categories)
        assertEquals(listOf(Skip("credentials", SkipReason.SENSITIVE)), selection.skipped)
        val unknown = assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.select(listOf("appearance", "nope")) }
        assertTrue(unknown.message!!, unknown.message!!.contains("\"nope\"") && unknown.message!!.contains("data_color_library"))
        assertThrows(IllegalArgumentException::class.java) { SettingsScriptExport.select(listOf("credentials")) }
    }

    @Test
    fun targetDefaultsToTheBackupDirectoryAndNeverReplacesADefaultName() {
        val wd = temp.newFolder("wd")
        val now = 1_761_080_400_000L // 2025-10-21T21:00:00Z
        val first = SettingsScriptExport.target(null, wd, now, ZoneOffset.UTC)
        assertEquals(File(wd, "backups/settings/autojs6-settings-20251021-210000.zip"), first)
        first.parentFile!!.mkdirs()
        first.writeText("x")
        assertEquals(File(wd, "backups/settings/autojs6-settings-20251021-210000-1.zip"), SettingsScriptExport.target(null, wd, now, ZoneOffset.UTC))
        val out = File(wd, "out")
        assertEquals(File(out, "autojs6-settings-20251021-210000.zip"), SettingsScriptExport.target("${out.path}/", wd, now, ZoneOffset.UTC))
        out.mkdirs()
        assertEquals(File(out, "autojs6-settings-20251021-210000.zip"), SettingsScriptExport.target(out.path, wd, now, ZoneOffset.UTC))
        // A named file is that file, replaced when it exists.
        assertEquals(first, SettingsScriptExport.target(first.path, wd, now, ZoneOffset.UTC))
    }

    @Test
    fun writesTheWizardArchiveWithoutCredentialsAndReportsTheSkippedCategories() {
        val selection = SettingsScriptExport.select(listOf("credentials", "appearance", "data_shortcuts", "data_color_library"))
        val snapshot = SettingsSnapshot(
            listOf(
                SettingsSnapshot.Category("appearance", linkedMapOf("default" to linkedMapOf("a" to TypedValue("int", 1), "b" to TypedValue("bool", true)))),
                SettingsSnapshot.Category("credentials", linkedMapOf("github_auth" to linkedMapOf("token" to TypedValue("string", "secret")))),
            ),
            unexportable = listOf("default:nan_float"),
        )
        val shortcuts = byteArrayOf(1, 2, 3)
        val data = listOf(SettingsArchiveWriter.DataFile("data_shortcuts", 1, "shortcuts.zip", shortcuts))
        val file = File(temp.newFolder("out"), "settings.zip").apply { writeText("old") }

        val result = SettingsScriptExport.write(file, selection, snapshot, data, 1_761_080_400_000L, host, device)

        assertEquals(file.absoluteFile, result.file)
        assertEquals(listOf("appearance", "data_shortcuts"), result.categories)
        assertEquals(3, result.entries)
        assertEquals(1, result.unexportable)
        assertEquals(listOf(Skip("credentials", SkipReason.SENSITIVE), Skip("data_color_library", SkipReason.EMPTY)), result.skipped)
        val reader = SettingsArchiveReader({ file.inputStream() })
        val manifest = reader.manifest()
        assertEquals(listOf("appearance"), manifest.categories.map { it.id })
        assertTrue(manifest.categories.none { it.sensitive })
        assertEquals(listOf("data_shortcuts"), manifest.data.map { it.id })
        assertEquals("6.8.0", manifest.host.versionName)
        assertEquals(TypedValue("int", 1), reader.category("appearance").category.stores.getValue("default").getValue("a"))
        assertArrayEquals(shortcuts, reader.data(manifest.data.single().file))
        assertEquals(listOf("settings.zip"), file.parentFile!!.list()!!.toList())
    }

    @Test
    fun aFailedWriteLeavesNoTemporaryFile() {
        val directory = temp.newFolder("out")
        val blocked = File(directory, "settings.zip").apply { mkdirs(); File(this, "inner").writeText("x") }
        val selection = SettingsScriptExport.select(listOf("appearance"))
        val snapshot = SettingsSnapshot(listOf(SettingsSnapshot.Category("appearance", emptyMap())))
        assertThrows(IOException::class.java) { SettingsScriptExport.write(blocked, selection, snapshot, emptyList(), 0L, host, device) }
        assertEquals(listOf("settings.zip"), directory.list()!!.toList())
    }

    @Test
    fun everyCategoryIsCountedInPageOrder() {
        val snapshot = SettingsSnapshot(
            listOf(SettingsSnapshot.Category("general", linkedMapOf("default" to linkedMapOf("a" to TypedValue("int", 1), "b" to TypedValue("int", 2))))),
        )
        val data = listOf(SettingsArchiveWriter.DataFile("data_color_library", 4, "color-library.json", byteArrayOf(0)))
        val entries = SettingsScriptExport.entries(snapshot, data)
        assertEquals(SettingCategory.ALL, entries.map { it.first })
        val counts = entries.associate { it.first.id to it.second }
        assertEquals(2, counts["general"])
        assertEquals(4, counts["data_color_library"])
        assertEquals(0, counts["data_shortcuts"])
        assertEquals(0, counts["appearance"])
    }
}
