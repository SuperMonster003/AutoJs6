package org.autojs.plugin.host.capability.api;

import android.os.Bundle;
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback;

/**
 * Shared host capability broker, contract version 1. Transaction order is frozen.
 * All arguments/results are non-null snapshots. Calls may arrive on Binder threads.
 * Synchronous methods return within 200 ms; dispatch queues work and reports exactly
 * one response through the callback. The plugin may use the broker only while attached.
 */
interface IHostCapabilityBroker {
    /** Identity and granted capabilities, using HostCapabilityContract keys; never null. */
    Bundle getBrokerInfo();
    /**
     * Non-null request and callback. Bridge JSON is bounded by MAX_BRIDGE_INLINE_JSON_BYTES.
     * A supplied descriptor is receiver-owned and must be closed even on rejection.
     */
    void dispatch(in Bundle request, IHostCapabilityCallback callback);
    /** Idempotently releases the broker; reason uses KEY_REASON_JSON or legacy KEY_ERROR_*. */
    void destroy(in Bundle reason);
}
