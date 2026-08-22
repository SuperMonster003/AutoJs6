package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceHostExecutionGateTest {
    @Test
    fun admitsOneExecutionWithZeroQueueAndRecoversAfterTerminalRelease() {
        val gate = JvmSourceHostExecutionGate<Any>()
        val first = Any()
        val concurrent = Any()
        val recovered = Any()

        assertEquals(JvmSourceHostExecutionGate.Admission.ACQUIRED, gate.tryAcquire(first))
        assertEquals(JvmSourceHostExecutionGate.Admission.BUSY, gate.tryAcquire(concurrent))
        assertFalse(gate.release(concurrent))
        assertTrue(gate.release(first))
        assertEquals(JvmSourceHostExecutionGate.Admission.ACQUIRED, gate.tryAcquire(recovered))
        assertTrue(gate.release(recovered))
    }

    @Test
    fun closeAtomicallyRejectsNewWorkAndReturnsTheActiveControlForCancellation() {
        val gate = JvmSourceHostExecutionGate<Any>()
        val active = Any()
        assertEquals(JvmSourceHostExecutionGate.Admission.ACQUIRED, gate.tryAcquire(active))

        assertSame(active, gate.close())
        assertNull(gate.close())
        assertEquals(JvmSourceHostExecutionGate.Admission.CLOSED, gate.tryAcquire(Any()))
        assertTrue(gate.snapshot().closed)
        assertTrue(gate.snapshot().active)

        assertTrue(gate.release(active))
        assertFalse(gate.snapshot().active)
    }
}
