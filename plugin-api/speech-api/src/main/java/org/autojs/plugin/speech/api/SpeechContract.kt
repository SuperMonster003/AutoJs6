package org.autojs.plugin.speech.api

/**
 * Frozen vocabulary of the speech contract (version 1, 3-Macaw Speech roadmap D21): the version, the Bundle keys every
 * request and answer share, the closed value sets (events, end reasons, session states, audio sources, model kinds and
 * sources, features, formats) and the host-side call timeouts. The request, result and model keys live in
 * [SpeechOptionKeys], [SpeechResultKeys] and [SpeechModelKeys], the ceilings in [SpeechLimits] and the error codes in
 * [SpeechErrorCodes]. The operations are described in `docs/dev/speech-plugin-protocol-v1.md`.
 *
 * The host writes its [CONTRACT_VERSION] into every request; a plugin answers requests whose version it supports and
 * reports [SpeechErrorCodes.PLUGIN_INCOMPATIBLE] otherwise. Later versions may only append keys, values and AIDL
 * transactions (`SpeechAidlOrderTest`).
 *
 * zh-CN: 语音契约 (版本 1, 3-Macaw Speech 路线图 D21) 的冻结词汇: 版本号, 每个请求与应答共用的 Bundle 键, 闭合取值集合 (事件, 结束原因,
 * 会话状态, 音频来源, 模型种类与来源, 功能, 格式) 与宿主侧调用超时. 请求, 结果与模型键见 [SpeechOptionKeys], [SpeechResultKeys] 与
 * [SpeechModelKeys], 上限见 [SpeechLimits], 错误码见 [SpeechErrorCodes]. 操作说明见 `docs/dev/speech-plugin-protocol-v1.md`. 宿主在每个请求
 * 中写入 [CONTRACT_VERSION]; 插件应答其支持的版本, 否则报告 [SpeechErrorCodes.PLUGIN_INCOMPATIBLE]. 后续版本只能追加键, 取值与 AIDL 事务.
 */
object SpeechContract {

    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

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
    val KINDS: Set<String> = setOf(KIND_ASR, KIND_TTS, KIND_KWS, KIND_VAD)

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
    val FEATURES: Set<String> = setOf(FEATURE_ASR, FEATURE_ASR_STREAMING, FEATURE_VAD, FEATURE_KWS, FEATURE_TTS, FEATURE_TTS_SYSTEM_ENGINE, FEATURE_MODELS)

    // Output formats ([SpeechOptionKeys.FORMAT]).

    const val FORMAT_WAV = "wav"
    val FORMATS: Set<String> = setOf(FORMAT_WAV)

    // Host-side call timeouts.

    /** `getInfo`, `getCapabilities`, `listModels`, `removeModel`, `setDefaultModel`, opening a session and the session calls. */
    const val CALL_TIMEOUT_MS = 30_000L

    /** One `recognizeFile` or `detectVoice` call (a 30 minute file on a slow device). */
    const val FILE_CALL_TIMEOUT_MS = 600_000L

    /** From `synthesizeToFile` to its first progress, between two progress callbacks and from the last one to the completion; the whole synthesis has no limit. */
    const val SYNTHESIS_TIMEOUT_MS = 600_000L

    /** From `refreshCatalog` to its completion callback. */
    const val CATALOG_TIMEOUT_MS = 120_000L
}
