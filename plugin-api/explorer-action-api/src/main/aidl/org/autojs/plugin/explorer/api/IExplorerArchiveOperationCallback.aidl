package org.autojs.plugin.explorer.api;

import android.os.Bundle;

/** Host callback for one archive extraction operation (v10, with typed interactions in v11). */
oneway interface IExplorerArchiveOperationCallback {

    void onProgress(in Bundle update);

    void onCompleted(in Bundle result);

    void onFailed(in Bundle failure);
}
