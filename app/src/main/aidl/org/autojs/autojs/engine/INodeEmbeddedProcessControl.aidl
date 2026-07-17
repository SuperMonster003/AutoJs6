package org.autojs.autojs.engine;

import android.os.Bundle;

interface INodeEmbeddedProcessControl {

    Bundle ensureRuntimeReady(boolean persistentEnabled);

    Bundle getRuntimeStatus();

    void requestShutdown(String reason);
}
