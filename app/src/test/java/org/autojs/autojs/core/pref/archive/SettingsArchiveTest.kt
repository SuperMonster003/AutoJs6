package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchiveException.Reason
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest.CategoryEntry
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest.DataEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * docs/settings/ROADMAP.md P6.1: writing and reading a settings archive in a JVM temporary directory
 * ([SettingsArchiveWriter], [SettingsArchiveReader], [SettingsArchiveManifest], T7, appendix B).
 *
 * zh-CN: ROADMAP P6.1: 在 JVM 临时目录中写入并读取设置归档 (T7, 附录 B).
 */
class SettingsArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5460, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-DQ72", 35)

    private val snapshot = SettingsSnapshot(
        listOf(
            SettingsSnapshot.Category(
                "general",
                linkedMapOf(
                    "default" to linkedMapOf("a" to TypedValue("bool", true), "b" to TypedValue("json", "[1,2]")),
                    "plugin_center_recent" to linkedMapOf("c" to TypedValue("stringSet", linkedSetOf("x"))),
                ),
            ),
            // A category without stored values restores its defaults on import (D8).
            SettingsSnapshot.Category("editor_log", emptyMap()),
            SettingsSnapshot.Category("credentials", linkedMapOf("default" to linkedMapOf("token" to TypedValue("string", "secret")))),
        ),
    )

    @Test
    fun anArchiveReadsBackWhatWasWritten() {
        val file = folder.newFile(SettingsArchive.defaultFileName(EXPORTED_AT, ZONE))
        val shortcuts = byteArrayOf(1, 2, 3, 0, -1)
        val written = file.outputStream().use { out ->
            SettingsArchiveWriter.write(
                out, snapshot, SettingsArchive.exportTime(EXPORTED_AT, ZONE), host, device,
                listOf(SettingsArchiveWriter.DataFile("data_shortcuts", 2, "shortcuts.zip", shortcuts)),
            )
        }
        val reader = SettingsArchiveReader({ file.inputStream() })
        val manifest = reader.manifest()
        assertEquals(written, manifest)
        assertEquals("2026-10-20T15:30:00+08:00", manifest.exportedAt)
        assertEquals(host, manifest.host)
        assertEquals(device, manifest.device)
        assertEquals(
            listOf(CategoryEntry("general", 3, false), CategoryEntry("editor_log", 0, false), CategoryEntry("credentials", 1, true)),
            manifest.categories,
        )
        assertEquals(listOf(DataEntry("data_shortcuts", 2, "data/shortcuts.zip")), manifest.data)
        assertEquals(emptyList<Map<String, Any?>>(), manifest.plugins)
        assertFalse(manifest.isNewerFormat)
        snapshot.categories.forEach { assertEquals(SettingsSnapshotCodec.Decoded(it, emptyList()), reader.category(it.id)) }
        assertArrayEquals(shortcuts, reader.data(manifest.data.single().file))
        assertEquals(Reason.MISSING_FILE, assertThrows(SettingsArchiveException::class.java) { reader.category("appearance") }.reason)
        assertThrows(IllegalArgumentException::class.java) { reader.data("categories/general.json") }

        // manifest.json comes first (T7); plugins/ is reserved and stays empty (D3).
        ZipFile(file).use { zip ->
            assertEquals(
                listOf("manifest.json", "categories/general.json", "categories/editor_log.json", "categories/credentials.json", "data/shortcuts.zip"),
                zip.entries().toList().map { it.name },
            )
            val root = SettingsJson.parse(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().toString(Charsets.UTF_8)) as Map<*, *>
            assertEquals(listOf("format", "formatVersion", "exportedAt", "host", "device", "categories", "data", "plugins"), root.keys.toList())
            assertEquals("autojs6-settings", root["format"])
            assertEquals(1, root["formatVersion"])
            assertEquals(emptyList<Any>(), root["plugins"])
            assertEquals(mapOf("id" to "credentials", "entries" to 1, "sensitive" to true), (root["categories"] as List<*>).last())
        }
    }

    @Test
    fun aManifestReadsBackItsOwnText() {
        val manifest = SettingsArchiveManifest(
            exportedAt = "2026-10-20T15:30:00+08:00",
            host = host,
            device = device,
            categories = listOf(CategoryEntry("general", 3, false)),
            data = listOf(DataEntry("data_color_library", 12, "data/color-library.json")),
            plugins = listOf(linkedMapOf("id" to "org.autojs.plugin.sample", "entries" to 2, "file" to "plugins/sample.json")),
        )
        assertEquals(manifest, SettingsArchiveManifest.fromText(manifest.toText()))
        assertTrue(manifest.toText(), manifest.toText().startsWith("{\n  \"format\": \"autojs6-settings\",\n  \"formatVersion\": 1,\n"))
    }

    @Test
    fun aManifestOfANewerFormatIsReadLeniently() {
        val manifest = SettingsArchiveManifest.fromText(
            """
            {
              "format": "autojs6-settings",
              "formatVersion": 2,
              "exportedAt": "2027-01-01T00:00:00Z",
              "host": {"packageName": "org.autojs.autojs6", "versionCode": 6000, "versionName": "7.0.0", "abi": "arm64-v8a"},
              "device": {"model": "Pixel", "sdk": 37},
              "categories": [{"id": "future_category", "entries": 1, "sensitive": false, "digest": "x"}],
              "plugins": [{"id": "org.autojs.plugin.sample", "entries": 3}],
              "signature": "..."
            }
            """.trimIndent(),
        )
        assertTrue(manifest.isNewerFormat)
        assertEquals(2, manifest.formatVersion)
        assertEquals(listOf(CategoryEntry("future_category", 1, false)), manifest.categories)
        assertEquals("categories/future_category.json", manifest.categories.single().file)
        assertEquals(emptyList<DataEntry>(), manifest.data)
        assertEquals(listOf(mapOf("id" to "org.autojs.plugin.sample", "entries" to 3)), manifest.plugins)
    }

    @Test
    fun manifestsOfAnotherFormatAreRejected() {
        val valid = SettingsJson.parse(SettingsArchiveManifest("t", host, device, emptyList()).toText()) as Map<*, *>
        listOf<(MutableMap<Any?, Any?>) -> Unit>(
            { it["format"] = "autojs6-backup" },
            { it.remove("format") },
            { it["formatVersion"] = 0 },
            { it["formatVersion"] = "1" },
            { it.remove("host") },
            { it["device"] = mapOf("model" to "x") },
            { it["categories"] = listOf(mapOf("id" to "General", "entries" to 1, "sensitive" to false)) },
            { it["categories"] = listOf(mapOf("id" to "general", "entries" to 1)) },
            { it["categories"] = mapOf<String, Any>() },
            { it["plugins"] = listOf(1) },
        ).forEachIndexed { index, change ->
            val text = SettingsJson.stringify(LinkedHashMap<Any?, Any?>(valid).also(change))
            val e = assertThrows("$index: $text", SettingsArchiveException::class.java) { SettingsArchiveManifest.fromText(text) }
            assertEquals(text, Reason.INVALID_MANIFEST, e.reason)
        }
        listOf("", "[]", "not json").forEach { text ->
            assertEquals(text, Reason.INVALID_MANIFEST, assertThrows(SettingsArchiveException::class.java) { SettingsArchiveManifest.fromText(text) }.reason)
        }
    }

    @Test
    fun filesThatAreNoSettingsArchivesAreRejected() {
        fun reasonOf(bytes: ByteArray, read: SettingsArchiveReader.() -> Unit = { manifest() }): Reason =
            assertThrows(SettingsArchiveException::class.java) { SettingsArchiveReader({ ByteArrayInputStream(bytes) }).read() }.reason

        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf(ByteArray(0)))
        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf("{\"format\": \"autojs6-settings\"}".toByteArray()))
        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf(ByteArray(4096) { (it * 31 + 7).toByte() }))
        assertEquals(Reason.MISSING_FILE, reasonOf(zip("readme.txt" to "x")))
        assertEquals(Reason.INVALID_MANIFEST, reasonOf(zip("manifest.json" to "{\"format\": \"autojs6-backup\"}")))
        assertEquals(Reason.INVALID_MANIFEST, reasonOf(zip("manifest.json" to "{")))
        // A category file that is malformed or holds another category.
        val manifest = SettingsArchiveManifest("t", host, device, listOf(CategoryEntry("general", 0, false))).toText()
        assertEquals(Reason.INVALID_FILE, reasonOf(zip("manifest.json" to manifest, "categories/general.json" to "[]")) { category("general") })
        assertEquals(
            Reason.INVALID_FILE,
            reasonOf(zip("manifest.json" to manifest, "categories/general.json" to "{\"id\": \"console\", \"stores\": {}}")) { category("general") },
        )
    }

    @Test
    fun archivesBeyondTheLimitAreRefused() {
        // The writer refuses an archive that grows beyond the limit.
        assertEquals(
            Reason.TOO_LARGE,
            assertThrows(SettingsArchiveException::class.java) {
                SettingsArchiveWriter.write(ByteArrayOutputStream(), snapshot, "t", host, device, maxBytes = 256)
            }.reason,
        )
        // The reader refuses an archive beyond the limit, and a file that inflates beyond it.
        val zeros = ByteArray(64 * 1024)
        val bytes = ByteArrayOutputStream().also { out ->
            SettingsArchiveWriter.write(out, snapshot, "t", host, device, listOf(SettingsArchiveWriter.DataFile("data_color_library", 1, "color-library.json", zeros)))
        }.toByteArray()
        assertTrue(bytes.size < 8 * 1024)
        assertEquals(Reason.TOO_LARGE, assertThrows(SettingsArchiveException::class.java) { SettingsArchiveReader({ ByteArrayInputStream(bytes) }, 256).manifest() }.reason)
        val reader = SettingsArchiveReader({ ByteArrayInputStream(bytes) }, 32 * 1024)
        assertEquals(3, reader.manifest().categories.size)
        assertEquals(Reason.TOO_LARGE, assertThrows(SettingsArchiveException::class.java) { reader.data("data/color-library.json") }.reason)
        assertArrayEquals(zeros, SettingsArchiveReader({ ByteArrayInputStream(bytes) }).data("data/color-library.json"))
        assertEquals(16L * 1024 * 1024, SettingsArchive.MAX_BYTES)
    }

    @Test
    fun namesFollowTheLayoutOfAppendixB() {
        assertEquals("2026-10-20T15:30:00+08:00", SettingsArchive.exportTime(EXPORTED_AT, ZONE))
        assertEquals("2026-10-20T07:30:00+00:00", SettingsArchive.exportTime(EXPORTED_AT, ZoneId.of("UTC")))
        assertEquals("autojs6-settings-20261020-153000.zip", SettingsArchive.defaultFileName(EXPORTED_AT, ZONE))
        assertEquals("categories/editor_general.json", SettingsArchive.categoryFile("editor_general"))
        assertEquals("data/color-library.json", SettingsArchive.dataFile("color-library.json"))
        listOf("General", "1st", "a-b", "", "../general").forEach { id ->
            assertFalse(id, SettingsArchive.isCategoryId(id))
            assertThrows(id, IllegalArgumentException::class.java) { SettingsArchive.categoryFile(id) }
        }
        listOf("../shortcuts.zip", "a/b.json", "Shortcuts.zip", "", ".hidden").forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) { SettingsArchive.dataFile(name) }
        }
        assertThrows(IllegalArgumentException::class.java) { SettingsArchiveWriter.DataFile("Shortcuts", 0, "shortcuts.zip", ByteArray(0)) }
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private companion object {

        /** 2026-10-20 15:30:00 at +08:00, the time of appendix B. */
        const val EXPORTED_AT = 1_792_481_400_000L

        val ZONE: ZoneId = ZoneId.of("+08:00")
    }
}
