package org.autojs.plugin.tool.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Host callback of one accepted call (docs/dev/plugin-tool-protocol-v1.md). A call ends with exactly
 * one onResult or onError; later reports are ignored. Transactions may only ever be appended
 * (PluginToolAidlOrderTest).
 */
oneway interface IPluginToolCallback {

    /** Reserved progress report (PluginToolProgressKeys); the host bridge of contract version 1 drops it. */
    void onProgress(in Bundle update);

    /**
     * The result (PluginToolResultKeys). outputs carries the binary outputs the tool declares, readable
     * descriptors in the order PluginToolResultKeys.OUTPUTS_JSON names them; the host reads each up to
     * PluginToolLimits.MAX_DESCRIPTOR_BYTES and closes it. The call is oneway: keep RESULT_JSON and TEXT
     * within PluginToolLimits.MAX_RESULT_CHARS, since a larger Parcel may fail in the provider with
     * TransactionTooLargeException and never reach the host (MCP roadmap D65).
     */
    void onResult(in Bundle result, in ParcelFileDescriptor[] outputs);

    /** The failure (PluginToolErrorKeys), including PluginToolErrorCodes.CANCELLED after cancel(). */
    void onError(in Bundle error);
}
