package org.autojs.plugin.jvmsource.api

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Canonical one-field JSON payload used by the allowlisted `toast.show` bridge call. */
object JvmToastPayload {
    private const val PREFIX = "{\"message\":\""
    private const val SUFFIX = "\"}"
    private const val HEX = "0123456789abcdef"

    @JvmStatic
    fun encode(message: String): String {
        validateMessage(message)
        return buildString(PREFIX.length + message.length + SUFFIX.length) {
            append(PREFIX)
            message.forEach { value ->
                when (value) {
                    '\"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    in '\u0000'..'\u001f' -> {
                        append("\\u")
                        append(HEX[(value.code ushr 12) and 0xf])
                        append(HEX[(value.code ushr 8) and 0xf])
                        append(HEX[(value.code ushr 4) and 0xf])
                        append(HEX[value.code and 0xf])
                    }
                    else -> append(value)
                }
            }
            append(SUFFIX)
        }.also { encoded ->
            require(encoded.toByteArray(StandardCharsets.UTF_8).size <= JvmSourceContract.MAX_HOST_PAYLOAD_BYTES)
        }
    }

    @JvmStatic
    fun decode(payloadJson: String): String {
        require(payloadJson.startsWith(PREFIX) && payloadJson.endsWith(SUFFIX)) {
            "Toast payload is not the canonical one-field object"
        }
        val end = payloadJson.length - SUFFIX.length
        val result = StringBuilder(end - PREFIX.length)
        var index = PREFIX.length
        while (index < end) {
            val value = payloadJson[index++]
            when {
                value == '\\' -> {
                    require(index < end) { "Toast payload ends in an incomplete escape" }
                    when (val escaped = payloadJson[index++]) {
                        '\"', '\\' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index <= end - 4) { "Toast payload has an incomplete Unicode escape" }
                            var code = 0
                            repeat(4) {
                                code = (code shl 4) or hexValue(payloadJson[index++])
                            }
                            result.append(code.toChar())
                        }
                        else -> throw IllegalArgumentException("Toast payload has an invalid escape")
                    }
                }
                value == '\"' || value < '\u0020' ->
                    throw IllegalArgumentException("Toast payload contains an unescaped JSON character")
                else -> result.append(value)
            }
        }
        return result.toString().also { message ->
            validateMessage(message)
            require(encode(message) == payloadJson) { "Toast payload is not canonical" }
        }
    }

    private fun validateMessage(message: String) {
        val size = try {
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(message))
                .remaining()
        } catch (error: Throwable) {
            throw IllegalArgumentException("Toast message is not valid UTF-8 text", error)
        }
        require(size in 1..JvmSourceContract.MAX_TOAST_MESSAGE_BYTES) {
            "Toast message exceeds its UTF-8 byte limit"
        }
    }

    private fun hexValue(value: Char): Int = when (value) {
        in '0'..'9' -> value.code - '0'.code
        in 'a'..'f' -> value.code - 'a'.code + 10
        else -> throw IllegalArgumentException("Toast payload has a non-canonical Unicode escape")
    }
}
