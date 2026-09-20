package org.autojs.plugin.epub.api;

import android.os.Bundle;

/**
 * Event channel registered with IEpubPlugin.openReader. The generation is assigned by the plugin
 * per session and the sequence number grows by one per event of that generation, so the host
 * can drop stale and duplicated deliveries. The event bundle carries EpubContract.KEY_EVENT and
 * the keys documented per event type; it stays below EpubContract.MAX_EVENT_BYTES.
 */
oneway interface IEpubReaderCallback {
    void onEvent(long generation, long seq, in Bundle event);
}
