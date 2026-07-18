package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRuntimeBackendTest {

    @Test
    fun pluginBackendsHaveDistinctRequiredAndOptionalValues() {
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("plugin"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("node-plugin"))
        assertEquals(NodeRuntimeBackend.PLUGIN_OPTIONAL, NodeRuntimeBackend.from("pluginOptional"))
        assertEquals(NodeRuntimeBackend.PLUGIN_OPTIONAL, NodeRuntimeBackend.from("plugin-optional"))
    }

    @Test
    fun unknownBackendFailsFast() {
        val failure = runCatching { NodeRuntimeBackend.from("not-a-backend") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("not-a-backend"))
    }
}
