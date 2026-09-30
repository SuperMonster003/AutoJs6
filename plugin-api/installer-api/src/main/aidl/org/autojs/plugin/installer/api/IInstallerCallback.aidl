package org.autojs.plugin.installer.api;

import android.os.Bundle;

/** One-shot result channel of the asynchronous IInstallerPlugin methods; exactly one of the two arrives. */
oneway interface IInstallerCallback {
    /** The result JSON under InstallerContract.KEY_RESULT_JSON. */
    void onResult(in Bundle result);

    /** The error JSON under InstallerContract.KEY_ERROR_JSON (`code`, `message`, `status`, `systemMessage`). */
    void onError(in Bundle error);
}
