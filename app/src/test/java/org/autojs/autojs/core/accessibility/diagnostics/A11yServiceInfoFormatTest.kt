package org.autojs.autojs.core.accessibility.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yServiceInfoFormatTest {

    @Test
    fun bitsOfZeroIsEmpty() {
        assertTrue(A11yServiceInfoFormat.bits(0).isEmpty())
    }

    @Test
    fun bitsListsEachSetBitLowestFirst() {
        assertEquals(listOf(1, 2, 8), A11yServiceInfoFormat.bits(0b1011))
        assertEquals(listOf(0x10, 0x100), A11yServiceInfoFormat.bits(0x110))
    }

    @Test
    fun bitsOfAllOnesCoversEveryBitIncludingTheSignBit() {
        val bits = A11yServiceInfoFormat.bits(-1)
        assertEquals(Int.SIZE_BITS, bits.size)
        assertEquals(1, bits.first())
        assertEquals(Int.MIN_VALUE, bits.last())
    }

    @Test
    fun formatStripsPrefixAndJoinsNames() {
        val names = mapOf(1 to "FLAG_INCLUDE_NOT_IMPORTANT_VIEWS", 2 to "FLAG_REQUEST_TOUCH_EXPLORATION_MODE")
        assertEquals(
            "INCLUDE_NOT_IMPORTANT_VIEWS, REQUEST_TOUCH_EXPLORATION_MODE",
            A11yServiceInfoFormat.format(0b11, "FLAG_", names::get),
        )
    }

    @Test
    fun formatFallsBackToHexForUnknownBits() {
        val names = mapOf(1 to "CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT")
        assertEquals(
            "CAN_RETRIEVE_WINDOW_CONTENT, 0x10, 0x80000000",
            A11yServiceInfoFormat.format(1 or 0x10 or Int.MIN_VALUE, "CAPABILITY_", names::get),
        )
    }

    @Test
    fun formatTreatsBlankNamesAsUnknown() {
        assertEquals("0x4", A11yServiceInfoFormat.format(4, "FLAG_") { "" })
    }

    @Test
    fun formatKeepsNamesWithoutThePrefix() {
        assertEquals("DEFAULT", A11yServiceInfoFormat.format(1, "FLAG_") { "DEFAULT" })
    }

    @Test
    fun formatOfEmptyMaskIsZero() {
        assertEquals("0", A11yServiceInfoFormat.format(0, "FLAG_") { error("must not be called") })
    }

    @Test
    fun formatElapsedPicksTheUnitByMagnitude() {
        assertEquals("0 ms", A11yServiceInfoFormat.formatElapsed(-5))
        assertEquals("0 ms", A11yServiceInfoFormat.formatElapsed(0))
        assertEquals("48 ms", A11yServiceInfoFormat.formatElapsed(48))
        assertEquals("1.0 s", A11yServiceInfoFormat.formatElapsed(1_000))
        assertEquals("2.5 s", A11yServiceInfoFormat.formatElapsed(2_500))
        assertEquals("1 min 1 s", A11yServiceInfoFormat.formatElapsed(61_000))
        assertEquals("59 min 59 s", A11yServiceInfoFormat.formatElapsed(3_599_999))
        assertEquals("1 h 1 min", A11yServiceInfoFormat.formatElapsed(3_660_000))
    }

}
