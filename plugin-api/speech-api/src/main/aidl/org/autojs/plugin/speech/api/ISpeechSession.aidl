package org.autojs.plugin.speech.api;

import android.os.Bundle;

/**
 * One dictation or wake session opened through ISpeechPlugin (contract version 1). The plugin owns the audio source
 * and the model; the host controls the session through these calls and follows it through the callback it registered.
 * Calls after the end event answer or throw SpeechErrorCodes.SESSION_CLOSED. The plugin ends the session on its own
 * when the host process dies (linkToDeath on the callback).
 */
interface ISpeechSession {
    /**
     * Appends 16 kHz mono PCM16 LE audio to an external session (at most SpeechLimits.MAX_FEED_BYTES per call); a
     * microphone session throws SpeechErrorCodes.INVALID_OPTIONS.
     */
    void feed(in byte[] pcm16);

    /**
     * Stops consuming audio, flushes the recognizer (a last final event when text is pending) and ends the session
     * with SpeechContract.REASON_STOPPED. A wake session ends as a whole. Idempotent.
     */
    void finish();

    /** Ends the session at once without a final event, with SpeechContract.REASON_CANCELLED. Idempotent. */
    void cancel();

    /** Whether audio is still consumed (pending or active). */
    boolean isActive();

    /** SpeechResultKeys.SESSION_ID, STATE, MODEL_ID and TEXT (the text accumulated so far). */
    Bundle snapshot();

    /**
     * Wake sessions with THEN_LISTEN only: ends the current dictation round (a last final event unless discard is
     * true, then the round's end event) and resumes keyword spotting. A no-op outside a round.
     */
    void finishRound(boolean discard);
}
