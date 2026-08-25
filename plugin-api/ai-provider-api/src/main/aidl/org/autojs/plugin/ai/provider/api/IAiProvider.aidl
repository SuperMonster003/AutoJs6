package org.autojs.plugin.ai.provider.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.ai.provider.api.IAiTargetListCallback;
import org.autojs.plugin.ai.provider.api.IAiCallback;
import org.autojs.plugin.ai.provider.api.IAiSession;

interface IAiProvider {

    byte[] getProviderInfo();

    byte[] getCapabilities();

    oneway void listTargets(in byte[] request, IAiTargetListCallback callback);

    IAiSession openSession(
        in byte[] request,
        in ParcelFileDescriptor[] descriptors,
        IAiCallback callback
    );
}
