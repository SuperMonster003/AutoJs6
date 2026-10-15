package org.autojs.plugin.gallery.api;

import android.os.Bundle;

/**
 * Event channel registered with the interactive IGalleryPlugin calls. The generation is assigned by
 * the plugin per session and the sequence number grows by one per event of that generation, so the
 * host can drop stale and duplicated deliveries. The event bundle carries GalleryContract.KEY_EVENT
 * and the keys documented per event type; it stays below GalleryContract.MAX_EVENT_BYTES.
 */
oneway interface IGalleryCallback {
    void onEvent(long generation, long seq, in Bundle event);
}
