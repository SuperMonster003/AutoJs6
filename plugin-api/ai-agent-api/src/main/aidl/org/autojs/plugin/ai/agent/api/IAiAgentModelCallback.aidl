package org.autojs.plugin.ai.agent.api;

import android.os.Bundle;


/**
 * AI Agent contract version 1; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Callbacks are oneway and must not block.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
oneway interface IAiAgentModelCallback {
    /**
     * KEY_MODEL_EVENT_JSON with requestId and increasing sequence; exactly one terminal event.
     * Inline events are bounded by MAX_EVENT_JSON_BYTES. Larger results use KEY_PAYLOAD_FD,
     * owned by the receiver and always closed after reading or rejecting the event.
     */
    void onEvent(in Bundle event);
}
