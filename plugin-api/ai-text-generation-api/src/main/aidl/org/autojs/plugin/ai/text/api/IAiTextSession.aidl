package org.autojs.plugin.ai.text.api;

import android.os.ParcelFileDescriptor;

oneway interface IAiTextSession {

    void grantCredits(int count);

    void submitToolResults(in byte[] results, in ParcelFileDescriptor[] descriptors);

    void cancel();

    void close();
}
