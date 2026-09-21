package org.autojs.plugin.mail.api;

import android.os.Bundle;

/** Event channel of IMailPlugin.openTrigger (contract version 2). The plugin must never block on a slow host. */
oneway interface IMailTriggerCallback {
    /**
     * One arrived message under MailContract.KEY_EVENT_JSON (`type` "mail", the watch's `triggerId`,
     * the account alias and address, the folder and the message envelope without a body).
     * `generation` echoes the subscription options and `seq` increases by one per event of the
     * background watch, so the host can drop stale or repeated deliveries.
     */
    void onMail(long generation, long seq, in Bundle event);

    /** Status of the background watch under MailContract.KEY_STATUS_JSON, sent when it changes and once after the subscription opens. */
    void onStatus(in Bundle status);
}
