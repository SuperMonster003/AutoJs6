package org.autojs.plugin.mail.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.mail.api.IMailCallCallback;
import org.autojs.plugin.mail.api.IMailWatch;
import org.autojs.plugin.mail.api.IMailWatchCallback;

/**
 * One account session. Calls are asynchronous: `call` returns immediately and the result arrives
 * exactly once through the callback, after zero or more progress notifications.
 */
interface IMailSession {
    /** Session status as JSON under MailContract.KEY_STATUS_JSON (state, reason, lastError). */
    Bundle getStatus();

    /**
     * Submits one operation. The request bundle carries the JSON envelope (id, op, args) under
     * MailContract.KEY_REQUEST_JSON. Descriptors are owned by the host: a single pipe write end
     * for download operations, or read-only attachment descriptors for send and append. The plugin
     * duplicates what it needs and closes its copies before onResult. Returns the request id.
     */
    String call(in Bundle request, in ParcelFileDescriptor[] descriptors, IMailCallCallback callback);

    /** Cancels a pending request; the request still completes with a CANCELLED error. */
    void cancel(String requestId);

    /** Starts a new-mail watch; options carry the folder, mode and MailContract.KEY_GENERATION. */
    IMailWatch watch(in Bundle options, IMailWatchCallback callback);

    /** Closes the session, its watches and its connections. Pending calls fail with SESSION_CLOSED. */
    void close();
}
