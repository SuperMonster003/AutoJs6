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
        assertTrue(NodeBridgeModules.supports("compose", "attach"))
        for (method in RuntimeUiInterop.methods) {
            assertTrue(NodeBridgeModules.supports("ui", method))
            assertEquals(listOf("ui"), NodeBridgePermissionManifest.requiredCapabilitiesFor("ui", method))
        }
    }

    @Test fun packagedBunCapabilitiesAreExplicitAndValidated() {
        val grants = org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
        assertEquals(emptyList<String>(), grants.validatePackagedCapabilities(emptyList()))
        assertEquals(listOf("ui.layout", "ui.compose"), grants.validatePackagedCapabilities(listOf("ui.layout", "ui.compose")))
        for (bad in listOf(listOf("ui.layout", "ui.layout"), listOf("all"))) {
            assertThrows(IllegalArgumentException::class.java) { grants.validatePackagedCapabilities(bad) }
        }
    }
}
