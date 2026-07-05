package org.autojs.plugin.mediainfo.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IMediainfoPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    String inform(in ParcelFileDescriptor mediaFd, String displayName);

    String get(in ParcelFileDescriptor mediaFd, String displayName, String streamKind, int streamNumber, String parameter);

    String snapshot(in ParcelFileDescriptor mediaFd, String displayName, in Bundle options);
}
