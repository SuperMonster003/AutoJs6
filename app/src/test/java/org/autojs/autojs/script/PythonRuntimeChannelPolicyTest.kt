package org.autojs.autojs.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeChannelPolicyTest {

    @Test
    fun bothChannelsRemainDisabledByDefault() {
        assertFalse(channelEnabled(debuggable = true))
        assertFalse(channelEnabled(debuggable = false))
    }

    @Test
    fun debugBuildAdmitsOnlyPreviewFlag() {
        assertTrue(channelEnabled(debuggable = true, previewEnabled = true))
        assertFalse(channelEnabled(debuggable = true, releaseCandidateEnabled = true))
    }

    @Test
    fun releaseBuildAdmitsOnlyReleaseCandidateFlag() {
        assertTrue(channelEnabled(debuggable = false, releaseCandidateEnabled = true))
        assertFalse(channelEnabled(debuggable = false, previewEnabled = true))
    }

    @Test
    fun activeChannelWinsWhenBothFlagsWereSupplied() {
        assertTrue(
            channelEnabled(
                debuggable = true,
                previewEnabled = true,
                releaseCandidateEnabled = true,
            ),
        )
        assertTrue(
            channelEnabled(
                debuggable = false,
                previewEnabled = true,
                releaseCandidateEnabled = true,
            ),
        )
    }

    private fun channelEnabled(
        debuggable: Boolean,
        previewEnabled: Boolean = false,
        releaseCandidateEnabled: Boolean = false,
    ): Boolean = PythonRuntimeChannelPolicy.isEnabled(
        debuggable = debuggable,
        previewEnabled = previewEnabled,
        releaseCandidateEnabled = releaseCandidateEnabled,
    )
}
