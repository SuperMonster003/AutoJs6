package org.autojs.plugin.speech.api

/**
 * Keys of the answer, event, progress and snapshot Bundles of [ISpeechPlugin] (roadmap appendix B.3). A failed call
 * carries [SpeechContract.KEY_ERROR_CODE] and [SpeechContract.KEY_ERROR_MESSAGE] only; a successful answer carries the
 * keys of its operation and never an error code.
 *
 * zh-CN: [ISpeechPlugin] 应答, 事件, 进度与快照 Bundle 的键 (路线图附录 B.3). 失败的调用只带 [SpeechContract.KEY_ERROR_CODE] 与
 * [SpeechContract.KEY_ERROR_MESSAGE]; 成功的应答带其操作的键, 且从不带错误码.
 */
object SpeechResultKeys {

    // Recognition results (`recognizeFile`, `partial` / `final` events).

    /** String: recognized text (a final result or the text so far). */
    const val TEXT = "text"

    /** ArrayList of Bundle: the utterances of a file, each keyed by [START_MILLIS], [END_MILLIS], [TEXT], [LANGUAGE] and [TAGS]. */
    const val SEGMENTS = "segments"

    /** Int: index of the utterance an event belongs to (0 based, per session). */
    const val SEGMENT_INDEX = "segmentIndex"

    /** Long: start of a segment in milliseconds from the beginning of the audio. */
    const val START_MILLIS = "startMillis"

    /** Long: end of a segment in milliseconds. */
    const val END_MILLIS = "endMillis"

    /** String: language the model reported for a result or segment. */
    const val LANGUAGE = "language"

    /** Bundle, optional: model tags of a segment ([TAG_EMOTION], [TAG_EVENT]; SenseVoice). */
    const val TAGS = "tags"
    const val TAG_EMOTION = "emotion"
    const val TAG_EVENT = "event"

    /** Long: duration of the processed audio in milliseconds. */
    const val DURATION_MILLIS = "durationMillis"

    /** String: id of the model that produced a result or serves a session. */
    const val MODEL_ID = "modelId"

    /** ArrayList of Bundle: warnings keyed by [SpeechContract.KEY_ERROR_CODE] and [SpeechContract.KEY_ERROR_MESSAGE]. */
    const val WARNINGS = "warnings"

    // Voice activity (`detectVoice`, `vad` events).

    /** Boolean: whether speech is active after a `vad` event. */
    const val ACTIVE = "active"

    // Wake hits (`wake` events).

    /** String: the keyword that fired, as the script wrote it. */
    const val KEYWORD = "keyword"

    /** Float: score of the hit. */
    const val SCORE = "score"

    /** Long: epoch milliseconds of the hit. */
    const val TIMESTAMP_MILLIS = "timestampMillis"

    // Session life cycle (`ready`, `end` events and `snapshot`).

    /** String: plugin-side session id (`ready` event, `snapshot`). */
    const val SESSION_ID = "sessionId"

    /** String: one of [SpeechContract.STATES] (`snapshot`). */
    const val STATE = "state"

    /** String: one of [SpeechContract.REASONS] (`end` event). */
    const val REASON = "reason"

    /** Int: index of the dictation round of a wake session (`ready` and `end` events of the listen callback). */
    const val ROUND = "round"

    // Synthesis (`synthesizeToFile` completion).

    /** Int: sample rate of the written WAV. */
    const val SAMPLE_RATE = "sampleRate"

    /** String: speaker that was used. */
    const val SPEAKER_ID = "speakerId"

    /** Long: bytes written. */
    const val SIZE_BYTES = "sizeBytes"

    // Model management answers.

    /** ArrayList of Bundle: models keyed by [SpeechModelKeys] (`listModels`). */
    const val MODELS = "models"

    /** Bundle: one model keyed by [SpeechModelKeys] (`installModel`, `importModel`, `setDefaultModel` completions). */
    const val MODEL = "model"

    /** Boolean: whether `removeModel` deleted anything. */
    const val REMOVED = "removed"

    /** Int: catalog version after `refreshCatalog`. */
    const val CATALOG_VERSION = "catalogVersion"

    /** Boolean: whether `refreshCatalog` installed a newer catalog. */
    const val UPDATED = "updated"
}
