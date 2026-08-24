package org.autojs.plugin.ai.provider.api;

import android.os.ParcelFileDescriptor;

oneway interface IAiCallback {

    void onStarted(in byte[] metadata);

    void onChunk(in byte[] chunk);

    void onToolCalls(in byte[] calls, in ParcelFileDescriptor[] descriptors);

    void onUsage(in byte[] usage);

    void onCompleted(in byte[] result, in ParcelFileDescriptor[] descriptors);

    void onFailed(in byte[] error);

    void onCancelled();
}
