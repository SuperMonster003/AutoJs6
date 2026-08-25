package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Short-lived, least-privilege host session attached to an Explorer Action v4 request.
 * Implementations must bind every call to the plugin UID that received the request.
 */
interface IExplorerActionHostSession {

    Bundle listChildren(String targetId, String relativePath, int offset, int limit);

    ParcelFileDescriptor openFile(String targetId, String relativePath);

    Bundle prepareOutput(String displayName, String mimeType, int conflictPolicy);

    ParcelFileDescriptor openOutput(String transactionId);

    Bundle commitOutput(String transactionId);

    void abortOutput(String transactionId);

    void close();

    /**
     * v7. Opens one read-only descriptor for a staged output after its writer is closed and before
     * commit. Appended after all v4 methods so their Binder transaction codes remain stable.
     */
    ParcelFileDescriptor openPendingOutput(String transactionId);

    /**
     * v8. Reserves a host-owned replacement transaction for one original request target. Existing
     * output open, verify, commit and abort methods drive the rest of the transaction.
     */
    Bundle prepareTargetReplacement(String targetId);
}
