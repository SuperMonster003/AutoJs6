package org.autojs.plugin.mcp.server.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerContractTest {

    @Test
    fun identityAndDiscoveryLiteralsAreStable() {
        assertEquals(PluginActions.INFO, McpServerActions.INFO)
        assertEquals("org.autojs.plugin.MCP_SERVER", McpServerActions.SERVICE_ACTION)
        assertEquals("mcp-server", McpServerActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", McpServerActions.PLUGIN_PERMISSION)

        assertEquals("mcp-server", McpServerIds.PLUGIN_ID)
        assertEquals("mcp-server", McpServerIds.ENGINE)
        assertEquals("default", McpServerIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.mcp.server", McpServerIds.DEFAULT_PACKAGE_NAME)
        assertEquals(5279L, McpServerIds.REQUIRED_HOST_VERSION_CODE)

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, McpServerCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("mcpServerContractVersion", McpServerCapabilityKeys.CONTRACT_VERSION)
        assertEquals("mcpServerToolGroups", McpServerCapabilityKeys.TOOL_GROUPS)
        assertEquals("mcpServerProtocolVersions", McpServerCapabilityKeys.PROTOCOL_VERSIONS)
    }

    @Test
    fun contractVersionRangesIncludeTheirBoundsAndRejectNeighbours() {
        assertEquals(1, McpServerContract.CONTRACT_VERSION)
        assertTrue(McpServerContract.MIN_CONTRACT_VERSION <= McpServerContract.MAX_CONTRACT_VERSION)
        assertTrue(McpServerContract.supportsContractVersion(McpServerContract.MIN_CONTRACT_VERSION))
        assertTrue(McpServerContract.supportsContractVersion(McpServerContract.CONTRACT_VERSION))
        assertTrue(McpServerContract.supportsContractVersion(McpServerContract.MAX_CONTRACT_VERSION))
        assertFalse(McpServerContract.supportsContractVersion(McpServerContract.MIN_CONTRACT_VERSION - 1))
        assertFalse(McpServerContract.supportsContractVersion(McpServerContract.MAX_CONTRACT_VERSION + 1))

        assertEquals(1, McpServerContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION)
        assertTrue(McpServerContract.supportsHostCapabilityBrokerContractVersion(McpServerContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION))
        assertFalse(McpServerContract.supportsHostCapabilityBrokerContractVersion(0))
        assertFalse(McpServerContract.supportsHostCapabilityBrokerContractVersion(McpServerContract.MAX_HOST_CAPABILITY_BROKER_CONTRACT_VERSION + 1))
    }

    @Test
    fun endpointDefaultsMatchDecisionD16() {
        assertEquals(9637, McpServerContract.DEFAULT_PORT)
        assertEquals("/mcp", McpServerContract.ENDPOINT_PATH)
        assertEquals("127.0.0.1", McpServerContract.LOOPBACK_HOST)
        assertTrue(McpServerContract.isValidPort(McpServerContract.DEFAULT_PORT))
        assertTrue(McpServerContract.isValidPort(McpServerContract.MIN_PORT))
        assertTrue(McpServerContract.isValidPort(McpServerContract.MAX_PORT))
        assertFalse(McpServerContract.isValidPort(McpServerContract.MIN_PORT - 1))
        assertFalse(McpServerContract.isValidPort(McpServerContract.MAX_PORT + 1))
        assertEquals("loopback", McpServerContract.BIND_SCOPE_LOOPBACK)
        assertEquals(setOf("loopback", "lan"), McpServerContract.BIND_SCOPES)
        assertEquals(setOf("stateful", "dual"), McpServerContract.PROTOCOL_MODES)
    }

    @Test
    fun bridgeEnvelopeKeysMirrorTheNodeJsFamily() {
        assertEquals("bridgeRequestJson", McpServerContract.KEY_BRIDGE_REQUEST_JSON)
        assertEquals("bridgeResponseJson", McpServerContract.KEY_BRIDGE_RESPONSE_JSON)
        assertEquals("bridgeResponseOk", McpServerContract.KEY_BRIDGE_RESPONSE_OK)
        assertEquals("bridgeErrorMessage", McpServerContract.KEY_BRIDGE_ERROR_MESSAGE)
        assertEquals("bridgeEngineInfo", McpServerContract.KEY_BRIDGE_ENGINE_INFO)
        assertEquals("hostCapabilityBrokerId", McpServerContract.KEY_HOST_CAPABILITY_BROKER_ID)
        assertEquals("hostCapabilityBrokerVersion", McpServerContract.KEY_HOST_CAPABILITY_BROKER_VERSION)
        assertEquals("hostCapabilityModules", McpServerContract.KEY_HOST_CAPABILITY_MODULES)
        assertEquals("errorCode", McpServerContract.KEY_ERROR_CODE)
        assertEquals("errorMessage", McpServerContract.KEY_ERROR_MESSAGE)
        assertEquals("contractVersion", McpServerContract.KEY_CONTRACT_VERSION)
    }

    @Test
    fun statesAndEventTypesAreClosedSets() {
        assertEquals(setOf("stopped", "starting", "running", "stopping", "failed"), McpServerContract.STATES)
        McpServerContract.STATES.forEach { state -> assertTrue(state, McpServerContract.isKnownState(state)) }
        assertFalse(McpServerContract.isKnownState(null))
        assertFalse(McpServerContract.isKnownState("paused"))
        assertFalse(McpServerContract.isActiveState(McpServerContract.STATE_STOPPED))
        assertFalse(McpServerContract.isActiveState(McpServerContract.STATE_FAILED))
        assertTrue(McpServerContract.isActiveState(McpServerContract.STATE_STARTING))
        assertTrue(McpServerContract.isActiveState(McpServerContract.STATE_RUNNING))
        assertTrue(McpServerContract.isActiveState(McpServerContract.STATE_STOPPING))
        assertEquals(
            setOf("pairing_requested", "client_paired", "client_revoked", "tool_call", "warning"),
            McpServerContract.EVENT_TYPES,
        )
    }

    @Test
    fun hardCeilingsArePositiveAndOrdered() {
        val ceilings = listOf(
            McpServerContract.MAX_REQUEST_BODY_BYTES.toLong(),
            McpServerContract.MAX_CONCURRENT_TOOL_CALLS.toLong(),
            McpServerContract.DEFAULT_TOOL_TIMEOUT_MS,
            McpServerContract.MAX_TOOL_TIMEOUT_MS,
            McpServerContract.MAX_DUMP_NODES.toLong(),
            McpServerContract.MAX_DUMP_DEPTH.toLong(),
            McpServerContract.MAX_DUMP_TEXT_BYTES.toLong(),
            McpServerContract.MAX_SCREENSHOT_ENCODED_BYTES.toLong(),
            McpServerContract.MAX_CONSOLE_TAIL_LINES.toLong(),
            McpServerContract.MAX_CONSOLE_TAIL_BYTES.toLong(),
            McpServerContract.MAX_FILE_READ_BYTES.toLong(),
            McpServerContract.MAX_FILE_WRITE_BYTES.toLong(),
            McpServerContract.MAX_LIST_ENTRIES.toLong(),
            McpServerContract.MAX_SHELL_OUTPUT_BYTES.toLong(),
            McpServerContract.MAX_PAIRED_CLIENTS.toLong(),
            McpServerContract.PAIRING_WINDOW_MS,
            McpServerContract.PAIRING_COOLDOWN_MS,
            McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES.toLong(),
            McpServerContract.MAX_BRIDGE_PAYLOAD_BYTES.toLong(),
            McpServerContract.MAX_ERROR_MESSAGE_BYTES.toLong(),
            McpServerContract.MAX_EVENT_TEXT_BYTES.toLong(),
            McpServerContract.MAX_CLIENT_NAME_BYTES.toLong(),
        )
        ceilings.forEach { value -> assertTrue("ceiling $value must be positive", value > 0L) }

        assertTrue(McpServerContract.DEFAULT_TOOL_TIMEOUT_MS <= McpServerContract.MAX_TOOL_TIMEOUT_MS)
        assertTrue(McpServerContract.PAIRING_COOLDOWN_MS <= McpServerContract.PAIRING_WINDOW_MS)
        // Tool inputs travel inside one HTTP body, so no input ceiling may exceed the body ceiling.
        assertTrue(McpServerContract.MAX_FILE_WRITE_BYTES <= McpServerContract.MAX_REQUEST_BODY_BYTES)
        // Inline Binder JSON stays well under the 1 MB transaction buffer; bigger payloads use the descriptor.
        assertTrue(McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES < 1024 * 1024)
        assertTrue(McpServerContract.MAX_DUMP_TEXT_BYTES <= McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertTrue(McpServerContract.MAX_CONSOLE_TAIL_BYTES <= McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertTrue(McpServerContract.MAX_SHELL_OUTPUT_BYTES <= McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertTrue(McpServerContract.MAX_SCREENSHOT_ENCODED_BYTES <= McpServerContract.MAX_BRIDGE_PAYLOAD_BYTES)
        assertTrue(McpServerContract.MAX_FILE_READ_BYTES <= McpServerContract.MAX_BRIDGE_PAYLOAD_BYTES)
        assertTrue(McpServerContract.MAX_EVENT_TEXT_BYTES <= McpServerContract.MAX_ERROR_MESSAGE_BYTES)
    }
}
