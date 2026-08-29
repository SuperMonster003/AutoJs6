package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * One operation-scoped, read-only snapshot of explicitly selected archive inputs. Added in
 * Explorer Action protocol v20. Nodes and files are addressed only by grant-scoped opaque IDs.
 */
interface IExplorerArchiveInputSession {

    Bundle getInfo();

    Bundle listChildren(String parentId, int offset, int limit);

    ParcelFileDescriptor openFile(String itemId);

    Bundle verifySnapshot();

    void close();
}
