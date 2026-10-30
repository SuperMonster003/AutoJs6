package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android-free part of the `ui` window family (api-coverage P3.3): routes, window options, bar options and capabilities. */
class RuntimeUiWindowControlsTest {

    @Test fun windowRoutesAreUiMethods() {
        for (method in RuntimeUiWindowControls.METHODS) {
            assertTrue(method, NodeBridgeModules.supports("ui", method))
        }
        assertTrue(NodeBridgeModules.supports("toast", "dismissAll"))
        assertEquals(listOf("toast"), NodeBridgePermissionManifest.requiredCapabilitiesFor("toast", "dismissAll"))
    }

    @Test fun windowControlChecksOperationsAndValues() {
        assertEquals(true, RuntimeUiWindowControls.checkControl("interceptBack", true))
        assertEquals(false, RuntimeUiWindowControls.checkControl("keepScreenOn", false))
        // A missing boolean is Rhino's default true (statusBarIconLight()).
        assertEquals(true, RuntimeUiWindowControls.checkControl("statusBarIconLight", null))
        assertEquals(true, RuntimeUiWindowControls.checkControl("navigationBarIconLight", JSONObject.NULL))
        assertEquals("#ff0000", RuntimeUiWindowControls.checkControl("statusBarColor", "#ff0000"))
        assertEquals(-16777216, RuntimeUiWindowControls.checkControl("backgroundColor", -16777216))
        assertEquals("red", RuntimeUiWindowControls.checkControl("navigationBarIconLightBy", "red"))
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkControl("statusBarColour", "red") }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkControl("interceptBack", "yes") }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkControl("statusBarColor", true) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkControl("statusBarColor", null) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkControl("backgroundColor", Double.NaN) }
    }

    @Test fun iconLightByShareTheOptionOfIconLight() {
        assertEquals("statusBarIconLight", RuntimeUiWindowControls.optionKey("statusBarIconLightBy"))
        assertEquals("navigationBarIconLight", RuntimeUiWindowControls.optionKey("navigationBarIconLightBy"))
        assertEquals("statusBarColor", RuntimeUiWindowControls.optionKey("statusBarColor"))
    }

    @Test fun barHeightOptionsFollowRhinoDefaults() {
        val (kind, plain) = RuntimeUiWindowControls.barRequest(JSONArray().put("status"))
        assertEquals("status", kind)
        assertEquals(RuntimeUiWindowControls.BarOptions(withComputed = true, withDimen = true, ignoreVisibility = true), plain)
        // getVisible*Height forces ignoreVisibility to false whatever the options say.
        val visible = RuntimeUiWindowControls.barRequest(JSONArray().put("navigation").put(true).put(JSONObject().put("ignoreVisibility", true).put("withDimen", false))).second
        assertEquals(RuntimeUiWindowControls.BarOptions(withComputed = true, withDimen = false, ignoreVisibility = false), visible)
        val explicit = RuntimeUiWindowControls.barRequest(JSONArray().put("status").put(false).put(JSONObject().put("ignoreVisibility", false))).second
        assertEquals(false, explicit.ignoreVisibility)
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.barRequest(JSONArray().put("title")) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.barRequest(JSONArray().put("status").put(false).put(JSONObject().put("withInsets", true))) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.barRequest(JSONArray().put("status").put("yes")) }
    }

    @Test fun floatyControlChecksOperationArity() {
        assertEquals("setPosition", RuntimeUiWindowControls.checkFloatyControl(JSONArray().put("floaty-1").put("setPosition").put(10).put(20)))
        assertEquals("geometry", RuntimeUiWindowControls.checkFloatyControl(JSONArray().put("floaty-1").put("geometry")))
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkFloatyControl(JSONArray().put("floaty-1").put("setPosition").put(10)) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkFloatyControl(JSONArray().put("floaty-1").put("exitOnClose")) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeUiWindowControls.checkFloatyControl(JSONArray().put("floaty-1")) }
    }

    @Test fun floatingWindowsNeedOverlayCapabilities() {
        fun required(method: String, args: JSONArray = JSONArray()) = NodeBridgePermissionManifest.requiredCapabilitiesFor("ui", method, args.toString())
        assertEquals(listOf("ui", "ui.overlay"), required("floatyWindow", JSONArray().put("<frame/>")))
        assertEquals(listOf("ui", "ui.overlay", "floaty.raw"), required("floatyRawWindow", JSONArray().put("<frame/>")))
        assertEquals(listOf("ui", "ui.overlay", "floaty.touch"), required("floatyControl", JSONArray().put("floaty-1").put("setTouchable").put(false)))
        assertEquals(listOf("ui", "ui.overlay"), required("floatyControl", JSONArray().put("floaty-1").put("setPosition").put(1).put(2)))
        for (method in listOf("windowControl", "barHeight", "floatyCloseAll", "showLayout", "inflate")) {
            assertEquals(method, listOf("ui"), required(method))
        }
        // floaty.touch is not implied by ui or ui.overlay.
        assertEquals(listOf("floaty.touch"), NodeBridgePermissionManifest.missingCapabilities(listOf("ui", "ui.overlay"), required("floatyControl", JSONArray().put("floaty-1").put("setTouchable").put(true))))
    }
}
