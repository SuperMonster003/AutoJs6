package org.autojs.plugin.mcp.server.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery contract of the MCP Server plugin family. The literals are frozen: the plugin
 * manifest, the host `<queries>` block, and the plugin center registration all repeat them.
 */
object McpServerActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [IMcpServerPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SERVICE_ACTION = "org.autojs.plugin.MCP_SERVER"

    /** Intent category of both plugin services, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "mcp-server"

    /** Signature permission every Binder entry point of the plugin requires from its caller. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /** Parameterless settings entry, exported only behind [PLUGIN_PERMISSION]. */
    const val OPEN_SETTINGS = "org.autojs.plugin.mcp.server.action.OPEN_SETTINGS"
}
