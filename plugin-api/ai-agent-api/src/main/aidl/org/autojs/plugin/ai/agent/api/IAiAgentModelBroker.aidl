package org.autojs.plugin.ai.agent.api;

import android.os.Bundle;
import org.autojs.plugin.ai.agent.api.IAiAgentModelCallback;

/**
 * AI Agent contract version 1; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Synchronous methods validate/enqueue within 200 ms.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
interface IAiAgentModelBroker {
    /** Identity and quotas under KEY_MODEL_BROKER_INFO_JSON and HostCapabilityContract.KEY_GRANT_JSON. */
    Bundle getBrokerInfo();
    /** Optional filters in KEY_MODEL_REQUEST_JSON; catalogue completion via callback. */
    void listTargets(in Bundle request, IAiAgentModelCallback callback);
    /**
     * KEY_MODEL_REQUEST_JSON; messages/schema are bounded by AiAgentContract limits.
     * Non-null callback receives started, chunk*, usage?, then exactly one terminal event.
     * Large JSON may use KEY_PAYLOAD_FD; the receiver owns and closes that descriptor.
     * Descriptor requests must include KEY_MODEL_REF_JSON with the same requestId as the body,
     * enabling cancellation while the payload is still being read. A mismatch is rejected.
     */
    void generate(in Bundle request, IAiAgentModelCallback callback);
    /** KEY_MODEL_REF_JSON with requestId; cancellation is idempotent. */
    void cancel(in Bundle ref);
    /** Cancels calls and releases the link broker; reason uses HostCapabilityContract.KEY_REASON_JSON. */
    void destroy(in Bundle reason);
}
