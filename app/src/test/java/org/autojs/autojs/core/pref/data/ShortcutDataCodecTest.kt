package org.autojs.autojs.core.pref.data

import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.external.shortcut.ShortcutBackupArchive
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * docs/settings/ROADMAP.md P10.1: the data category `data_shortcuts` ([ShortcutDataCodec], D21 / T19) is the export of
 * the page "Shortcut backup" byte for byte, reads back, and travels through a settings archive unchanged.
 *
 * zh-CN: ROADMAP P10.1: 数据分类 `data_shortcuts` 与 "快捷方式备份" 页面的导出逐字节相同, 可以读回, 经设置归档原样往返.
 */
class ShortcutDataCodecTest {

    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jX1sAAAAASUVORK5CYII=")

    // Device-local fields set on purpose: the export drops them.
    private val state = ShortcutBackupState(
        listOf(
            ShortcutBackupEntry(
                "copy-1", "脚本", "Script label", "script", "/sdcard/scripts/a.py", "python_file_v1", "/sdcard/scripts",
                png, ShortcutBackupEntry.Status.PINNED, notified = true, requestToken = "token", iconNoticeShown = true,
            ),
            ShortcutBackupEntry("copy-2", "Settings", "AutoJs6 settings", "settings", icon = null, status = ShortcutBackupEntry.Status.MISSING),
        ),
        markerToken = "generation", launcher = "home/Launcher", activity = "org.autojs.autojs6/.Main",
    )

    @Test
    fun theDataFileIsTheExportOfThePageByteForByte() {
        val file = ShortcutDataCodec.encode(state, TIME)
        assertEquals("data_shortcuts", file.id)
        assertEquals("shortcuts.zip", file.name)
        assertEquals(2, file.entries)
        // ShortcutBackupRepository.export, which the page calls, writes ShortcutBackupArchive.writeExport.
        val page = ByteArrayOutputStream().also { ShortcutBackupArchive.writeExport(state, it, TIME) }.toByteArray()
        assertArrayEquals(page, file.bytes)
        assertArrayEquals(page, ShortcutDataCodec.encode(state, TIME).bytes)
        // The same files as the export before P10.1, which wrote the state without its device-local fields.
        val before = ByteArrayOutputStream().also {
            ShortcutBackupArchive.write(ShortcutBackupState(state.entries.map { e -> e.copy(requestToken = null, notified = false, iconNoticeShown = false) }), it)
        }.toByteArray()
        assertEquals(files(before), files(file.bytes))
        assertEquals(listOf("manifest.json", "icons/0.png"), files(file.bytes).keys.toList())
        ZipInputStream(ByteArrayInputStream(file.bytes)).use { zip ->
            while (true) assertEquals(TIME, (zip.nextEntry ?: break).time)
        }
    }

    @Test
    fun theDataFileReadsBackWithoutDeviceLocalFields() {
        val decoded = ShortcutDataCodec.decode(ShortcutDataCodec.encode(state).bytes)
        assertNull(decoded.markerToken)
        assertNull(decoded.launcher)
        assertNull(decoded.activity)
        assertEquals(state.entries.map { it.id }, decoded.entries.map { it.id })
        state.entries.zip(decoded.entries).forEach { (before, after) ->
            assertTrue(before.sameDefinition(after))
            assertEquals(before.status, after.status)
            assertEquals(false, after.notified)
            assertNull(after.requestToken)
            assertEquals(false, after.iconNoticeShown)
        }
        // A file the page exported (the current time) reads the same.
        val page = ByteArrayOutputStream().also { ShortcutBackupArchive.writeExport(state, it) }.toByteArray()
        assertEquals(decoded.entries.map { it.id }, ShortcutDataCodec.decode(page).entries.map { it.id })
    }

    @Test
    fun aDataFileTheArchiveCannotReadIsInvalid() {
        val traversal = rewrite(ShortcutDataCodec.encode(state).bytes) { name -> if (name.startsWith("icons/")) "../icon.png" else name }
        val other = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { it.putNextEntry(ZipEntry("manifest.json")); it.write("""{"format":"other"}""".toByteArray()); it.closeEntry() }
        }.toByteArray()
        listOf(byteArrayOf(1, 2, 3), ByteArray(0), traversal, other).forEach { bytes ->
            val e = assertThrows(SettingsArchiveException::class.java) { ShortcutDataCodec.decode(bytes) }
            assertEquals(SettingsArchiveException.Reason.INVALID_FILE, e.reason)
            assertTrue(e.message!!.startsWith("data/shortcuts.zip: "))
        }
    }

    @Test
    fun theDataFileTravelsThroughASettingsArchiveUnchanged() {
        val file = ShortcutDataCodec.encode(state, TIME)
        val out = ByteArrayOutputStream()
        SettingsArchiveWriter.write(out, SettingsSnapshot(emptyList()), SettingsArchive.exportTime(TIME, ZONE), HOST, DEVICE, listOf(file))
        val reader = SettingsArchiveReader({ ByteArrayInputStream(out.toByteArray()) })
        val entry = reader.manifest().data.single()
        assertEquals(SettingsArchiveManifest.DataEntry("data_shortcuts", 2, "data/shortcuts.zip"), entry)
        assertArrayEquals(file.bytes, reader.data(entry.file))
        assertEquals(listOf("copy-1", "copy-2"), ShortcutDataCodec.decode(reader.data(entry.file)).entries.map { it.id })
    }

    private fun files(zip: ByteArray): Map<String, List<Byte>> = linkedMapOf<String, List<Byte>>().apply {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) put((input.nextEntry ?: break).name, input.readBytes().toList())
        }
    }

    private fun rewrite(zip: ByteArray, rename: (String) -> String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { target ->
            files(zip).forEach { (name, bytes) ->
                target.putNextEntry(ZipEntry(rename(name)))
                target.write(bytes.toByteArray())
                target.closeEntry()
            }
        }
    }.toByteArray()

    companion object {

        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

        /** 2026-10-20T15:30:00+08:00, an even second (the DOS time of a ZIP entry has a resolution of 2 seconds). */
        private const val TIME = 1_792_481_400_000L

        private val HOST = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")

        private val DEVICE = SettingsArchiveManifest.Device("XQ-DQ72", 33)
    }
}
