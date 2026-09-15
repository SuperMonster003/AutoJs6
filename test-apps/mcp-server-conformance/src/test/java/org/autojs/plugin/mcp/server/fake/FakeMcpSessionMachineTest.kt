package org.autojs.plugin.mcp.server.fake

import org.autojs.plugin.mcp.server.api.McpServerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMcpSessionMachineTest {

    @Test
    fun startsAsStartingWithoutEndpoints() {
        val machine = FakeMcpSessionMachine(9655)
        assertEquals(FakeMcpSessionMachine.State.OPENED, machine.state)
        assertEquals(McpServerContract.STATE_STARTING, machine.contractState)
        assertTrue(machine.endpoints.isEmpty())
        assertEquals(0, machine.boundPort)
        assertEquals(0L, machine.startedAt)
    }

    @Test
    fun runningPublishesTheLoopbackEndpointOnce() {
        val machine = FakeMcpSessionMachine(9655)
        assertTrue(machine.markRunning(1_000L))
        assertFalse("running twice is not a transition", machine.markRunning(2_000L))
        assertEquals(McpServerContract.STATE_RUNNING, machine.contractState)
        assertEquals(listOf("http://127.0.0.1:9655/mcp"), machine.endpoints)
        assertEquals(9655, machine.boundPort)
        assertEquals(1_000L, machine.startedAt)
        assertNull(machine.lastReasonCode)
    }

    @Test
    fun stopIsATransitionOnlyFromRunning() {
        val machine = FakeMcpSessionMachine(9655)
        assertFalse(machine.stop(McpServerContract.REASON_USER_REQUEST))
        machine.markRunning(1_000L)
        assertTrue(machine.stop(McpServerContract.REASON_HOST_SHUTDOWN))
        assertFalse(machine.stop(McpServerContract.REASON_HOST_SHUTDOWN))
        assertEquals(McpServerContract.STATE_STOPPED, machine.contractState)
        assertEquals(McpServerContract.REASON_HOST_SHUTDOWN, machine.lastReasonCode)
        assertTrue(machine.endpoints.isEmpty())
        assertEquals(0, machine.boundPort)
    }

    @Test
    fun stopWithoutAReasonRecordsUserRequest() {
        val machine = FakeMcpSessionMachine(9655)
        machine.markRunning(1_000L)
        assertTrue(machine.stop(null))
        assertEquals(McpServerContract.REASON_USER_REQUEST, machine.lastReasonCode)
    }

    @Test
    fun aStoppedSessionCanRunAgainUntilClosed() {
        val machine = FakeMcpSessionMachine(9655)
        machine.markRunning(1_000L)
        machine.stop(null)
        assertTrue(machine.markRunning(3_000L))
        assertEquals(3_000L, machine.startedAt)
        assertTrue(machine.close())
        assertFalse(machine.close())
        assertFalse(machine.markRunning(4_000L))
        assertEquals(McpServerContract.STATE_STOPPED, machine.contractState)
    }

    @Test
    fun updatePortChangesTheEndpoint() {
        val machine = FakeMcpSessionMachine(9655)
        machine.markRunning(1_000L)
        machine.updatePort(9700)
        assertEquals(listOf("http://127.0.0.1:9700/mcp"), machine.endpoints)
        assertEquals(9700, machine.boundPort)
    }
}
