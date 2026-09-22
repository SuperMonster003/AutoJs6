package org.autojs.plugin.mcp.server.api;

import android.os.Bundle;
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker;
import org.autojs.plugin.mcp.server.api.IMcpServerCallback;
import org.autojs.plugin.mcp.server.api.IMcpServerSession;

/**
 * Contract version 2 control plane (version 1 transactions are unchanged) of the MCP Server plugin (host -> plugin).
 *
 * Transaction order is frozen: new methods are appended at the end and gated by
 * McpServerContract.CONTRACT_VERSION. The host binds the service with the
 * org.autojs.plugin.MCP_SERVER action and the mcp-server category; the plugin
 * runs the HTTP listener in its own process and never receives a host Context.
 */
interface IMcpServerPlugin {

    /** Standard plugin identity; capabilities carry the McpServerCapabilityKeys entries. */
    PluginInfo getInfo();

    /** Bundle keyed by McpServerCapabilityKeys, readable without opening a server. */
    Bundle getCapabilities();

    /**
     * Starts the listener with the McpServerContract.KEY_SERVER_CONFIG_* keys in config.
     * The plugin keeps the broker for the lifetime of the returned session and reports
     * McpServerContract.STATE_* transitions through callback.onStatus.
     */
    IMcpServerSession openServer(in Bundle config, IMcpHostCapabilityBroker broker, IMcpServerCallback callback);

    /** Version 2 only: opens the same listener using the shared host capability data plane. */
    IMcpServerSession openServerV2(in Bundle config, IHostCapabilityBroker broker, IMcpServerCallback callback);

}
