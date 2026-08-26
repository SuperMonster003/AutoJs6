package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.explorer.api.IExplorerActionHostSession;
import org.autojs.plugin.explorer.api.IExplorerArchiveOperationCallback;

/** A caller-bound view of one archive. Added in Explorer Action protocol v5. */
interface IExplorerArchiveSession {

    Bundle getInfo();

    Bundle listChildren(String parentId, int offset, int limit);

    void close();

    /** Streams one regular entry through a read-only pipe. Added in protocol v6. */
    ParcelFileDescriptor openEntry(String entryId);

    /**
     * Starts one asynchronous extraction into a host-owned output tree. Added in protocol v10.
     * The request contains opaque entry IDs and a caller-generated operation ID.
     */
    void extractEntries(
        in Bundle request,
        IExplorerActionHostSession outputSession,
        IExplorerArchiveOperationCallback callback
    );

    /** Requests cancellation of one extraction started by this caller. Added in protocol v10. */
    void cancelExtraction(String operationId);
}
