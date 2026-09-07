package org.autojs.plugin.mcp.server.api;

import android.os.Bundle;

/**
 * Completion of one IMcpHostCapabilityBroker.dispatch (broker contract version 1). oneway: the
 * host never waits for the plugin to consume the response.
 */
oneway interface IMcpHostCapabilityCallback {

    /**
     * response carries McpServerContract.KEY_BRIDGE_RESPONSE_JSON, KEY_BRIDGE_RESPONSE_OK, and
     * optionally KEY_BRIDGE_ERROR_MESSAGE plus a KEY_BRIDGE_PAYLOAD_FD the receiver must close.
     */
    void onResponse(in Bundle response);

}
