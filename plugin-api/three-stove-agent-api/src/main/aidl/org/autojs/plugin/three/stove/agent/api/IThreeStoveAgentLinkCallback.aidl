package org.autojs.plugin.three.stove.agent.api;

import android.os.Bundle;


/**
 * 3-Stove Agent contract version 2; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Callbacks are oneway and must not block.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
oneway interface IThreeStoveAgentLinkCallback {
    /** KEY_STATUS_JSON snapshot, at most MAX_EVENT_JSON_BYTES. */
    void onStatus(in Bundle status);
    /** KEY_RUN_EVENT_JSON with queue_changed or preset_changed. */
    void onEvent(in Bundle event);
}
