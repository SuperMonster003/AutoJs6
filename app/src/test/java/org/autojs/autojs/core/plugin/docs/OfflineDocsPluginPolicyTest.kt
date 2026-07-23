package org.autojs.autojs.core.plugin.docs

import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineDocsPluginPolicyTest {

    @Test
    fun `disabled candidates are rejected before content inspection`() {
        assertEquals(
            OfflineDocsPluginLoadError.PLUGIN_DISABLED,
            rejection(enabled = false, contractVersion = OfflineDocsPluginContract.VERSION + 1),
        )
    }

    @Test
    fun `only official or same-signer candidates may supply executable web content`() {
        assertNull(rejection(authorizedState = PluginAuthorizedState.OFFICIAL))
        listOf(
            PluginAuthorizedState.TRUSTED,
            PluginAuthorizedState.USER_GRANTED,
            PluginAuthorizedState.REQUIRED,
        ).forEach { state ->
            assertEquals(
                state.name,
                OfflineDocsPluginLoadError.IN_PROCESS_TRUST_REQUIRED,
                rejection(authorizedState = state),
            )
            assertNull(state.name, rejection(authorizedState = state, sameSignerAsHost = true))
        }
    }

    @Test
    fun `contract version and fixed content layout must exactly match`() {
        assertEquals(
            OfflineDocsPluginLoadError.CONTRACT_VERSION_UNSUPPORTED,
            rejection(contractVersion = OfflineDocsPluginContract.VERSION + 1),
        )
        assertEquals(
            OfflineDocsPluginLoadError.CONTENT_FORMAT_UNSUPPORTED,
            rejection(contentFormat = "untrusted-format"),
        )
        assertEquals(
            OfflineDocsPluginLoadError.ASSET_ROOT_UNSUPPORTED,
            rejection(assetRoot = "web"),
        )
        assertEquals(
            OfflineDocsPluginLoadError.ENTRY_POINT_UNSUPPORTED,
            rejection(entryPoint = "home.html"),
        )
    }

    @Test
    fun `content identity and inventory metadata are bounded and validated`() {
        assertEquals(
            OfflineDocsPluginLoadError.CONTENT_VERSION_INVALID,
            rejection(contentVersion = "version with spaces"),
        )
        assertEquals(OfflineDocsPluginLoadError.FILE_COUNT_INVALID, rejection(fileCount = 0L))
        assertEquals(
            OfflineDocsPluginLoadError.FILE_COUNT_INVALID,
            rejection(fileCount = OfflineDocsPluginContract.MAX_FILE_COUNT + 1L),
        )
        assertEquals(OfflineDocsPluginLoadError.TOTAL_BYTES_INVALID, rejection(totalBytes = 0L))
        assertEquals(
            OfflineDocsPluginLoadError.TOTAL_BYTES_INVALID,
            rejection(totalBytes = OfflineDocsPluginContract.MAX_TOTAL_BYTES + 1L),
        )
        assertEquals(
            OfflineDocsPluginLoadError.CONTENT_SHA256_INVALID,
            rejection(contentSha256 = "not-a-sha256"),
        )
        assertEquals(
            OfflineDocsPluginLoadError.CONTENT_SHA256_INVALID,
            rejection(contentSha256 = "ABCDEF".repeat(10) + "ABCD"),
        )
    }

    @Test
    fun `required host metadata cannot predate this contract`() {
        assertEquals(
            OfflineDocsPluginLoadError.CONTRACT_METADATA_INVALID,
            rejection(requiresHostVersion = null),
        )
        assertEquals(
            OfflineDocsPluginLoadError.CONTRACT_METADATA_INVALID,
            rejection(requiresHostVersion = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE - 1L),
        )
        assertEquals(
            OfflineDocsPluginLoadError.CONTRACT_METADATA_INVALID,
            rejection(maxHostVersion = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE - 1L),
        )
        assertEquals(
            OfflineDocsPluginLoadError.HOST_VERSION_TOO_LOW,
            rejection(
                hostVersionCode = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE,
                requiresHostVersion = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE + 1L,
            ),
        )
        assertEquals(
            OfflineDocsPluginLoadError.HOST_VERSION_TOO_HIGH,
            rejection(
                hostVersionCode = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE + 1L,
                maxHostVersion = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE,
            ),
        )
    }

    private fun rejection(
        enabled: Boolean = true,
        authorizedState: PluginAuthorizedState = PluginAuthorizedState.OFFICIAL,
        sameSignerAsHost: Boolean = false,
        contractVersion: Int = OfflineDocsPluginContract.VERSION,
        contentVersion: String = "1.0.0",
        contentFormat: String = OfflineDocsPluginContract.CONTENT_FORMAT,
        assetRoot: String = OfflineDocsPluginContract.ASSET_ROOT,
        entryPoint: String = OfflineDocsPluginContract.ENTRY_POINT,
        fileCount: Long = 161L,
        totalBytes: Long = 7_088_551L,
        contentSha256: String = "a".repeat(64),
        hostVersionCode: Long = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE,
        requiresHostVersion: Long? = OfflineDocsPluginContract.REQUIRED_HOST_VERSION_CODE,
        maxHostVersion: Long? = null,
    ): OfflineDocsPluginLoadError? = OfflineDocsPluginPolicy.rejectionCode(
        enabled = enabled,
        authorizedState = authorizedState,
        sameSignerAsHost = sameSignerAsHost,
        contractVersion = contractVersion,
        contentVersion = contentVersion,
        contentFormat = contentFormat,
        assetRoot = assetRoot,
        entryPoint = entryPoint,
        fileCount = fileCount,
        totalBytes = totalBytes,
        contentSha256 = contentSha256,
        hostVersionCode = hostVersionCode,
        requiresHostVersion = requiresHostVersion,
        maxHostVersion = maxHostVersion,
    )
}
