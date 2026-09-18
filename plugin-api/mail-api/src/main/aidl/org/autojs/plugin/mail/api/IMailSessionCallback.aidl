package org.autojs.plugin.mail.api;

import android.os.Bundle;

/** Session lifecycle channel registered with IMailPlugin.openSession. */
oneway interface IMailSessionCallback {
    /** Session status under MailContract.KEY_STATUS_JSON, sent when the state changes. */
    void onStatus(in Bundle status);
}
