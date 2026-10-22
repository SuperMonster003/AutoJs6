package org.autojs.autojs.core.plugin.center

import com.google.gson.Gson
import org.autojs.autojs.core.plugin.center.InstalledPluginMetadataStore.Entry
import org.autojs.autojs.core.pref.SettingsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * The installed plugin metadata cache is read and written with SettingsJson instead of Gson (settings P5.3,
 * docs/settings/ROADMAP.md): caches written by Gson keep reading as the former reader read them, and the new text is
 * byte-identical to Gson's.
 * zh-CN: 已安装插件元数据缓存改由 SettingsJson 读写, 不再经由 Gson (settings P5.3): Gson 写出的缓存按原读取器的方式照常可读,
 * 新写出的文本与 Gson 逐字节相同.
 */
class InstalledPluginMetadataJsonTest {

    // The former payload classes with the fields of the entry in name order. ART returns the declared fields of a class
    // sorted by name, so this is the text Gson wrote on Android; a HotSpot JVM returns them in declaration order.
    private class AndroidPayload(val entries: List<AndroidEntry?>? = null)

    private class AndroidEntry(
        val author: String? = null,
        val description: String? = null,
        val engine: String? = null,
        val engineId: String? = null,
        val instruction: String? = null,
        val languageTag: String? = null,
        val lastUpdateTime: Long? = null,
        val nativePageAlignment: Long? = null,
        val packageName: String? = null,
        val requiresHostVersion: Long? = null,
        val supportedAbis: List<String?>? = null,
        val title: String? = null,
        val variant: String? = null,
        val versionCode: Long? = null,
        val versionDate: String? = null,
    )

    // The former payload classes exactly as they were declared.
    private class DeclaredPayload(val entries: List<DeclaredEntry?>? = null)

    private class DeclaredEntry(
        val packageName: String? = null,
        val versionCode: Long? = null,
        val lastUpdateTime: Long? = null,
        val languageTag: String? = null,
        val title: String? = null,
        val description: String? = null,
        val author: String? = null,
        val instruction: String? = null,
        val versionDate: String? = null,
        val requiresHostVersion: Long? = null,
        val supportedAbis: List<String?>? = null,
        val engine: String? = null,
        val variant: String? = null,
        val engineId: String? = null,
        val nativePageAlignment: Long? = null,
    )

    private val q = Char(34).toString()
    private val bs = Char(92).toString()

    // Text that Gson escapes: HTML characters, quotes, a backslash, line and paragraph separators, a tab, a control
    // character, non-ASCII text and a slash (which Gson does not escape).
    private val tricky = "OCR <b>&amp;='x'</b> / ${q}q$q $bs 文字识别 ${Char(0x2028)}${Char(0x2029)}${Char(9)}${Char(1)}"

    private fun entry(
        packageName: String,
        versionCode: Long? = null,
        lastUpdateTime: Long? = null,
        languageTag: String? = null,
        title: String? = null,
        description: String? = null,
        author: String? = null,
        instruction: String? = null,
        versionDate: String? = null,
        requiresHostVersion: Long? = null,
        supportedAbis: List<String>? = null,
        engine: String? = null,
        variant: String? = null,
        engineId: String? = null,
        nativePageAlignment: Long? = null,
    ) = Entry(
        packageName = packageName,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        languageTag = languageTag,
        title = title,
        description = description,
        author = author,
        instruction = instruction,
        versionDate = versionDate,
        requiresHostVersion = requiresHostVersion,
        supportedAbis = supportedAbis,
        engine = engine,
        variant = variant,
        engineId = engineId,
        nativePageAlignment = nativePageAlignment,
    )

    private val full = entry(
        packageName = "org.autojs.plugin.full",
        versionCode = Long.MAX_VALUE,
        lastUpdateTime = 1_726_000_000_000L,
        languageTag = "zh-CN",
        title = tricky,
        description = "识别屏幕上的文字. <script>&</script>",
        author = "SuperMonster003 / 'quoted'",
        instruction = "a=b & c<d",
        versionDate = "2026-08-01",
        requiresHostVersion = 6_800L,
        supportedAbis = listOf("arm64-v8a", "x86_64", "<&>"),
        engine = "mlkit-ocr",
        variant = "bundled/=",
        engineId = "mlkit-ocr-google",
        nativePageAlignment = 16_384L,
    )
    private val minimal = entry(packageName = "org.autojs.plugin.minimal")
    private val sparse = entry(packageName = "org.autojs.plugin.sparse", versionCode = 0L, title = "", supportedAbis = emptyList(), nativePageAlignment = 0L)

    private val collections = listOf(emptyList<Entry>(), listOf(minimal), listOf(full, minimal, sparse))

    private fun androidGson(entries: Collection<Entry>): String = Gson().toJson(AndroidPayload(entries.map {
        AndroidEntry(it.author, it.description, it.engine, it.engineId, it.instruction, it.languageTag, it.lastUpdateTime,
            it.nativePageAlignment, it.packageName, it.requiresHostVersion, it.supportedAbis, it.title, it.variant, it.versionCode, it.versionDate)
    }))

    private fun declaredGson(entries: Collection<Entry>): String = Gson().toJson(DeclaredPayload(entries.map {
        DeclaredEntry(it.packageName, it.versionCode, it.lastUpdateTime, it.languageTag, it.title, it.description, it.author,
            it.instruction, it.versionDate, it.requiresHostVersion, it.supportedAbis, it.engine, it.variant, it.engineId, it.nativePageAlignment)
    }))

    // The former parseJson, which read the text into the former payload classes with a default (lenient) Gson.
    private fun gsonParseJson(json: String): Map<String, Entry> {
        val payload = runCatching { Gson().fromJson(json, DeclaredPayload::class.java) }.getOrNull() ?: return emptyMap()
        return payload.entries.orEmpty().mapNotNull { raw ->
            if (raw == null) return@mapNotNull null
            val packageName = raw.packageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Entry(
                packageName = packageName,
                versionCode = raw.versionCode,
                lastUpdateTime = raw.lastUpdateTime,
                languageTag = raw.languageTag,
                title = raw.title,
                description = raw.description,
                author = raw.author,
                instruction = raw.instruction,
                versionDate = raw.versionDate,
                requiresHostVersion = raw.requiresHostVersion,
                supportedAbis = raw.supportedAbis?.filterNotNull(),
                nativePageAlignment = PluginNativeAlignmentPolicy.parse(raw.nativePageAlignment),
                engine = raw.engine,
                variant = raw.variant,
                engineId = raw.engineId,
            )
        }.associateBy { it.packageName }
    }

    private fun fieldNames(type: Class<*>): List<String> =
        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }.map { it.name }

    @Test
    fun encodedCacheIsByteIdenticalToGsonOnAndroid() {
        for (entries in collections) {
            assertEquals(androidGson(entries), InstalledPluginMetadataStore.toJson(entries))
        }
        // The data exercises Gson's escaping: HTML characters, separators and control characters are escaped, a slash and
        // non-ASCII text are not; null members are omitted.
        val text = InstalledPluginMetadataStore.toJson(listOf(full))
        listOf("u003c", "u003e", "u0026", "u003d", "u0027", "u2028", "u2029", "u0001", "t", q, bs).forEach { assertTrue(it, text.contains(bs + it)) }
        assertTrue(text.contains("/") && text.contains("文字识别"))
        assertFalse(InstalledPluginMetadataStore.toJson(listOf(minimal)).contains("null"))
    }

    @Test
    fun encoderWritesTheFormerFieldsInNameOrder() {
        val declared = fieldNames(DeclaredEntry::class.java)
        assertEquals(
            listOf("packageName", "versionCode", "lastUpdateTime", "languageTag", "title", "description", "author", "instruction",
                "versionDate", "requiresHostVersion", "supportedAbis", "engine", "variant", "engineId", "nativePageAlignment"),
            declared,
        )
        assertEquals(declared.sorted(), fieldNames(AndroidEntry::class.java))
        // The entry has the same members as the former payload entry, so the encoder covers every one of them.
        assertEquals(declared.sorted(), fieldNames(Entry::class.java).sorted())
        val raw = SettingsJson.parseMap(InstalledPluginMetadataStore.toJson(listOf(full)))
        assertEquals(listOf("entries"), raw.keys.toList())
        assertEquals(declared.sorted(), ((raw["entries"] as List<*>).single() as Map<*, *>).keys.toList())
    }

    @Test
    fun readsCachesWrittenByGsonInEitherFieldOrder() {
        for (entries in collections) {
            val expected = entries.associateBy { it.packageName }
            assertEquals(expected, InstalledPluginMetadataStore.parseJson(androidGson(entries)))
            assertEquals(expected, InstalledPluginMetadataStore.parseJson(declaredGson(entries)))
            assertEquals(expected, gsonParseJson(androidGson(entries)))
        }
    }

    @Test
    fun roundTripKeepsTheEntries() {
        for (entries in collections) {
            val text = InstalledPluginMetadataStore.toJson(entries)
            assertEquals(entries.associateBy { it.packageName }, InstalledPluginMetadataStore.parseJson(text))
            assertEquals(entries.associateBy { it.packageName }, gsonParseJson(text))
        }
    }

    @Test
    fun readsWhatTheFormerReaderReadAndRejectsWhatItRejected() {
        val readable = listOf(
            // Missing and null members stay null, unknown members are ignored, null ABIs are dropped, integral fractions
            // are read into the numbers, and an invalid native page alignment is unknown.
            """{"entries":[{"packageName":"p","extra":{"a":[1,null]},"title":null,"supportedAbis":[null,"x86"],"versionCode":12.0,"lastUpdateTime":1.5E3,"nativePageAlignment":3}],"other":true}""",
            """{"entries":[null,{"title":"x"},{"packageName":" "},{"packageName":"q","nativePageAlignment":4096}]}""",
            "${Char(0xFEFF)}" + InstalledPluginMetadataStore.toJson(listOf(full)),
            SettingsJson.stringify(SettingsJson.parseMap(InstalledPluginMetadataStore.toJson(listOf(full, minimal))), indent = 2),
        )
        for (text in readable) {
            val expected = gsonParseJson(text)
            assertTrue(text, expected.isNotEmpty())
            assertEquals(text, expected, InstalledPluginMetadataStore.parseJson(text))
        }
        assertEquals(entry("p", versionCode = 12L, lastUpdateTime = 1_500L, supportedAbis = listOf("x86")), InstalledPluginMetadataStore.parseJson(readable[0])["p"])

        // A member of another type made the whole cache unreadable.
        val unreadable = listOf(
            "",
            " ",
            "null",
            "[]",
            "not a json document",
            "{}",
            """{"entries":null}""",
            """{"entries":{}}""",
            """{"entries":[1]}""",
            """{"entries":[{"packageName":"p","versionCode":1.5}]}""",
            """{"entries":[{"packageName":"p","versionCode":"x"}]}""",
            """{"entries":[{"packageName":"p","supportedAbis":"x86"}]}""",
            """{"entries":[{"packageName":"p","supportedAbis":[{}]}]}""",
            """{"entries":[{"packageName":{}}]}""",
            """{"entries":[{"packageName":"p"}]}{}""",
        )
        for (text in unreadable) {
            assertEquals(text, emptyMap<String, Entry>(), gsonParseJson(text))
            assertEquals(text, emptyMap<String, Entry>(), InstalledPluginMetadataStore.parseJson(text))
        }
    }

    @Test
    fun gsonLeniencyThatGsonNeverWroteIsDropped() {
        // The former reader read each of these; SettingsJson reads strict JSON only, so the cache reads as empty and the
        // next complete discovery rewrites it.
        val cases = listOf(
            """{"entries":[{"packageName":"p","versionCode":"12"}]}""" to entry("p", versionCode = 12L),
            """{"entries":[{"packageName":"p","title":12}]}""" to entry("p", title = "12"),
            """{"entries":[{"packageName":"p","title":true}]}""" to entry("p", title = "true"),
            """{"entries":[{"packageName":"p","supportedAbis":[64]}]}""" to entry("p", supportedAbis = listOf("64")),
            """{"entries":[{"packageName":"q","packageName":"p"}]}""" to entry("p"),
            """{entries:[{packageName:'p'}]}""" to entry("p"),
            """{"entries":[{"packageName":"p","versionCode":9223372036854775808}]}""" to entry("p", versionCode = Long.MAX_VALUE),
        )
        for ((text, former) in cases) {
            assertEquals(text, mapOf("p" to former), gsonParseJson(text))
            assertEquals(text, emptyMap<String, Entry>(), InstalledPluginMetadataStore.parseJson(text))
        }
    }
}
