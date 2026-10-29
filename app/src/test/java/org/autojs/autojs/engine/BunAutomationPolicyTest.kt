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
        // 194, then app.control +6 (P4.1a), mediainfo.control 4 (P4.2b), the system and engines family 51 and plugin tools 3 (P4.1b).
        assertEquals(258,BunAutomationBridge.methods.values.sumOf{it.size})
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

    // api-coverage P4.1b (Q136 (a)): a query grant and a write grant per namespace, after the Node capabilities of Q133 (b).
    @Test fun systemGrantsCoverTheirRoutesAndWritesNeedTheirOwnGrant() {
        val pairs = mapOf(Grants.AUTOJS_CONTROL to Grants.AUTOJS_MANAGE, Grants.SETTINGS_CONTROL to Grants.SETTINGS_WRITE, Grants.SYSPROPS_CONTROL to null,
            Grants.MEMORY_CONTROL to Grants.MEMORY_DUMP, Grants.INSTALLER_CONTROL to Grants.INSTALLER_MUTATE)
        val modules = mapOf(Grants.AUTOJS_CONTROL to "autojs", Grants.SETTINGS_CONTROL to "settings", Grants.SYSPROPS_CONTROL to "sysprops",
            Grants.MEMORY_CONTROL to "memory", Grants.INSTALLER_CONTROL to "installer")
        for ((grant, write) in pairs) {
            val module = modules.getValue(grant)
            assertEquals(RuntimeSystemBridge.methodsByModule[module], BunAutomationBridge.methods[grant])
            val all = BunAutomationBridge.nodeCapabilities(listOfNotNull(grant, write, Grants.FILES_CONTROL))
            val query = BunAutomationBridge.nodeCapabilities(listOf(grant))
            for (method in BunAutomationBridge.methods.getValue(grant)) {
                val required = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method)
                assertEquals(method, emptyList<String>(), NodeBridgePermissionManifest.missingCapabilities(all, required))
                val missing = NodeBridgePermissionManifest.missingCapabilities(query, required)
                assertTrue("$module.$method", missing.isEmpty() || missing.any { it.contains('.') })
            }
        }
        assertEquals(listOf("autojs.control"), NodeBridgePermissionManifest.missingCapabilities(BunAutomationBridge.nodeCapabilities(listOf(Grants.AUTOJS_CONTROL)),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("autojs", "setRootMode")))
        assertEquals(listOf("installer.mutate"), NodeBridgePermissionManifest.missingCapabilities(BunAutomationBridge.nodeCapabilities(listOf(Grants.INSTALLER_CONTROL)),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("installer", "install")))
        assertEquals(listOf("files.write"), NodeBridgePermissionManifest.missingCapabilities(BunAutomationBridge.nodeCapabilities(listOf(Grants.SETTINGS_CONTROL)),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("settings", "export")))
        assertEquals(Grants.SYSTEM_FAMILY, Grants.validatePackagedCapabilities(Grants.SYSTEM_FAMILY))
    }

    @Test fun enginesGrantMapsToEnginesAndNoticeAndPowerManagerFollowTheirGrants() {
        assertEquals(listOf("engines"), BunAutomationBridge.nodeCapabilities(listOf(Grants.ENGINES_CONTROL)))
        assertEquals(listOf("engines", "engines.exec"), BunAutomationBridge.nodeCapabilities(listOf(Grants.ENGINES_CONTROL, Grants.ENGINES_EXEC)))
        assertEquals(NodeBridgeModules.enginesMethods, BunAutomationBridge.methods[Grants.ENGINES_CONTROL])
        val notice = BunAutomationBridge.extraModules.getValue(Grants.NOTIFICATIONS_POST).getValue("notice")
        val noticeGrants = BunAutomationBridge.nodeCapabilities(listOf(Grants.NOTIFICATIONS_POST, Grants.NOTIFICATIONS_SETTINGS))
        for (method in notice) assertEquals(method, emptyList<String>(),
            NodeBridgePermissionManifest.missingCapabilities(noticeGrants, NodeBridgePermissionManifest.requiredCapabilitiesFor("notice", method)))
        val power = BunAutomationBridge.extraModules.getValue(Grants.DEVICE_CONTROL).getValue("power_manager")
        val powerGrants = BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_CONTROL, Grants.DEVICE_POWER))
        for (method in power) assertEquals(method, emptyList<String>(),
            NodeBridgePermissionManifest.missingCapabilities(powerGrants, NodeBridgePermissionManifest.requiredCapabilitiesFor("power_manager", method)))
    }

    // Q137 (a): plugin_tools.control lists and calls the tools; USER_GRANTED providers also need plugin_tools.user_granted.
    @Test fun pluginToolsGrantsMapToNodePluginToolsAndUserGrantedStaysSeparate() {
        assertEquals(listOf("plugin_tools"), BunAutomationBridge.nodeCapabilities(listOf(Grants.PLUGIN_TOOLS_CONTROL)))
        assertEquals(listOf("plugin_tools", "plugin_tools.user_granted"),
            BunAutomationBridge.nodeCapabilities(listOf(Grants.PLUGIN_TOOLS_CONTROL, Grants.PLUGIN_TOOLS_USER_GRANTED)))
        assertEquals(NodeBridgeModules.pluginToolsMethods, BunAutomationBridge.methods[Grants.PLUGIN_TOOLS_CONTROL])
        for (method in NodeBridgeModules.pluginToolsMethods)
            assertEquals(listOf("plugin_tools"), NodeBridgePermissionManifest.requiredCapabilitiesFor("plugin_tools", method))
        assertEquals(listOf("files", "files.write"), BunAutomationBridge.nodeCapabilities(listOf(Grants.FILES_CONTROL)))
    }

}
