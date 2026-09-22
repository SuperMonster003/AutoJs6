package org.autojs.plugin.ai.agent.api;

import android.os.Bundle;


/**
 * AI Agent contract version 1; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Callbacks are oneway and must not block.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
oneway interface IAiAgentRunCallback {
    /**
     * KEY_RUN_EVENT_JSON, at most MAX_EVENT_JSON_BYTES, with runId and increasing sequence.
     * Ignore duplicate/out-of-order sequence numbers; events after a terminal state are invalid.
     * Callback death removes the observer but does not cancel the task.
     */
    void onRunEvent(in Bundle event);
}
