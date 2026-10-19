package org.autojs.plugin.three.stove.agent.api;

import android.os.Bundle;


/**
 * 3-Stove Agent contract version 2; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Callbacks are oneway and must not block.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
oneway interface IThreeStoveAgentRunCallback {
    /**
     * KEY_RUN_EVENT_JSON, at most MAX_EVENT_JSON_BYTES, with runId and increasing sequence.
     * Ignore duplicate/out-of-order sequence numbers; events after a terminal state are invalid.
     * Callback death removes the observer but does not cancel the task.
     */
    void onRunEvent(in Bundle event);
}
