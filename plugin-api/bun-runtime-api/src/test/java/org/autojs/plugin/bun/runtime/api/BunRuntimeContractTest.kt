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
}
