package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import org.autojs.plugin.explorer.api.IExplorerArchiveSession;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IExplorerActionPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    Bundle getActionCatalog();

    IExplorerArchiveSession openArchive(in ParcelFileDescriptor source, in Bundle request);
}
