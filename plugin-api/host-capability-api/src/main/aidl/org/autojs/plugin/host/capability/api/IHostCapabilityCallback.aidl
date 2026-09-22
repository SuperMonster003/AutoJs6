package org.autojs.plugin.host.capability.api;

import android.os.Bundle;

/** Non-blocking completion of one dispatch. Transaction order is frozen. */
oneway interface IHostCapabilityCallback {
    /**
     * Non-null immutable response snapshot using HostCapabilityContract.KEY_BRIDGE_*.
     * Inline JSON is at most MAX_BRIDGE_INLINE_JSON_BYTES; an optional payload descriptor
     * is owned by the receiver and must be closed after reading or rejecting the response.
     */
    void onResponse(in Bundle response);
}
