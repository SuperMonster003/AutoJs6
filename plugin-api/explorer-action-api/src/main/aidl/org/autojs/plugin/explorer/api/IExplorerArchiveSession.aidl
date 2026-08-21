package org.autojs.plugin.explorer.api;

import android.os.Bundle;

/** A caller-bound, read-only view of one archive. Added in Explorer Action protocol v5. */
interface IExplorerArchiveSession {

    Bundle getInfo();

    Bundle listChildren(String parentId, int offset, int limit);

    void close();
}
