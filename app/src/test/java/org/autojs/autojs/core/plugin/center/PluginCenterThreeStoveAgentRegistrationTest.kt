package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.junit.Assert.*
import org.junit.Test

class PluginCenterThreeStoveAgentRegistrationTest {
    @Test fun agentUsesItsDedicatedServiceWithoutAffectingModelProviders() {
        assertEquals(ThreeStoveAgentActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine("three-stove-agent"))
        assertEquals(ThreeStoveAgentActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine(" THREE-STOVE-AGENT "))
        assertNotEquals(ThreeStoveAgentActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine("three-stone-ai"))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("agent"))
    }
}
