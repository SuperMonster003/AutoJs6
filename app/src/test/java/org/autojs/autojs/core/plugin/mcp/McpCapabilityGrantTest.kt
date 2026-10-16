package org.autojs.autojs.core.plugin.mcp

import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.engine.NodeBridgeModules
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpCapabilityGrantTest {

    private val grant = McpCapabilityGrant.DEFAULT

    @Test
    fun workspaceToolsHaveAnAppQueryAndRootRequiresItsOwnToken() {
        assertTrue(grant.allowsMethod("package_manager", "listApps"))
        assertTrue(NodeBridgePermissionManifest.SHELL_ROOT in grant.permissions)
        val restricted = grant.copy(permissions = grant.permissions - NodeBridgePermissionManifest.SHELL_ROOT)
        assertEquals(HostCapabilityGrant.Decision.Allowed, restricted.evaluate("shell", "exec", listOf("shell"), 100))
        assertTrue(restricted.evaluate("shell", "exec", listOf("shell", "shell.root"), 100) is HostCapabilityGrant.Decision.Denied)
    }

    @Test
    fun defaultGrantCoversTheRoadmapToolGroupsOnly() {
        assertTrue(grant.allowsMethod("accessibility", "dump"))
        assertTrue(grant.allowsMethod("engines", "execScript"))
        assertTrue(grant.allowsMethod("files", "write"))
        assertTrue(grant.allowsMethod("console", "tail"))
        assertTrue(grant.allowsMethod("device", "info"))
        listOf("notifications", "quickSettings", "powerDialog", "lockScreen").forEach { method ->
            assertTrue("keys.$method serves ui_press_key", grant.allowsMethod("keys", method))
        }
        assertFalse(grant.allowsMethod("keys", "takeScreenshot"))
        assertFalse(grant.allowsMethod("keys", "splitScreen"))
        assertTrue(NodeBridgePermissionManifest.KEYS in grant.permissions)
        listOf("rhino", "java", "websocket", "fetch", "ui_overlay", "input_observer", "http", "storage").forEach { module ->
            assertFalse("module $module must stay outside the default grant", module in grant.modules)
        }
        assertFalse(grant.allowsMethod("accessibility", "setAccessibilityService"))
    }

    @Test
    fun defaultGrantCoversThePluginFamilyToolsOfP11() {
        listOf("ocr.recognize", "ocr.recognizeText", "ocr.detectTextBounds", "barcode.detect", "barcode.detectAll",
            "pinyin.convert", "pinyin4j.of", "mediainfo.read", "mediainfo.capabilities",
            "image.readImage", "image.findImage", "image.matchTemplate", "image.findColor", "image.findMultiColors",
            "accessibility.readScreenText", "accessibility.captureScreenImage", "app.pluginCapabilities").forEach { key ->
            assertTrue("$key must be in the default grant", key in grant.allowedMethods)
        }
        listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.BARCODE, NodeBridgePermissionManifest.PINYIN,
            NodeBridgePermissionManifest.PINYIN4J, NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_METADATA,
            NodeBridgePermissionManifest.FILES_SHARED_READ).forEach { token ->
            assertTrue("$token must be in the default permissions", token in grant.permissions)
        }
        // Writes and raw pixel export stay outside: tools return handles and paths, never pixels.
        assertFalse(grant.allowsMethod("image", "saveImage"))
        assertFalse(grant.allowsMethod("image", "toBytes"))
        assertFalse(grant.allowsMethod("media_store", "query"))
        assertFalse(NodeBridgePermissionManifest.MEDIA_LIBRARY in grant.permissions)
    }

    @Test
    fun defaultGrantCoversTheRegisteredScriptToolsOfP11() {
        listOf("agent.listScripts", "agent.readManifest", "agent.execRegistered").forEach { key ->
            assertTrue("$key must be in the default grant", key in grant.allowedMethods)
        }
        assertTrue(NodeBridgePermissionManifest.AGENT in grant.permissions)
        assertTrue(NodeBridgePermissionManifest.AGENT_EXEC in grant.permissions)
        assertEquals(HostCapabilityGrant.Decision.Allowed, grant.evaluate("agent", "listScripts", listOf("agent"), 100))
        assertEquals(HostCapabilityGrant.Decision.Allowed,
            grant.evaluate("agent", "execRegistered", NodeBridgePermissionManifest.requiredCapabilitiesFor("agent", "execRegistered"), 100))
        val withoutExec = grant.copy(permissions = grant.permissions - NodeBridgePermissionManifest.AGENT_EXEC)
        assertTrue(withoutExec.evaluate("agent", "execRegistered", listOf("agent", "agent.exec", "engines", "engines.exec"), 100) is HostCapabilityGrant.Decision.Denied)
    }

    @Test
    fun defaultGrantCoversThePluginToolsModuleOfP12() {
        listOf("plugin_tools.list", "plugin_tools.invoke", "plugin_tools.cancel").forEach { key ->
            assertTrue("$key must be in the default grant", key in grant.allowedMethods)
        }
        assertTrue(NodeBridgePermissionManifest.PLUGIN_TOOLS in grant.permissions)
        assertTrue(NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED in grant.permissions)
        assertEquals(listOf("plugin_tools"), NodeBridgePermissionManifest.requiredCapabilitiesFor("plugin_tools", "invoke"))
        assertEquals(HostCapabilityGrant.Decision.Allowed,
            grant.evaluate("plugin_tools", "invoke", listOf("plugin_tools", "plugin_tools.user_granted", "screen_capture", "files", "files.write"), 100))
        val withoutUserGranted = grant.copy(permissions = grant.permissions - NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED)
        assertTrue(withoutUserGranted.evaluate("plugin_tools", "invoke", listOf("plugin_tools", "plugin_tools.user_granted"), 100) is HostCapabilityGrant.Decision.Denied)
    }

    @Test
    fun evaluateDeniesUnknownMethodsBeforeLookingAtPermissions() {
        val decision = grant.evaluate("rhino", "eval", emptyList(), 10)
        assertTrue(decision is HostCapabilityGrant.Decision.Denied)
        assertEquals(HostCapabilityGrant.Reason.METHOD_NOT_GRANTED, (decision as HostCapabilityGrant.Decision.Denied).reason)
        assertEquals(listOf("rhino.eval"), decision.detail)
    }

    @Test
    fun evaluateAllowsSubsetPermissionsAndReportsMissingOnes() {
        val allowed = grant.evaluate("accessibility", "click", listOf("accessibility", " ACCESSIBILITY.GESTURE "), 100)
        assertEquals(HostCapabilityGrant.Decision.Allowed, allowed)

        val denied = grant.evaluate("accessibility", "click", listOf("accessibility", "websocket", "ui_overlay"), 100)
        assertTrue(denied is HostCapabilityGrant.Decision.Denied)
        denied as HostCapabilityGrant.Decision.Denied
        assertEquals(HostCapabilityGrant.Reason.PERMISSION_NOT_GRANTED, denied.reason)
        assertEquals(listOf("websocket", "ui_overlay"), denied.detail)
    }

    @Test
    fun evaluateRejectsOversizedRequests() {
        val decision = grant.evaluate("files", "write", listOf("files", "files.write"), grant.maxRequestBytes + 1)
        assertTrue(decision is HostCapabilityGrant.Decision.Denied)
        assertEquals(HostCapabilityGrant.Reason.REQUEST_TOO_LARGE, (decision as HostCapabilityGrant.Decision.Denied).reason)
        assertEquals(HostCapabilityGrant.Decision.Allowed, grant.evaluate("files", "write", listOf("files"), grant.maxRequestBytes))
    }

    @Test
    fun timeoutIsClampedIntoTheGrant() {
        assertEquals(McpServerContract.DEFAULT_TOOL_TIMEOUT_MS, grant.effectiveTimeoutMs(null))
        assertEquals(McpServerContract.DEFAULT_TOOL_TIMEOUT_MS, grant.effectiveTimeoutMs(0L))
        assertEquals(McpServerContract.DEFAULT_TOOL_TIMEOUT_MS, grant.effectiveTimeoutMs(-5L))
        assertEquals(1_500L, grant.effectiveTimeoutMs(1_500L))
        assertEquals(McpServerContract.MAX_TOOL_TIMEOUT_MS, grant.effectiveTimeoutMs(McpServerContract.MAX_TOOL_TIMEOUT_MS + 1))
    }

    @Test
    fun grantValidationRejectsInconsistentCeilings() {
        assertThrows { McpCapabilityGrant(setOf("accessibility.click"), emptySet(), maxRequestBytes = 0) }
        assertThrows { McpCapabilityGrant(setOf("accessibility.click"), emptySet(), maxRequestBytes = McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES + 1) }
        assertThrows { McpCapabilityGrant(setOf("accessibility.click"), emptySet(), maxConcurrentCalls = McpServerContract.MAX_CONCURRENT_TOOL_CALLS + 1) }
        assertThrows { McpCapabilityGrant(setOf("accessibility.click"), emptySet(), defaultTimeoutMs = 10L, maxTimeoutMs = 5L) }
        assertThrows { McpCapabilityGrant(setOf("accessibility.click"), emptySet(), maxTimeoutMs = McpServerContract.MAX_TOOL_TIMEOUT_MS + 1) }
        assertThrows { McpCapabilityGrant(setOf("accessibility"), emptySet()) }
        assertThrows { McpCapabilityGrant(setOf("a.b.c"), emptySet()) }
    }

    @Test
    fun defaultPermissionsCoverEveryBridgeMethodTheDefaultGrantAllows() {
        val supported = NodeBridgeModules.supportedMethodsByModule
        val uncovered = mutableListOf<String>()
        grant.allowedMethods.forEach { entry ->
            val module = entry.substringBefore('.')
            val method = entry.substringAfter('.')
            val methods = supported[module] ?: return@forEach
            if (method !in methods) {
                return@forEach
            }
            val required = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method)
            val missing = grant.missingPermissions(required)
            if (missing.isNotEmpty()) {
                uncovered += "$entry -> $missing"
            }
        }
        assertTrue("default permissions must cover the bridge requirements: $uncovered", uncovered.isEmpty())
    }

    @Test
    fun defaultGrantCeilingsMatchTheContract() {
        assertEquals(McpServerContract.MAX_CONCURRENT_TOOL_CALLS, grant.maxConcurrentCalls)
        assertEquals(McpServerContract.DEFAULT_TOOL_TIMEOUT_MS, grant.defaultTimeoutMs)
        assertEquals(McpServerContract.MAX_TOOL_TIMEOUT_MS, grant.maxTimeoutMs)
        assertTrue(grant.maxRequestBytes <= McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertTrue(grant.modules.containsAll(listOf("accessibility", "engines", "console", "files", "app", "device", "shell")))
    }

    private fun assertThrows(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        assertTrue("expected an IllegalArgumentException", thrown is IllegalArgumentException)
    }
}
