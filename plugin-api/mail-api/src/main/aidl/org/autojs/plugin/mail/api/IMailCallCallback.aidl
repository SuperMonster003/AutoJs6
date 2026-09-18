package org.autojs.plugin.mail.api;

import android.os.Bundle;

/** Result channel of IMailSession.call; both notifications carry a JSON document. */
oneway interface IMailCallCallback {
    /** Transfer progress under MailContract.KEY_PROGRESS_JSON (id, transferred, total). */
    void onProgress(in Bundle progress);

    /** Final response under MailContract.KEY_RESPONSE_JSON; exactly once per request id. */
    void onResult(in Bundle response);
}
