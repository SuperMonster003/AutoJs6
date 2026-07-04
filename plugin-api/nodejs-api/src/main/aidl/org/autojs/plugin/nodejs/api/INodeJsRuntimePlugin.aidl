package org.autojs.plugin.nodejs.api;

import android.os.Bundle;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;

interface INodeJsRuntimePlugin {

    Bundle getRuntimeInfo();

    Bundle runScript(in Bundle request, INodeJsRuntimeCallback callback);

    boolean cancelScript(String executionId);

    Bundle prewarmRuntime(in Bundle request);

}
