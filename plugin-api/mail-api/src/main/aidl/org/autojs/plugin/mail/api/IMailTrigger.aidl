package org.autojs.plugin.mail.api;

import android.os.Bundle;

/** Handle of one trigger subscription (contract version 2, plugin roadmap P8). */
interface IMailTrigger {
    /** Status of the background watch as JSON under MailContract.KEY_STATUS_JSON (triggerId, state, mode, lastError, since). */
    Bundle getStatus();

    /** Replaces the subscription options (MailContract.KEY_TRIGGER_OPTIONS_JSON: the filter); the watch itself is unchanged. */
    void update(in Bundle options);

    /** Ends the subscription; the background watch keeps running for the plugin's own records and other subscribers. */
    void stop();
}
