package org.autojs.plugin.ai.provider.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.ai.provider.api.IAiModelListCallback;
import org.autojs.plugin.ai.provider.api.IAiCallback;
import org.autojs.plugin.ai.provider.api.IAiSession;

interface IAiProvider {

    byte[] getProviderInfo();

    byte[] getCapabilities();

    oneway void listModels(in byte[] request, IAiModelListCallback callback);

    IAiSession openSession(
        in byte[] request,
        in ParcelFileDescriptor[] descriptors,
        IAiCallback callback
    );
}
