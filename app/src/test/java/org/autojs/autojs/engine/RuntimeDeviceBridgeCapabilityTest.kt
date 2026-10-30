package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capabilities of Rhino's device members in the JS runtimes (api-coverage P3.1c, Q146 (a)). */
class RuntimeDeviceBridgeCapabilityTest {

    private val identifiers = listOf("getIMEI", "getSerial", "getAndroidId", "getMacAddress", "getSharedDeviceId")

    private val power = listOf("setPointerLocation", "togglePointerLocation", "keepAwake", "keepScreenOn")

    @Test fun identifiersNeedTheirOwnCapability() {
        for (method in identifiers) {
            val required = NodeBridgePermissionManifest.requiredCapabilitiesFor("device", method)
            assertEquals(method, listOf("device", "device.identifiers"), required)
            assertEquals(method, listOf("device.identifiers"), NodeBridgePermissionManifest.missingCapabilities(listOf("device", "device.power"), required))
        }
    }

    @Test fun switchesAndWakeLocksNeedDevicePower() {
        for (method in power) {
            assertEquals(method, listOf("device", "device.power"), NodeBridgePermissionManifest.requiredCapabilitiesFor("device", method))
        }
    }

    @Test fun otherRhinoMembersNeedDeviceOnly() {
        for (method in RuntimeDeviceBridge.methods - identifiers.toSet() - power.toSet()) {
            assertEquals(method, listOf("device"), NodeBridgePermissionManifest.requiredCapabilitiesFor("device", method))
        }
        for (method in RuntimeDeviceBridge.methods + listOf("keepAwake", "cancelVibration")) {
            assertTrue(method, NodeBridgeModules.supports("device", method))
        }
    }
}
