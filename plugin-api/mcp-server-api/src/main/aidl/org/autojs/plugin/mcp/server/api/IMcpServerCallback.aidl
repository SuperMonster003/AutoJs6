package org.autojs.plugin.mcp.server.api;

import android.os.Bundle;

/**
 * Plugin -> host notifications for one session (contract version 1). Both methods are oneway:
 * the plugin must not block on the host, and the host must treat every Bundle as a snapshot.
 */
oneway interface IMcpServerCallback {

    /** Full status snapshot keyed by McpServerContract.KEY_STATUS_*; sent on every transition. */
    void onStatus(in Bundle status);

    /** Informational event keyed by McpServerContract.KEY_EVENT_*; never required for correctness. */
    void onEvent(in Bundle event);

}
