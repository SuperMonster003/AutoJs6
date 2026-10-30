package org.autojs.plugin.bun.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BunRuntimeContractTest {
    @Test
    fun identityIsIndependentFromNodeRuntime() {
        assertEquals("bun-runtime", BunPluginIds.ID)
        assertEquals("bun", BunPluginIds.ENGINE)
        assertEquals("org.autojs.plugin.bun.RUNTIME", BunPluginActions.RUNTIME)
        assertEquals(
            "org.autojs.plugin.bun.runtime.api.IBunRuntimePlugin",
            BunRuntimeContract.BINDER_DESCRIPTOR,
        )
    }

    @Test
    fun protocolLimitsAreBounded() {
        assertTrue(BunRuntimeContract.MAX_SOURCE_BYTES > 0)
        assertTrue(BunRuntimeContract.MAX_OUTPUT_BYTES > 0)
        assertTrue(BunRuntimeContract.DEFAULT_TIMEOUT_MILLIS <= BunRuntimeContract.MAX_TIMEOUT_MILLIS)
    }

    @Test
    fun workspaceArchiveKeysAreAdditiveToTheSingleSourceContract() {
        assertEquals(1, BunRuntimeContract.PROTOCOL_VERSION)
        assertEquals(1, BunRuntimeContract.WORKSPACE_ARCHIVE_VERSION)
        assertEquals("workspaceArchiveVersion", BunRuntimeContract.KEY_WORKSPACE_ARCHIVE_VERSION)
        assertEquals("workspaceEntryPoint", BunRuntimeContract.KEY_WORKSPACE_ENTRY_POINT)
        assertEquals("workspaceMaxEntries", BunRuntimeContract.KEY_WORKSPACE_MAX_ENTRIES)
        assertEquals("workspaceMaxBytes", BunRuntimeContract.KEY_WORKSPACE_MAX_BYTES)
        assertEquals("workspaceFileCount", BunRuntimeContract.KEY_WORKSPACE_FILE_COUNT)
        assertEquals("workspaceBytes", BunRuntimeContract.KEY_WORKSPACE_BYTES)
        assertEquals(
            "org.autojs.plugin.bun.SUPPORTS_WORKSPACE_ARCHIVE",
            BunPluginCapabilityKeys.SUPPORTS_WORKSPACE_ARCHIVE,
        )
        assertTrue(BunRuntimeContract.MAX_WORKSPACE_ENTRIES > 0)
        assertTrue(BunRuntimeContract.MAX_WORKSPACE_BYTES >= BunRuntimeContract.MAX_SOURCE_BYTES)
    }

    @Test
    fun hostInfoSnapshotKeysAreAdditiveAndBounded() {
        assertEquals(1, BunRuntimeContract.PROTOCOL_VERSION)
        assertEquals(1, BunRuntimeContract.HOST_INFO_VERSION)
        assertEquals("hostInfoVersion", BunRuntimeContract.KEY_HOST_INFO_VERSION)
        assertEquals("hostInfo", BunRuntimeContract.KEY_HOST_INFO)
        assertEquals("packageName", BunRuntimeContract.HOST_INFO_KEY_PACKAGE_NAME)
        assertEquals("versionDate", BunRuntimeContract.HOST_INFO_KEY_VERSION_DATE)
        assertEquals("languageTag", BunRuntimeContract.HOST_INFO_KEY_LANGUAGE_TAG)
        assertEquals("hostInfoDelivered", BunRuntimeContract.KEY_HOST_INFO_DELIVERED)
        assertEquals("AUTOJS6_HOST_INFO_FILE", BunRuntimeContract.HOST_INFO_ENVIRONMENT_VARIABLE)
        assertEquals("AUTOJS6_", BunRuntimeContract.RESERVED_ENVIRONMENT_PREFIX)
        assertTrue(BunRuntimeContract.HOST_INFO_ENVIRONMENT_VARIABLE.startsWith(BunRuntimeContract.RESERVED_ENVIRONMENT_PREFIX))
        assertEquals("org.autojs.plugin.bun.SUPPORTS_HOST_INFO", BunPluginCapabilityKeys.SUPPORTS_HOST_INFO)
        assertTrue(BunRuntimeContract.MAX_HOST_INFO_VALUE_BYTES in 1..BunRuntimeContract.MAX_HOST_INFO_BYTES)
        assertTrue(BunRuntimeContract.MAX_HOST_INFO_BYTES <= BunRuntimeContract.MAX_TERMINAL_MESSAGE_BYTES)
    }

    @Test
    fun hostCapabilityBridgeKeysAreAdditiveAndBounded() {
        assertEquals(1, BunRuntimeContract.PROTOCOL_VERSION)
        assertEquals(1, BunRuntimeContract.HOST_CAPABILITY_BRIDGE_VERSION)
        assertEquals("hostCapabilityBridgeVersion", BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION)
        assertEquals("hostCapabilityBroker", BunRuntimeContract.KEY_HOST_CAPABILITY_BROKER)
        assertEquals("hostCapabilities", BunRuntimeContract.KEY_HOST_CAPABILITIES)
        assertEquals("hostCallId", BunRuntimeContract.KEY_HOST_CALL_ID)
        assertEquals("hostCapability", BunRuntimeContract.KEY_HOST_CAPABILITY)
        assertEquals("hostCallRequestJson", BunRuntimeContract.KEY_HOST_CALL_REQUEST_JSON)
        assertEquals("hostCallResultJson", BunRuntimeContract.KEY_HOST_CALL_RESULT_JSON)
        assertEquals("hostBridgeDelivered", BunRuntimeContract.KEY_HOST_BRIDGE_DELIVERED)
        assertEquals("hostCalls", BunRuntimeContract.KEY_HOST_CALLS)
        assertEquals("ui.toast", BunRuntimeContract.HOST_CAPABILITY_UI_TOAST)
        assertEquals("device.info", BunRuntimeContract.HOST_CAPABILITY_DEVICE_INFO)
        assertEquals("AUTOJS6_HOST_BRIDGE_SOCKET", BunRuntimeContract.HOST_BRIDGE_ENVIRONMENT_VARIABLE)
        assertTrue(BunRuntimeContract.HOST_BRIDGE_ENVIRONMENT_VARIABLE.startsWith(BunRuntimeContract.RESERVED_ENVIRONMENT_PREFIX))
        assertEquals("org.autojs.plugin.bun.SUPPORTS_HOST_CAPABILITY_BRIDGE", BunPluginCapabilityKeys.SUPPORTS_HOST_CAPABILITY_BRIDGE)
        assertEquals("org.autojs.plugin.bun.runtime.api.IBunHostCapabilityBroker", IBunHostCapabilityBroker.DESCRIPTOR)
        assertEquals("org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback", IBunHostCapabilityCallback.DESCRIPTOR)
        assertTrue(BunRuntimeContract.MAX_HOST_CAPABILITY_COUNT in 1..1024)
        assertEquals(64, BunRuntimeContract.LEGACY_MAX_HOST_CAPABILITY_COUNT)
        assertTrue(BunRuntimeContract.LEGACY_MAX_HOST_CAPABILITY_COUNT <= BunRuntimeContract.MAX_HOST_CAPABILITY_COUNT)
        assertEquals("org.autojs.plugin.bun.MAX_HOST_CAPABILITY_COUNT", BunPluginCapabilityKeys.MAX_HOST_CAPABILITY_COUNT)
        assertTrue(BunRuntimeContract.MAX_HOST_CAPABILITY_ID_BYTES in 1..256)
        assertTrue(BunRuntimeContract.MAX_HOST_CALL_REQUEST_BYTES in 1..BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES)
        assertTrue(BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES < 1024 * 1024)
        assertTrue(BunRuntimeContract.MAX_CONCURRENT_HOST_CALLS in 1..BunRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION)
        assertTrue(BunRuntimeContract.HOST_CALL_TIMEOUT_MILLIS in 1..BunRuntimeContract.DEFAULT_TIMEOUT_MILLIS)
        val codes = listOf(
            BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED,
            BunRuntimeContract.HOST_CALL_ERROR_UNKNOWN_CAPABILITY, BunRuntimeContract.HOST_CALL_ERROR_PAYLOAD_TOO_LARGE,
            BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS, BunRuntimeContract.HOST_CALL_ERROR_QUOTA_EXCEEDED,
            BunRuntimeContract.HOST_CALL_ERROR_TIMEOUT, BunRuntimeContract.HOST_CALL_ERROR_HOST_UNAVAILABLE,
            BunRuntimeContract.HOST_CALL_ERROR_INTERNAL,
        )
        assertEquals(codes.size, codes.toSet().size)
    }
}
