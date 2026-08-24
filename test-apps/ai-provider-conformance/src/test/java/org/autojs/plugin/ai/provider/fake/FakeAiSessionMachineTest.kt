package org.autojs.plugin.ai.provider.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiSessionMachineTest {
    @Test
    fun creditsCanBeSnapshottedPregrantedReplenishedAndSequencesAdvanceExactlyOnce() {
        val machine = machine()
        machine.grantCredits(2)
        val initialWindow = machine.creditCount()
        machine.start(firstChunkSequence = 4L)

        assertEquals(4L, machine.reserveNextChunk())
        assertEquals(initialWindow - 1, machine.creditCount())
        machine.grantCredits(1)
        assertEquals(initialWindow, machine.creditCount())
        assertEquals(5L, machine.reserveNextChunk())
        assertEquals(6L, machine.reserveNextChunk())
        assertNull(machine.reserveNextChunk())
        assertEquals(0, machine.creditCount())
    }

    @Test
    fun creditWindowRejectsNonPositiveAndOverflowGrants() {
        val machine = machine()
        assertThrows(IllegalArgumentException::class.java) { machine.grantCredits(0) }
        machine.grantCredits(16)
        assertThrows(IllegalArgumentException::class.java) { machine.grantCredits(1) }
    }

    @Test
    fun toolTurnRequiresExactResultsAndPausesChunks() {
        val machine = machine(maximumToolRounds = 2)
        machine.grantCredits(1)
        machine.start()
        machine.openToolTurn(listOf("call-a", "call-b"))

        assertThrows(IllegalStateException::class.java) { machine.reserveNextChunk() }
        assertThrows(IllegalArgumentException::class.java) { machine.submitToolResults(listOf("call-a")) }
        machine.submitToolResults(listOf("call-b", "call-a"))
        assertEquals(0L, machine.reserveNextChunk())
        assertEquals(1, machine.completedToolRounds)
    }

    @Test
    fun toolCallIdsCannotReplayAcrossRounds() {
        val machine = machine(maximumToolRounds = 2)
        machine.start()
        machine.openToolTurn(listOf("call-a"))
        machine.submitToolResults(listOf("call-a"))

        assertThrows(IllegalArgumentException::class.java) {
            machine.openToolTurn(listOf("call-a"))
        }
    }

    @Test
    fun terminalCancelAndCloseOperationsAreIdempotent() {
        val machine = machine()
        machine.start()
        assertTrue(machine.complete())
        assertFalse(machine.cancel())
        assertTrue(machine.close())
        assertFalse(machine.close())
        assertEquals(FakeAiSessionMachine.Terminal.COMPLETED, machine.terminal)
    }

    @Test
    fun cancelBeforeStartIsTerminalAndCannotBeRepeated() {
        val machine = machine()

        assertTrue(machine.cancel())
        assertFalse(machine.cancel())
        assertEquals(FakeAiSessionMachine.Terminal.CANCELLED, machine.terminal)
    }

    private fun machine(maximumToolRounds: Int = 0) = FakeAiSessionMachine(
        maximumToolRounds = maximumToolRounds,
        maximumOutstandingToolCalls = 2,
    )
}
