package org.autojs.autojs.core.plugin.mcp

import org.autojs.plugin.mcp.server.api.McpServerContract as C
import org.junit.Assert.*
import org.junit.Test

class McpServerUiPolicyTest {
    private val ready = McpServerPluginFacts(installed = true, pluginEnabled = true, authorized = true, trustedSigner = true)

    @Test fun `discovery distinguishes missing disabled unactivated incompatible and available`() {
        val cases = mapOf(
            ready.copy(installed = false) to McpServerUiState.NOT_INSTALLED,
            ready.copy(applicationEnabled = false) to McpServerUiState.APPLICATION_DISABLED,
            ready.copy(stopped = true) to McpServerUiState.ACTIVATION_REQUIRED,
            ready.copy(pluginEnabled = false) to McpServerUiState.PLUGIN_DISABLED,
            ready.copy(compatible = false) to McpServerUiState.INCOMPATIBLE,
            ready to McpServerUiState.AVAILABLE,
        )
        cases.forEach { (facts, state) -> assertEquals(state, McpServerUiPolicy.availability(facts)) }
    }

    @Test fun `manual authorization still requires signer confirmation before device access`() {
        assertEquals(McpServerUiState.AUTHORIZATION_REQUIRED, McpServerUiPolicy.availability(ready.copy(authorized = false)))
        val manual = ready.copy(trustedSigner = false)
        assertEquals(McpServerUiState.TRUST_CONFIRMATION_REQUIRED, McpServerUiPolicy.availability(manual))
        assertEquals(McpServerUiState.AVAILABLE, McpServerUiPolicy.availability(manual.copy(signerConfirmed = true)))
        assertEquals(McpServerUiState.INCOMPATIBLE, McpServerUiPolicy.availability(manual.copy(compatible = false)))
    }

    @Test fun `degraded running server keeps the switch on and a user stop turns it off`() {
        val running = McpServerStatus(state = C.STATE_RUNNING, hostAvailable = true)
        assertEquals(McpServerUiState.RUNNING, McpServerUiPolicy.state(running))
        val degraded = McpServerUiPolicy.state(running.copy(hostAvailable = false))
        assertEquals(McpServerUiState.HOST_UNAVAILABLE, degraded)
        assertTrue(McpServerUiPolicy.checked(degraded))
        assertFalse(McpServerUiPolicy.progress(degraded))
        assertFalse(McpServerUiPolicy.checked(McpServerUiPolicy.state(McpServerStatus.stopped(C.REASON_USER_REQUEST))))
        assertFalse(McpServerUiPolicy.checked(McpServerUiPolicy.state(McpServerStatus.failed(C.ERROR_PORT_IN_USE, "occupied"))))
        assertTrue(McpServerUiPolicy.progress(McpServerUiPolicy.state(McpServerStatus.starting(McpServerConfig()))))
    }

    @Test fun `an idle switch names only an installed plugin that still needs activation`() {
        assertEquals(McpServerUiState.ACTIVATION_REQUIRED, McpServerUiPolicy.refreshedGuidance(McpServerUiState.STOPPED, McpServerUiState.ACTIVATION_REQUIRED))
        listOf(
            McpServerUiState.NOT_INSTALLED, McpServerUiState.APPLICATION_DISABLED, McpServerUiState.PLUGIN_DISABLED,
            McpServerUiState.AUTHORIZATION_REQUIRED, McpServerUiState.TRUST_CONFIRMATION_REQUIRED, McpServerUiState.INCOMPATIBLE,
            McpServerUiState.AVAILABLE, McpServerUiState.FAILED,
        ).forEach { assertNull(it.name, McpServerUiPolicy.refreshedGuidance(McpServerUiState.STOPPED, it)) }
        // Once activated, the hint clears like any other stale guidance.
        assertEquals(McpServerUiState.STOPPED, McpServerUiPolicy.refreshedGuidance(McpServerUiState.ACTIVATION_REQUIRED, McpServerUiState.AVAILABLE))
        listOf(McpServerUiState.RUNNING, McpServerUiState.STARTING, McpServerUiState.CHECKING, McpServerUiState.STOPPING).forEach {
            assertNull(it.name, McpServerUiPolicy.refreshedGuidance(it, McpServerUiState.ACTIVATION_REQUIRED))
        }
    }

    @Test fun `a plugin change refreshes only stale guidance while the switch is off`() {
        assertEquals(McpServerUiState.STOPPED, McpServerUiPolicy.refreshedGuidance(McpServerUiState.NOT_INSTALLED, McpServerUiState.AVAILABLE))
        assertEquals(McpServerUiState.ACTIVATION_REQUIRED,
            McpServerUiPolicy.refreshedGuidance(McpServerUiState.NOT_INSTALLED, McpServerUiState.ACTIVATION_REQUIRED))
        assertEquals(McpServerUiState.STOPPED, McpServerUiPolicy.refreshedGuidance(McpServerUiState.PLUGIN_DISABLED, McpServerUiState.AVAILABLE))
        assertEquals(McpServerUiState.NOT_INSTALLED, McpServerUiPolicy.refreshedGuidance(McpServerUiState.INCOMPATIBLE, McpServerUiState.NOT_INSTALLED))
        assertNull(McpServerUiPolicy.refreshedGuidance(McpServerUiState.NOT_INSTALLED, McpServerUiState.NOT_INSTALLED))
        // Discovery failures never replace guidance; states without guidance only take idle guidance (above).
        assertNull(McpServerUiPolicy.refreshedGuidance(McpServerUiState.NOT_INSTALLED, McpServerUiState.FAILED))
        listOf(McpServerUiState.STOPPED, McpServerUiState.FAILED, McpServerUiState.RUNNING, McpServerUiState.CHECKING).forEach {
            assertFalse(McpServerUiPolicy.guidance(it))
            assertNull(McpServerUiPolicy.refreshedGuidance(it, McpServerUiState.NOT_INSTALLED))
        }
    }
}
