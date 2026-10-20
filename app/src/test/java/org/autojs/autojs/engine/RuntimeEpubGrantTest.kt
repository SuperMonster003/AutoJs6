package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.junit.Assert.*
import org.junit.Test

class RuntimeEpubGrantTest {
    @Test fun epubRoutesUseAnIndependentGrantInScriptsAndPackagedApps() {
        assertEquals(listOf("epub"), BunAutomationBridge.nodeCapabilities(listOf(Grants.EPUB_CONTROL)))
        assertEquals(RuntimeEpubBridge.methods, BunAutomationBridge.methods[Grants.EPUB_CONTROL])
        assertEquals(listOf("epub.control"), Grants.validatePackagedCapabilities(listOf("epub.control")))
        for (method in RuntimeEpubBridge.methods) {
            assertTrue(NodeBridgeModules.supports("epub", method))
            assertEquals(listOf("epub"), NodeBridgePermissionManifest.requiredCapabilitiesFor("epub", method))
        }
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("epub"))
        // The EPUB provider consumes caller-owned descriptors; no Android microphone permission is needed.
        assertFalse(manifest.androidPermissions.contains("android.permission.RECORD_AUDIO"))
        assertFalse(manifest.androidPermissions.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
    }
}
