package org.autojs.plugin.dexcompiler.fake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSingleActiveSessionGateTest {
    @Test
    fun onlyTheOwningSessionCanReleaseTheProcessSlot() {
        val gate = FakeSingleActiveSessionGate<Any>()
        val first = Any()
        val second = Any()

        assertTrue(gate.tryAcquire(first))
        assertFalse(gate.tryAcquire(second))
        assertFalse(gate.release(second))
        assertFalse(gate.tryAcquire(second))
        assertTrue(gate.release(first))
        assertTrue(gate.tryAcquire(second))
    }
}
