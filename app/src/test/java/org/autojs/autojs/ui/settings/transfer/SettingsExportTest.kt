package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * docs/settings/ROADMAP.md P6.4: the export writes the archive of appendix B within the size limit (T7) and maps every
 * reason of a failure to its own message.
 * zh-CN: ROADMAP P6.4: 导出在大小上限 (T7) 内写出附录 B 的归档, 每种失败原因各有提示.
 */
class SettingsExportTest {

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")

    private val device = SettingsArchiveManifest.Device("OPD2413", 35)

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
        val manifest = SettingsExporter.write(out, snapshot, 1_761_051_600_000L, host, device)
        val names = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertEquals(listOf("manifest.json", "categories/appearance.json", "categories/editor_log.json", "categories/credentials.json"), names)
        assertEquals(listOf(false, false, true), manifest.categories.map { it.sensitive })
        val result = SettingsExporter.result("autojs6-settings-20261021-210000.zip", manifest, snapshot)
        assertEquals(SettingsExporter.Result("autojs6-settings-20261021-210000.zip", 3, 3, 1), result)
    }

    @Test
    fun refusesAnArchiveBeyondTheLimit() {
        val error = assertThrows(SettingsArchiveException::class.java) {
            SettingsExporter.write(ByteArrayOutputStream(), snapshot, 0L, host, device, maxBytes = 64)
        }
        assertEquals(SettingsArchiveException.Reason.TOO_LARGE, error.reason)
        assertEquals(Failure.TOO_LARGE, Failure.of(error))
        assertEquals(16L * 1024 * 1024, SettingsArchive.MAX_BYTES)
        assertEquals("16 MB", SettingsTransferMessages.sizeLimit())
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
