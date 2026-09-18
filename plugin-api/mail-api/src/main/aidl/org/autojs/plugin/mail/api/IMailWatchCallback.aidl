package org.autojs.plugin.mail.api;

import android.os.Bundle;

/** Event channel of IMailSession.watch. The plugin must never block on a slow host. */
oneway interface IMailWatchCallback {
    /**
     * One watch event under MailContract.KEY_EVENT_JSON. `generation` echoes the value the host
     * put into the watch options and `seq` increases by one per event, so the host can drop stale
     * or reordered deliveries.
     */
    void onEvent(long generation, long seq, in Bundle event);
}
