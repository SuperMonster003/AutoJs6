package org.autojs.plugin.three.stove.agent.api;

import android.os.Bundle;
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelCallback;

/**
 * 3-Stove Agent contract version 2; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Synchronous methods validate/enqueue within 200 ms.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
interface IThreeStoveAgentModelBroker {
    /** Identity and quotas under KEY_MODEL_BROKER_INFO_JSON and HostCapabilityContract.KEY_GRANT_JSON. */
    Bundle getBrokerInfo();
    /** Optional filters in KEY_MODEL_REQUEST_JSON; catalogue completion via callback. */
    void listTargets(in Bundle request, IThreeStoveAgentModelCallback callback);
    /**
     * KEY_MODEL_REQUEST_JSON; messages/schema are bounded by ThreeStoveAgentContract limits.
     * Non-null callback receives started, chunk*, usage?, then exactly one terminal event.
     * Large JSON may use KEY_PAYLOAD_FD; the receiver owns and closes that descriptor.
     * Descriptor requests must include KEY_MODEL_REF_JSON with the same requestId as the body,
     * enabling cancellation while the payload is still being read. A mismatch is rejected.
     */
    void generate(in Bundle request, IThreeStoveAgentModelCallback callback);
    /** KEY_MODEL_REF_JSON with requestId; cancellation is idempotent. */
    void cancel(in Bundle ref);
    /** Cancels calls and releases the link broker; reason uses HostCapabilityContract.KEY_REASON_JSON. */
    void destroy(in Bundle reason);
    /**
     * Additive toolCallingVersion 1 extension. Call only after getBrokerInfo advertises it.
     * KEY_MODEL_REQUEST_JSON contains requestId, round and the exact pending tool results.
     * Completion/failure continues on the original generate callback. No new generation is opened.
     * The same payload descriptor ownership and requestId correlation rules as generate apply.
     */
    void submitToolResults(in Bundle request);
}
