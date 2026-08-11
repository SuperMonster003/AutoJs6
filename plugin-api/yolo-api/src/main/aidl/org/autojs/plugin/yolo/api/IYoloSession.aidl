package org.autojs.plugin.yolo.api;

import android.os.ParcelFileDescriptor;

oneway interface IYoloSession {

    void detect(in byte[] request, in ParcelFileDescriptor imageFd);

    void cancel(in byte[] request);

    void close();
}
