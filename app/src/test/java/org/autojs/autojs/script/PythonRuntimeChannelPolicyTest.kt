package org.autojs.autojs.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeChannelPolicyTest {

    @Test
    fun stableAutoJs6AppHostChannelIsEnabled() {
        assertTrue(channelEnabled(isInrt = false))
    }

    @Test
    fun packagedInrtHostChannelRemainsUnsupported() {
        assertFalse(channelEnabled(isInrt = true))
    }

    private fun channelEnabled(isInrt: Boolean): Boolean =
        PythonRuntimeChannelPolicy.isEnabled(isInrt = isInrt)
}
