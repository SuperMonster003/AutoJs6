package org.autojs.plugin.bun.runtime.api;

import android.os.Bundle;

oneway interface IBunRuntimeCallback {

    void onEvent(in Bundle event);

}
