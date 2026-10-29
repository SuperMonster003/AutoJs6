package org.autojs.plugin.speech.api

/**
 * Frozen vocabulary of the speech contract (versions 1 and 2, 3-Macaw Speech roadmap D21, D132, D136): the version, the Bundle keys every
 * request and answer share, the closed value sets (events, end reasons, session states, audio sources, model kinds and
 * sources, features, formats) and the host-side call timeouts. The request, result and model keys live in
 * [SpeechOptionKeys], [SpeechResultKeys] and [SpeechModelKeys], the ceilings in [SpeechLimits] and the error codes in
 * [SpeechErrorCodes]. The operations are described in `docs/dev/speech-plugin-protocol-v1.md`.
 *
 * Every request carries the version of the call it belongs to ([CALL_VERSION_1] for the 14 methods of version 1,
 * [CALL_VERSION_2] for the methods appended by version 2), never more than the version agreed with the plugin
 * ([negotiate]); a plugin answers requests whose version it supports, stamps its answer with the request's version, and
 * reports [SpeechErrorCodes.PLUGIN_INCOMPATIBLE] otherwise. A plugin keeps advertising `speechContractVersion` 1, which
 * every host since build 5456 accepts, and advertises the highest version it implements as
 * [SpeechCapabilityKeys.MAX_CONTRACT_VERSION] (roadmap D136). Later versions may only append keys, values and AIDL
 * transactions (`SpeechAidlOrderTest`).
 *
 * zh-CN: 语音契约 (版本 1 与 2, 3-Macaw Speech 路线图 D21, D132, D136) 的冻结词汇: 版本号, 每个请求与应答共用的 Bundle 键, 闭合取值集合
 * (事件, 结束原因, 会话状态, 音频来源, 模型种类与来源, 功能, 格式) 与宿主侧调用超时. 请求, 结果与模型键见 [SpeechOptionKeys],
 * [SpeechResultKeys] 与 [SpeechModelKeys], 上限见 [SpeechLimits], 错误码见 [SpeechErrorCodes]. 操作说明见
 * `docs/dev/speech-plugin-protocol-v1.md`. 每个请求写入其所属调用的版本 (版本 1 的 14 个方法写 [CALL_VERSION_1], 版本 2 追加的方法写
 * [CALL_VERSION_2]), 且不超过与插件商定的版本 ([negotiate]); 插件应答其支持的版本并以请求的版本标记应答, 否则报告
 * [SpeechErrorCodes.PLUGIN_INCOMPATIBLE]. 插件继续声明 `speechContractVersion` 1 (5456 起的宿主都接受), 并以
 * [SpeechCapabilityKeys.MAX_CONTRACT_VERSION] 声明其实现的最高版本 (路线图 D136). 后续版本只能追加键, 取值与 AIDL 事务.
 */
object SpeechContract {

    /** Highest version this build of the contract defines. */
    const val CONTRACT_VERSION = 2
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 2

    /** Version stamped on the requests of the 14 methods of version 1 (and their answers). */
    const val CALL_VERSION_1 = 1

    /** Version stamped on the requests of the methods version 2 appends (speakers, roadmap P8.4 analyses). */
    const val CALL_VERSION_2 = 2

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    /**
     * The version agreed with a plugin that advertises [baseVersion] as `speechContractVersion` and [maxVersion] as
     * [SpeechCapabilityKeys.MAX_CONTRACT_VERSION] (null when absent, a version 1 plugin): the highest version both
     * sides implement, or 0 when the base version is not supported.
     */
    fun negotiate(baseVersion: Int, maxVersion: Int?): Int {
        if (!supportsContractVersion(baseVersion)) return 0
        return minOf(CONTRACT_VERSION, maxOf(baseVersion, maxVersion ?: baseVersion))
    }

    // Bundle keys every request and answer share.

    /** Int: contract version of the writer (every request and answer). */
    const val KEY_CONTRACT_VERSION = "contractVersion"

    /** String: a [SpeechErrorCodes] code; present in a Bundle answer or an error event only on failure. */
    const val KEY_ERROR_CODE = "errorCode"

    /** String: human-readable detail of [KEY_ERROR_CODE] (at most [SpeechLimits.MAX_ERROR_MESSAGE_BYTES]). */
    const val KEY_ERROR_MESSAGE = "errorMessage"

    // Session events (ISpeechSessionCallback / ISpeechWakeCallback); the script sees them under the same names.

    const val EVENT_READY = "ready"
    const val EVENT_PARTIAL = "partial"
    const val EVENT_FINAL = "final"
    const val EVENT_ENDPOINT = "endpoint"
    const val EVENT_VAD = "vad"
    const val EVENT_WAKE = "wake"
    const val EVENT_WARNING = "warning"
    const val EVENT_ERROR = "error"
    const val EVENT_END = "end"
    val EVENTS: Set<String> = setOf(EVENT_READY, EVENT_PARTIAL, EVENT_FINAL, EVENT_ENDPOINT, EVENT_VAD, EVENT_WAKE, EVENT_WARNING, EVENT_ERROR, EVENT_END)

    // End reasons ([SpeechResultKeys.REASON] of the end event).

    /** `finish()` flushed the session, or a dictation round of a wake session ended on its endpoint. */
    const val REASON_STOPPED = "stopped"

    /** No speech arrived within `timeoutMillis`. */
    const val REASON_TIMEOUT = "timeout"

    /** `maxDurationMillis` elapsed. */
    const val REASON_MAX_DURATION = "maxDuration"

    /** `cancel()` discarded the session. */
    const val REASON_CANCELLED = "cancelled"

    /** The session ended after its error event. */
    const val REASON_ERROR = "error"
    val REASONS: Set<String> = setOf(REASON_STOPPED, REASON_TIMEOUT, REASON_MAX_DURATION, REASON_CANCELLED, REASON_ERROR)

    // Session states ([SpeechResultKeys.STATE] of `snapshot`).

    /** The model is loading; no audio is consumed yet. */
    const val STATE_PENDING = "pending"

    /** Audio is consumed (microphone or `feed`). */
    const val STATE_ACTIVE = "active"

    /** The end event was sent. */
    const val STATE_ENDED = "ended"
    val STATES: Set<String> = setOf(STATE_PENDING, STATE_ACTIVE, STATE_ENDED)

    // Audio sources ([SpeechOptionKeys.SOURCE]).

    /** The plugin records the microphone itself (roadmap D6). */
    const val SOURCE_MIC = "mic"

    /** The host feeds 16 kHz mono PCM16 LE through `ISpeechSession.feed`. */
    const val SOURCE_EXTERNAL = "external"
    val SOURCES: Set<String> = setOf(SOURCE_MIC, SOURCE_EXTERNAL)

    // Model kinds ([SpeechModelKeys.KIND]).

    const val KIND_ASR = "asr"
    const val KIND_TTS = "tts"
    const val KIND_KWS = "kws"
    const val KIND_VAD = "vad"

    /** Speaker embedding models (version 2, roadmap D125). */
    const val KIND_SPEAKER = "speaker"

    /** Speaker segmentation models of diarization (version 2, roadmap D126). */
    const val KIND_SEGMENT = "segment"

    /** Spoken language identification models (version 2, roadmap D126). */
    const val KIND_LANGID = "langid"

    /** Audio event tagging models (version 2, roadmap D126). */
    const val KIND_TAGGER = "tagger"
    val KINDS: Set<String> = setOf(KIND_ASR, KIND_TTS, KIND_KWS, KIND_VAD, KIND_SPEAKER, KIND_SEGMENT, KIND_LANGID, KIND_TAGGER)

    // Model sources ([SpeechModelKeys.SOURCE]).

    /** Listed in the signed catalog (downloaded or not). */
    const val MODEL_SOURCE_CATALOG = "catalog"

    /** Imported by the user from a local archive or directory. */
    const val MODEL_SOURCE_IMPORTED = "imported"
    val MODEL_SOURCES: Set<String> = setOf(MODEL_SOURCE_CATALOG, MODEL_SOURCE_IMPORTED)

    // Features ([SpeechCapabilityKeys.FEATURES], roadmap D28).

    /** File and external PCM recognition (`recognizeFile`, `openSession` with [SOURCE_EXTERNAL]). */
    const val FEATURE_ASR = "asr"

    /** Microphone dictation (`openSession` with [SOURCE_MIC]). */
    const val FEATURE_ASR_STREAMING = "asrStreaming"

    /** `detectVoice` and VAD gating. */
    const val FEATURE_VAD = "vad"

    /** Wake sessions (`openWakeSession`). */
    const val FEATURE_KWS = "kws"

    /** `synthesizeToFile`. */
    const val FEATURE_TTS = "tts"

    /** The system TTS engine of [SpeechCapabilityKeys.TTS_ENGINE_PACKAGE]. */
    const val FEATURE_TTS_SYSTEM_ENGINE = "ttsSystemEngine"

    /** Model management beyond listing: `installModel`, `importModel`, `removeModel`, `setDefaultModel`, `refreshCatalog`. */
    const val FEATURE_MODELS = "models"

    /**
     * Audio files beyond WAV for `recognizeFile` / `detectVoice` (MP3, AAC, Ogg, FLAC and the audio track of a video file;
     * 3-Macaw Speech roadmap D84): the host hands a non-WAV file only to a plugin that declares it.
     */
    const val FEATURE_AUDIO_DECODE = "audioDecode"

    /** Version 2: `enrollSpeaker`, `verifySpeaker`, `identifySpeaker`, `listSpeakers`, `removeSpeaker` (roadmap D125, D132). */
    const val FEATURE_SPEAKER = "speaker"

    /** Version 2: `diarizeFile` (roadmap D126). */
    const val FEATURE_DIARIZATION = "diarization"

    /** Version 2: `detectLanguage` (roadmap D126). */
    const val FEATURE_LANGUAGE_ID = "languageId"

    /** Version 2: `tagAudio` (roadmap D126). */
    const val FEATURE_AUDIO_TAGGING = "audioTagging"

    /** Features of version 1; a plugin that agreed on version 1 offers only these. */
    val FEATURES_V1: Set<String> = setOf(FEATURE_ASR, FEATURE_ASR_STREAMING, FEATURE_VAD, FEATURE_KWS, FEATURE_TTS, FEATURE_TTS_SYSTEM_ENGINE, FEATURE_MODELS, FEATURE_AUDIO_DECODE)
    val FEATURES: Set<String> = FEATURES_V1 + setOf(FEATURE_SPEAKER, FEATURE_DIARIZATION, FEATURE_LANGUAGE_ID, FEATURE_AUDIO_TAGGING)

    // Output formats ([SpeechOptionKeys.FORMAT]).

    const val FORMAT_WAV = "wav"
    val FORMATS: Set<String> = setOf(FORMAT_WAV)

    // Host-side call timeouts.

    /** `getInfo`, `getCapabilities`, `listModels`, `removeModel`, `setDefaultModel`, opening a session and the session calls. */
    const val CALL_TIMEOUT_MS = 30_000L

    /** One `recognizeFile`, `detectVoice` or speaker call (a 30 minute file on a slow device). */
    const val FILE_CALL_TIMEOUT_MS = 600_000L

    /** From `synthesizeToFile` to its first progress, between two progress callbacks and from the last one to the completion; the whole synthesis has no limit. */
    const val SYNTHESIS_TIMEOUT_MS = 600_000L

    /** From `refreshCatalog` to its completion callback. */
    const val CATALOG_TIMEOUT_MS = 120_000L

    /**
     * From `diarizeFile`, `detectLanguage` or `tagAudio` to its first progress, between two progress callbacks and from
     * the last one to the completion; the whole analysis has no limit (roadmap D150).
     */
    const val ANALYSIS_TIMEOUT_MS = 600_000L
}
