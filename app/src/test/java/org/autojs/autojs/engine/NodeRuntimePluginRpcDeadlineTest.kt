package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRuntimePluginRpcDeadlineTest {

    @Test
    fun responseDeadlineIncludesBoundedGrace() {
        assertEquals(5_250L, NodeRuntimePluginRpcDeadline.responseWaitTimeoutMs(250L))
    }

    @Test
    fun responseDeadlineSaturatesInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            NodeRuntimePluginRpcDeadline.responseWaitTimeoutMs(Long.MAX_VALUE - 1L),
        )
    }

    @Test
    fun disabledRemoteTimeoutKeepsHostWaitUnbounded() {
        assertEquals(0L, NodeRuntimePluginRpcDeadline.responseWaitTimeoutMs(0L))
    }
}
