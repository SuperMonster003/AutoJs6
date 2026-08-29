package org.autojs.plugin.explorer.api;

import android.os.Bundle;

/** Host callback for one asynchronous archive operation (v10 extraction and v19 mutation). */
oneway interface IExplorerArchiveOperationCallback {

    void onProgress(in Bundle update);

    void onCompleted(in Bundle result);

    void onFailed(in Bundle failure);
}
