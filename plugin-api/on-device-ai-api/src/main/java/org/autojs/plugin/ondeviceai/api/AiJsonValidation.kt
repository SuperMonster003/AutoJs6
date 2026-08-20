package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import java.security.MessageDigest

/**
 * Dependency-free JSON syntax and complexity validation for protocol payloads.
 *
 * Every JSON value, including a scalar or container, consumes one node. Every object key consumes
 * one additional node. String limits apply to decoded UTF-8 bytes, so escape spelling does not
 * change the quota.
 */
object AiJsonSyntaxValidator {
    fun requireValid(value: String) {
        Parser(value).parse()
    }

    private class Parser(private val source: String) {
        private var index = 0
        private var nodes = 0

        fun parse() {
            skipWhitespace()
            parseValue(depth = 0)
            skipWhitespace()
            require(index == source.length) { "JSON contains trailing data" }
        }

        private fun parseValue(depth: Int) {
            consumeNode()
            require(index < source.length) { "JSON value is missing" }
            when (val character = source[index]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> parseLiteral("true")
                'f' -> parseLiteral("false")
                'n' -> parseLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Invalid JSON value prefix: $character")
            }
        }

        private fun parseObject(depth: Int) {
            requireDepth(depth)
            index += 1
            skipWhitespace()
            if (consume('}')) return
            val decodedKeys = hashSetOf<String>()
            while (true) {
                require(peek() == '"') { "JSON object key must be a string" }
                consumeNode()
                val decodedKey = parseString(captureDecodedValue = true)
                require(decodedKeys.add(requireNotNull(decodedKey))) {
                    "JSON object contains a duplicate decoded key"
                }
                skipWhitespace()
                require(consume(':')) { "JSON object key is missing a colon" }
                skipWhitespace()
                parseValue(depth)
                skipWhitespace()
                if (consume('}')) return
                require(consume(',')) { "JSON object entry is missing a comma" }
                skipWhitespace()
            }
        }

        private fun parseArray(depth: Int) {
            requireDepth(depth)
            index += 1
            skipWhitespace()
            if (consume(']')) return
            while (true) {
                parseValue(depth)
                skipWhitespace()
                if (consume(']')) return
                require(consume(',')) { "JSON array element is missing a comma" }
                skipWhitespace()
            }
        }

        private data class ParsedEscape(
            val first: Char,
            val second: Char? = null,
            val utf8Bytes: Int,
        )

        private fun parseString(captureDecodedValue: Boolean = false): String? {
            require(consume('"')) { "JSON string is missing its opening quote" }
            var decodedUtf8Bytes = 0
            val decoded = if (captureDecodedValue) StringBuilder() else null
            while (index < source.length) {
                val character = source[index++]
                when {
                    character == '"' -> return decoded?.toString()
                    character == '\\' -> {
                        val escape = parseEscape()
                        decodedUtf8Bytes = addDecodedStringBytes(decodedUtf8Bytes, escape.utf8Bytes)
                        decoded?.append(escape.first)
                        escape.second?.let { decoded?.append(it) }
                    }
                    character.code < 0x20 -> throw IllegalArgumentException("JSON string contains a control character")
                    Character.isHighSurrogate(character) -> {
                        require(index < source.length && Character.isLowSurrogate(source[index])) {
                            "JSON string contains an unpaired surrogate"
                        }
                        val lowSurrogate = source[index++]
                        decodedUtf8Bytes = addDecodedStringBytes(decodedUtf8Bytes, 4)
                        decoded?.append(character)?.append(lowSurrogate)
                    }
                    Character.isLowSurrogate(character) ->
                        throw IllegalArgumentException("JSON string contains an unpaired surrogate")
                    else -> {
                        decodedUtf8Bytes = addDecodedStringBytes(decodedUtf8Bytes, utf8Bytes(character))
                        decoded?.append(character)
                    }
                }
            }
            throw IllegalArgumentException("JSON string is unterminated")
        }

        private fun parseEscape(): ParsedEscape {
            require(index < source.length) { "JSON escape is truncated" }
            return when (source[index++]) {
                '"' -> ParsedEscape('"', utf8Bytes = 1)
                '\\' -> ParsedEscape('\\', utf8Bytes = 1)
                '/' -> ParsedEscape('/', utf8Bytes = 1)
                'b' -> ParsedEscape('\b', utf8Bytes = 1)
                'f' -> ParsedEscape('\u000C', utf8Bytes = 1)
                'n' -> ParsedEscape('\n', utf8Bytes = 1)
                'r' -> ParsedEscape('\r', utf8Bytes = 1)
                't' -> ParsedEscape('\t', utf8Bytes = 1)
                'u' -> {
                    val first = parseHexCodeUnit().toChar()
                    when {
                        Character.isHighSurrogate(first) -> {
                            require(index + 2 <= source.length && source[index] == '\\' && source[index + 1] == 'u') {
                                "JSON escape contains an unpaired surrogate"
                            }
                            index += 2
                            val second = parseHexCodeUnit().toChar()
                            require(Character.isLowSurrogate(second)) { "JSON escape contains an unpaired surrogate" }
                            ParsedEscape(first, second, utf8Bytes = 4)
                        }
                        Character.isLowSurrogate(first) ->
                            throw IllegalArgumentException("JSON escape contains an unpaired surrogate")
                        else -> ParsedEscape(first, utf8Bytes = utf8Bytes(first))
                    }
                }
                else -> throw IllegalArgumentException("JSON string contains an invalid escape")
            }
        }

        private fun parseHexCodeUnit(): Int {
            require(index + 4 <= source.length) { "JSON Unicode escape is truncated" }
            var value = 0
            repeat(4) {
                val digit = when (val character = source[index++]) {
                    in '0'..'9' -> character - '0'
                    in 'a'..'f' -> character - 'a' + 10
                    in 'A'..'F' -> character - 'A' + 10
                    else -> throw IllegalArgumentException("JSON Unicode escape is invalid")
                }
                value = value * 16 + digit
            }
            return value
        }

        private fun parseNumber() {
            consume('-')
            require(index < source.length) { "JSON number is truncated" }
            if (consume('0')) {
                require(peek()?.let { it in '0'..'9' } != true) { "JSON number contains a leading zero" }
            } else {
                require(peek() in '1'..'9') { "JSON number has an invalid integer part" }
                consumeDigits()
            }
            if (consume('.')) {
                require(peek()?.let { it in '0'..'9' } == true) { "JSON number has an invalid fraction" }
                consumeDigits()
            }
            if (peek() == 'e' || peek() == 'E') {
                index += 1
                if (peek() == '+' || peek() == '-') index += 1
                require(peek()?.let { it in '0'..'9' } == true) { "JSON number has an invalid exponent" }
                consumeDigits()
            }
        }

        private fun consumeDigits() {
            while (peek()?.let { it in '0'..'9' } == true) index += 1
        }

        private fun parseLiteral(literal: String) {
            require(source.regionMatches(index, literal, 0, literal.length)) { "JSON literal is invalid" }
            index += literal.length
        }

        private fun requireDepth(depth: Int) {
            require(depth <= OnDeviceAiLimits.MAX_JSON_NESTING_DEPTH) {
                "JSON nesting exceeds the protocol limit"
            }
        }

        private fun consumeNode() {
            require(nodes < OnDeviceAiLimits.MAX_JSON_NODES) { "JSON node count exceeds the protocol limit" }
            nodes += 1
        }

        private fun addDecodedStringBytes(current: Int, additional: Int): Int {
            require(current <= OnDeviceAiLimits.MAX_JSON_STRING_BYTES - additional) {
                "JSON string exceeds the protocol limit"
            }
            return current + additional
        }

        private fun utf8Bytes(character: Char): Int = when {
            character.code <= 0x7F -> 1
            character.code <= 0x7FF -> 2
            else -> 3
        }

        private fun skipWhitespace() {
            while (peek() == ' ' || peek() == '\t' || peek() == '\r' || peek() == '\n') index += 1
        }

        private fun consume(expected: Char): Boolean {
            if (peek() != expected) return false
            index += 1
            return true
        }

        private fun peek(): Char? = source.getOrNull(index)
    }
}

object OnDeviceAiPayloadPolicy {
    fun materializeAndValidate(
        payload: AiPayloadReference,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): String = materializeAndValidateBounded(
        payload = payload,
        maximumBytes = AiCommonLimits.MAX_PAYLOAD_BYTES,
        readDescriptor = readDescriptor,
    )

    /**
     * Materializes one text payload only after its declaration fits [maximumBytes]. Descriptor
     * readers must honor the supplied declared length and avoid unbounded reads; the returned
     * bytes are checked again before UTF-8, digest, and JSON validation.
     */
    fun materializeAndValidateBounded(
        payload: AiPayloadReference,
        maximumBytes: Long,
        readDescriptor: ((index: Int, declaredLengthBytes: Long) -> ByteArray)? = null,
    ): String {
        require(maximumBytes in 0L..AiCommonLimits.MAX_PAYLOAD_BYTES) { "Invalid text payload byte limit" }
        require(payload.declaredLengthBytes <= maximumBytes) { "Text payload declaration exceeds its byte limit" }
        val materializedBytes = payload.inlineBytes ?: requireNotNull(readDescriptor) {
            "A descriptor reader is required for an out-of-line text payload"
        }.invoke(requireNotNull(payload.descriptorIndex), payload.declaredLengthBytes)
        require(materializedBytes.size.toLong() <= maximumBytes) { "Materialized text payload exceeds its byte limit" }
        val bytes = materializedBytes.copyOf()
        return validateMaterialized(payload, bytes)
    }

    fun validateMaterialized(payload: AiPayloadReference, bytes: ByteArray): String {
        require(bytes.size.toLong() == payload.declaredLengthBytes) {
            "Text payload length does not match its declaration"
        }
        payload.sha256?.let { expected ->
            require(sha256(bytes) == expected) { "Text payload digest does not match its declaration" }
        }
        requireTextEncoding(payload)
        val text = AiValidation.decodeUtf8(bytes)
        if (payload.mimeType == OnDeviceAiMimeType.JSON) AiJsonSyntaxValidator.requireValid(text)
        return text
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}

internal fun requireInlineTextPayload(payload: AiPayloadReference) {
    requireTextEncoding(payload)
    if (payload.inlineBytes != null) OnDeviceAiPayloadPolicy.materializeAndValidate(payload)
}
