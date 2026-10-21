package org.autojs.autojs.engine

import org.autojs.autojs.core.automator.FakeUiNode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RuntimeFlowQueryTest {
    @Test fun contentMatchesTextDescriptionOrIdAndOtherFieldsStillConstrainIt() {
        val node = FakeUiNode.root { text = "ready"; desc = "description"; id = "pkg:id/anchor"; clickable = false }
        for (value in listOf("ready", "description", "pkg:id/anchor")) {
            assertTrue(RuntimeFlowQuery.filter(JSONObject().put("content", value)).filter(node))
            assertFalse(RuntimeFlowQuery.filter(JSONObject().put("content", value).put("clickable", true)).filter(node))
        }
        assertFalse(RuntimeFlowQuery.filter(JSONObject().put("text", "description")).filter(node))
    }
    @Test fun visibilityAndRegexAreRealConditions() {
        val node = FakeUiNode.root { text = "Ready"; visibleToUser = false }
        assertTrue(RuntimeFlowQuery.filter(JSONObject("""{"contentMatches":{"pattern":"ready","flags":"i"},"visibleToUser":false}""")).filter(node))
        assertFalse(RuntimeFlowQuery.filter(JSONObject("""{"contentMatches":"ead"}""")).filter(node))
        assertFalse(RuntimeFlowQuery.filter(JSONObject("""{"visibleToUser":true}""")).filter(node))
    }
    @Test fun malformedDescriptorsNeverSilentlyBroadenTheQuery() {
        for (json in listOf("{}", """{"typo":"anything"}""", """{"text":4}""", """{"text":" "}""", """{"clickable":"true"}""",
            """{"textMatches":"["}""", """{"textMatches":{"pattern":"x","flags":"g"}}""", """{"depth":1.5}""",
            """{"boundsInside":{"left":0,"top":0,"right":4}}""")) {
            assertThrows("Must reject $json", IllegalArgumentException::class.java) { RuntimeFlowQuery.filter(JSONObject(json)) }
        }
    }
    @Test fun flowQueriesUseTheExistingAccessibilityCapability() {
        for (method in listOf("flowProbe", "flowForeground")) {
            assertTrue(NodeBridgeModules.supports("accessibility", method))
            assertEquals(listOf("accessibility"), NodeBridgePermissionManifest.requiredCapabilitiesFor("accessibility", method))
            assertTrue(method in BunAutomationBridge.methods.getValue("accessibility.control"))
        }
    }
}
