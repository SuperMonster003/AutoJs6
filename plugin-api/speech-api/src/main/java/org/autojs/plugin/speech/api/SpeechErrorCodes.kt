package org.autojs.plugin.speech.api

/**
 * Error codes of Bundle answers ([SpeechContract.KEY_ERROR_CODE]), of the exceptions thrown by calls that do not answer
 * with a Bundle, of error events and progress failures, and of the host-side `SpeechError` (roadmap D27). Contract
 * version 2 appends [SPEAKER_NOT_FOUND] and [AUDIO_TOO_SHORT]; a code the host does not know is reported as [INTERNAL].
 *
 * zh-CN: Bundle 应答 ([SpeechContract.KEY_ERROR_CODE]), 不以 Bundle 应答的调用抛出的异常, 错误事件与进度失败, 以及宿主侧 `SpeechError`
 * 的错误码 (路线图 D27). 契约版本 2 追加 [SPEAKER_NOT_FOUND] 与 [AUDIO_TOO_SHORT]; 宿主不认识的码报告为 [INTERNAL].
 */
object SpeechErrorCodes {

    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val PLUGIN_DISABLED = "PLUGIN_DISABLED"
    const val PLUGIN_INCOMPATIBLE = "PLUGIN_INCOMPATIBLE"

    /** The plugin does not hold `RECORD_AUDIO`; the user grants it on the plugin's own screen. */
    const val PERMISSION_DENIED = "PERMISSION_DENIED"

    /** No model of the kind is installed, or the requested model id is unknown or not installed. */
    const val MODEL_MISSING = "MODEL_MISSING"
    const val MODEL_DOWNLOAD_FAILED = "MODEL_DOWNLOAD_FAILED"
    const val MODEL_CHECKSUM_MISMATCH = "MODEL_CHECKSUM_MISMATCH"

    /** A non-commercial model needs `acceptLicense: true` (roadmap D11). */
    const val MODEL_LICENSE_NOT_ACCEPTED = "MODEL_LICENSE_NOT_ACCEPTED"
    const val INVALID_OPTIONS = "INVALID_OPTIONS"

    /** The audio file is not a WAV the plugin reads, or an output format is unknown. */
    const val UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"

    /** The microphone is held by another session of this host, or the session table is full. */
    const val SESSION_BUSY = "SESSION_BUSY"

    /** A call reached a session that already ended. */
    const val SESSION_CLOSED = "SESSION_CLOSED"
    const val SESSION_CANCELLED = "SESSION_CANCELLED"

    /** The runtime failed to load a model or to process audio. */
    const val ENGINE_FAILED = "ENGINE_FAILED"
    const val IO_FAILED = "IO_FAILED"
    const val TIMEOUT = "TIMEOUT"
    const val INTERNAL = "INTERNAL"

    /** Version 2: the speaker name is not enrolled (roadmap D132). */
    const val SPEAKER_NOT_FOUND = "SPEAKER_NOT_FOUND"

    /** Version 2: a recording holds less than [SpeechLimits.MIN_SPEAKER_SPEECH_MILLIS] of speech (roadmap D132). */
    const val AUDIO_TOO_SHORT = "AUDIO_TOO_SHORT"

    /**
     * Every code, in the order of roadmap D27 followed by the host additions and the codes of contract version 2. A host
     * of version 1 reports the version 2 codes as [INTERNAL]; a plugin sends them only in answers to version 2 calls.
     */
    val ALL: List<String> = listOf(
        PLUGIN_UNAVAILABLE,
        PLUGIN_DISABLED,
        PLUGIN_INCOMPATIBLE,
        PERMISSION_DENIED,
        MODEL_MISSING,
        MODEL_DOWNLOAD_FAILED,
        MODEL_CHECKSUM_MISMATCH,
        MODEL_LICENSE_NOT_ACCEPTED,
        INVALID_OPTIONS,
        UNSUPPORTED_FORMAT,
        LIMIT_EXCEEDED,
        SESSION_BUSY,
        SESSION_CLOSED,
        SESSION_CANCELLED,
        ENGINE_FAILED,
        IO_FAILED,
        TIMEOUT,
        INTERNAL,
        SPEAKER_NOT_FOUND,
        AUDIO_TOO_SHORT,
    )

    /** Codes the host produces itself before any Binder call reaches the plugin. */
    val HOST_ONLY: Set<String> = setOf(PLUGIN_UNAVAILABLE, PLUGIN_DISABLED, PLUGIN_INCOMPATIBLE)

    private const val SEPARATOR = ": "

    fun isKnown(code: String?): Boolean = code != null && code in ALL

    /**
     * Message of an exception thrown across Binder by a call that cannot answer with a Bundle: the code, [SEPARATOR]
     * and the detail (empty detail yields the bare code).
     */
    fun encode(code: String, detail: String? = null): String {
        require(isKnown(code)) { "Unknown speech error code: $code" }
        val text = detail?.trim().orEmpty()
        return if (text.isEmpty()) code else code + SEPARATOR + text
    }

    /**
     * Splits an [encode]d message into its code and detail; null when the message does not start with a known code, in
     * which case the caller reports [INTERNAL] with the whole message.
     */
    fun decode(message: String?): Pair<String, String>? {
        val text = message?.trim() ?: return null
        val code = text.substringBefore(SEPARATOR)
        if (!isKnown(code)) return null
        val detail = if (code.length == text.length) "" else text.substring(code.length + SEPARATOR.length)
        return code to detail
    }
}
