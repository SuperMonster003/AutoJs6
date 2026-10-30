package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.junit.Assert.*
import org.junit.Test

class RuntimePluginFamilyGrantTest {
    @Test fun everyModuleHasItsOwnIndependentGrant() {
        val routes = listOf(
            Triple(Grants.THEME_CONTROL, NodeBridgePermissionManifest.THEME, RuntimeThemeBridge.METHODS),
            Triple(Grants.GALLERY_CONTROL, NodeBridgePermissionManifest.GALLERY, RuntimeOperationClients.METHODS),
            Triple(Grants.BATCHRENAME_CONTROL, NodeBridgePermissionManifest.BATCH_RENAME, RuntimeOperationClients.METHODS),
        )
        for ((grant, module, methods) in routes) {
            assertEquals(listOf(module), BunAutomationBridge.nodeCapabilities(listOf(grant)))
            assertEquals(methods, BunAutomationBridge.methods[grant])
            assertEquals(listOf(grant), Grants.validatePackagedCapabilities(listOf(grant)))
            for (method in methods) {
                assertTrue(NodeBridgeModules.supports(module, method))
                assertEquals(listOf(module), NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method))
            }
        }
        assertEquals(setOf("theme", "gallery", "batch_rename"), routes.map { it.second }.toSet())
        assertEquals(listOf(Grants.THEME_CONTROL, Grants.GALLERY_CONTROL, Grants.BATCHRENAME_CONTROL), Grants.PLUGIN_FAMILY)
    }

    @Test fun themeBridgeNamesRhinoMembers() {
        assertEquals(setOf("get", "set", "reset", "libraries", "activate", "deactivate", "next", "state", "current"), RuntimeThemeBridge.METHODS)
    }

    @Test fun theFamilyNeedsNoAndroidPermission() {
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("theme", "gallery", "batch_rename"))
        assertTrue(manifest.androidPermissions.toString(), manifest.androidPermissions.isEmpty())
    }
}
