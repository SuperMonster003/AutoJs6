package org.autojs.autojs.core.plugin.center

import android.net.Uri
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

class PluginIndexCacheDeviceTest {
    private val repository = PluginIndexRepository()

    private fun parse(json: String): Any = PluginIndexRepository::class.java
        .getDeclaredMethod("parseCacheJson", String::class.java)
        .apply { isAccessible = true }.invoke(repository, json)

    private fun field(snapshot: Any, name: String): Any? = snapshot.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(snapshot)

    @Test fun legacySnapshotsAreReadableButHaveNoFreshnessClaim() {
        val snapshot = parse("""{"items":[{"packageName":"old.mail","title":"Old mail"}],"fetchedSources":["OFFICIAL"]}""")
        assertEquals(emptyMap<PluginIndexSource, Long>(), field(snapshot, "fetchedAtMs"))
        assertEquals(1, (field(snapshot, "entries") as List<*>).size)
    }

    @Test fun malformedResponsesAreNotTreatedAsEmptySuccessfulSnapshots() {
        for (json in listOf("{}", """{"items":null}""", """{"items":[{}]}""", """{"items":[42]}""")) {
            try {
                parse(json)
                fail("Malformed source accepted: $json")
            } catch (expected: InvocationTargetException) {
                assertTrue(expected.cause is IllegalArgumentException)
            }
        }
        assertEquals(emptyList<Any>(), field(parse("""{"items":[]}"""), "entries"))
    }

    @Test fun diskRoundTripKeepsFreshnessAndTheSameOfficialArtworkInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("plugin-index-contract-", ".json", context.cacheDir)
        val entry = PluginIndexEntry(packageName = "example.plugin", title = "Example",
            iconUrl = Uri.parse("https://example.test/day-hash.png"),
            nightIconUrl = Uri.parse("https://example.test/night-hash.png"), forceIgnoreLocalIcon = true)
        val writer = PluginIndexRepository::class.java.getDeclaredMethod("writeCache",
            File::class.java, List::class.java, Set::class.java, String::class.java, Map::class.java)
            .apply { isAccessible = true }
        try {
            writer.invoke(repository, file, listOf(entry), setOf(PluginIndexSource.OFFICIAL), "en",
                mapOf(PluginIndexSource.OFFICIAL to 123_456L))
            val snapshot = parse(file.readText())
            assertEquals(mapOf(PluginIndexSource.OFFICIAL to 123_456L), field(snapshot, "fetchedAtMs"))
            val restored = (field(snapshot, "entries") as List<*>).single() as PluginIndexEntry
            assertEquals(entry.iconUrl, restored.iconUrl)
            assertEquals(entry.nightIconUrl, restored.nightIconUrl)
            assertTrue(restored.forceIgnoreLocalIcon)
        } finally {
            AtomicFile(file).delete()
        }
    }
}
