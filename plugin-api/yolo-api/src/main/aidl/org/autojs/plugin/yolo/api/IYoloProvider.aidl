package org.autojs.plugin.yolo.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.yolo.api.IYoloCallback;
import org.autojs.plugin.yolo.api.IYoloSession;

interface IYoloProvider {

    byte[] getProviderInfo();

    byte[] getCapabilities();

    IYoloSession openSession(
        in byte[] request,
        in ParcelFileDescriptor[] modelDescriptors,
        IYoloCallback callback
    );
}
