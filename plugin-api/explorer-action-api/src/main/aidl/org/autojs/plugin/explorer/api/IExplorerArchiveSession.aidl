package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/** A caller-bound, read-only view of one archive. Added in Explorer Action protocol v5. */
interface IExplorerArchiveSession {

    Bundle getInfo();

    Bundle listChildren(String parentId, int offset, int limit);

    void close();

    /** Streams one regular entry through a read-only pipe. Added in protocol v6. */
    ParcelFileDescriptor openEntry(String entryId);
}
