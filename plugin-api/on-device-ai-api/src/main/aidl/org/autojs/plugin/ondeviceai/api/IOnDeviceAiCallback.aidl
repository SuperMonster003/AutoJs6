package org.autojs.plugin.ondeviceai.api;

import android.os.ParcelFileDescriptor;

oneway interface IOnDeviceAiCallback {

    void onStarted(in byte[] metadata);

    void onChunk(in byte[] chunk);

    void onToolCalls(in byte[] calls, in ParcelFileDescriptor[] descriptors);

    void onUsage(in byte[] usage);

    void onCompleted(in byte[] result, in ParcelFileDescriptor[] descriptors);

    void onFailed(in byte[] error);

    void onCancelled();
}
