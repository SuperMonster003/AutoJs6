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
}
