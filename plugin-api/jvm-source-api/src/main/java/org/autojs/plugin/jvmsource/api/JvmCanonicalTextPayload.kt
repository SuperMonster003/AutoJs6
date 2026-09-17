package org.autojs.plugin.jvmsource.api

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object JvmCanonicalTextPayload {
    private const val SUFFIX = "\"}"
    private const val HEX = "0123456789abcdef"

    fun encode(
        fieldName: String,
        text: String,
        minimumBytes: Int,
        maximumBytes: Int,
        valueName: String,
    ): String {
        validateText(text, minimumBytes, maximumBytes, valueName)
        val prefix = "{\"$fieldName\":\""
        return buildString(prefix.length + text.length + SUFFIX.length) {
            append(prefix)
            text.forEach { value ->
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
            require(encoded.toByteArray(StandardCharsets.UTF_8).size <= JvmSourceContract.MAX_HOST_PAYLOAD_BYTES) {
                "$valueName payload exceeds the host-call wire limit"
            }
        }
    }

    fun decode(
        fieldName: String,
        payloadJson: String,
        minimumBytes: Int,
        maximumBytes: Int,
        valueName: String,
    ): String {
        require(payloadJson.toByteArray(StandardCharsets.UTF_8).size <= JvmSourceContract.MAX_HOST_PAYLOAD_BYTES) {
            "$valueName payload exceeds the host-call wire limit"
        }
        val prefix = "{\"$fieldName\":\""
        require(payloadJson.startsWith(prefix) && payloadJson.endsWith(SUFFIX)) {
            "$valueName payload is not the canonical one-field object"
        }
        val end = payloadJson.length - SUFFIX.length
        val result = StringBuilder(end - prefix.length)
        var index = prefix.length
        while (index < end) {
            val value = payloadJson[index++]
            when {
                value == '\\' -> {
                    require(index < end) { "$valueName payload ends in an incomplete escape" }
                    when (val escaped = payloadJson[index++]) {
                        '\"', '\\' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index <= end - 4) { "$valueName payload has an incomplete Unicode escape" }
                            var code = 0
                            repeat(4) {
                                code = (code shl 4) or hexValue(payloadJson[index++], valueName)
                            }
                            result.append(code.toChar())
                        }
                        else -> throw IllegalArgumentException("$valueName payload has an invalid escape")
                    }
                }
                value == '\"' || value < '\u0020' ->
                    throw IllegalArgumentException("$valueName payload contains an unescaped JSON character")
                else -> result.append(value)
            }
        }
        return result.toString().also { text ->
            validateText(text, minimumBytes, maximumBytes, valueName)
            require(encode(fieldName, text, minimumBytes, maximumBytes, valueName) == payloadJson) {
                "$valueName payload is not canonical"
            }
        }
    }

    private fun validateText(text: String, minimumBytes: Int, maximumBytes: Int, valueName: String) {
        val size = try {
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
                .remaining()
        } catch (error: Throwable) {
            throw IllegalArgumentException("$valueName is not valid UTF-8 text", error)
        }
        require(size in minimumBytes..maximumBytes) {
            "$valueName exceeds its UTF-8 byte limit"
        }
    }

    private fun hexValue(value: Char, valueName: String): Int = when (value) {
        in '0'..'9' -> value.code - '0'.code
        in 'a'..'f' -> value.code - 'a'.code + 10
        else -> throw IllegalArgumentException("$valueName payload has a non-canonical Unicode escape")
    }
}
