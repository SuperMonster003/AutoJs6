package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.junit.Assert.*
import org.junit.Test

class BunAutomationPolicyTest {
    @Test fun javaGrantMatchesPublicRoutesAndRemainsIndependent() {
        assertEquals(listOf("java_interop"),BunAutomationBridge.nodeCapabilities(listOf(Grants.JAVA_INTEROP)))
        assertEquals(ControlledJavaInteropPolicy.methods,BunAutomationBridge.methods[Grants.JAVA_INTEROP])
        assertEquals(listOf("java.interop"),Grants.validatePackagedCapabilities(listOf("java.interop")))
    }
    @Test fun appGrantCoversTheActualProviderRequirementsWithoutOtherCapabilities() {
        val granted=BunAutomationBridge.nodeCapabilities(listOf(Grants.APP_CONTROL)).toSet()
        assertEquals(setOf("app","app.launch","app.settings","app.activity","app.query"),granted)
        for(method in BunAutomationBridge.methods.getValue(Grants.APP_CONTROL))
            assertTrue(method,granted.containsAll(NodeBridgePermissionManifest.requiredCapabilitiesFor("app",method)))
    }
    @Test fun gesturesNeedBothGrantsAndClipboardDoesNotGrantOtherAutomation() {
        assertEquals(emptyList<String>(),BunAutomationBridge.nodeCapabilities(listOf(Grants.ACCESSIBILITY_GESTURE)))
        assertEquals(listOf("accessibility"),BunAutomationBridge.nodeCapabilities(listOf(Grants.ACCESSIBILITY_CONTROL)))
        assertEquals(listOf("accessibility","accessibility.gesture"),BunAutomationBridge.nodeCapabilities(listOf(Grants.ACCESSIBILITY_CONTROL,Grants.ACCESSIBILITY_GESTURE)))
        assertEquals(listOf("clipboard"),BunAutomationBridge.nodeCapabilities(listOf(Grants.CLIPBOARD_CONTROL)))
    }
    @Test fun publicAutomationDoesNotExposeInternalAgentRoutes() {
        assertFalse(BunAutomationBridge.methods.values.flatten().any{it in setOf("dump","inspectNode","readScreenText","readDoc","pluginCapabilities","currentWindow")})
        assertEquals(169,BunAutomationBridge.methods.values.sumOf{it.size})
    }
    @Test fun devicePowerAudioAndDialogGrantsRemainIndependent() {
        assertEquals(listOf("device"),BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_CONTROL)))
        assertEquals(listOf("device.power"),BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_POWER)))
        assertEquals(listOf("media","media.audio"),BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_AUDIO)))
        assertEquals(listOf("dialogs"),BunAutomationBridge.nodeCapabilities(listOf(Grants.DIALOGS_CONTROL)))
        assertEquals(setOf("getAudioStreamVolume","getAudioStreamMaxVolume","setAudioStreamVolume"),BunAutomationBridge.methods[Grants.DEVICE_AUDIO])
        val permissions=NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("device","device.power","media.audio")).androidPermissions
        assertTrue(permissions.containsAll(listOf("android.permission.WAKE_LOCK","android.permission.VIBRATE","android.permission.WRITE_SETTINGS","android.permission.MODIFY_AUDIO_SETTINGS")))
    }
    @Test fun sensorsGrantMapsOnlyToSensorsAndCoversEveryNativeMethod() {
        assertEquals(listOf("sensors"),BunAutomationBridge.nodeCapabilities(listOf(Grants.SENSORS_CONTROL)))
        for (method in BunAutomationBridge.methods.getValue(Grants.SENSORS_CONTROL))
            assertEquals(listOf("sensors"),NodeBridgePermissionManifest.requiredCapabilitiesFor("sensors",method))
    }

    @Test fun mailGrantMapsOnlyToMailAndCoversEveryTransportMethod() {
        assertEquals(listOf("mail"), BunAutomationBridge.nodeCapabilities(listOf(Grants.MAIL_CONTROL)))
        assertEquals(RuntimeMailBridge.methods, BunAutomationBridge.methods[Grants.MAIL_CONTROL])
        assertEquals(listOf("mail.control"), Grants.validatePackagedCapabilities(listOf("mail.control")))
        for (method in RuntimeMailBridge.methods)
            assertEquals(listOf("mail"), NodeBridgePermissionManifest.requiredCapabilitiesFor("mail", method))
    }

}
