package org.autojs.autojs.core.plugin.agent

import org.junit.Assert.*
import org.junit.Test

class AiAgentDrawerStateTest {
    @Test fun onlyAnAttachedLinkChecksTheSwitchAndOnlyAttachingShowsProgress() {
        for (state in AiAgentUiState.entries) {
            assertEquals(state.name, state == AiAgentUiState.ATTACHED, AiAgentUiPolicy.checked(state))
            assertEquals(state.name, state == AiAgentUiState.ATTACHING, AiAgentUiPolicy.progress(state))
        }
    }

    @Test fun transportStatesNeverTurnFailuresOrUnknownValuesIntoConnectedUi() {
        assertEquals(AiAgentUiState.ATTACHED, AiAgentUiPolicy.linkState("attached"))
        assertEquals(AiAgentUiState.ATTACHING, AiAgentUiPolicy.linkState("attaching"))
        assertEquals(AiAgentUiState.HOST_UNAVAILABLE, AiAgentUiPolicy.linkState("host_unavailable"))
        assertEquals(AiAgentUiState.FAILED, AiAgentUiPolicy.linkState("failed"))
        assertEquals(AiAgentUiState.DETACHED, AiAgentUiPolicy.linkState("unknown"))
    }

    @Test fun discoveryGuidanceDoesNotPromptForNormalConnectionStates() {
        for (state in listOf(AiAgentUiState.NOT_INSTALLED, AiAgentUiState.APPLICATION_DISABLED,
            AiAgentUiState.ACTIVATION_REQUIRED, AiAgentUiState.PLUGIN_DISABLED, AiAgentUiState.AUTHORIZATION_REQUIRED,
            AiAgentUiState.TRUST_CONFIRMATION_REQUIRED, AiAgentUiState.INCOMPATIBLE, AiAgentUiState.FAILED)) {
            assertTrue(state.name, AiAgentUiPolicy.needsGuide(state))
        }
        for (state in listOf(AiAgentUiState.ATTACHED, AiAgentUiState.ATTACHING, AiAgentUiState.AVAILABLE, AiAgentUiState.DETACHED)) {
            assertFalse(state.name, AiAgentUiPolicy.needsGuide(state))
        }
    }
}
