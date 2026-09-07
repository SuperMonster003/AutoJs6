package org.autojs.plugin.mcp.server.api;

import android.os.Bundle;
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback;

/**
 * Host -> plugin capability proxy handed over in IMcpServerPlugin.openServer (broker contract
 * version 1). Every MCP tool call becomes one dispatch of a NodeBridgeRequest JSON envelope; the
 * host applies the session grant before executing it and answers through the callback.
 */
interface IMcpHostCapabilityBroker {

    /**
     * Broker identity, the bridge engine info, and the grant, keyed by
     * McpServerContract.KEY_HOST_CAPABILITY_* and KEY_GRANT_*; never null.
     */
    Bundle getBrokerInfo();

    /**
     * request carries McpServerContract.KEY_BRIDGE_REQUEST_JSON (at most
     * MAX_BRIDGE_INLINE_JSON_BYTES) and optionally KEY_BRIDGE_CLIENT_NAME; the response arrives
     * exactly once on the callback, possibly on a host worker thread.
     */
    void dispatch(in Bundle request, IMcpHostCapabilityCallback callback);

    /** Releases every host resource of the session; reason uses McpServerContract.KEY_ERROR_*. */
    void destroy(in Bundle reason);

}
