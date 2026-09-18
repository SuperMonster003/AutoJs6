package org.autojs.plugin.mail.api;

import android.os.Bundle;

/** Handle of one new-mail watch. */
interface IMailWatch {
    /** Watch status as JSON under MailContract.KEY_STATUS_JSON (state, mode, reason, lastError). */
    Bundle getStatus();

    /** Stops the watch; a final `closed` event follows. */
    void stop();
}
