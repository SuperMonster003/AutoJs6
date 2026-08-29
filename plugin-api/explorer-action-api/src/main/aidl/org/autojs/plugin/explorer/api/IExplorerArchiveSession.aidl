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
     * The request contains opaque entry IDs and a caller-generated operation ID. Protocol v11
     * may also carry bounded, one-shot recovery options after an interaction callback.
     */
    void extractEntries(
        in Bundle request,
        IExplorerActionHostSession outputSession,
        IExplorerArchiveOperationCallback callback
    );

    /** Requests cancellation of one extraction started by this caller. Added in protocol v10. */
    void cancelExtraction(String operationId);

    /**
     * Rebuilds this read-only session with an optional legacy filename charset override. Added in
     * protocol v13. A null charset restores automatic detection. The previous index remains active
     * if rebuilding fails.
     */
    Bundle reindexFilenameCharset(String filenameCharsetName);

    /**
     * Starts one asynchronous, host-transactional archive mutation. Added in protocol v19.
     * The initial protocol slice supports deletion and rename of existing entries. The supplied
     * host session exposes only the archive source target and its atomic replacement transaction.
     */
    void mutateEntries(
        in Bundle request,
        IExplorerActionHostSession outputSession,
        IExplorerArchiveOperationCallback callback
    );

    /** Requests cancellation of one mutation started by this caller. Added in protocol v19. */
    void cancelMutation(String operationId);
}
