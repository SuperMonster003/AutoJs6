package org.autojs.plugin.speech.api

/**
 * Hard ceilings and defaults both sides enforce (roadmap D15, D16, D18, D20, appendix B.2 and C.1). A request beyond a
 * ceiling is answered with [SpeechErrorCodes.LIMIT_EXCEEDED] instead of being truncated; the host checks them before
 * anything reaches the plugin.
 *
 * zh-CN: 双方都执行的硬上限与默认值 (路线图 D15, D16, D18, D20, 附录 B.2 与 C.1). 超限请求以 [SpeechErrorCodes.LIMIT_EXCEEDED] 应答,
 * 不做截断; 宿主在请求到达插件前先行检查.
 */
object SpeechLimits {

    /** Microphone sessions (dictation or wake) one host Binder token may hold at a time. */
    const val MAX_MIC_SESSIONS_PER_TOKEN = 1

    /** External PCM sessions one host Binder token may hold at a time. */
    const val MAX_PCM_SESSIONS_PER_TOKEN = 4

    /** Bytes of one `ISpeechSession.feed` call (16 kHz mono PCM16, about 32 seconds). */
    const val MAX_FEED_BYTES = 1024 * 1024

    /** Bytes of one audio file given to `recognizeFile` or `detectVoice`. */
    const val MAX_AUDIO_FILE_BYTES = 200L * 1024 * 1024

    /** Milliseconds of one audio file, and the largest `maxDurationMillis` of a session (30 minutes). */
    const val MAX_AUDIO_MILLIS = 1_800_000L

    /** UTF-16 characters of one synthesis text (the plugin splits sentences itself). */
    const val MAX_SYNTH_TEXT_CHARS = 8000

    /** Bytes of one synthesized WAV file. */
    const val MAX_SYNTH_OUTPUT_BYTES = 100L * 1024 * 1024

    /** Keywords of one wake session. */
    const val MAX_KEYWORDS = 50

    /** Hotwords of one dictation session or file recognition. */
    const val MAX_HOTWORDS = 200

    /** UTF-16 characters of one keyword, hotword, model id, language tag or speaker name. */
    const val MAX_TOKEN_CHARS = 128

    /** The only external PCM sample rate of contract version 1 (16 kHz mono PCM16 LE). */
    const val EXTERNAL_SAMPLE_RATE = 16_000

    /** `maxDurationMillis` default of a session (5 minutes). */
    const val DEFAULT_MAX_DURATION_MILLIS = 300_000L

    /** `silenceEndMillis` default: silence that ends an utterance. */
    const val DEFAULT_SILENCE_END_MILLIS = 1200L

    /** `timeoutMillis` default: a session without any speech ends after it (0 disables the timeout). */
    const val DEFAULT_TIMEOUT_MILLIS = 15_000L

    /** `minIntervalMillis` default between two wake hits. */
    const val DEFAULT_MIN_INTERVAL_MILLIS = 1500L

    /** `speed` range of synthesis (1.0 is the model's own pace). */
    const val MIN_SPEED = 0.5
    const val MAX_SPEED = 2.0

    /** Longest [SpeechContract.KEY_ERROR_MESSAGE] either side keeps; longer text is truncated. */
    const val MAX_ERROR_MESSAGE_BYTES = 4 * 1024

    /** Largest options or request Bundle, measured as the parcel size. */
    const val MAX_OPTIONS_BYTES = 64 * 1024

    /** Largest event or progress Bundle, measured as the parcel size (a full result with segments fits). */
    const val MAX_EVENT_BYTES = 256 * 1024

    /** `listModels` answers at most this many models. */
    const val MAX_MODELS = 512
}
