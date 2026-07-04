package org.autojs.plugin.nodejs.api;

import android.os.Bundle;

interface INodeJsRuntimeCallback {

    void onEvent(in Bundle event);

}
