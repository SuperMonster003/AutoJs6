package org.autojs.plugin.quickjs.runtime.api;

import android.os.Bundle;

oneway interface IQuickJsRuntimeCallback {

    void onEvent(in Bundle event);

}
