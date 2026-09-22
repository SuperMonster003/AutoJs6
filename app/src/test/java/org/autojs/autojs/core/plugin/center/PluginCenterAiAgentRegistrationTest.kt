package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.junit.Assert.*
import org.junit.Test

class PluginCenterAiAgentRegistrationTest {
    @Test fun agentUsesItsDedicatedServiceWithoutAffectingModelProviders() {
        assertEquals(AiAgentActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine("ai-agent"))
        assertEquals(AiAgentActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine(" AI-AGENT "))
        assertNotEquals(AiAgentActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine("three-stone-ai"))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("agent"))
    }
}
