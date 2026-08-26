package org.autojs.plugin.explorer.api;

import android.os.Bundle;

/** Host callback for one protocol v10 archive extraction operation. */
oneway interface IExplorerArchiveOperationCallback {

    void onProgress(in Bundle update);

    void onCompleted(in Bundle result);

    void onFailed(in Bundle failure);
}
