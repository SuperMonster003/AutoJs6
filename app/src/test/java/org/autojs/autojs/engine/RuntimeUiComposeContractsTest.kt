package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test

class RuntimeUiComposeContractsTest {
    @Test fun discoveryAndPermissionsCoverEveryPortableComposeOperation() {
        val provider = NodeBridgeProviderRegistry.definitions.single { NodeBridgeModules.COMPOSE in it.modules }
        assertEquals("ui", provider.id)
        assertTrue(NodeBridgeModules.UI in provider.modules)
        for (method in RuntimeComposeBridge.methods) {
            assertTrue(NodeBridgeModules.supports("compose", method))
            assertEquals(listOf("ui"), NodeBridgePermissionManifest.requiredCapabilitiesFor("compose", method))
        }
        assertFalse(NodeBridgeModules.supports("compose", "AndroidView"))
        assertFalse(NodeBridgeModules.supports("compose", "attach"))
    }
}
