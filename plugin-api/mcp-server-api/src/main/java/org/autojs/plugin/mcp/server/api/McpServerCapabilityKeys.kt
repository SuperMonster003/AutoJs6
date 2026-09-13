package org.autojs.plugin.mcp.server.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [IMcpServerPlugin.getCapabilities]. Every
 * value is readable without opening a server, so the plugin center and the drawer can show
 * compatibility before the user switches the listener on.
 */
object McpServerCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /** Int: [McpServerContract.CONTRACT_VERSION] implemented by the plugin. */
    const val CONTRACT_VERSION = "mcpServerContractVersion"

    /** String array: tool groups the plugin can expose, for example `script`, `ui`, `screen`, `files`. */
    const val TOOL_GROUPS = "mcpServerToolGroups"

    /** String array: MCP protocol versions the listener negotiates, newest first. */
    const val PROTOCOL_VERSIONS = "mcpServerProtocolVersions"

    /** String: version of the MCP SDK bundled in the plugin, for diagnostics only. */
    const val SDK_VERSION = "mcpServerSdkVersion"

    /** Int: optional settings extension. Version 1 supports OPEN_SETTINGS and saved configuration. */
    const val SETTINGS_VERSION = "mcpServerSettingsVersion"

    /** Boolean, getCapabilities only: true after a user stop or before the first successful start. */
    const val USER_STOPPED = "mcpServerUserStopped"
}
