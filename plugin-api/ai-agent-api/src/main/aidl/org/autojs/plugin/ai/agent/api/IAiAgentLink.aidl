package org.autojs.plugin.ai.agent.api;

import android.os.Bundle;
import org.autojs.plugin.ai.agent.api.IAiAgentRunCallback;

/**
 * AI Agent contract version 1; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Synchronous methods validate/enqueue within 200 ms.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
interface IAiAgentLink {
    /** Snapshot under KEY_STATUS_JSON. */
    Bundle getStatus();
    /** Queues KEY_RUN_REQUEST_JSON; returns KEY_RUN_RESPONSE_JSON (runId/position or error). */
    Bundle startRun(in Bundle request, IAiAgentRunCallback callback);
    /** Answers input/confirmation under KEY_RUN_RESPONSE_JSON; returns an acknowledgement or error. */
    Bundle respond(in Bundle response);
    /** Cancels KEY_RUN_REF_JSON; does not undo already completed operations. */
    void cancelRun(in Bundle ref);
    /** Query under KEY_RUN_REQUEST_JSON; bounded summaries under KEY_RUN_RESPONSE_JSON. */
    Bundle listRuns(in Bundle query);
    /** KEY_RUN_REF_JSON lookup; response includes a summary and at most 50 recent steps. */
    Bundle getRun(in Bundle ref);
    /** Query under KEY_RUN_REQUEST_JSON; presets under KEY_RUN_RESPONSE_JSON. */
    Bundle listPresets(in Bundle query);
    /** Applies KEY_LINK_CONFIG_JSON; existing grants may only be narrowed. */
    void updateConfig(in Bundle config);
    /** Releases both brokers; reason uses HostCapabilityContract.KEY_REASON_JSON. Idempotent. */
    void detach(in Bundle reason);
}
