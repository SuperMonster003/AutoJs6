package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.junit.Assert.*
import org.junit.Test

class BunAutomationPolicyTest {
    @Test fun appGrantCoversTheActualProviderRequirementsWithoutOtherCapabilities() {
        val granted=BunAutomationBridge.nodeCapabilities(listOf(Grants.APP_CONTROL)).toSet()
        assertEquals(setOf("app","app.launch","app.settings","app.activity","app.query"),granted)
        for(method in BunAutomationBridge.methods.getValue(Grants.APP_CONTROL))
            assertTrue(method,granted.containsAll(NodeBridgePermissionManifest.requiredCapabilitiesFor("app",method)))
    }
    @Test fun gesturesNeedBothGrantsAndClipboardDoesNotGrantOtherAutomation() {
        assertEquals(emptyList<String>(),BunAutomationBridge.nodeCapabilities(listOf(Grants.ACCESSIBILITY_GESTURE)))
        assertEquals(listOf("accessibility"),BunAutomationBridge.nodeCapabilities(listOf(Grants.ACCESSIBILITY_CONTROL)))
        assertEquals(listOf("accessibility","accessibility.gesture"),BunAutomationBridge.nodeCapabilities(listOf(Grants.ACCESSIBILITY_CONTROL,Grants.ACCESSIBILITY_GESTURE)))
        assertEquals(listOf("clipboard"),BunAutomationBridge.nodeCapabilities(listOf(Grants.CLIPBOARD_CONTROL)))
    }
    @Test fun publicAutomationDoesNotExposeInternalAgentRoutes() {
        assertFalse(BunAutomationBridge.methods.values.flatten().any{it in setOf("dump","inspectNode","readScreenText","readDoc","pluginCapabilities","currentWindow")})
        assertEquals(45,BunAutomationBridge.methods.values.sumOf{it.size})
    }
}
