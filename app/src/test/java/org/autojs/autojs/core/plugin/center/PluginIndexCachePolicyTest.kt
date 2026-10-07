package org.autojs.autojs.core.plugin.center

import org.junit.Assert.*
import org.junit.Test

class PluginIndexCachePolicyTest {
    private data class Entry(val source: String, val packageName: String)

    @Test fun successfulRefreshRemovesRenamedPackagesAndKeepsOtherSources() {
        val old = Entry("official", "old.mail")
        val new = Entry("official", "three.stamp.mail")
        val external = Entry("thirdParty", "external.plugin")
        val result = PluginIndexCachePolicy.replaceSuccessfulSources(
            listOf(old, external), mapOf("official" to listOf(new)), Entry::source)
        assertEquals(listOf(new, external), result)
        assertFalse(result.contains(old))
    }

    @Test fun anEmptySuccessfulSourceReallyRemovesItsLastEntry() {
        val external = Entry("thirdParty", "external.plugin")
        val result = PluginIndexCachePolicy.replaceSuccessfulSources(
            listOf(Entry("official", "retired.plugin"), external),
            mapOf("official" to emptyList<Entry>()), Entry::source)
        assertEquals(listOf(external), result)
    }

    @Test fun failedAndDisabledSourcesKeepTheirLastGoodSnapshot() {
        val cached = listOf(Entry("official", "official.plugin"), Entry("thirdParty", "external.plugin"))
        assertEquals(cached, PluginIndexCachePolicy.replaceSuccessfulSources(cached, emptyMap(), Entry::source))
    }

    @Test fun freshnessExpiresAndOldCacheFormatsAreImmediatelyStale() {
        val fetched = 1_000L
        assertTrue(PluginIndexCachePolicy.isFresh(fetched, fetched))
        assertTrue(PluginIndexCachePolicy.isFresh(fetched, fetched + PluginIndexCachePolicy.MAX_AGE_MS - 1))
        assertFalse(PluginIndexCachePolicy.isFresh(fetched, fetched + PluginIndexCachePolicy.MAX_AGE_MS))
        assertFalse(PluginIndexCachePolicy.isFresh(null, fetched))
        assertFalse(PluginIndexCachePolicy.isFresh(0, fetched))
        assertFalse(PluginIndexCachePolicy.isFresh(fetched + 1, fetched))
    }

    @Test fun retryBackoffDoesNotTrapADeviceAfterTheClockMovesBack() {
        assertTrue(PluginIndexCachePolicy.wasAttemptRecent(10_000, 11_000, 15_000))
        assertFalse(PluginIndexCachePolicy.wasAttemptRecent(10_000, 25_000, 15_000))
        assertFalse(PluginIndexCachePolicy.wasAttemptRecent(10_000, 9_000, 15_000))
        assertFalse(PluginIndexCachePolicy.wasAttemptRecent(0, 1_000, 15_000))
    }
}
