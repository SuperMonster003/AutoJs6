package org.autojs.plugin.yolo.api

import java.nio.charset.StandardCharsets

data class YoloOpenSessionFailure(
    val code: YoloErrorCode,
    val message: String,
)

/**
 * Encodes synchronous open-session failures into an exception type that the public Android
 * Binder SDK transports. The prefix is versioned so a Host never parses arbitrary provider text.
 */
object YoloOpenSessionFailureCodec {
    const val MESSAGE_PREFIX = "AUTOJS_YOLO_OPEN_V1:"

    fun exception(code: YoloErrorCode, message: String): IllegalArgumentException =
        IllegalArgumentException(encodeMessage(code, message))

    fun encodeMessage(code: YoloErrorCode, message: String): String {
        validateMessage(message)
        return "$MESSAGE_PREFIX${code.wireCode}:$message"
    }

    fun decode(error: IllegalArgumentException): YoloOpenSessionFailure? =
        decodeMessage(error.message)

    fun decodeMessage(value: String?): YoloOpenSessionFailure? {
        if (value == null || !value.startsWith(MESSAGE_PREFIX)) return null
        val codeEnd = value.indexOf(':', MESSAGE_PREFIX.length)
        if (codeEnd < 0) return null
        val codeValue = value.substring(MESSAGE_PREFIX.length, codeEnd).toIntOrNull() ?: return null
        val code = YoloErrorCode.values().firstOrNull { it.wireCode == codeValue } ?: return null
        val message = value.substring(codeEnd + 1)
        return runCatching {
            validateMessage(message)
            YoloOpenSessionFailure(code, message)
        }.getOrNull()
    }

    private fun validateMessage(message: String) {
        if (message.isBlank() || '\u0000' in message) {
            throw YoloContractException(
                YoloContractViolation.INVALID_VALUE,
                "YOLO open-session failure message is invalid",
            )
        }
        if (message.toByteArray(StandardCharsets.UTF_8).size > YoloContract.MAX_ERROR_MESSAGE_BYTES) {
            throw YoloContractException(
                YoloContractViolation.INVALID_VALUE,
                "YOLO open-session failure message exceeds the UTF-8 limit",
            )
        }
    }
}
