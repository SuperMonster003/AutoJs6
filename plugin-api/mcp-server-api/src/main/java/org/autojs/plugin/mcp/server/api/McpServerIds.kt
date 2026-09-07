package org.autojs.plugin.mcp.server.api

/**
 * Identity of the official MCP Server plugin as reported by `PluginInfo` and checked by the host
 * before binding. A third-party build with a different package name is never selected.
 */
object McpServerIds {

    const val PLUGIN_ID = "mcp-server"
    const val ENGINE = "mcp-server"
    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.mcp.server"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0 host build that ships the MCP contract
     * module, the capability broker and the P1.3 bridge methods (roadmap P1.4).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5279L
}
