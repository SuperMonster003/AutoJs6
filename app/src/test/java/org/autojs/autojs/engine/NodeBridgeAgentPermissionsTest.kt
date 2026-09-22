package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test

class NodeBridgeAgentPermissionsTest {
    @Test fun scriptCatalogCannotImplyExecutionPermission() {
        val required = NodeBridgePermissionManifest.requiredCapabilitiesFor("agent", "execRegistered")
        assertEquals(listOf("agent", "agent.exec", "engines", "engines.exec"), required)
        assertEquals(listOf("agent.exec"), NodeBridgePermissionManifest.missingCapabilities(listOf("agent", "engines"), required))
        assertEquals(listOf("agent"), NodeBridgePermissionManifest.requiredCapabilitiesFor("agent", "listScripts"))
        assertEquals(listOf("agent"), NodeBridgePermissionManifest.requiredCapabilitiesFor("agent", "readManifest"))
    }

    @Test fun screenTextNeedsCaptureAndOcrInAdditionToAccessibility() {
        val required = NodeBridgePermissionManifest.requiredCapabilitiesFor("accessibility", "readScreenText")
        assertEquals(listOf("accessibility", "screen_capture", "ocr"), required)
        assertEquals(listOf("screen_capture", "ocr"), NodeBridgePermissionManifest.missingCapabilities(listOf("accessibility"), required))
        assertTrue(NodeBridgeModules.supports("accessibility", "readScreenText"))
        assertFalse(NodeBridgeModules.supports("agent", "execScript"))
    }
}
