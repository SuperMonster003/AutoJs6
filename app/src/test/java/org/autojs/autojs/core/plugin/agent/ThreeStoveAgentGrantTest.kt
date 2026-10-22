package org.autojs.autojs.core.plugin.agent

import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeStoveAgentGrantTest {
    private val grant = ThreeStoveAgentGrant.default()

    @Test fun appendixC4MethodsAreAnExactSnapshot() {
        val expected = """
            accessibility.isEnabled accessibility.ensureEnabled accessibility.dump accessibility.explain
            accessibility.screenshot accessibility.readScreenText accessibility.findOne accessibility.findAll
            accessibility.findByText accessibility.inspectNode accessibility.click accessibility.longClick accessibility.setText
            accessibility.scrollForward accessibility.scrollBackward accessibility.swipe accessibility.gesture
            accessibility.back accessibility.home accessibility.recentApps
            keys.notifications keys.quickSettings
            agent.listScripts agent.readManifest agent.execRegistered
            engines.execScript engines.execScriptFile engines.list engines.stop engines.stopAll console.tail
            files.list files.stat files.read files.write
            app.launchPackage app.launchApp app.isInstalled app.currentWindow app.listSamples app.readSample
            package_manager.list package_manager.verify package_manager.listApps
            clipboard.getText clipboard.setText clipboard.hasText device.info device.isScreenOn device.wakeUp
            media_projection.requestScreenCapture media_projection.stop image.captureScreen image.recycle
            shell.exec toast.toast toast.showToast
            plugin_tools.list plugin_tools.invoke plugin_tools.cancel
        """.trim().split(Regex("\\s+")).toSet()
        assertEquals(expected, grant.allowedMethods)
        assertEquals(2, grant.maxConcurrentCalls)
        assertEquals(30, grant.modelCallsPerMinute)
        assertEquals(128 * 1024, grant.maxInputBytesPerRequest)
        assertEquals(1_000_000L, grant.maxTotalTokens)
    }

    @Test fun unrelatedCapabilitiesCannotBeEnabledByPluginPreferences() {
        listOf("files.delete", "rhino.run", "java.invoke", "websocket.connect", "fetch.fetch",
            "ui.overlay", "input_observer.observe", "events.on", "keys.lockScreen", "keys.powerDialog",
            "keys.takeScreenshot", "keys.splitScreen").forEach { method ->
            val result = grant.evaluate(method.substringBefore('.'), method.substringAfter('.'), emptyList(), 1)
            assertTrue(method, result is HostCapabilityGrant.Decision.Denied)
            assertEquals(HostCapabilityGrant.Reason.METHOD_NOT_GRANTED, (result as HostCapabilityGrant.Decision.Denied).reason)
        }
        assertFalse("files.delete" in grant.permissions)
        assertTrue(grant.evaluate("files", "read", listOf("java_interop"), 1) is HostCapabilityGrant.Decision.Denied)
    }

    @Test fun pluginToolsAreGrantedWithoutTheToolsOfUserGrantedProviders() {
        for (method in listOf("list", "invoke", "cancel")) {
            val result = grant.evaluate("plugin_tools", method, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS), 1)
            assertTrue(method, result is HostCapabilityGrant.Decision.Allowed)
        }
        assertTrue(NodeBridgePermissionManifest.PLUGIN_TOOLS in grant.permissions)
        assertFalse(NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED in grant.permissions)
        val userGranted = grant.evaluate("plugin_tools", "list", listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED), 1)
        assertEquals(HostCapabilityGrant.Reason.PERMISSION_NOT_GRANTED, (userGranted as HostCapabilityGrant.Decision.Denied).reason)
    }

    @Test fun onlyTheTwoPanelActionsReceiveTheKeysCapability() {
        for (method in listOf("notifications", "quickSettings")) {
            assertTrue(grant.evaluate("keys", method, listOf("keys"), 1) is HostCapabilityGrant.Decision.Allowed)
        }
    }
}
