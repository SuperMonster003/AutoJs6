package org.autojs.plugin.ai.provider.api;

import android.os.ParcelFileDescriptor;

oneway interface IAiSession {

    void generateNext(in byte[] request, in ParcelFileDescriptor[] descriptors);

    void grantCredits(int count);

    void submitToolResults(in byte[] results, in ParcelFileDescriptor[] descriptors);

    void cancel();

    void close();
}
