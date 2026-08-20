package org.autojs.plugin.ondeviceai.api;

import android.os.ParcelFileDescriptor;

oneway interface IOnDeviceAiSession {

    void grantCredits(int count);

    void submitToolResults(in byte[] results, in ParcelFileDescriptor[] descriptors);

    void cancel();

    void close();
}
