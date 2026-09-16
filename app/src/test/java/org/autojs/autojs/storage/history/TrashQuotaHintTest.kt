package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashQuotaHintTest {

    @Test
    fun unlimitedLimitNeverAsksNorMeasures() {
        assertFalse(TrashQuotaHint.needsMeasurement(0L))
        assertFalse(TrashQuotaHint.needsMeasurement(-1L))
        assertEquals(TrashQuotaHint.Decision.PROCEED, TrashQuotaHint.decide(0L, Long.MAX_VALUE))
        assertEquals(TrashQuotaHint.Decision.PROCEED, TrashQuotaHint.decide(-1L, 1L))
    }

    @Test
    fun batchWithinLimitProceeds() {
        assertTrue(TrashQuotaHint.needsMeasurement(1024L))
        assertEquals(TrashQuotaHint.Decision.PROCEED, TrashQuotaHint.decide(1024L, 0L))
        assertEquals(TrashQuotaHint.Decision.PROCEED, TrashQuotaHint.decide(1024L, 1023L))
        // Exactly at the limit still fits.
        assertEquals(TrashQuotaHint.Decision.PROCEED, TrashQuotaHint.decide(1024L, 1024L))
    }

    @Test
    fun batchOverLimitAsks() {
        assertEquals(TrashQuotaHint.Decision.ASK, TrashQuotaHint.decide(1024L, 1025L))
        assertEquals(TrashQuotaHint.Decision.ASK, TrashQuotaHint.decide(1L, Long.MAX_VALUE))
    }
}
