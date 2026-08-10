package org.autojs.plugin.lua.runtime.api;

import android.os.ParcelFileDescriptor;
import org.autojs.plugin.lua.runtime.api.ILuaExecutionCallback;
import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession;
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityBroker;

interface ILuaRuntimeProvider {

    byte[] getRuntimeInfo();

    ILuaExecutionSession createExecution(
        in byte[] request,
        in ParcelFileDescriptor source,
        ILuaExecutionCallback callback,
        ILuaHostCapabilityBroker hostBroker
    );
}
