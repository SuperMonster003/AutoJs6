package org.autojs.autojs.core.plugin.agent

import org.junit.Assert.*
import org.junit.Test

class ThreeStoveAgentUiStateTest {
    @Test fun disabledPluginIsNotActivatedByAnAutomaticConnection() {
        assertEquals(ThreeStoveAgentUiState.PLUGIN_DISABLED, ThreeStoveAgentUiPolicy.availability(
            ThreeStoveAgentPluginFacts(installed = true, stopped = true, pluginEnabled = false, authorized = true, trustedSigner = true),
        ))
        assertEquals(ThreeStoveAgentUiState.ACTIVATION_REQUIRED, ThreeStoveAgentUiPolicy.availability(
            ThreeStoveAgentPluginFacts(installed = true, stopped = true, pluginEnabled = true, authorized = true, trustedSigner = true),
        ))
    }
    @Test fun transportStatesNeverTurnFailuresOrUnknownValuesIntoConnectedUi() {
        assertEquals(ThreeStoveAgentUiState.ATTACHED, ThreeStoveAgentUiPolicy.linkState("attached"))
        assertEquals(ThreeStoveAgentUiState.ATTACHING, ThreeStoveAgentUiPolicy.linkState("attaching"))
        assertEquals(ThreeStoveAgentUiState.HOST_UNAVAILABLE, ThreeStoveAgentUiPolicy.linkState("host_unavailable"))
        assertEquals(ThreeStoveAgentUiState.FAILED, ThreeStoveAgentUiPolicy.linkState("failed"))
        assertEquals(ThreeStoveAgentUiState.DETACHED, ThreeStoveAgentUiPolicy.linkState("unknown"))
    }

    @Test fun discoveryGuidanceDoesNotPromptForNormalConnectionStates() {
        for (state in listOf(ThreeStoveAgentUiState.NOT_INSTALLED, ThreeStoveAgentUiState.APPLICATION_DISABLED,
            ThreeStoveAgentUiState.ACTIVATION_REQUIRED, ThreeStoveAgentUiState.PLUGIN_DISABLED, ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED,
            ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED, ThreeStoveAgentUiState.INCOMPATIBLE, ThreeStoveAgentUiState.FAILED)) {
            assertTrue(state.name, ThreeStoveAgentUiPolicy.needsGuide(state))
        }
        for (state in listOf(ThreeStoveAgentUiState.ATTACHED, ThreeStoveAgentUiState.ATTACHING, ThreeStoveAgentUiState.AVAILABLE, ThreeStoveAgentUiState.DETACHED)) {
            assertFalse(state.name, ThreeStoveAgentUiPolicy.needsGuide(state))
        }
    }
}
