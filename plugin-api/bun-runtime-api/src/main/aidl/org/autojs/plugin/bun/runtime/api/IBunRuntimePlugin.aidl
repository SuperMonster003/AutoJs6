package org.autojs.plugin.bun.runtime.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.bun.runtime.api.IBunRuntimeCallback;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IBunRuntimePlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    Bundle getRuntimeInfo();

    Bundle runScript(in Bundle request, in ParcelFileDescriptor source, IBunRuntimeCallback callback);

    boolean cancelScript(String executionId);

    Bundle prewarmRuntime();

}
