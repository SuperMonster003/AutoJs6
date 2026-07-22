package org.autojs.autojs.core.plugin.editor

import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.plugin.editor.api.EditorPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AceEditorPluginPolicyTest {

    @Test
    fun `official and trusted candidates may execute in process`() {
        listOf(PluginAuthorizedState.OFFICIAL, PluginAuthorizedState.TRUSTED).forEach { state ->
            assertNull(rejection(authorizedState = state))
        }
    }

    @Test
    fun `user grant is insufficient for in-process execution`() {
        assertEquals(
            EditorPluginLoadError.IN_PROCESS_TRUST_REQUIRED,
            rejection(authorizedState = PluginAuthorizedState.USER_GRANTED),
        )
    }

    @Test
    fun `package signed by the host may execute in process`() {
        assertNull(
            rejection(
                authorizedState = PluginAuthorizedState.REQUIRED,
                sameSignerAsHost = true,
            ),
        )
    }

    @Test
    fun `disabled candidate is rejected before loading`() {
        assertEquals(EditorPluginLoadError.PLUGIN_DISABLED, rejection(enabled = false))
    }

    @Test
    fun `contract version must exactly match host`() {
        assertEquals(
            EditorPluginLoadError.CONTRACT_VERSION_UNSUPPORTED,
            rejection(contractVersion = EditorPluginContract.VERSION + 1),
        )
    }

    @Test
    fun `required host version is enforced`() {
        assertEquals(
            EditorPluginLoadError.HOST_VERSION_TOO_LOW,
            rejection(hostVersionCode = 10L, requiresHostVersion = 11L),
        )
        assertNull(rejection(hostVersionCode = 11L, requiresHostVersion = 11L))
    }

    @Test
    fun `maximum host version is enforced`() {
        assertEquals(
            EditorPluginLoadError.HOST_VERSION_TOO_HIGH,
            rejection(hostVersionCode = 12L, maxHostVersion = 11L),
        )
        assertNull(rejection(hostVersionCode = 11L, maxHostVersion = 11L))
    }

    private fun rejection(
        enabled: Boolean = true,
        authorizedState: PluginAuthorizedState = PluginAuthorizedState.OFFICIAL,
        sameSignerAsHost: Boolean = false,
        contractVersion: Int = EditorPluginContract.VERSION,
        hostVersionCode: Long = 100L,
        requiresHostVersion: Long? = null,
        maxHostVersion: Long? = null,
    ): EditorPluginLoadError? = AceEditorPluginPolicy.rejectionCode(
        enabled = enabled,
        authorizedState = authorizedState,
        sameSignerAsHost = sameSignerAsHost,
        contractVersion = contractVersion,
        hostVersionCode = hostVersionCode,
        requiresHostVersion = requiresHostVersion,
        maxHostVersion = maxHostVersion,
    )
}
