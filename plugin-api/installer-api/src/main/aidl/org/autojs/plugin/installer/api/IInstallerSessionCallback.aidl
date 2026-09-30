package org.autojs.plugin.installer.api;

import android.os.Bundle;

/** Progress and result channel of one installation session. The plugin never blocks on a slow host. */
oneway interface IInstallerSessionCallback {
    /**
     * The session entered a stage (InstallerContract.STAGE_*). The detail JSON under
     * InstallerContract.KEY_DETAIL_JSON carries `index` and `packageName` of the batch item.
     */
    void onStage(String sessionId, String stage, in Bundle detail);

    /** Write progress in [0, 1]; the detail JSON carries `index`, `bytesWritten` and `totalBytes`. */
    void onProgress(String sessionId, float progress, in Bundle detail);

    /**
     * Terminal: the result JSON under KEY_RESULT_JSON is one InstallResult document, or
     * `{results: [...]}` for a batch (items that failed carry their own `error`).
     */
    void onCompleted(String sessionId, in Bundle result);

    /** Terminal: the whole session failed; the error JSON under KEY_ERROR_JSON carries `code`, `message`, `status`, `systemMessage`. */
    void onFailed(String sessionId, in Bundle error);
}
