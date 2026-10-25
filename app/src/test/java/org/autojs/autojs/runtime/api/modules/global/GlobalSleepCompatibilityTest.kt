package org.autojs.autojs.runtime.api.modules.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSleepCompatibilityTest {

    @Test
    fun decimalAndExponentDeltasAreParsedAsNumbers() {
        assertEquals(Global.SleepRange(998.5, 1001.5), Global.resolveSleepRange(1000, "1.5"))
        assertEquals(Global.SleepRange(0.0, 2000.0), Global.resolveSleepRange(1000, "1e3"))
    }

    @Test
    fun lowerBoundIsClampedAndReversedBoundsAreNormalized() {
        assertEquals(Global.SleepRange(0.0, 1500.0), Global.resolveSleepRange(500, "1e3"))
        assertEquals(Global.SleepRange(50.0, 100.0), Global.resolveSleepRange(100, 50))
        assertEquals(Global.SleepRange(98.5, 101.5), Global.resolveSleepRange(100, "-1.5"))
    }

    @Test
    fun randomSelectionUsesCeilAndSafelyConvertsToLong() {
        val range = Global.SleepRange(98.5, 101.5)

        assertEquals(98L, Global.selectSleepMillis(range, 0.0))
        assertEquals(99L, Global.selectSleepMillis(range, 0.1))
        assertEquals(101L, Global.selectSleepMillis(range, 0.99))
        assertTrue(Global.selectSleepMillis(Global.SleepRange(0.0, Long.MAX_VALUE.toDouble()), 0.99) >= 0L)
    }

}
