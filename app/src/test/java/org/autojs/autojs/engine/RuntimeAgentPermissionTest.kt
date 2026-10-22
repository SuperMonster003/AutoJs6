package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.junit.Assert.*
import org.junit.Test

class RuntimeAgentPermissionTest {
    @Test fun textGenerationPermissionDoesNotAuthorizeAgentTaskExecution() {
        val required = NodeBridgePermissionManifest.requiredCapabilitiesFor("aiAgent", "start")
        assertEquals(listOf("ai.agent"), required)
        assertEquals(required, NodeBridgePermissionManifest.missingCapabilities(listOf("ai"), required))
        assertTrue(NodeBridgePermissionManifest.missingCapabilities(listOf("ai.agent"), required).isEmpty())
        val generated = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("ai.agent"))
        assertTrue("org.autojs.permission.AGENT_RUNTIME" in generated.androidPermissions)
        assertFalse("org.autojs.permission.AI_RUNTIME" in generated.androidPermissions)
    }

    @Test fun bunAgentGrantSelectsTaskControlWithoutImplicitInferenceOrDeviceGrants() {
        val selected = BunHostCapabilityGrants.validatePackagedCapabilities(listOf("agent.control"))
        val capabilities = BunAutomationBridge.nodeCapabilities(selected)
        assertTrue(NodeBridgePermissionManifest.AI_AGENT in capabilities)
        assertFalse(NodeBridgePermissionManifest.AI in capabilities)
        assertFalse(NodeBridgePermissionManifest.ACCESSIBILITY in capabilities)
        assertFalse(NodeBridgePermissionManifest.SHELL in capabilities)
        assertFalse(NodeBridgePermissionManifest.AI_AGENT in BunAutomationBridge.nodeCapabilities(listOf("ai.control")))
    }
}
