package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.junit.Assert.*
import org.junit.Test

class RuntimeSpeechGrantTest {
    @Test fun speechRoutesUseAnIndependentGrantInScriptsAndPackagedApps() {
        assertEquals(listOf("speech"), BunAutomationBridge.nodeCapabilities(listOf(Grants.SPEECH_CONTROL)))
        assertEquals(RuntimeSpeechBridge.methods, BunAutomationBridge.methods[Grants.SPEECH_CONTROL])
        assertEquals(listOf("speech.control"), Grants.validatePackagedCapabilities(listOf("speech.control")))
        for (method in RuntimeSpeechBridge.methods) {
            assertTrue(NodeBridgeModules.supports("speech", method))
            assertEquals(listOf("speech"), NodeBridgePermissionManifest.requiredCapabilitiesFor("speech", method))
        }
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("speech"))
        // The actual speech provider owns the microphone permission; the host only transports audio.
        assertFalse(manifest.androidPermissions.contains("android.permission.RECORD_AUDIO"))
        assertFalse(manifest.androidPermissions.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
    }
}
