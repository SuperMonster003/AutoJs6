package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.mcp.server.api.McpServerActions
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginCenterMcpServerRegistrationTest {

    @Test
    fun engineResolvesToDedicatedServiceAction() {
        assertEquals(
            McpServerActions.SERVICE_ACTION,
            PluginCenterViewModel.serviceActionFromEngine(McpServerIds.ENGINE),
        )
        assertEquals(
            McpServerActions.SERVICE_ACTION,
            PluginCenterViewModel.serviceActionFromEngine(" MCP-SERVER "),
        )
    }

    @Test
    fun unrelatedEnginesStayUnmapped() {
        assertNull(PluginCenterViewModel.serviceActionFromEngine("mcp"))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("mcp-client"))
    }
}
