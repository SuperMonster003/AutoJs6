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
}
