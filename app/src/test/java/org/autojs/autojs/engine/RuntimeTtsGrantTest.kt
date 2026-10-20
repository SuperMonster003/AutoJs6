package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.junit.Assert.*
import org.junit.Test

class RuntimeTtsGrantTest {
    @Test fun ttsHasAnIndependentGrantAndEveryRouteRequiresIt() {
        assertEquals(listOf("tts"), BunAutomationBridge.nodeCapabilities(listOf(Grants.TTS_CONTROL)))
        assertEquals(RuntimeTtsBridge.methods, BunAutomationBridge.methods[Grants.TTS_CONTROL])
        assertEquals(listOf("tts.control"), Grants.validatePackagedCapabilities(listOf("tts.control")))
        for (method in RuntimeTtsBridge.methods) assertEquals(listOf("tts"), NodeBridgePermissionManifest.requiredCapabilitiesFor("tts", method))
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("tts"))
        assertFalse(manifest.androidPermissions.contains("android.permission.RECORD_AUDIO"))
        assertFalse(manifest.androidPermissions.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
    }
}
