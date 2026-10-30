package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The console window family of the JS runtimes (api-coverage P3.3): new controls, `configure` option mapping and capabilities. */
class RuntimeConsoleBridgeConfigureTest {

    private fun calls(options: JSONObject): Map<String, String> =
        RuntimeConsoleBridge.configureCalls(options).associate { (setter, args) -> setter to args.toString() }

    @Test fun newControlsAreConsoleMethodsNeedingConsole() {
        val added = listOf(
            "isShowing", "resetBackgroundAlpha", "resetContentBackgroundAlpha", "resetTitleBackgroundAlpha", "setAvoidStatusBar",
            "setColorful", "setInputVisible", "setTimeVisible", "setTouchThrough", "setGravity", "setTimeFormat", "configure", "rawInput",
        )
        for (method in added) {
            assertTrue(method, NodeBridgeModules.supports("console", method))
            assertEquals(method, listOf("console"), NodeBridgePermissionManifest.requiredCapabilitiesFor("console", method))
        }
        // rawInput is asynchronous, so the synchronous control table (shared with Bun's console.control) does not hold it.
        assertFalse(RuntimeConsoleBridge.METHODS.contains("rawInput"))
        assertFalse(RuntimeConsoleBridge.CONFIGURE_SETTERS.contains("setGlobalLogConfig"))
        assertTrue(RuntimeConsoleBridge.METHODS.containsAll(RuntimeConsoleBridge.CONFIGURE_SETTERS))
    }

    @Test fun configureKeysNameTheirSetters() {
        assertEquals(mapOf("setTitle" to "[\"Build\"]"), calls(JSONObject().put("title", "Build")))
        assertEquals(mapOf("setTitle" to "[\"Build\"]"), calls(JSONObject().put("setTitle", "Build")))
        assertEquals(mapOf("setTouchThrough" to "[true]"), calls(JSONObject().put("touchThrough", true)))
        assertEquals(mapOf("setExitOnClose" to "[3000]"), calls(JSONObject().put("exitOnClose", 3000)))
        assertEquals(mapOf("setGravity" to "[17]"), calls(JSONObject().put("gravity", 17)))
        assertEquals(mapOf("setTitle" to "[null]"), calls(JSONObject().put("title", JSONObject.NULL)))
        assertEquals(8, RuntimeConsoleBridge.configureCalls(JSONObject().put("title", "t").put("textSize", 12).put("colorful", false)
            .put("timeVisible", true).put("timeFormat", "HH:mm").put("avoidStatusBar", false).put("inputVisible", true).put("backgroundAlpha", 0.5)).size)
    }

    @Test fun configureSpreadsArraysAndAcceptsSizeObjects() {
        assertEquals(mapOf("setSize" to "[300,400]"), calls(JSONObject().put("size", JSONArray().put(300).put(400))))
        assertEquals(mapOf("setSize" to "[300,400]"), calls(JSONObject().put("size", JSONObject().put("width", 300).put("height", 400))))
        assertEquals(mapOf("setPosition" to "[10,20]"), calls(JSONObject().put("position", JSONObject().put("x", 10).put("y", 20))))
        assertEquals(mapOf("setContentTextColors" to "[\"red\",\"green\"]"), calls(JSONObject().put("contentTextColors", JSONArray().put("red").put("green"))))
    }

    @Test fun configureRefusesUnknownKeysBeforeAnySetter() {
        val error = assertThrows(IllegalArgumentException::class.java) { RuntimeConsoleBridge.configureCalls(JSONObject().put("title", "t").put("opacity", 1)) }
        assertTrue(error.message!!.contains("'opacity'"))
        assertThrows(IllegalArgumentException::class.java) { RuntimeConsoleBridge.configureCalls(JSONObject().put("globalLogConfig", JSONObject())) }
    }
}
