package org.autojs.plugin.speech.api;

import android.os.Bundle;

/**
 * Event channel of a dictation session (ISpeechPlugin.openSession) and of the dictation rounds of a wake session
 * (the listen callback of openWakeSession). Every event bundle stays below SpeechLimits.MAX_EVENT_BYTES. The plugin
 * sends onReady first (or onError then onEnd when the model fails to load), the recognition events in audio order and
 * exactly one onEnd last; a oneway Binder delivers them in call order.
 */
oneway interface ISpeechSessionCallback {
    /** SpeechResultKeys.SESSION_ID, MODEL_ID and, for a wake session round, ROUND. */
    void onReady(in Bundle info);

    /** SpeechResultKeys.TEXT and SEGMENT_INDEX: the text of the current utterance so far. */
    void onPartial(in Bundle result);

    /** SpeechResultKeys.TEXT, SEGMENT_INDEX, START_MILLIS, END_MILLIS, LANGUAGE and TAGS of one finished utterance. */
    void onFinal(in Bundle result);

    /** The endpoint detector closed an utterance (streaming models). */
    void onEndpoint();

    /** SpeechResultKeys.ACTIVE: speech started or stopped (microphone sessions with VAD). */
    void onVoiceActivity(in Bundle activity);

    /** SpeechContract.KEY_ERROR_CODE and KEY_ERROR_MESSAGE of a non-fatal condition (ignored hotwords, for instance). */
    void onWarning(in Bundle warning);

    /** SpeechContract.KEY_ERROR_CODE and KEY_ERROR_MESSAGE of the failure that ends the session; onEnd follows. */
    void onError(in Bundle error);

    /** SpeechResultKeys.REASON (one of SpeechContract.REASONS), TEXT (the whole session) and, for a round, ROUND. */
    void onEnd(in Bundle end);
}
