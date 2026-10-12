package org.autojs.plugin.autojs4.runtime.api;

import android.os.Bundle;
import org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimeCallback;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IAutoJs4RuntimePlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    Bundle getRuntimeInfo();

    Bundle runScript(in Bundle request, IAutoJs4RuntimeCallback callback);

    boolean cancelScript(String executionId);

}
