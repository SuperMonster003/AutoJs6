package org.autojs.plugin.lua.runtime.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityCallback;

oneway interface ILuaHostCapabilityBroker {

    void invoke(
        in byte[] request,
        in ParcelFileDescriptor[] descriptors,
        ILuaHostCapabilityCallback callback
    );
}
