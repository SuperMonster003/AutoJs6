package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capabilities of Rhino's automation family in the JS runtimes (api-coverage P3.4, Q178 (a)). */
class RuntimeAutomationBridgeCapabilityTest {

    private fun required(module: String, method: String, args: String = "[]") = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method, args)

    @Test fun serviceManagementNeedsItsOwnCapability() {
        for (method in listOf("start", "stop", "launchSettings", "clearCache")) {
            val needed = required("auto", method)
            assertEquals(method, listOf("accessibility", "accessibility.service"), needed)
            assertEquals(method, listOf("accessibility.service"), NodeBridgePermissionManifest.missingCapabilities(listOf("accessibility"), needed))
        }
        for (method in listOf("state", "service", "services", "windows", "windowRoot", "roots", "activeRoot")) {
            assertEquals(method, listOf("accessibility"), required("auto", method))
        }
    }

    @Test fun pressesAndGesturesNeedTheGestureCapability() {
        for (method in listOf("press", "gestures")) {
            assertEquals(method, listOf("accessibility", "accessibility.gesture"), required("automator", method))
        }
        for (method in listOf("tap", "targetAction", "editText", "scroll", "switchToInputMethod", "switchToInputMethodWithId", "isServiceRunning", "ensureService")) {
            assertEquals(method, listOf("accessibility"), required("automator", method))
        }
    }

    @Test fun globalActionsFollowTheKeysModule() {
        for (name in listOf("back", "home", "recents")) {
            assertEquals(name, listOf("accessibility"), required("automator", "globalAction", "[\"$name\"]"))
        }
        for (name in listOf("notifications", "lockScreen", "headsetHook", "accessibilityButton", "dismissNotificationShade")) {
            assertEquals(name, listOf("keys"), required("automator", "globalAction", "[\"$name\"]"))
        }
    }

    @Test fun touchesAndKeyInterceptionAreNeverImplied() {
        val touch = required("events", "observeTouch")
        assertEquals(listOf("events", "events.touch"), touch)
        assertEquals(listOf("events.touch"), NodeBridgePermissionManifest.missingCapabilities(listOf("events"), touch))
        val intercept = required("events", "interceptKeys")
        assertEquals(listOf("events", "events.key", "events.key.intercept"), intercept)
        assertEquals(listOf("events.key.intercept"), NodeBridgePermissionManifest.missingCapabilities(listOf("events", "events.key"), intercept))
        assertEquals(listOf("accessibility"), required("events", "observeAccessibility"))
        assertEquals(listOf("events"), required("events", "sendScriptBroadcast"))
    }

    @Test fun yoloNeedsItsCapability() {
        assertEquals(listOf("yolo", "image"), required("yolo", "load"))
        assertEquals(listOf("yolo", "image"), required("yolo", "detect"))
        assertEquals(listOf("yolo"), required("yolo", "close"))
    }

    @Test fun everyRouteIsSupported() {
        for ((module, methods) in RuntimeAutomationBridge.methodsByModule) {
            for (method in methods) assertTrue("$module.$method", NodeBridgeModules.supports(module, method))
        }
        for (method in NodeBridgeModules.automationEventsMethods) assertTrue(method, NodeBridgeModules.supports("events", method))
        for (capability in listOf("accessibility.service", "events.key.intercept", "events.touch", "yolo")) {
            assertTrue(capability, capability in NodeBridgePermissionManifest.definedCapabilities)
        }
    }
}
