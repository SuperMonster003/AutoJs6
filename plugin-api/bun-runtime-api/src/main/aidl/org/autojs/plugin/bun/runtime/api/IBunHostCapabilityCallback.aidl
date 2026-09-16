package org.autojs.plugin.bun.runtime.api;

import android.os.Bundle;

/** Answers exactly one broker call; the result Bundle uses BunRuntimeContract.KEY_HOST_CALL_* and the shared result keys. */
oneway interface IBunHostCapabilityCallback {

    void onResult(in Bundle result);

}
