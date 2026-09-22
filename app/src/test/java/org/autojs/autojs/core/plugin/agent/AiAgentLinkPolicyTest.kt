package org.autojs.autojs.core.plugin.agent

import org.junit.Assert.*
import org.junit.Test

class AiAgentLinkPolicyTest {
    @Test fun `an explicit detach invalidates pending callbacks and suppresses rebind`() {
        val policy = AiAgentLinkPolicy()
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
        val policy = AiAgentLinkPolicy()
        val generation = policy.attach()
        policy.failed(generation)
        assertFalse(policy.wanted)
        assertNull(policy.died(generation, true))
    }

    @Test fun `repeated process deaths use a bounded schedule without resuming runs`() {
        val policy = AiAgentLinkPolicy()
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
        val policy = AiAgentLinkPolicy()
        val generation = policy.attach()
        assertNull(policy.died(generation, false))
        assertFalse(policy.wanted)
    }

    @Test fun `availability keeps activation enablement trust and compatibility distinct`() {
        val ready = AiAgentPluginFacts(installed = true, pluginEnabled = true, authorized = true, trustedSigner = true)
        val cases = mapOf(
            ready to AiAgentUiState.AVAILABLE,
            ready.copy(installed = false) to AiAgentUiState.NOT_INSTALLED,
            ready.copy(applicationEnabled = false) to AiAgentUiState.APPLICATION_DISABLED,
            ready.copy(stopped = true) to AiAgentUiState.ACTIVATION_REQUIRED,
            ready.copy(pluginEnabled = false) to AiAgentUiState.PLUGIN_DISABLED,
            ready.copy(authorized = false) to AiAgentUiState.AUTHORIZATION_REQUIRED,
            ready.copy(trustedSigner = false) to AiAgentUiState.TRUST_CONFIRMATION_REQUIRED,
            ready.copy(trustedSigner = false, signerConfirmed = true) to AiAgentUiState.AVAILABLE,
            ready.copy(compatible = false) to AiAgentUiState.INCOMPATIBLE,
        )
        cases.forEach { (facts, state) -> assertEquals(state, AiAgentUiPolicy.availability(facts)) }
    }
}
