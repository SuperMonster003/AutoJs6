package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchiveException.Reason
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest.CategoryEntry
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest.Warning
import org.autojs.autojs.core.pref.archive.SettingsKeyMigrations.Rename
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * docs/settings/ROADMAP.md P7.1: what an import checks when it reads an archive ([SettingsArchiveReader],
 * [SettingsArchiveManifest], [SettingsKeyMigrations]): damaged zips, a missing manifest, the warnings of a newer format
 * and of another host package, manifests an import cannot rely on, the decoded rejections and the key renames.
 *
 * zh-CN: ROADMAP P7.1: 导入读取归档时的校验: 损坏的 zip, 缺少 manifest, 更新格式与其他宿主包名的提示, 导入无法依赖的 manifest,
 * 结构化的略去条目与键改名.
 */
class SettingsArchiveReaderTest {

    private val host = SettingsArchiveManifest.Host(HOST_PACKAGE, 5460, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-DQ72", 35)

    private val snapshot = SettingsSnapshot(
        listOf(
            SettingsSnapshot.Category("general", linkedMapOf("default" to linkedMapOf("a" to TypedValue("bool", true), "b" to TypedValue("int", 2)))),
            // Letters that do not compress much, so that the deflated file is large enough to be cut in the middle.
            SettingsSnapshot.Category("console", linkedMapOf("default" to linkedMapOf("c" to TypedValue("string", Random(1).let { r -> String(CharArray(4096) { 'a' + r.nextInt(26) }) })))),
        ),
    )

    private val archive: ByteArray = ByteArrayOutputStream().also { SettingsArchiveWriter.write(it, snapshot, "t", host, device) }.toByteArray()

    @Test
    fun damagedArchivesAreNoArchives() {
        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf(ByteArray(0)))
        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf(archive.copyOf(20)))
        // Cut in the middle of the deflated manifest.
        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf(archive.copyOf(60)))
        // A byte of the deflated manifest changed: the data or its CRC no longer fits.
        assertEquals(Reason.NOT_AN_ARCHIVE, reasonOf(archive.copyOf().also { it[60] = (it[60].toInt() xor 0x5a).toByte() }))
        // Cut in the middle of the last category file: the manifest still reads, that category file does not.
        val cut = archive.copyOf(String(archive, Charsets.ISO_8859_1).indexOf("categories/console.json") + 1024)
        val reader = SettingsArchiveReader({ ByteArrayInputStream(cut) })
        assertEquals(listOf("general", "console"), reader.manifest().categories.map { it.id })
        assertEquals(Reason.NOT_AN_ARCHIVE, assertThrows(SettingsArchiveException::class.java) { reader.category("console") }.reason)
    }

    @Test
    fun anArchiveWithoutManifestIsRefused() {
        val bytes = zip("categories/general.json" to SettingsSnapshotCodec.categoryText(snapshot.categories.first()))
        assertEquals(Reason.MISSING_FILE, reasonOf(bytes))
        // A directory entry named like the manifest is no manifest.
        assertEquals(Reason.MISSING_FILE, reasonOf(zip("manifest.json/" to "")))
    }

    @Test
    fun aNewerFormatIsFlaggedAndStillRead() {
        val reader = SettingsArchiveReader({ ByteArrayInputStream(archive) })
        assertEquals(emptySet<Warning>(), reader.manifest().warnings(HOST_PACKAGE))
        val newer = manifestJson().also { it["formatVersion"] = 2 }
        val bytes = zip("manifest.json" to SettingsJson.stringify(newer), "categories/general.json" to SettingsSnapshotCodec.categoryText(snapshot.categories.first()))
        val newerReader = SettingsArchiveReader({ ByteArrayInputStream(bytes) })
        val manifest = newerReader.manifest()
        assertTrue(manifest.isNewerFormat)
        assertEquals(setOf(Warning.NEWER_FORMAT), manifest.warnings(HOST_PACKAGE))
        assertEquals(snapshot.categories.first(), newerReader.categories(listOf("general"), manifest).single().category)
    }

    @Test
    fun anotherHostPackageIsFlagged() {
        val manifest = SettingsArchiveReader({ ByteArrayInputStream(archive) }).manifest()
        assertEquals(setOf(Warning.OTHER_HOST_PACKAGE), manifest.warnings("$HOST_PACKAGE.inrt"))
        val both = manifest.copy(formatVersion = 3)
        assertEquals(listOf(Warning.NEWER_FORMAT, Warning.OTHER_HOST_PACKAGE), both.warnings("org.example.fork").toList())
    }

    @Test
    fun manifestsAnImportCannotRelyOnAreRejected() {
        listOf<(MutableMap<Any?, Any?>) -> Unit>(
            { it["host"] = mapOf("packageName" to "", "versionCode" to 1, "versionName" to "1") },
            { it["host"] = mapOf("packageName" to "autojs6", "versionCode" to 1, "versionName" to "1") },
            { it["host"] = mapOf("packageName" to "org.autojs.autojs6/../x", "versionCode" to 1, "versionName" to "1") },
            { it["categories"] = listOf(category("general", 1), category("general", 2)) },
            { it["categories"] = listOf(category("general", -1)) },
            { it["data"] = listOf(data("data_shortcuts", 1, "categories/general.json")) },
            { it["data"] = listOf(data("data_shortcuts", 1, "data/../manifest.json")) },
            { it["data"] = listOf(data("data_shortcuts", 1, "data/")) },
            { it["data"] = listOf(data("Data Shortcuts", 1, "data/shortcuts.zip")) },
            { it["data"] = listOf(data("data_shortcuts", -2, "data/shortcuts.zip")) },
            { it["data"] = listOf(data("data_shortcuts", 1, "data/shortcuts.zip"), data("data_shortcuts", 1, "data/other.zip")) },
            { it["data"] = listOf(data("data_shortcuts", 1, "data/shortcuts.zip"), data("data_color_library", 1, "data/shortcuts.zip")) },
        ).forEachIndexed { index, change ->
            val text = SettingsJson.stringify(manifestJson().also(change))
            val e = assertThrows("$index: $text", SettingsArchiveException::class.java) { SettingsArchiveManifest.fromText(text) }
            assertEquals(text, Reason.INVALID_MANIFEST, e.reason)
        }
        // The valid counterparts read.
        val valid = manifestJson().also {
            it["categories"] = listOf(category("general", 0), category("console", 1))
            it["data"] = listOf(data("data_shortcuts", 0, "data/shortcuts.zip"), data("data_color_library", 1, "data/color-library.json"))
        }
        assertEquals(2, SettingsArchiveManifest.fromText(SettingsJson.stringify(valid)).data.size)
    }

    @Test
    fun rejectionsTellUnknownTagsFromBadValues() {
        val text = """
            {"id": "general", "stores": {
              "default": {"a": {"t": "int", "v": 1}, "b": {"t": "decimal", "v": "1.5"}, "c": {"t": "int", "v": "x"}, "d": {"v": 1}},
              "broken": 1
            }}
        """.trimIndent()
        val decoded = SettingsSnapshotCodec.decodeCategoryText(text)
        assertEquals(mapOf("default" to mapOf("a" to TypedValue("int", 1))), decoded.category.stores)
        assertEquals(
            listOf(
                SettingsSnapshotCodec.Rejection("default", "b", "unknown type tag \"decimal\"", unknownTag = true),
                SettingsSnapshotCodec.Rejection("default", "c", "not a int value"),
                SettingsSnapshotCodec.Rejection("default", "d", "no type tag"),
                SettingsSnapshotCodec.Rejection("broken", null, "not an object of entries"),
            ),
            decoded.rejections,
        )
        assertEquals("default:b (unknown type tag \"decimal\")", decoded.rejected.first())
        assertEquals("broken (not an object of entries)", decoded.rejected.last())
    }

    @Test
    fun theRenameTableIsEmptyAndRenamesOlderArchivesOnly() {
        assertEquals(emptyList<Rename>(), SettingsKeyMigrations.RENAMES)
        val decoded = SettingsSnapshotCodec.Decoded(
            SettingsSnapshot.Category(
                "general",
                linkedMapOf(
                    "default" to linkedMapOf("a1" to TypedValue("int", 1), "keep" to TypedValue("bool", true), "b1" to TypedValue("string", "s")),
                    "named" to linkedMapOf("a1" to TypedValue("int", 9)),
                ),
            ),
            emptyList(),
        )
        assertSame(decoded, SettingsKeyMigrations.migrate(decoded, 1))
        // Listed out of order on purpose: they apply by version.
        val renames = listOf(Rename("default", "a2", "a3", 5000), Rename("default", "a1", "a2", 4000), Rename("default", "b1", "b2", 4500))
        // Exported by 3000: a1 -> a2 -> a3 and b1 -> b2, in place.
        val migrated = SettingsKeyMigrations.migrate(decoded, 3000, renames).category.stores
        assertEquals(listOf("a3", "keep", "b2"), migrated.getValue("default").keys.toList())
        assertEquals(linkedMapOf("a3" to TypedValue("int", 1), "keep" to TypedValue("bool", true), "b2" to TypedValue("string", "s")), migrated["default"])
        // Another store keeps its names.
        assertEquals(mapOf("a1" to TypedValue("int", 9)), migrated["named"])
        // Exported by 4200: the renames of 4500 and 5000 apply, and there is no a2 to rename.
        assertEquals(listOf("a1", "keep", "b2"), SettingsKeyMigrations.migrate(decoded, 4200, renames).category.stores.getValue("default").keys.toList())
        // Exported by 5000 or later: nothing to rename.
        assertSame(decoded, SettingsKeyMigrations.migrate(decoded, 5000, renames))
        // The new name wins when the archive has both.
        val both = decoded.copy(category = decoded.category.copy(stores = mapOf("default" to linkedMapOf("a1" to TypedValue("int", 1), "a2" to TypedValue("int", 2)))))
        assertEquals(mapOf("a2" to TypedValue("int", 2)), SettingsKeyMigrations.migrate(both, 1, renames.drop(1).take(1)).category.stores["default"])
        assertThrows(IllegalArgumentException::class.java) { Rename("default", "a", "a", 1) }
    }

    @Test
    fun theReaderRenamesWhatItReadsForAnImport() {
        val reader = SettingsArchiveReader({ ByteArrayInputStream(archive) })
        val manifest = reader.manifest()
        val read = reader.categories(listOf("general", "console", "general"), manifest, listOf(Rename("default", "b", "b2", 6000)))
        assertEquals(listOf("general", "console"), read.map { it.category.id })
        assertEquals(listOf("a", "b2"), read.first().category.stores.getValue("default").keys.toList())
        assertEquals(snapshot.categories.last(), read.last().category)
        assertEquals(snapshot.categories, reader.categories(listOf("general", "console")).map { it.category })
        assertEquals(Reason.MISSING_FILE, assertThrows(SettingsArchiveException::class.java) { reader.categories(listOf("appearance"), manifest) }.reason)
    }

    private fun reasonOf(bytes: ByteArray): Reason =
        assertThrows(SettingsArchiveException::class.java) { SettingsArchiveReader({ ByteArrayInputStream(bytes) }).manifest() }.reason

    private fun manifestJson(): MutableMap<Any?, Any?> =
        LinkedHashMap(SettingsJson.parse(SettingsArchiveManifest("t", host, device, listOf(CategoryEntry("general", 2, false))).toText()) as Map<*, *>)

    private fun category(id: String, entries: Int) = mapOf("id" to id, "entries" to entries, "sensitive" to false)

    private fun data(id: String, entries: Int, file: String) = mapOf("id" to id, "entries" to entries, "file" to file)

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
        const val HOST_PACKAGE = "org.autojs.autojs6"
    }
}
