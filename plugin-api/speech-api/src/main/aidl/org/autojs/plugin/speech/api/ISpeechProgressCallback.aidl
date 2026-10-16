package org.autojs.plugin.speech.api;

import android.os.Bundle;

/**
 * Completion channel of the operations that return an operation id (installModel, importModel, synthesizeToFile,
 * refreshCatalog). The plugin reports zero or more onProgress and then exactly one onCompleted or onFailed; a cancelled
 * operation fails with SpeechErrorCodes.SESSION_CANCELLED. Every bundle stays below SpeechLimits.MAX_EVENT_BYTES.
 */
oneway interface ISpeechProgressCallback {
    /** done and total in the unit of the operation (bytes for downloads and imports, characters for synthesis); total may be 0 when unknown. */
    void onProgress(int opId, long done, long total);

    /** The result keys of the operation (SpeechResultKeys.MODEL, the synthesis keys, or CATALOG_VERSION and UPDATED). */
    void onCompleted(int opId, in Bundle result);

    /** SpeechContract.KEY_ERROR_CODE and KEY_ERROR_MESSAGE. */
    void onFailed(int opId, in Bundle error);
}
