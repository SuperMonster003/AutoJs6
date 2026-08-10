package org.autojs.plugin.lua.runtime.api;

import android.os.ParcelFileDescriptor;

oneway interface ILuaExecutionCallback {

    void onStarted(in byte[] metadata);

    void onOutput(in byte[] output);

    void onCompleted(in byte[] result, in ParcelFileDescriptor[] descriptors);

    void onFailed(in byte[] error);

    void onCancelled(in byte[] cancellation);
}
