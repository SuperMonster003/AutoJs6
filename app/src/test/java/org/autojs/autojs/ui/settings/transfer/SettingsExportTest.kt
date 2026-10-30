package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsDeviceKey
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.data.ColorLibraryDataCodec
import org.autojs.autojs.core.pref.data.ShortcutDataCodec
import org.autojs.autojs.external.shortcut.ShortcutBackupState
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * docs/settings/ROADMAP.md P6.4: the export writes the archive of appendix B within the size limit (T7) and maps every
 * reason of a failure to its own message.
 * zh-CN: ROADMAP P6.4: 导出在大小上限 (T7) 内写出附录 B 的归档, 每种失败原因各有提示.
 */
class SettingsExportTest {

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")

    private val device = SettingsArchiveManifest.Device("OPD2413", 35)

    private val key = SettingsDeviceKey.fromAndroidId("0123456789abcdef")

    private val snapshot = SettingsSnapshot(
        listOf(
            SettingsSnapshot.Category("appearance", linkedMapOf("default" to linkedMapOf("a" to TypedValue("int", 1), "b" to TypedValue("bool", true)))),
            SettingsSnapshot.Category("editor_log", emptyMap()),
            SettingsSnapshot.Category("credentials", linkedMapOf("github_auth" to linkedMapOf("token" to TypedValue("string", "secret")))),
        ),
        unexportable = listOf("default:nan_float"),
    )

    @Test
    fun writesTheManifestFirstAndCountsWhatWasWritten() {
        val out = ByteArrayOutputStream()
        val manifest = SettingsExporter.write(out, snapshot, 1_761_051_600_000L, host, device, key = key)
        val names = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertEquals(listOf("manifest.json", "categories/appearance.json", "categories/editor_log.json", "categories/credentials.json"), names)
        assertEquals(listOf(false, false, true), manifest.categories.map { it.sensitive })
        val result = SettingsExporter.result("autojs6-settings-20261021-210000.zip", manifest, snapshot)
        assertEquals(SettingsExporter.Result("autojs6-settings-20261021-210000.zip", 3, 3, 1), result)
        // P6.5: the credentials are sealed for the device key (D23) and read back only with it.
        val bytes = out.toByteArray()
        assertEquals("secret", SettingsArchiveReader({ ByteArrayInputStream(bytes) }, key = key).category("credentials").category.stores.getValue("github_auth").getValue("token").value)
        assertEquals(true, SettingsArchiveReader({ ByteArrayInputStream(bytes) }).isLocked("credentials"))
        assertEquals(false, SettingsArchiveReader({ ByteArrayInputStream(bytes) }).isLocked("appearance"))
        assertThrows(IllegalArgumentException::class.java) { SettingsExporter.write(ByteArrayOutputStream(), snapshot, 0L, host, device) }
    }

    @Test
    fun refusesAnArchiveBeyondTheLimit() {
        val error = assertThrows(SettingsArchiveException::class.java) {
            SettingsExporter.write(ByteArrayOutputStream(), snapshot, 0L, host, device, maxBytes = 64, key = key)
        }
        assertEquals(SettingsArchiveException.Reason.TOO_LARGE, error.reason)
        assertEquals(Failure.TOO_LARGE, Failure.of(error))
        assertEquals(16L * 1024 * 1024, SettingsArchive.MAX_BYTES)
        assertEquals("16 MB", SettingsTransferMessages.sizeLimit())
    }

    @Test
    fun aChosenDataCategoryWithoutEntriesIsLeftOutButOneThatCannotBeReadFails() {
        val app = ColorLibraryJson.AppInfo("6.8.0", 5781)
        val zone = ZoneId.of("Asia/Shanghai")
        val noLibraries = ColorLibraryDataCodec.encode(emptyList(), 0L, zone, app)
        val libraries = ColorLibraryDataCodec.encode(listOf(ColorLibraryJson.PlainLibrary("灰阶", listOf(ColorLibraryJson.Color(-0x616162)))), 0L, zone, app)
        val noShortcuts = ShortcutDataCodec.encode(ShortcutBackupState(emptyList()), 0L)
        val wanted = listOf(SettingCategory.DATA_SHORTCUTS, SettingCategory.DATA_COLOR_LIBRARY)

        val none = mapOf(SettingCategory.DATA_SHORTCUTS to noShortcuts, SettingCategory.DATA_COLOR_LIBRARY to noLibraries)
        assertEquals(emptyList<SettingsArchiveWriter.DataFile>(), SettingsExporter.dataFiles(wanted, none, emptyMap(), 0L, app))

        val some = mapOf(SettingCategory.DATA_SHORTCUTS to noShortcuts, SettingCategory.DATA_COLOR_LIBRARY to libraries)
        assertEquals(listOf("data_color_library"), SettingsExporter.dataFiles(wanted, some, emptyMap(), 0L, app).map { it.id })

        val unreadable = mapOf(SettingCategory.DATA_SHORTCUTS to noShortcuts, SettingCategory.DATA_COLOR_LIBRARY to null)
        val error = assertThrows(IOException::class.java) { SettingsExporter.dataFiles(wanted, unreadable, emptyMap(), 0L, app) }
        assertEquals("Cannot read the data of data_color_library", error.message)
    }

    @Test
    fun everyReasonHasItsOwnFailureAndMessage() {
        SettingsArchiveException.Reason.entries.forEach { reason ->
            assertEquals(reason.name, Failure.of(SettingsArchiveException(reason, "x")).name)
        }
        assertEquals(Failure.IO, Failure.of(IOException("No space left on device")))
        assertEquals(Failure.IO, Failure.of(SecurityException("Permission denied")))
        val messages = Failure.entries.map(SettingsTransferMessages::failureRes)
        assertEquals(Failure.entries.size, messages.toSet().size)
    }
}
