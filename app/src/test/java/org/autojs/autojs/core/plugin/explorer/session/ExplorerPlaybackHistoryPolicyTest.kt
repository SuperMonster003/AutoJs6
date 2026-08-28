package org.autojs.autojs.core.plugin.explorer.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ExplorerPlaybackHistoryPolicyTest {

    @Test
    fun reportsStoreOnlyMeaningfulIncompleteProgress() {
        assertEquals(
            ExplorerPlaybackReportDecision.STORE,
            ExplorerPlaybackHistoryPolicy.reportDecision(30_000L, 120_000L, completed = false),
        )
        assertEquals(
            ExplorerPlaybackReportDecision.CLEAR,
            ExplorerPlaybackHistoryPolicy.reportDecision(4_999L, 120_000L, completed = false),
        )
        assertEquals(
            ExplorerPlaybackReportDecision.CLEAR,
            ExplorerPlaybackHistoryPolicy.reportDecision(10_000L, 29_999L, completed = false),
        )
        assertEquals(
            ExplorerPlaybackReportDecision.CLEAR,
            ExplorerPlaybackHistoryPolicy.reportDecision(95_000L, 100_000L, completed = false),
        )
        assertEquals(
            ExplorerPlaybackReportDecision.CLEAR,
            ExplorerPlaybackHistoryPolicy.reportDecision(30_000L, 120_000L, completed = true),
        )
    }

    @Test
    fun reportsRejectInvalidTimelines() {
        assertThrows(IllegalArgumentException::class.java) {
            ExplorerPlaybackHistoryPolicy.reportDecision(-1L, 100_000L, completed = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExplorerPlaybackHistoryPolicy.reportDecision(1L, 0L, completed = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExplorerPlaybackHistoryPolicy.reportDecision(100_001L, 100_000L, completed = false)
        }
    }

    @Test
    fun codecRoundTripsValidRecordsAndRejectsMalformedValues() {
        val record = ExplorerPlaybackProgressRecord(30_000L, 120_000L, 42L)

        assertEquals(record, ExplorerPlaybackHistoryPolicy.decode(ExplorerPlaybackHistoryPolicy.encode(record)))
        assertNull(ExplorerPlaybackHistoryPolicy.decode(null))
        assertNull(ExplorerPlaybackHistoryPolicy.decode("not|a|record"))
        assertNull(ExplorerPlaybackHistoryPolicy.decode("100|1000|1"))
    }

    @Test
    fun pathKeysAreStableOpaqueAndPathSpecific() {
        val first = ExplorerPlaybackHistoryPolicy.keyForCanonicalPath("/storage/videos/episode01.mkv")
        val again = ExplorerPlaybackHistoryPolicy.keyForCanonicalPath("/storage/videos/episode01.mkv")
        val second = ExplorerPlaybackHistoryPolicy.keyForCanonicalPath("/storage/videos/episode02.mkv")

        assertEquals(first, again)
        assertNotEquals(first, second)
        assertEquals(64, first.length)
        assertNotEquals("/storage/videos/episode01.mkv", first)
    }
}
