package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/** Host-owned, UID-bound access to a bounded set of same-directory archive volumes. */
interface IExplorerArchiveVolumeSource {

    /** Returns the immutable candidate set after revalidating every volume identity. */
    Bundle getCatalog();

    /** Opens one catalog member by opaque ID after another identity and caller check. */
    ParcelFileDescriptor openVolume(String volumeId);
}
