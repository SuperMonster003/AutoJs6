package org.autojs.autojs.core.plugin.agent

import org.junit.Assert.*
import org.junit.Test

class ThreeStoveAgentLinkPolicyTest {
    @Test fun `an explicit detach invalidates pending callbacks and suppresses rebind`() {
        val policy = ThreeStoveAgentLinkPolicy()
        val first = policy.attach()
        assertTrue(policy.accepts(first))
        policy.detach()
        assertFalse(policy.accepts(first))
        assertNull(policy.died(first, true))
        val second = policy.attach()
        policy.failed(first)
        assertTrue(policy.accepts(second))
    }

    @Test fun `an attach rejection is terminal and cannot replay acceptance`() {
        val policy = ThreeStoveAgentLinkPolicy()
        val generation = policy.attach()
        policy.failed(generation)
        assertFalse(policy.wanted)
        assertNull(policy.died(generation, true))
    }

    @Test fun `repeated process deaths use a bounded schedule without resuming runs`() {
        val policy = ThreeStoveAgentLinkPolicy()
        policy.attach()
        listOf(1_000L, 3_000L, 9_000L).forEach { delay ->
            val previous = policy.generation
            assertEquals(delay, policy.died(previous, true))
            assertFalse(policy.accepts(previous))
        }
        assertNull(policy.died(policy.generation, true))
        assertFalse(policy.wanted)
    }

    @Test fun `force stopped disabled missing and uninspectable plugins cannot be rebound`() {
        val policy = ThreeStoveAgentLinkPolicy()
        val generation = policy.attach()
        assertNull(policy.died(generation, false))
        assertFalse(policy.wanted)
    }

    @Test fun `availability keeps activation enablement trust and compatibility distinct`() {
        val ready = ThreeStoveAgentPluginFacts(installed = true, pluginEnabled = true, authorized = true, trustedSigner = true)
        val cases = mapOf(
            ready to ThreeStoveAgentUiState.AVAILABLE,
            ready.copy(installed = false) to ThreeStoveAgentUiState.NOT_INSTALLED,
            ready.copy(applicationEnabled = false) to ThreeStoveAgentUiState.APPLICATION_DISABLED,
            ready.copy(stopped = true) to ThreeStoveAgentUiState.ACTIVATION_REQUIRED,
            ready.copy(pluginEnabled = false) to ThreeStoveAgentUiState.PLUGIN_DISABLED,
            ready.copy(authorized = false) to ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED,
            ready.copy(trustedSigner = false) to ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED,
            ready.copy(trustedSigner = false, signerConfirmed = true) to ThreeStoveAgentUiState.AVAILABLE,
            ready.copy(compatible = false) to ThreeStoveAgentUiState.INCOMPATIBLE,
        )
        cases.forEach { (facts, state) -> assertEquals(state, ThreeStoveAgentUiPolicy.availability(facts)) }
    }
}
