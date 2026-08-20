package org.autojs.plugin.ondeviceai.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.ondeviceai.api.IAiModelListCallback;
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiCallback;
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiSession;

interface IOnDeviceAiProvider {

    byte[] getProviderInfo();

    byte[] getCapabilities();

    oneway void listModels(in byte[] request, IAiModelListCallback callback);

    IOnDeviceAiSession openSession(
        in byte[] request,
        in ParcelFileDescriptor[] descriptors,
        IOnDeviceAiCallback callback
    );
}
