package org.autojs.autojs.runtime.api.tts

open class TtsException(
    val code: String,
    message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    override fun toString(): String = buildString {
        append("TtsException(code=")
        append(code)
        errorCode?.let {
            append(", errorCode=")
            append(it)
        }
        append(", message=")
        append(message)
        append(')')
    }
}

class TtsInterruptedException(
    val reason: String,
    message: String,
) : TtsException(
    code = "ABORTED",
    message = message,
)

internal fun ttsPlatformError(
    errorCode: Int?,
    fallbackMessage: String = "Text-to-speech synthesis failed",
): TtsException {
    val code = when (errorCode) {
        -3 -> "SYNTHESIS"
        -4 -> "SERVICE"
        -5 -> "OUTPUT"
        -6 -> "NETWORK"
        -7 -> "NETWORK_TIMEOUT"
        -8 -> "INVALID_REQUEST"
        -9 -> "VOICE_NOT_INSTALLED"
        else -> "PLATFORM"
    }
    val message = when (code) {
        "SYNTHESIS" -> "The TTS engine could not synthesize the text"
        "SERVICE" -> "The TTS engine service is unavailable"
        "OUTPUT" -> "The TTS engine could not produce audio output"
        "NETWORK" -> "The TTS engine encountered a network error"
        "NETWORK_TIMEOUT" -> "The TTS engine network request timed out"
        "INVALID_REQUEST" -> "The TTS engine rejected the synthesis request"
        "VOICE_NOT_INSTALLED" -> "The selected TTS voice data is not installed"
        else -> fallbackMessage
    }
    return TtsException(code, message, errorCode)
}
