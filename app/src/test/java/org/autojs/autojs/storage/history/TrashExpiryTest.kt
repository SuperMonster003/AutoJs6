package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashExpiryTest {

    private val trashedAt = 1_800_000_000_000L

    @Test
    fun unlimitedDaysNeverExpire() {
        assertNull(TrashExpiry.expiresAtOrNull(trashedAt, 0))
        assertNull(TrashExpiry.expiresAtOrNull(trashedAt, -5))
    }

    @Test
    fun expiryMatchesRetentionPolicy() {
        val maxDays = 30
        val expiresAt = TrashExpiry.expiresAtOrNull(trashedAt, maxDays)!!
        assertEquals(trashedAt + 30L * TrashExpiry.DAY_MS, expiresAt)

        // One millisecond before the moment the item is still kept, at the moment it is expired.
        val keptAt = expiresAt - 1
        assertTrue(HistoryRetentionPolicy.selectExpired(listOf(trashedAt), keptAt, maxDays) { it }.isEmpty())
        assertFalse(TrashExpiry.remaining(expiresAt, keptAt).expired)
        assertTrue(TrashExpiry.remaining(expiresAt, expiresAt).expired)
        assertEquals(listOf(trashedAt), HistoryRetentionPolicy.selectExpired(listOf(trashedAt), expiresAt + 1, maxDays) { it })
    }

    @Test
    fun remainingUnitsAndHighlight() {
        val expiresAt = trashedAt + 3 * TrashExpiry.DAY_MS + 5 * TrashExpiry.HOUR_MS + 1
        val remaining = TrashExpiry.remaining(expiresAt, trashedAt)
        assertEquals(3L, remaining.days)
        assertEquals(5L, remaining.hours)
        assertFalse(remaining.expired)
        assertFalse(remaining.withinOneDay)

        val soon = TrashExpiry.remaining(expiresAt, expiresAt - 2 * TrashExpiry.HOUR_MS)
        assertEquals(0L, soon.days)
        assertEquals(2L, soon.hours)
        assertTrue(soon.withinOneDay)
        assertFalse(soon.expired)

        val past = TrashExpiry.remaining(expiresAt, expiresAt + 1)
        assertTrue(past.expired)
        assertTrue(past.withinOneDay)
        assertEquals(0L, past.days)
    }

    @Test
    fun oneDayBoundaryAndSubHourRemainder() {
        val expiresAt = trashedAt + 10 * TrashExpiry.DAY_MS

        // Exactly one day left is not "within one day"; one millisecond less is.
        val oneDay = TrashExpiry.remaining(expiresAt, expiresAt - TrashExpiry.DAY_MS)
        assertEquals(1L, oneDay.days)
        assertEquals(0L, oneDay.hours)
        assertFalse(oneDay.withinOneDay)
        val justUnder = TrashExpiry.remaining(expiresAt, expiresAt - TrashExpiry.DAY_MS + 1)
        assertEquals(0L, justUnder.days)
        assertEquals(23L, justUnder.hours)
        assertTrue(justUnder.withinOneDay)

        // Less than an hour left rounds down to zero hours but is not expired yet.
        val minutes = TrashExpiry.remaining(expiresAt, expiresAt - 59L * 60L * 1000L)
        assertEquals(0L, minutes.days)
        assertEquals(0L, minutes.hours)
        assertFalse(minutes.expired)
        assertTrue(minutes.withinOneDay)

        // Large retention periods do not overflow the day arithmetic.
        val farExpiry = TrashExpiry.expiresAtOrNull(trashedAt, 3650)!!
        assertEquals(3650L, TrashExpiry.remaining(farExpiry, trashedAt).days)
    }
}
