package org.autojs.plugin.ai.text.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.ai.text.api.IAiModelListCallback;
import org.autojs.plugin.ai.text.api.IAiTextCallback;
import org.autojs.plugin.ai.text.api.IAiTextSession;

interface IAiTextProvider {

    byte[] getProviderInfo();

    byte[] getCapabilities();

    oneway void listModels(in byte[] request, IAiModelListCallback callback);

    IAiTextSession openSession(
        in byte[] request,
        in ParcelFileDescriptor[] descriptors,
        IAiTextCallback callback
    );
}
