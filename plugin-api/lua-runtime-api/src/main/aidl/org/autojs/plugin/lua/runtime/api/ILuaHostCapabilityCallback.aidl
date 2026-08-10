package org.autojs.plugin.lua.runtime.api;

import android.os.ParcelFileDescriptor;

oneway interface ILuaHostCapabilityCallback {

    void onCompleted(in byte[] result, in ParcelFileDescriptor[] descriptors);

    void onFailed(in byte[] error);
}
