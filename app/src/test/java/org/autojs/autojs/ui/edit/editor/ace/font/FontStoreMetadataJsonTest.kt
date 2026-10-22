package org.autojs.autojs.ui.edit.editor.ace.font

import com.google.gson.Gson
import org.autojs.autojs.core.pref.SettingsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Modifier

/**
 * The metadata of installed fonts (`installed.json`) is read and written with SettingsJson instead of Gson (settings
 * P5.3, docs/settings/ROADMAP.md): files written by Gson keep reading, and the new text is byte-identical to Gson's.
 * zh-CN: 已安装字体的元数据 (`installed.json`) 改由 SettingsJson 读写, 不再经由 Gson (settings P5.3): Gson 写出的文件照常可读,
 * 新写出的文本与 Gson 逐字节相同.
 */
class FontStoreMetadataJsonTest {
    @get:Rule val temporary = TemporaryFolder()

    // The former FontStore.InstalledFontDto with its fields in name order. ART returns the declared fields of a class sorted
    // by name, so this is the text Gson wrote on Android; a HotSpot JVM returns them in declaration order.
    private data class AndroidOrderDto(
        val fontId: String = "",
        val formatVersion: Int = 1,
        val installedAtMillis: Long = 0,
        val sha256: String = "",
        val sizeBytes: Long = 0,
        val version: String? = null,
    )

    // The former DTO exactly as it was declared.
    private data class DeclaredOrderDto(
        val formatVersion: Int = 1,
        val fontId: String = "",
        val version: String? = null,
        val sha256: String = "",
        val sizeBytes: Long = 0,
        val installedAtMillis: Long = 0,
    )

    private val sha = "ab".repeat(32)

    // Text that Gson escapes: HTML characters, quotes, a backslash, line and paragraph separators, a tab, a control
    // character, non-ASCII text and a slash (which Gson does not escape).
    private val trickyVersions = listOf(
        null,
        "1.2.0",
        "v2 <b>&amp;='x'</b> / ${Char(34)}q${Char(34)} ${Char(92)} 字体 ${Char(0x2028)}${Char(0x2029)}${Char(9)}${Char(1)}",
    )

    private fun font(
        version: String?,
        directory: File = temporary.root,
        sizeBytes: Long = 96,
        installedAtMillis: Long = 1_726_000_000_123L,
        sha256: String = sha,
    ) = InstalledFont("test_font", version, sha256, sizeBytes, File(directory, "$sha256.woff2"), installedAtMillis)

    private fun androidGson(font: InstalledFont): String =
        Gson().toJson(AndroidOrderDto(font.fontId, 1, font.installedAtMillis, font.sha256, font.sizeBytes, font.version))

    private fun declaredGson(font: InstalledFont): String =
        Gson().toJson(DeclaredOrderDto(1, font.fontId, font.version, font.sha256, font.sizeBytes, font.installedAtMillis))

    private fun fieldNames(type: Class<*>): List<String> =
        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }.map { it.name }

    @Test
    fun encodedMetadataIsByteIdenticalToGsonOnAndroid() {
        for (version in trickyVersions) {
            for (installed in listOf(font(version), font(version, sizeBytes = FontFileVerifier.MAX_FONT_BYTES, installedAtMillis = 0))) {
                assertEquals(androidGson(installed), FontStore.encodeMetadata(installed))
            }
        }
    }

    @Test
    fun encoderWritesTheFormerFieldsInNameOrder() {
        val declared = fieldNames(DeclaredOrderDto::class.java)
        assertEquals(listOf("formatVersion", "fontId", "version", "sha256", "sizeBytes", "installedAtMillis"), declared)
        assertEquals(declared.sorted(), fieldNames(AndroidOrderDto::class.java))
        assertEquals(declared.sorted(), SettingsJson.parseMap(FontStore.encodeMetadata(font("1"))).keys.toList())
        // A null version is omitted like Gson's default.
        assertEquals(declared.sorted() - "version", SettingsJson.parseMap(FontStore.encodeMetadata(font(null))).keys.toList())
    }

    @Test
    fun readsMetadataWrittenByGsonInEitherFieldOrder() {
        val directory = temporary.newFolder("fonts", "test_font")
        for (version in trickyVersions) {
            val expected = font(version, directory)
            assertEquals(expected, FontStore.decodeMetadata(androidGson(expected), directory))
            assertEquals(expected, FontStore.decodeMetadata(declaredGson(expected), directory))
        }
    }

    @Test
    fun roundTripKeepsTheRecord() {
        for (version in trickyVersions) {
            val installed = font(version, installedAtMillis = Long.MAX_VALUE)
            assertEquals(installed, FontStore.decodeMetadata(FontStore.encodeMetadata(installed), temporary.root))
        }
    }

    @Test
    fun missingMembersGetTheFormerDefaultsAndUnknownMembersAreIgnored() {
        val directory = temporary.root
        // formatVersion defaults to 1 and installedAtMillis to 0; the digest is normalized to lower case.
        val text = """{"fontId":"test_font","sha256":"${sha.uppercase()}","sizeBytes":96,"extra":{"nested":[1,null]},"version":null}"""
        assertEquals(font(null, directory, installedAtMillis = 0), FontStore.decodeMetadata(text, directory))
        // Gson read an integral fraction into its Int and Long fields.
        val fractions = """{"fontId":"test_font","formatVersion":1.0,"installedAtMillis":1.726000000123E12,"sha256":"$sha","sizeBytes":96.0,"version":"1"}"""
        assertEquals(font("1", directory), FontStore.decodeMetadata(fractions, directory))
    }

    @Test
    fun invalidRecordsAndMalformedTextAreRejected() {
        val directory = temporary.root
        val valid = FontStore.encodeMetadata(font("1", directory))
        assertEquals(font("1", directory), FontStore.decodeMetadata(valid, directory))
        val invalid = listOf(
            "",
            "null",
            "[]",
            "not a json document",
            valid.dropLast(1),
            valid.replace("\"formatVersion\":1", "\"formatVersion\":2"),
            valid.replace("\"test_font\"", "\"Test Font\""),
            valid.replace("\"sizeBytes\":96", "\"sizeBytes\":47"),
            valid.replace("\"sizeBytes\":96", "\"sizeBytes\":96.5"),
            valid.replace("\"sha256\":\"$sha\"", "\"sha256\":null"),
            valid.replace("\"version\":\"1\"", "\"version\":{}"),
            // sizeBytes defaults to 0, below the WOFF2 header size.
            """{"fontId":"test_font","sha256":"$sha"}""",
        )
        invalid.forEach { assertNull(it, FontStore.decodeMetadata(it, directory)) }
    }

    @Test
    fun gsonLeniencyThatGsonNeverWroteIsDropped() {
        // Gson read each of these; SettingsJson reads strict JSON only, so the store falls back to recovering the record
        // from the font file.
        val directory = temporary.root
        val valid = FontStore.encodeMetadata(font("1", directory))
        listOf(
            valid.replace("\"sizeBytes\":96", "\"sizeBytes\":\"96\""),
            valid.replace("\"version\":\"1\"", "\"version\":1"),
            valid.replace("{", "{\"fontId\":\"other\","),
            valid.replace("\"fontId\"", "fontId"),
            valid.replace("\"version\":\"1\"", "\"version\":'1'"),
        ).forEach { assertNull(it, FontStore.decodeMetadata(it, directory)) }
    }

    @Test
    fun storeReadsInstalledJsonWrittenByGson() {
        val root = temporary.newFolder()
        val bytes = FontTestFixtures.validWoff2(size = 128)
        val sha256 = FontTestFixtures.sha256(bytes)
        val directory = File(File(root, "fonts"), "test_font").also { assertTrue(it.mkdirs()) }
        File(directory, "$sha256.woff2").writeBytes(bytes)
        // Version and time come from the metadata; a record recovered from the font file would have neither.
        val expected = InstalledFont("test_font", trickyVersions.last(), sha256, 128, File(directory, "$sha256.woff2"), 1_726_000_000_123L)
        val gsonText = androidGson(expected)
        File(directory, "installed.json").writeText(gsonText)

        val store = FontStore(root)

        assertEquals(expected, store.installedFont("test_font"))
        assertEquals(listOf(expected), store.installedFonts())
        assertEquals(gsonText, File(directory, "installed.json").readText())
    }

    @Test
    fun storeWritesInstalledJsonByteIdenticalToGson() {
        val root = temporary.newFolder()
        val bytes = FontTestFixtures.validWoff2(size = 160)
        val font = FontTestFixtures.remoteFont(bytes, version = "3 <&>='/ 字体")
        val store = FontStore(root)
        val part = store.createPartFile(font).also { it.writeBytes(bytes) }

        val installed = store.installVerifiedPart(font, part)

        assertEquals(androidGson(installed), File(File(File(root, "fonts"), font.id), "installed.json").readText())
        assertEquals(installed, FontStore(root).installedFont(font.id))
    }
}
