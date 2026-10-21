package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RuntimeFlowPolicyTest {
    private fun caps(name: String, args: String = "[]", options: String = "{}") = NodeBridgePermissionManifest.requiredCapabilitiesFor("flow", "start",
        JSONArray().put("client").put("operation").put(name).put(JSONArray(args)).put(JSONObject(options)).put(10000).toString()).toSet()

    @Test fun extraPermissionsAreDerivedBeforeAnyClickOrCallback() {
        assertEquals(setOf("accessibility", "accessibility.gesture"), caps("smartClick"))
        assertEquals(setOf("accessibility"), caps("smartClick", options = """{"gestureFallback":false}"""))
        assertTrue("accessibility.gesture" in caps("toggle"))
        assertFalse("accessibility.gesture" in caps("toggle", options = """{"click":{"gestureFallback":false}}"""))
        assertTrue("accessibility.gesture" in caps("scrollUntil", options = """{"method":"GESTURE"}"""))
        assertTrue("accessibility.gesture" in caps("smartClickBounds"))
    }
    @Test fun clipboardEventAndAppOperationsRetainTheirIndependentCapabilities() {
        assertEquals(setOf("accessibility", "clipboard"), caps("nodeAction", """[{},"performAction",[16384]]"""))
        assertEquals(setOf("accessibility"), caps("nodeAction", """[{},"performAction",[16]]"""))
        assertEquals(setOf("accessibility", "events", "events.toast"), caps("waitForToast"))
        assertEquals(setOf("accessibility", "events", "events.notification"), caps("waitForNotification"))
        assertEquals(setOf("accessibility", "app.query", "app.launch"), caps("launchAndWait"))
        assertEquals(setOf("accessibility", "app.query"), caps("backToApp"))
    }
    @Test fun flowGrantDoesNotImplicitlyGrantAccessibilityOrEvents() {
        assertEquals(listOf("flow.control"), Grants.validatePackagedCapabilities(listOf("flow.control")))
        assertTrue(BunAutomationBridge.nodeCapabilities(listOf(Grants.FLOW_CONTROL)).isEmpty())
        assertFalse("events.toast" in BunAutomationBridge.nodeCapabilities(listOf(Grants.FLOW_CONTROL, Grants.ACCESSIBILITY_CONTROL)))
        assertEquals(RuntimeFlowBridge.methods, BunAutomationBridge.methods[Grants.FLOW_CONTROL])
        assertTrue(NodeBridgeModules.supports("flow", "answer"))
    }
    @Test fun optionTyposAndInvalidHumanizationCannotSilentlyChangeAnAction() {
        assertThrows(IllegalArgumentException::class.java) { RuntimeFlowOptions(JSONObject("""{"gestureFallbak":false}"""), "smartClick") }
        assertThrows(IllegalArgumentException::class.java) { RuntimeFlowOptions(JSONObject("""{"watch":true}"""), "findAny") }
        assertThrows(IllegalArgumentException::class.java) { RuntimeFlowOptions(JSONObject("""{"humanize":{"offset":-1}}"""), "smartClick").humanize() }
        assertThrows(IllegalArgumentException::class.java) { RuntimeFlowOptions(JSONObject("""{"humanize":{"delay":[2,1]}}"""), "smartClick").humanize() }
    }
    @Test fun wireTruthinessAndUnboundedDurationsDoNotCollapseToJsonNull() {
        assertTrue(RuntimeFlowJson.truthy(0, wait = true))
        assertFalse(RuntimeFlowJson.truthy(0))
        assertTrue(RuntimeFlowJson.truthy("", wait = true))
        assertFalse(RuntimeFlowJson.truthy(JSONObject("""{"__flowNumber":"NaN"}"""), wait = true))
        assertEquals(Long.MAX_VALUE, RuntimeFlowJson.millis(JSONObject("""{"__flowNumber":"Infinity"}"""), 5, "timeout"))
        assertThrows(IllegalArgumentException::class.java) { RuntimeFlowJson.millis(-1, 5, "timeout") }
    }
}
