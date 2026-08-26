package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import org.autojs.plugin.explorer.api.IExplorerArchiveSession;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IExplorerActionPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    Bundle getActionCatalog();

    // Legacy v5-v10 entry point. Its signature and transaction number remain unchanged.
    IExplorerArchiveSession openArchive(in ParcelFileDescriptor source, in Bundle request);

    // v11 structured result: session Binder on success, or a bounded ExplorerArchiveOpenValues
    // error. The transient request password must be erased by both peers after this call returns.
    Bundle openArchiveV11(in ParcelFileDescriptor source, in Bundle request);
}
