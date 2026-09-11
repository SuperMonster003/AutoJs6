package org.autojs.plugin.quickjs.runtime.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.quickjs.runtime.api.IQuickJsRuntimeCallback;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IQuickJsRuntimePlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    Bundle getRuntimeInfo();

    Bundle runScript(in Bundle request, in ParcelFileDescriptor source, IQuickJsRuntimeCallback callback);

    boolean cancelScript(String executionId);

    Bundle prewarmRuntime();

}
