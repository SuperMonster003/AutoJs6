package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.pref.SettingsJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The local cache of [PluginIndexRepository] as plain values of [SettingsJson] (docs/settings/ROADMAP.md P5.3): a cache
 * that Android's org.json wrote decodes to the entries that the index parser read from it, the encoder writes the
 * members in the order they always had, a snapshot round trips, and a malformed cache is rejected.
 * [android.net.Uri] cannot be built on the JVM, so the icon addresses are checked as the texts handed to the decoder.
 *
 * zh-CN: [PluginIndexRepository] 的本地缓存以 [SettingsJson] 普通值表示 (ROADMAP P5.3): Android org.json 写的缓存解码为索引解析器
 * 从中读出的条目, 编码器按以往的成员顺序写出, 快照写读往返不变, 格式错误的缓存被拒绝. JVM 上无法构造 [android.net.Uri],
 * 因此图标地址只核对交给解码器的文本.
 */
class PluginIndexCacheJsonTest {

    // A cache as the repository wrote it before P5.1 (Android's `JSONObject.toString()`, escaped slashes): members whose
    // value is null are left out, except `languageTag` and `supportedAbis`; sizes and times are Long values.
    private val oldCache = """{"fetchedAtMs":{"OFFICIAL":1761134400000,"THIRD_PARTY":1761130800000},"languageTag":"zh-hans","items":[""" +
        """{"packageName":"org.autojs.plugin.ocr","forceIgnoreLocalIcon":false,"iconBackgroundColor":"#e4b2ff","title":"OCR",""" +
        """"description":"Text recognition","instructionMarkdownUrl":"https:\/\/example.test\/ocr.md","author":"SuperMonster003",""" +
        """"collaborators":["a","b"],"engine":"paddle-ocr","variant":"v5","engineId":"paddle-ocr-pp-ocrv5","featured":true,""" +
        """"supportedAbis":["arm64-v8a","x86_64"],"tags":["ocr","image"],"requiresHostVersion":5455,"nativePageAlignment":16384,""" +
        """"nativePageAlignmentSource":"measured","source":"OFFICIAL","releases":[""" +
        """{"versionName":"1.2.0","versionCode":12,"versionDate":"2026-10-01",""" +
        """"apkUrl":"https:\/\/github.com\/example\/ocr\/releases\/download\/v1.2.0\/ocr-arm64-v8a.apk","apkSha256":"0123abcd",""" +
        """"apkSizeBytes":5368709120,"changelogUrl":"https:\/\/example.test\/changelog.md","changelogText":"Faster",""" +
        """"compatibilityMatrixMatched":false,"nativePageAlignment":16384,"nativePageAlignmentSource":"measured"},""" +
        """{"versionName":"1.1.0","versionCode":11,"compatibilityMatrixMatched":true}]},""" +
        """{"packageName":"org.example.noarch","forceIgnoreLocalIcon":false,"nightIconBackgroundColor":"transparent","title":"No arch",""" +
        """"collaborators":[],"featured":false,"supportedAbis":[],"tags":[],"nativePageAlignment":0,"nativePageAlignmentSource":"declared",""" +
        """"source":"THIRD_PARTY","releases":[{"versionName":"2.0.0","versionCode":20,"compatibilityMatrixMatched":false,""" +
        """"nativePageAlignment":0,"nativePageAlignmentSource":"measured"}]},""" +
        """{"packageName":"org.example.unknown","forceIgnoreLocalIcon":false,"title":"Unknown","collaborators":[],"featured":true,""" +
        """"supportedAbis":null,"tags":[],"source":"OFFICIAL","releases":[]}""" +
        """],"fetchedSources":["OFFICIAL","THIRD_PARTY"]}"""

    private val expectedEntries = listOf(
        PluginIndexEntry(
            packageName = "org.autojs.plugin.ocr", iconBackgroundColor = "#e4b2ff", title = "OCR", description = "Text recognition",
            instructionMarkdownUrl = "https://example.test/ocr.md", author = "SuperMonster003", collaborators = listOf("a", "b"),
            engine = "paddle-ocr", variant = "v5", engineId = "paddle-ocr-pp-ocrv5", isFeatured = true,
            releases = listOf(
                PluginIndexRelease(
                    versionName = "1.2.0", versionCode = 12L, versionDate = "2026-10-01",
                    apkUrl = "https://github.com/example/ocr/releases/download/v1.2.0/ocr-arm64-v8a.apk", apkSha256 = "0123abcd",
                    apkSizeBytes = 5368709120L, changelogUrl = "https://example.test/changelog.md", changelogText = "Faster",
                    compatibilityMatrixMatched = false, nativePageAlignment = 16384L, nativePageAlignmentSource = "measured",
                ),
                PluginIndexRelease(versionName = "1.1.0", versionCode = 11L, compatibilityMatrixMatched = true),
            ),
            supportedAbis = listOf("arm64-v8a", "x86_64"), tags = listOf("ocr", "image"), requiresHostVersion = 5455L,
            nativePageAlignment = 16384L, nativePageAlignmentSource = "measured", source = PluginIndexSource.OFFICIAL,
        ),
        PluginIndexEntry(
            packageName = "org.example.noarch", nightIconBackgroundColor = "transparent", title = "No arch", isFeatured = false,
            releases = listOf(PluginIndexRelease(versionName = "2.0.0", versionCode = 20L, nativePageAlignment = 0L, nativePageAlignmentSource = "measured")),
            supportedAbis = emptyList(), nativePageAlignment = 0L, nativePageAlignmentSource = "declared", source = PluginIndexSource.THIRD_PARTY,
        ),
        PluginIndexEntry(packageName = "org.example.unknown", title = "Unknown"),
    )

    private val expectedTimes = linkedMapOf(PluginIndexSource.OFFICIAL to 1761134400000L, PluginIndexSource.THIRD_PARTY to 1761130800000L)

    // The path the cache took before P5.3: the index parser, with no language, over the org.json form of the text.
    private fun indexParserEntries(text: String): List<PluginIndexEntry> {
        val parser = PluginIndexRepository::class.java.getDeclaredMethod(
            "parseIndexEntries", JSONObject::class.java, PluginIndexSource::class.java, String::class.java,
        ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return parser.invoke(PluginIndexRepository(), SettingsJson.toOrgObject(SettingsJson.parseMap(text)), PluginIndexSource.OFFICIAL, null) as List<PluginIndexEntry>
    }

    @Test
    fun cachesThatOrgJsonWroteStillRead() {
        val snapshot = PluginIndexRepository.decodeCache(oldCache)
        assertEquals(expectedEntries, snapshot.entries)
        assertEquals(setOf(PluginIndexSource.OFFICIAL, PluginIndexSource.THIRD_PARTY), snapshot.fetchedSources)
        assertEquals("zh-hans", snapshot.languageTag)
        assertEquals(expectedTimes, snapshot.fetchedAtMs)
    }

    @Test
    fun cachesDecodeToTheEntriesTheIndexParserReadFromThem() {
        val texts = listOf(
            oldCache,
            // An older host wrote null for an entry without native code; a measured absence in its latest release means all ABIs.
            """{"languageTag":null,"items":[{"packageName":"example.noarch","title":"No arch","supportedAbis":null,"source":"OFFICIAL",""" +
                """"releases":[{"versionName":"1","versionCode":1,"nativePageAlignment":0,"nativePageAlignmentSource":"measured"},""" +
                """{"versionName":"0","versionCode":0,"nativePageAlignment":16384,"nativePageAlignmentSource":"measured"}]}]}""",
            // A cache from before the snapshot recorded its sources and times.
            """{"items":[{"packageName":"old.mail","title":"Old mail"},{"packageName":"old.tags","tags":["a","","b"],"featured":false}]}""",
        )
        for (text in texts) {
            assertEquals(text, indexParserEntries(text), PluginIndexRepository.decodeCache(text).entries)
        }
        assertEquals(emptyList<String>(), PluginIndexRepository.decodeCache(texts[1]).entries.single().supportedAbis)
        val legacy = PluginIndexRepository.decodeCache(texts[2])
        assertEquals("old.mail", legacy.entries[0].packageName)
        assertEquals("Old mail", legacy.entries[0].title)
        assertEquals(listOf("a", "b"), legacy.entries[1].tags)
        assertNull(legacy.languageTag)
        assertEquals(emptyMap<PluginIndexSource, Long>(), legacy.fetchedAtMs)
        assertEquals(setOf(PluginIndexSource.OFFICIAL), legacy.fetchedSources)
    }

    @Test
    fun iconAddressesReachTheDecoderUnescaped() {
        val seen = mutableListOf<String>()
        val text = """{"languageTag":"en","items":[{"packageName":"example.plugin","iconUrl":"https:\/\/example.test\/day.png",""" +
            """"nightIconUrl":"https:\/\/example.test\/night.png","forceIgnoreLocalIcon":true,"title":"Example"}],"fetchedSources":["OFFICIAL"]}"""
        val entry = PluginIndexRepository.decodeCache(text) { seen += it; null }.entries.single()
        assertEquals(listOf("https://example.test/day.png", "https://example.test/night.png"), seen)
        assertEquals(true, entry.forceIgnoreLocalIcon)
    }

    @Test
    fun cachesAreWrittenWithTheMembersInTheirOrder() {
        // The sources are written sorted, whatever order the set has.
        val snapshot = PluginIndexRepository.CacheSnapshot(
            entries = expectedEntries,
            fetchedSources = linkedSetOf(PluginIndexSource.THIRD_PARTY, PluginIndexSource.OFFICIAL),
            languageTag = "zh-hans",
            fetchedAtMs = expectedTimes,
        )
        val text = PluginIndexRepository.encodeCache(snapshot)
        assertFalse(text.contains("\\/"))
        assertEquals(oldCache.replace("\\/", "/"), text)
    }

    @Test
    fun snapshotsRoundTrip() {
        val snapshots = listOf(
            PluginIndexRepository.decodeCache(oldCache),
            PluginIndexRepository.CacheSnapshot(
                entries = listOf(
                    PluginIndexEntry(packageName = "example.unknown", title = "Unknown", supportedAbis = null),
                    PluginIndexEntry(packageName = "example.declared", title = "Declared", supportedAbis = listOf("x86_64"),
                        releases = listOf(PluginIndexRelease("3.0.0", 30L, apkSizeBytes = 12L))),
                ),
                fetchedSources = setOf(PluginIndexSource.OFFICIAL),
                languageTag = null,
                fetchedAtMs = emptyMap(),
            ),
        )
        for (snapshot in snapshots) {
            assertEquals(snapshot, PluginIndexRepository.decodeCache(PluginIndexRepository.encodeCache(snapshot)))
        }
        assertEquals(
            """{"fetchedAtMs":{},"languageTag":null,"items":[{"packageName":"example.unknown","forceIgnoreLocalIcon":false,"title":"Unknown",""" +
                """"collaborators":[],"featured":true,"supportedAbis":null,"tags":[],"source":"OFFICIAL","releases":[]},""" +
                """{"packageName":"example.declared","forceIgnoreLocalIcon":false,"title":"Declared","collaborators":[],"featured":true,""" +
                """"supportedAbis":["x86_64"],"tags":[],"source":"OFFICIAL","releases":[{"versionName":"3.0.0","versionCode":30,""" +
                """"apkSizeBytes":12,"compatibilityMatrixMatched":false}]}],"fetchedSources":["OFFICIAL"]}""",
            PluginIndexRepository.encodeCache(snapshots[1]),
        )
    }

    @Test
    fun malformedCachesAreRejected() {
        for (text in listOf("{}", """{"items":null}""", """{"items":{}}""", """{"items":[{}]}""", """{"items":[42]}""",
            """{"items":[{"packageName":"  "}]}""", "[]", "not json")) {
            assertThrows(text, IllegalArgumentException::class.java) { PluginIndexRepository.decodeCache(text) }
        }
        assertEquals(emptyList<PluginIndexEntry>(), PluginIndexRepository.decodeCache("""{"items":[]}""").entries)
    }
}
