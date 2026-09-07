package org.autojs.autojs.core.plugin.mcp

import org.autojs.plugin.mcp.server.api.McpServerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerConfigTest {

    @Test
    fun defaultConfigIsValidAndTargetsTheContractDefaults() {
        val config = McpServerConfig()
        assertNull(config.validate())
        assertEquals(McpServerContract.DEFAULT_PORT, config.port)
        assertEquals(McpServerContract.BIND_SCOPE_LOOPBACK, config.bindScope)
        assertEquals(McpServerContract.PROTOCOL_MODE_STATEFUL, config.protocolMode)
        assertEquals("http://127.0.0.1:9637/mcp", config.loopbackEndpoint)
    }

    @Test
    fun portOutsideTheContractRangeIsRejected() {
        listOf(0, 80, McpServerContract.MIN_PORT - 1, McpServerContract.MAX_PORT + 1).forEach { port ->
            val error = McpServerConfig(port = port).validate()
            assertNotNull("port $port must be rejected", error)
            assertEquals(McpServerContract.ERROR_INVALID_CONFIG, error!!.code)
        }
        assertNull(McpServerConfig(port = McpServerContract.MIN_PORT).validate())
        assertNull(McpServerConfig(port = McpServerContract.MAX_PORT).validate())
    }

    @Test
    fun unknownBindScopeOrProtocolModeIsRejected() {
        assertEquals(McpServerContract.ERROR_INVALID_CONFIG, McpServerConfig(bindScope = "wan").validate()?.code)
        assertEquals(McpServerContract.ERROR_INVALID_CONFIG, McpServerConfig(protocolMode = "stateless").validate()?.code)
        assertNull(McpServerConfig(bindScope = McpServerContract.BIND_SCOPE_LAN).validate())
    }

    @Test
    fun dualProtocolModeStaysReserved() {
        val error = McpServerConfig(protocolMode = McpServerContract.PROTOCOL_MODE_DUAL).validate()
        assertNotNull(error)
        assertTrue(error!!.message.contains("reserved"))
    }

    @Test
    fun hostLabelIsBounded() {
        assertNull(McpServerConfig(hostLabel = "a".repeat(McpServerConfig.MAX_HOST_LABEL_LENGTH)).validate())
        assertNotNull(McpServerConfig(hostLabel = "a".repeat(McpServerConfig.MAX_HOST_LABEL_LENGTH + 1)).validate())
    }

    @Test
    fun statusHelpersExposeTheContractStates() {
        val config = McpServerConfig(port = 9700, bindScope = McpServerContract.BIND_SCOPE_LAN)
        val starting = McpServerStatus.starting(config)
        assertEquals(McpServerContract.STATE_STARTING, starting.state)
        assertEquals(9700, starting.port)
        assertEquals(McpServerContract.BIND_SCOPE_LAN, starting.bindScope)
        assertTrue(starting.isActive)
        assertFalse(starting.isRunning)

        val stopped = McpServerStatus.stopped(McpServerContract.REASON_PLUGIN_DIED, "gone")
        assertEquals(McpServerContract.STATE_STOPPED, stopped.state)
        assertEquals(McpServerContract.REASON_PLUGIN_DIED, stopped.lastErrorCode)
        assertEquals("gone", stopped.lastError)
        assertFalse(stopped.isActive)

        val failed = McpServerStatus.failed(McpServerContract.ERROR_PORT_IN_USE, "9637 busy")
        assertEquals(McpServerContract.STATE_FAILED, failed.state)
        assertFalse(failed.isActive)
        assertFalse(failed.isRunning)

        val running = McpServerStatus(state = McpServerContract.STATE_RUNNING)
        assertTrue(running.isActive)
        assertTrue(running.isRunning)
    }

    @Test
    fun rebindPolicyIsBoundedToThreeGrowingDelays() {
        assertEquals(3, McpRebindPolicy.MAX_ATTEMPTS)
        assertEquals(1_000L, McpRebindPolicy.delayForAttempt(1))
        assertEquals(3_000L, McpRebindPolicy.delayForAttempt(2))
        assertEquals(9_000L, McpRebindPolicy.delayForAttempt(3))
        assertNull(McpRebindPolicy.delayForAttempt(4))
        assertNull(McpRebindPolicy.delayForAttempt(0))
        assertFalse(McpRebindPolicy.isExhausted(3))
        assertTrue(McpRebindPolicy.isExhausted(4))
    }
}
