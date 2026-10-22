package org.autojs.autojs.core.plugin.center

import android.net.Uri
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PluginIndexCacheDeviceTest {
    private val repository = PluginIndexRepository()

    // The local cache (settings P5.3: plain values of SettingsJson).
    private fun parse(json: String) = PluginIndexRepository.decodeCache(json)

    private fun entries(json: String): List<PluginIndexEntry> = parse(json).entries

    // The index as it comes from the network, whose releases may list their assets.
    private fun indexEntries(json: String): List<PluginIndexEntry> {
        @Suppress("UNCHECKED_CAST")
        return PluginIndexRepository::class.java
            .getDeclaredMethod("parseIndexEntriesJson", String::class.java, PluginIndexSource::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(repository, json, PluginIndexSource.OFFICIAL, null) as List<PluginIndexEntry>
    }

    @Test fun measuredAbsenceOfNativeCodeResolvesAnUninstalledPluginAsAllAbis() {
        val json = """{"items":[{"packageName":"example.noarch","supportedAbis":null,
            "releases":[{"versionCode":1,"nativePageAlignment":0,"nativePageAlignmentSource":"measured"}]}]}"""
        for (entry in listOf(entries(json).single(), indexEntries(json).single())) {
            assertEquals(emptyList<String>(), entry.supportedAbis)
            assertEquals("All", PluginAbiUtils.formatSupportedAbisForDisplay(entry.supportedAbis, "Unknown", "All"))
        }
    }

    @Test fun aUniversalFilenameOrUnverifiedZeroDoesNotClaimAllAbis() {
        for (metadata in listOf("", "\"nativePageAlignment\":0,\"nativePageAlignmentSource\":\"declared\",",
            "\"nativePageAlignment\":16384,\"nativePageAlignmentSource\":\"measured\",")) {
            val entry = indexEntries("""{"items":[{"packageName":"example.unknown","releases":[{$metadata
                "assets":[{"name":"plugin-universal.apk","downloadUrl":"https://example.test/plugin.apk"}]}]}]}""").single()
            assertNull(entry.supportedAbis)
        }
    }

    @Test fun explicitAndNativeAssetAbisKeepTheirRestrictions() {
        val entries = indexEntries("""{"items":[
            {"packageName":"example.declared","supportedAbis":["arm64-v8a","x86_64"],
             "releases":[{"nativePageAlignment":0,"nativePageAlignmentSource":"measured"}]},
            {"packageName":"example.native","releases":[{"nativePageAlignment":16384,"nativePageAlignmentSource":"measured",
             "assets":[{"name":"plugin-x86_64.apk","downloadUrl":"https://example.test/plugin.apk"}]}]}]}""")
        assertEquals(listOf("arm64-v8a", "x86_64"), entries[0].supportedAbis)
        assertEquals(listOf("x86_64"), entries[1].supportedAbis)
    }

    @Test fun olderReleasesCannotSupplyMissingAbiEvidenceForTheLatestRelease() {
        val entry = indexEntries("""{"items":[{"packageName":"example.changed","releases":[
            {"versionCode":1,"nativePageAlignment":0,"nativePageAlignmentSource":"measured",
             "assets":[{"name":"old-x86.apk","downloadUrl":"https://example.test/old.apk"}]},
            {"versionCode":2,"assets":[{"name":"new-universal.apk","downloadUrl":"https://example.test/new.apk"}]}]}]}""").single()
        assertNull(entry.supportedAbis)
    }

    @Test fun diskRoundTripPreservesAbiAndNativeAlignmentEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("plugin-index-abi-", ".json", context.cacheDir)
        val input = listOf(
            PluginIndexEntry(packageName = "example.noarch", title = "No native code", supportedAbis = emptyList(),
                nativePageAlignment = 0L, nativePageAlignmentSource = "measured",
                releases = listOf(PluginIndexRelease("1", 1, nativePageAlignment = 0L, nativePageAlignmentSource = "measured"))),
            PluginIndexEntry(packageName = "example.native", title = "Native code", supportedAbis = listOf("x86_64"),
                nativePageAlignment = 16384L, nativePageAlignmentSource = "declared",
                releases = listOf(PluginIndexRelease("2", 2, nativePageAlignment = 16384L, nativePageAlignmentSource = "measured"))),
            PluginIndexEntry(packageName = "example.unknown", title = "Unknown"),
        )
        val writer = PluginIndexRepository::class.java.getDeclaredMethod("writeCache",
            File::class.java, List::class.java, Set::class.java, String::class.java, Map::class.java)
            .apply { isAccessible = true }
        try {
            writer.invoke(repository, file, input, setOf(PluginIndexSource.OFFICIAL), "en", emptyMap<PluginIndexSource, Long>())
            val restored = entries(file.readText())
            input.zip(restored).forEach { (before, after) ->
                assertEquals(before.supportedAbis, after.supportedAbis)
                assertEquals(before.nativePageAlignment, after.nativePageAlignment)
                assertEquals(before.nativePageAlignmentSource, after.nativePageAlignmentSource)
                assertEquals(before.releases, after.releases)
            }
        } finally {
            AtomicFile(file).delete()
        }
    }

    @Test fun legacySnapshotsAreReadableButHaveNoFreshnessClaim() {
        val snapshot = parse("""{"items":[{"packageName":"old.mail","title":"Old mail"}],"fetchedSources":["OFFICIAL"]}""")
        assertEquals(emptyMap<PluginIndexSource, Long>(), snapshot.fetchedAtMs)
        assertEquals(1, snapshot.entries.size)
    }

    @Test fun malformedResponsesAreNotTreatedAsEmptySuccessfulSnapshots() {
        for (json in listOf("{}", """{"items":null}""", """{"items":[{}]}""", """{"items":[42]}""")) {
            try {
                parse(json)
                fail("Malformed source accepted: $json")
            } catch (expected: IllegalArgumentException) {
                // A malformed cache is a cache miss, not an empty snapshot.
            }
        }
        assertEquals(emptyList<PluginIndexEntry>(), parse("""{"items":[]}""").entries)
    }

    @Test fun diskRoundTripKeepsFreshnessAndTheSameOfficialArtworkInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("plugin-index-contract-", ".json", context.cacheDir)
        val entry = PluginIndexEntry(packageName = "example.plugin", title = "Example",
            iconUrl = Uri.parse("https://example.test/day-hash.png"),
            nightIconUrl = Uri.parse("https://example.test/night-hash.png"), forceIgnoreLocalIcon = true,
            iconBackgroundColor = "#e4b2ff", nightIconBackgroundColor = "transparent")
        val writer = PluginIndexRepository::class.java.getDeclaredMethod("writeCache",
            File::class.java, List::class.java, Set::class.java, String::class.java, Map::class.java)
            .apply { isAccessible = true }
        try {
            writer.invoke(repository, file, listOf(entry), setOf(PluginIndexSource.OFFICIAL), "en",
                mapOf(PluginIndexSource.OFFICIAL to 123_456L))
            val snapshot = parse(file.readText())
            assertEquals(mapOf(PluginIndexSource.OFFICIAL to 123_456L), snapshot.fetchedAtMs)
            val restored = snapshot.entries.single()
            assertEquals(entry.iconUrl, restored.iconUrl)
            assertEquals(entry.nightIconUrl, restored.nightIconUrl)
            assertTrue(restored.forceIgnoreLocalIcon)
            assertEquals(entry.iconBackgroundColor, restored.iconBackgroundColor)
            assertEquals(entry.nightIconBackgroundColor, restored.nightIconBackgroundColor)
            // The device's own org.json still reads the cache as the index parser reads it.
            assertEquals(snapshot.entries, indexEntries(file.readText()))
        } finally {
            AtomicFile(file).delete()
        }
    }
}
