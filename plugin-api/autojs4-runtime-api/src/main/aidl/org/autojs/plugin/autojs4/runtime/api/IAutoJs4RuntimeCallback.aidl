package org.autojs.plugin.autojs4.runtime.api;

import android.os.Bundle;

oneway interface IAutoJs4RuntimeCallback {

    void onEvent(in Bundle event);

}
