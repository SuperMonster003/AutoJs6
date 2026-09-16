package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetentionLimitUnitsTest {

    private val mb = RetentionLimitUnits.MEGABYTE

    @Test
    fun megabytesRoundTrip() {
        assertEquals(0L, RetentionLimitUnits.megabytesToBytes(0L))
        assertEquals(0L, RetentionLimitUnits.megabytesToBytes(-3L))
        assertEquals(200L * mb, RetentionLimitUnits.megabytesToBytes(200L))
        assertEquals(200L, RetentionLimitUnits.bytesToMegabytesRounded(200L * mb))
        assertEquals(2048L, RetentionLimitUnits.bytesToMegabytesRounded(2L * 1024L * mb))
    }

    @Test
    fun roundingNeverDropsPositiveLimitToZero() {
        assertEquals(0L, RetentionLimitUnits.bytesToMegabytesRounded(0L))
        assertEquals(1L, RetentionLimitUnits.bytesToMegabytesRounded(1L))
        assertEquals(1L, RetentionLimitUnits.bytesToMegabytesRounded(mb / 2 + 1))
        assertEquals(1L, RetentionLimitUnits.bytesToMegabytesRounded(mb + mb / 2 - 1))
        assertEquals(2L, RetentionLimitUnits.bytesToMegabytesRounded(mb + mb / 2))
    }

    @Test
    fun overflowIsCapped() {
        val bytes = RetentionLimitUnits.megabytesToBytes(Long.MAX_VALUE)
        assertEquals(0L, bytes % mb)
        assertEquals(Long.MAX_VALUE / mb * mb, bytes)
    }

    @Test
    fun parsing() {
        assertEquals(0L, RetentionLimitUnits.parseNonNegativeOrNull("0"))
        assertEquals(30L, RetentionLimitUnits.parseNonNegativeOrNull(" 30 "))
        assertNull(RetentionLimitUnits.parseNonNegativeOrNull(""))
        assertNull(RetentionLimitUnits.parseNonNegativeOrNull(null))
        assertNull(RetentionLimitUnits.parseNonNegativeOrNull("-1"))
        assertNull(RetentionLimitUnits.parseNonNegativeOrNull("1.5"))
        assertNull(RetentionLimitUnits.parseNonNegativeOrNull("1".repeat(RetentionLimitUnits.MAX_INPUT_DIGITS + 1)))
    }
}
