package org.autojs.plugin.bun.runtime.api;

import android.os.Bundle;
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback;

/**
 * Host-owned capability proxy handed over in the runScript request (BunRuntimeContract.KEY_HOST_CAPABILITY_BROKER,
 * bridge version 1). Every script call becomes one invoke; the host checks the caller UID and execution ID,
 * applies the grant and the capability quotas, and answers exactly once through the callback.
 */
oneway interface IBunHostCapabilityBroker {

    void invoke(in Bundle request, IBunHostCapabilityCallback callback);

}
