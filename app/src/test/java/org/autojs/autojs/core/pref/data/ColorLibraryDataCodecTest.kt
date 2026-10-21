package org.autojs.autojs.core.pref.data

import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.StateColors
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.theme.library.ColorLibraryJson.ColorLibraryJsonException
import org.autojs.autojs.theme.library.ColorLibraryJson.IntelligentLibrary
import org.autojs.autojs.theme.library.ColorLibraryJson.PlainLibrary
import org.autojs.autojs.theme.library.ColorLibraryJson.Reason
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId

/**
 * docs/settings/ROADMAP.md P10.1: the data category `data_color_library` ([ColorLibraryDataCodec], D21 / T19) is the
 * export of the page "Color libraries" byte for byte, reads back like its import, and travels through a settings
 * archive unchanged.
 *
 * zh-CN: ROADMAP P10.1: 数据分类 `data_color_library` 与 "颜色库" 页面的导出逐字节相同, 与页面的导入一样读回, 经设置归档原样往返.
 */
class ColorLibraryDataCodecTest {

    private fun argb(rgb: Long) = (0xFF000000 or rgb).toInt()

    private val libraries = listOf(
        PlainLibrary("我的品牌色", listOf(ColorLibraryJson.Color(argb(0x00695C), "主色", TIME, TIME), ColorLibraryJson.Color(argb(0x37474F)))),
        IntelligentLibrary(
            "暖冷交替",
            IntelligentConfig(),
            listOf(
                IntelligentGroup(10L, "橙", position = 0, states = mapOf("day" to StateColors(argb(0xFFB74D)), "night" to StateColors(argb(0xE65100)))),
                IntelligentGroup(20L, "青", position = 1, states = mapOf("day" to StateColors(argb(0x4DD0E1), accent = argb(0xFFD54F)))),
            ),
        ),
    )

    private val app = ColorLibraryJson.AppInfo("6.8.0", 5781)

    @Test
    fun theDataFileIsTheExportOfThePageByteForByte() {
        val file = ColorLibraryDataCodec.encode(libraries, TIME, ZONE, app)
        assertEquals("data_color_library", file.id)
        assertEquals("color-library.json", file.name)
        assertEquals(2, file.entries)
        // ColorLibraryExchange.write: ColorLibraryJson.encode(libraries, now, zone, app) written as UTF-8.
        assertArrayEquals(ColorLibraryJson.encode(libraries, TIME, ZONE, app).toByteArray(Charsets.UTF_8), file.bytes)
        assertEquals(0, ColorLibraryDataCodec.encode(emptyList(), TIME, ZONE).entries)
    }

    @Test
    fun theDataFileReadsBackLikeTheImportOfThePage() {
        val document = ColorLibraryDataCodec.decode(ColorLibraryDataCodec.encode(libraries, TIME, ZONE, app).bytes)
        assertEquals(TIME, document.exportedAt)
        assertEquals(app, document.app)
        assertEquals(listOf("我的品牌色", "暖冷交替"), document.libraries.map { it.library.name })
        assertEquals(listOf(2, 2), document.libraries.map { it.library.itemCount })
        assertEquals(libraries[0], document.libraries[0].library)
        // Groups are numbered by their index in the file (ColorLibraryJson), the importer remaps them (P10.2).
        assertEquals(listOf(0L, 1L), (document.libraries[1].library as IntelligentLibrary).groups.map { it.id })

        // A byte order mark is ignored, a blank name gets the fallback name, as the page reads a file.
        val text = """{"format":"autojs6.color-library","schemaVersion":1,"libraries":[{"type":"plain","name":" ","colors":[]}]}"""
        val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        assertEquals("Unnamed", ColorLibraryDataCodec.decode(marked, "Unnamed").libraries.single().library.name)
    }

    @Test
    fun aDataFileThePageWouldRefuseKeepsItsReason() {
        fun reasonOf(bytes: ByteArray) = assertThrows(ColorLibraryJsonException::class.java) { ColorLibraryDataCodec.decode(bytes) }.reason
        assertEquals(Reason.TOO_LARGE, reasonOf(ByteArray(ColorLibraryJson.MAX_FILE_BYTES + 1) { ' '.code.toByte() }))
        assertEquals(Reason.MALFORMED, reasonOf("{".toByteArray()))
        assertEquals(Reason.NOT_A_COLOR_LIBRARY, reasonOf("""{"format":"autojs6-settings"}""".toByteArray()))
        assertEquals(Reason.NEWER_SCHEMA, reasonOf("""{"format":"autojs6.color-library","schemaVersion":2,"libraries":[]}""".toByteArray()))
        assertEquals(Reason.NO_LIBRARIES, reasonOf(ColorLibraryDataCodec.encode(emptyList(), TIME, ZONE).bytes))
    }

    @Test
    fun theDataFileTravelsThroughASettingsArchiveUnchanged() {
        val colors = ColorLibraryDataCodec.encode(libraries, TIME, ZONE, app)
        val shortcuts = SettingsArchiveWriter.DataFile("data_shortcuts", 0, "shortcuts.zip", byteArrayOf(1, 2, 3))
        val out = ByteArrayOutputStream()
        SettingsArchiveWriter.write(out, SettingsSnapshot(emptyList()), SettingsArchive.exportTime(TIME, ZONE), HOST, DEVICE, listOf(shortcuts, colors))
        val reader = SettingsArchiveReader({ ByteArrayInputStream(out.toByteArray()) })
        val manifest = reader.manifest()
        assertEquals(
            listOf(
                SettingsArchiveManifest.DataEntry("data_shortcuts", 0, "data/shortcuts.zip"),
                SettingsArchiveManifest.DataEntry("data_color_library", 2, "data/color-library.json"),
            ),
            manifest.data,
        )
        val bytes = reader.data(manifest.data[1].file)
        assertArrayEquals(colors.bytes, bytes)
        assertEquals(2, ColorLibraryDataCodec.decode(bytes).libraries.size)
    }

    companion object {

        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

        /** 2026-10-20T15:30:00+08:00. */
        private const val TIME = 1_792_481_400_000L

        private val HOST = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")

        private val DEVICE = SettingsArchiveManifest.Device("XQ-DQ72", 33)
    }
}
