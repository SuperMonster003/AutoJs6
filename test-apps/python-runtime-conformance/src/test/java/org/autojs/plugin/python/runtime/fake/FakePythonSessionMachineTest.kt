package org.autojs.plugin.python.runtime.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePythonSessionMachineTest {
    @Test
    fun explicitStartCreditsSequenceAndSingleTerminalAreDeterministic() {
        val machine = FakePythonSessionMachine(maximumCredits = 2, firstOutputSequence = 7L)
        machine.grantCredits(2)
        assertTrue(machine.start())
        assertFalse(machine.start())
        assertEquals(7L, machine.consumeOutputCredit())
        assertEquals(8L, machine.consumeOutputCredit())
        assertTrue(machine.finish(FakePythonSessionMachine.Terminal.RESULT))
        assertFalse(machine.finish(FakePythonSessionMachine.Terminal.FAILED))
        assertEquals(FakePythonSessionMachine.Terminal.RESULT, machine.terminal)
    }

    @Test
    fun zeroNegativeAndOverflowCreditsFailClosedAtThePolicyBoundary() {
        listOf(0, -1).forEach { count ->
            val machine = FakePythonSessionMachine(2, 0L)
            assertTrue(runCatching { machine.grantCredits(count) }.isFailure)
        }
        val machine = FakePythonSessionMachine(2, 0L)
        machine.grantCredits(2)
        assertTrue(runCatching { machine.grantCredits(1) }.isFailure)
        machine.start()
        machine.consumeOutputCredit()
        assertEquals(1, machine.outstandingCredits)
    }

    @Test
    fun cancelBeforeStartAndCloseAreIdempotent() {
        val cancelled = FakePythonSessionMachine(1, 0L)
        assertTrue(cancelled.cancel())
        assertFalse(cancelled.cancel())
        assertFalse(cancelled.start())

        val closed = FakePythonSessionMachine(1, 0L)
        assertTrue(closed.close())
        assertFalse(closed.close())
        assertFalse(closed.start())
    }

    @Test
    fun processGateNeverQueuesAndOnlyOwnerReleases() {
        val gate = FakeSingleActiveSessionGate<Any>()
        val first = Any()
        val second = Any()
        assertTrue(gate.tryAcquire(first))
        assertFalse(gate.tryAcquire(second))
        assertFalse(gate.release(second))
        assertEquals(first, gate.current())
        assertTrue(gate.release(first))
        assertTrue(gate.tryAcquire(second))
    }
}
