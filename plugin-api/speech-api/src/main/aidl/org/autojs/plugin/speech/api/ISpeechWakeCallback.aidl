package org.autojs.plugin.speech.api;

import android.os.Bundle;

/**
 * Event channel of a wake session (ISpeechPlugin.openWakeSession). The plugin sends onReady first (or onError then
 * onEnd when the model fails to load), one onWake per hit and exactly one onEnd last; the dictation rounds that follow
 * hits with THEN_LISTEN report on the listen callback instead.
 */
oneway interface ISpeechWakeCallback {
    /** SpeechResultKeys.SESSION_ID and MODEL_ID. */
    void onReady(in Bundle info);

    /** SpeechResultKeys.KEYWORD, SCORE and TIMESTAMP_MILLIS of one hit. */
    void onWake(in Bundle hit);

    /** SpeechContract.KEY_ERROR_CODE and KEY_ERROR_MESSAGE of a non-fatal condition. */
    void onWarning(in Bundle warning);

    /** SpeechContract.KEY_ERROR_CODE and KEY_ERROR_MESSAGE of the failure that ends the session; onEnd follows. */
    void onError(in Bundle error);

    /** SpeechResultKeys.REASON (one of SpeechContract.REASONS). */
    void onEnd(in Bundle end);
}
