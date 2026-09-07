package org.autojs.autojs.core.plugin.mcp

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
    fun defaultGrantCoversTheRoadmapToolGroupsOnly() {
        assertTrue(grant.allowsMethod("accessibility", "dump"))
        assertTrue(grant.allowsMethod("engines", "execScript"))
        assertTrue(grant.allowsMethod("files", "write"))
        assertTrue(grant.allowsMethod("console", "tail"))
        assertTrue(grant.allowsMethod("device", "info"))
        listOf("rhino", "java", "websocket", "fetch", "ui_overlay", "input_observer", "http", "storage").forEach { module ->
            assertFalse("module $module must stay outside the default grant", module in grant.modules)
        }
        assertFalse(grant.allowsMethod("accessibility", "setAccessibilityService"))
    }

    @Test
    fun evaluateDeniesUnknownMethodsBeforeLookingAtPermissions() {
        val decision = grant.evaluate("rhino", "eval", emptyList(), 10)
        assertTrue(decision is McpCapabilityGrant.Decision.Denied)
        assertEquals(McpCapabilityGrant.Reason.METHOD_NOT_GRANTED, (decision as McpCapabilityGrant.Decision.Denied).reason)
        assertEquals(listOf("rhino.eval"), decision.detail)
    }

    @Test
    fun evaluateAllowsSubsetPermissionsAndReportsMissingOnes() {
        val allowed = grant.evaluate("accessibility", "click", listOf("accessibility", " ACCESSIBILITY.GESTURE "), 100)
        assertEquals(McpCapabilityGrant.Decision.Allowed, allowed)

        val denied = grant.evaluate("accessibility", "click", listOf("accessibility", "websocket", "ui_overlay"), 100)
        assertTrue(denied is McpCapabilityGrant.Decision.Denied)
        denied as McpCapabilityGrant.Decision.Denied
        assertEquals(McpCapabilityGrant.Reason.PERMISSION_NOT_GRANTED, denied.reason)
        assertEquals(listOf("websocket", "ui_overlay"), denied.detail)
    }

    @Test
    fun evaluateRejectsOversizedRequests() {
        val decision = grant.evaluate("files", "write", listOf("files", "files.write"), grant.maxRequestBytes + 1)
        assertTrue(decision is McpCapabilityGrant.Decision.Denied)
        assertEquals(McpCapabilityGrant.Reason.REQUEST_TOO_LARGE, (decision as McpCapabilityGrant.Decision.Denied).reason)
        assertEquals(McpCapabilityGrant.Decision.Allowed, grant.evaluate("files", "write", listOf("files"), grant.maxRequestBytes))
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
