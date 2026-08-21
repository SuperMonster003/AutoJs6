package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRuntimeBackendTest {

    @Test
    fun pluginAndHistoricalHostBackendsResolveToPlugin() {
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("plugin"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("node-plugin"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("pluginOptional"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("plugin-optional"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("embedded"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("native"))
        assertEquals(NodeRuntimeBackend.PLUGIN, NodeRuntimeBackend.from("auto"))
        assertEquals(NodeRuntimeBackend.DISABLED, NodeRuntimeBackend.from("disabled"))
    }

    @Test
    fun unknownBackendFailsFast() {
        val failure = runCatching { NodeRuntimeBackend.from("not-a-backend") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("not-a-backend"))
    }
}
