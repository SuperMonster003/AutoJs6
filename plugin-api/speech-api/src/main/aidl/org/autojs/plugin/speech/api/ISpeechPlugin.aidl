package org.autojs.plugin.speech.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.speech.api.ISpeechProgressCallback;
import org.autojs.plugin.speech.api.ISpeechSession;
import org.autojs.plugin.speech.api.ISpeechSessionCallback;
import org.autojs.plugin.speech.api.ISpeechWakeCallback;

/**
 * Entry point of the speech plugin (contract versions 1 and 2, docs/dev/speech-plugin-protocol-v1.md). Transaction codes
 * follow the declaration order, so methods may only ever be appended (SpeechAidlOrderTest). The first 14 methods are
 * contract version 1; the host calls the methods of version 2 only when the plugin's SpeechCapabilityKeys
 * MAX_CONTRACT_VERSION is at least 2 and its features list the method's feature (3-Macaw Speech roadmap D132, D136).
 *
 * Audio never crosses Binder as a stream: the plugin records the microphone itself (roadmap D6) and files travel as
 * read-only descriptors the host opened for the script's own paths. Methods that answer with a Bundle report failures
 * through SpeechContract.KEY_ERROR_CODE; methods that return a session or an operation id throw
 * IllegalArgumentException or IllegalStateException whose message starts with a SpeechErrorCodes code followed by
 * ": " (SpeechErrorCodes.encode). The Binder main path never loads a model: opening a session returns at once and the
 * ready or error event follows (roadmap D30).
 */
interface ISpeechPlugin {
    /** Identity, version and capabilities of the plugin; never null. */
    PluginInfo getInfo();

    /** Live capability bundle keyed by SpeechCapabilityKeys; may be null when the plugin has nothing to add. */
    Bundle getCapabilities();

    /**
     * Models of the bundled catalog plus the imported ones, filtered by SpeechOptionKeys.KIND, LANGUAGE and INSTALLED
     * of the query; the answer carries SpeechResultKeys.MODELS (at most SpeechLimits.MAX_MODELS entries).
     */
    Bundle listModels(in Bundle query);

    /**
     * Downloads and installs the catalog model SpeechOptionKeys.MODEL (roadmap D23, D24); a non-commercial model needs
     * SpeechOptionKeys.ACCEPT_LICENSE. Returns the operation id reported to the callback, whose completion carries
     * SpeechResultKeys.MODEL.
     */
    int installModel(in Bundle request, ISpeechProgressCallback callback);

    /** Cancels a running operation; its callback receives onFailed with SpeechErrorCodes.SESSION_CANCELLED. Unknown ids are ignored. */
    void cancelOperation(int opId);

    /** Deletes the installed model SpeechOptionKeys.MODEL and its unreferenced files; the answer carries SpeechResultKeys.REMOVED. */
    Bundle removeModel(in Bundle request);

    /**
     * Recognizes one WAV file (roadmap D15): a read-only descriptor of a regular file, owned and closed by the host
     * after the call returns (the plugin duplicates it when needed), at most SpeechLimits.MAX_AUDIO_FILE_BYTES and
     * MAX_AUDIO_MILLIS. Options: MODEL, LANGUAGE, HOTWORDS, PUNCTUATION, VAD and DISPLAY_NAME. The answer carries
     * SpeechResultKeys.TEXT, SEGMENTS, LANGUAGE, DURATION_MILLIS, MODEL_ID and WARNINGS.
     */
    Bundle recognizeFile(in ParcelFileDescriptor audio, in Bundle options);

    /**
     * Splits one WAV file into speech segments with the bundled VAD model. Options: MODEL, VAD_THRESHOLD,
     * MIN_SPEECH_MILLIS, MIN_SILENCE_MILLIS and DISPLAY_NAME. The answer carries SpeechResultKeys.SEGMENTS (START_MILLIS
     * and END_MILLIS only) and DURATION_MILLIS.
     */
    Bundle detectVoice(in ParcelFileDescriptor audio, in Bundle options);

    /**
     * Opens a dictation session (roadmap D16, D17): microphone (SpeechContract.SOURCE_MIC) or external PCM
     * (SOURCE_EXTERNAL, fed through ISpeechSession.feed). Options: MODEL, LANGUAGE, SOURCE, SAMPLE_RATE, VAD,
     * SILENCE_END_MILLIS, MAX_DURATION_MILLIS, TIMEOUT_MILLIS, HOTWORDS and PUNCTUATION. Events arrive on the callback
     * in order: ready, then partial / final / endpoint / vad / warning, and exactly one end (an error event precedes
     * end with REASON_ERROR). At most SpeechLimits.MAX_MIC_SESSIONS_PER_TOKEN microphone and
     * MAX_PCM_SESSIONS_PER_TOKEN external sessions per host Binder token (SESSION_BUSY otherwise).
     */
    ISpeechSession openSession(in Bundle options, ISpeechSessionCallback callback);

    /**
     * Opens a wake session (roadmap D18): SpeechOptionKeys.KEYWORDS (at most SpeechLimits.MAX_KEYWORDS), MODEL,
     * MIN_INTERVAL_MILLIS and the optional THEN_LISTEN dictation options. Hits arrive as wake events on the wake
     * callback; with THEN_LISTEN every hit is followed by one dictation round whose ready / partial / final /
     * endpoint / vad / warning / end events (SpeechResultKeys.ROUND set) arrive on the listen callback, after which
     * keyword spotting resumes. The listen callback may be null without THEN_LISTEN. A wake session counts as the
     * microphone session of its token.
     */
    ISpeechSession openWakeSession(in Bundle options, ISpeechWakeCallback callback, ISpeechSessionCallback listenCallback);

    /**
     * Synthesizes SpeechOptionKeys.TEXT into the read-write descriptor of an empty regular file the host staged
     * (roadmap D20): WAV PCM16 at the model's sample rate, at most SpeechLimits.MAX_SYNTH_OUTPUT_BYTES. Options:
     * MODEL, SPEAKER, SPEED and FORMAT. Returns the operation id; the completion carries SpeechResultKeys.SAMPLE_RATE,
     * DURATION_MILLIS, MODEL_ID, SPEAKER_ID and SIZE_BYTES, progress reports characters done over characters total.
     */
    int synthesizeToFile(in Bundle request, in ParcelFileDescriptor output, ISpeechProgressCallback callback);

    /**
     * Imports a model from a read-only descriptor of a local archive (tar.bz2 or zip, roadmap D23): a catalog model
     * is recognized by its files, any other needs SpeechOptionKeys.KIND, MODEL_TYPE, NAME and LANGUAGES and gets
     * SpeechOptionKeys.MODEL as its id. Returns the operation id; the completion carries SpeechResultKeys.MODEL.
     */
    int importModel(in ParcelFileDescriptor archive, in Bundle request, ISpeechProgressCallback callback);

    /** Makes the installed model SpeechOptionKeys.MODEL the default of SpeechOptionKeys.KIND; the answer carries SpeechResultKeys.MODEL. */
    Bundle setDefaultModel(in Bundle request);

    /**
     * Refreshes the signed catalog from the shared assets repository (roadmap D24). Returns the operation id; the
     * completion carries SpeechResultKeys.CATALOG_VERSION and UPDATED.
     */
    int refreshCatalog(ISpeechProgressCallback callback);

    // Contract version 2 (3-Macaw Speech roadmap D125, D132): speaker recognition, feature SpeechContract.FEATURE_SPEAKER.
    // Every audio descriptor is read-only, owned and closed by the host after the call returns, at most
    // SpeechLimits.MAX_AUDIO_FILE_BYTES and MAX_SPEAKER_AUDIO_MILLIS; the plugin keeps the voiceprints in its private
    // storage and never returns an embedding.

    /**
     * Enrolls SpeechOptionKeys.NAME from 1 to SpeechLimits.MAX_ENROLL_FILES recordings, all or nothing: the mean of
     * their embeddings joins the speaker's existing samples, or replaces them with SpeechOptionKeys.REPLACE. Options:
     * NAME, REPLACE, MODEL (default: the default speaker model) and DISPLAY_NAMES. The answer carries
     * SpeechResultKeys.NAME, SAMPLES, DURATION_MILLIS and MODEL_ID.
     */
    Bundle enrollSpeaker(in ParcelFileDescriptor[] audio, in Bundle options);

    /**
     * Compares one recording with the enrolled SpeechOptionKeys.NAME (SpeechErrorCodes.SPEAKER_NOT_FOUND otherwise).
     * Options: NAME, THRESHOLD (default: the plugin's value for the speaker's model) and DISPLAY_NAME. The answer carries
     * SpeechResultKeys.NAME, SCORE, MATCHED and THRESHOLD.
     */
    Bundle verifySpeaker(in ParcelFileDescriptor audio, in Bundle options);

    /**
     * Finds the enrolled speaker closest to one recording. Options: THRESHOLD, TOP_K (default 3, at most
     * SpeechLimits.MAX_IDENTIFY_CANDIDATES) and DISPLAY_NAME. The answer carries SpeechResultKeys.NAME (absent when no
     * score reaches the threshold), SCORE, MATCHED, THRESHOLD and CANDIDATES (NAME and SCORE, best first).
     */
    Bundle identifySpeaker(in ParcelFileDescriptor audio, in Bundle options);

    /** The enrolled speakers, SpeechResultKeys.SPEAKERS (NAME, SAMPLES, UPDATED_AT and MODEL_ID, sorted by name). */
    Bundle listSpeakers(in Bundle query);

    /** Deletes the voiceprint of SpeechOptionKeys.NAME; the answer carries SpeechResultKeys.REMOVED. */
    Bundle removeSpeaker(in Bundle request);

    // Contract version 2 (3-Macaw Speech roadmap D126, D147 - D151): offline analyses of one audio file (at most
    // SpeechLimits.MAX_AUDIO_FILE_BYTES and MAX_AUDIO_MILLIS, the formats of recognizeFile) that return the operation id.
    // Progress reports milliseconds of audio done over the total and comes at least every SpeechContract.ANALYSIS_TIMEOUT_MS;
    // cancelOperation answers SpeechErrorCodes.SESSION_CANCELLED at once; the completion carries the result plus
    // SpeechResultKeys.DURATION_MILLIS and MODEL_ID. Long lists travel as parallel primitive arrays (D151).

    /**
     * Speaker diarization (feature SpeechContract.FEATURE_DIARIZATION). Options: MODEL (segment model), SPEAKER_MODEL,
     * SPEAKER_COUNT (0 or absent: count them), THRESHOLD (clustering, 0.0 - 2.0), IDENTIFY and DISPLAY_NAME. The
     * completion carries SpeechResultKeys.SEGMENT_STARTS, SEGMENT_ENDS, SEGMENT_SPEAKERS (numbered from 0 in order of
     * appearance), SPEAKER_COUNT and, with IDENTIFY, NAMES (SPEAKER, NAME when matched, SCORE).
     */
    int diarizeFile(in ParcelFileDescriptor audio, in Bundle options, ISpeechProgressCallback callback);

    /**
     * Spoken language identification (feature SpeechContract.FEATURE_LANGUAGE_ID): a vote over the first WINDOWS speech
     * windows (default 3). Options: MODEL, WINDOWS and DISPLAY_NAME. The completion carries SpeechResultKeys.LANGUAGE,
     * CANDIDATES (LANGUAGE and SCORE, the share of the votes, best first) and SEGMENTS (START_MILLIS, END_MILLIS and
     * LANGUAGE per window).
     */
    int detectLanguage(in ParcelFileDescriptor audio, in Bundle options, ISpeechProgressCallback callback);

    /**
     * Audio event tagging (feature SpeechContract.FEATURE_AUDIO_TAGGING) over windows of SpeechLimits.TAG_WINDOW_MILLIS.
     * Options: MODEL, TOP_K (default 5, at most 20), TIMELINE and DISPLAY_NAME. The completion carries
     * SpeechResultKeys.LABELS (LABEL, MID, SCORE, START_MILLIS and END_MILLIS of each label's best window, best first)
     * and, with TIMELINE, TIMELINE_STARTS, TIMELINE_ENDS, TIMELINE_LABELS, TIMELINE_SCORES, TIMELINE_TOP_K, LABEL_NAMES
     * and LABEL_MIDS.
     */
    int tagAudio(in ParcelFileDescriptor audio, in Bundle options, ISpeechProgressCallback callback);
}
