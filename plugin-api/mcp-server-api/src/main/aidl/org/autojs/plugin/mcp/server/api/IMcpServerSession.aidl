package org.autojs.plugin.mcp.server.api;

import android.os.Bundle;

/**
 * One opened server (contract version 1). The host owns the session: it must call close()
 * when it no longer needs the listener, and it drops the binding lease afterwards.
 */
interface IMcpServerSession {

    /** Snapshot keyed by McpServerContract.KEY_STATUS_*; never null. */
    Bundle getStatus();

    /**
     * Applies a partial McpServerContract.KEY_SERVER_CONFIG_* Bundle. Changing the port or the
     * bind scope restarts the listener and is reported through onStatus.
     */
    void updateConfig(in Bundle config);

    /** Stops the listener but keeps the session alive; reason uses McpServerContract.KEY_ERROR_*. */
    void stop(in Bundle reason);

    /** Stops the listener if needed and releases the broker and the callback. */
    void close();

}
