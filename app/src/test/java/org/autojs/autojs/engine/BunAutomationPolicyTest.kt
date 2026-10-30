package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
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
        // 194, then app.control +6 (P4.1a), mediainfo.control 4 (P4.2b), the system and engines family 51 and plugin tools 3 (P4.1b),
        // media playback 6 and the media library scan 1 (P4.1c), Rhino's device members 30 (keepAwake / cancelVibration and the 28
        // RuntimeDeviceBridge routes, P3.1c / P4.1c).
        assertEquals(295,BunAutomationBridge.methods.values.sumOf{it.size})
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

    // Q137 (a) / Q162 (a): plugintools.control lists and calls the tools of every provider, USER_GRANTED ones included as in
    // Rhino and Node; there is no second grant.
    @Test fun pluginToolsGrantMapsToNodePluginToolsWithoutASecondGrant() {
        assertEquals(listOf("plugin_tools"), BunAutomationBridge.nodeCapabilities(listOf(Grants.PLUGIN_TOOLS_CONTROL)))
        assertEquals(NodeBridgeModules.pluginToolsMethods, BunAutomationBridge.methods[Grants.PLUGIN_TOOLS_CONTROL])
        for (method in NodeBridgeModules.pluginToolsMethods)
            assertEquals(listOf("plugin_tools"), NodeBridgePermissionManifest.requiredCapabilitiesFor("plugin_tools", method))
        assertEquals(listOf("files", "files.write"), BunAutomationBridge.nodeCapabilities(listOf(Grants.FILES_CONTROL)))
        assertEquals(12, Grants.SYSTEM_FAMILY.size)
        assertEquals(listOf(Grants.PLUGIN_TOOLS_CONTROL), Grants.SYSTEM_FAMILY.filter { it.startsWith("plugintools.") })
    }

    // Q139 (b): execution.control goes with every broker and is no grant, so neither a switch nor project.json names it.
    @Test fun executionControlIsNotAGrant() {
        assertFalse(Grants.EXECUTION_CONTROL in Grants.SYSTEM_FAMILY)
        assertNull(BunAutomationBridge.methods[Grants.EXECUTION_CONTROL])
        assertThrows(IllegalArgumentException::class.java) { Grants.validatePackagedCapabilities(listOf(Grants.EXECUTION_CONTROL)) }
    }

    // api-coverage Q170 (a): a request offers at most the plugin's bound, execution.control included; a plugin that advertises
    // no bound keeps the 64 of its request parser.
    @Test fun offeredCapabilitiesKeepExecutionControlWithinThePluginBound() {
        val granted = (1..70).map { "ns.cap$it" }
        val legacy = Grants.offeredCapabilities(granted, BunRuntimeContract.LEGACY_MAX_HOST_CAPABILITY_COUNT)
        assertEquals(granted.take(63) + Grants.EXECUTION_CONTROL, legacy)
        assertEquals(granted + Grants.EXECUTION_CONTROL, Grants.offeredCapabilities(granted, BunRuntimeContract.MAX_HOST_CAPABILITY_COUNT))
        assertEquals(BunRuntimeContract.MAX_HOST_CAPABILITY_COUNT, Grants.offeredCapabilities((1..200).map { "ns.cap$it" }, 1000).size)
        assertEquals(listOf(Grants.EXECUTION_CONTROL), Grants.offeredCapabilities(granted, 0))
        assertEquals(listOf(Grants.UI_LAYOUT, Grants.EXECUTION_CONTROL), Grants.offeredCapabilities(listOf(Grants.UI_LAYOUT), 64))
    }

    // api-coverage P4.1c (Q152 (a)): Rhino's media module, its playback and the media library scan as the Node capabilities.
    @Test fun mediaGrantsMapToNodePlaybackAndTheLibraryScan() {
        val playback = BunAutomationBridge.nodeCapabilities(listOf(Grants.MEDIA_CONTROL))
        val library = BunAutomationBridge.nodeCapabilities(listOf(Grants.MEDIA_LIBRARY))
        assertEquals(listOf("media", "media.playback"), playback)
        assertEquals(listOf("media", "media.library"), library)
        assertEquals(NodeBridgeModules.mediaPlaybackMethods, BunAutomationBridge.methods[Grants.MEDIA_CONTROL])
        assertEquals(setOf("scanFile"), BunAutomationBridge.methods[Grants.MEDIA_LIBRARY])
        for (method in NodeBridgeModules.mediaPlaybackMethods)
            assertTrue(method, playback.containsAll(NodeBridgePermissionManifest.requiredCapabilitiesFor("media", method)))
        assertTrue(library.containsAll(NodeBridgePermissionManifest.requiredCapabilitiesFor("media_store", "scanFile")))
        // Neither grant reaches the audio streams of device.audio or a library change.
        assertFalse(NodeBridgePermissionManifest.MEDIA_AUDIO in playback + library)
        assertFalse(NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE in playback + library)
        assertEquals(listOf(Grants.MEDIA_CONTROL, Grants.MEDIA_LIBRARY), Grants.validatePackagedCapabilities(listOf(Grants.MEDIA_CONTROL, Grants.MEDIA_LIBRARY)))
        val pattern = Regex("""[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+""")
        for (capability in listOf(Grants.MEDIA_CONTROL, Grants.MEDIA_LIBRARY)) assertTrue(capability, pattern.matches(capability))
    }

    // api-coverage P4.1c (Q153 (a)): the hardware identifiers need device.identifiers on top of device.control, the pointer
    // location switches and keepAwake device.power, as the Node capabilities of Q146 (a).
    @Test fun deviceIdentifiersNeedTheirOwnGrantAndTheRhinoDeviceRoutesFollowNode() {
        assertEquals(listOf("device.identifiers"), BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_IDENTIFIERS)))
        val device = BunAutomationBridge.methods.getValue(Grants.DEVICE_CONTROL)
        assertTrue(device.containsAll(RuntimeDeviceBridge.methods + NodeBridgeModules.deviceRhinoControls))
        val control = BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_CONTROL)).toSet()
        val identifiers = BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_CONTROL, Grants.DEVICE_IDENTIFIERS)).toSet()
        val power = BunAutomationBridge.nodeCapabilities(listOf(Grants.DEVICE_CONTROL, Grants.DEVICE_POWER)).toSet()
        for (method in listOf("getIMEI", "getSerial", "getAndroidId", "getMacAddress", "getSharedDeviceId")) {
            val required = NodeBridgePermissionManifest.requiredCapabilitiesFor("device", method)
            assertFalse(method, control.containsAll(required))
            assertTrue(method, identifiers.containsAll(required))
        }
        for (method in listOf("keepAwake", "setPointerLocation", "togglePointerLocation")) {
            val required = NodeBridgePermissionManifest.requiredCapabilitiesFor("device", method)
            assertFalse(method, control.containsAll(required))
            assertTrue(method, power.containsAll(required))
        }
        for (method in listOf("summary", "getRotation", "isPowerSourceAC", "getIpAddress", "isPointerLocationEnabled", "isBackgroundStartGranted", "cancelVibration"))
            assertTrue(method, control.containsAll(NodeBridgePermissionManifest.requiredCapabilitiesFor("device", method)))
        assertEquals(listOf(Grants.DEVICE_IDENTIFIERS), Grants.validatePackagedCapabilities(listOf(Grants.DEVICE_IDENTIFIERS)))
    }

    // The plugin's request parser (BunExecutionRequestParser) rejects the whole execution when a granted capability ID is not
    // dotted lowercase words, so a grant ID with an underscore would break every script while that grant is on.
    @Test fun systemFamilyGrantIdsPassThePluginCapabilityIdPattern() {
        val pattern = Regex("""[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+""")
        for (capability in Grants.SYSTEM_FAMILY + Grants.EXECUTION_CONTROL) assertTrue(capability, pattern.matches(capability))
    }

}
