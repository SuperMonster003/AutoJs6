package org.autojs.autojs.core.plugin.typescript

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Small dependency-free JSON reader for the provisional R2 request manifest.
 *
 * It deliberately keeps JSON numbers as their original token. Manifest consumers can therefore
 * require canonical non-negative integers instead of accepting a rounded Double.
 */
internal object TypeScriptStrictJson {

    fun parseObject(
        bytes: ByteArray,
        maximumDepth: Int = TypeScriptCompilerContract.MAX_JSON_DEPTH,
        maximumNodes: Int = DEFAULT_MAXIMUM_NODES,
    ): Map<String, Any?> {
        require(maximumDepth > 0)
        require(maximumNodes > 0)
        val source = TypeScriptUtf8.decode(bytes, "JSON document")
        return Parser(source, maximumDepth, maximumNodes).parseObjectDocument()
    }

    internal data class NumberToken(
        val source: String,
    )

    private class Parser(
        private val source: String,
        private val maximumDepth: Int,
        private val maximumNodes: Int,
    ) {

        private var offset = 0
        private var nodes = 0

        fun parseObjectDocument(): Map<String, Any?> {
            skipWhitespace()
            require(offset < source.length && source[offset] == '{') {
                "JSON document must contain one object."
            }
            val result = parseObject(depth = 1)
            skipWhitespace()
            require(offset == source.length) {
                "Unexpected JSON content at offset $offset."
            }
            return result
        }

        private fun parseValue(depth: Int): Any? {
            require(depth <= maximumDepth) {
                "JSON nesting exceeds depth $maximumDepth."
            }
            countNode()
            skipWhitespace()
            require(offset < source.length) { "Unexpected end of JSON." }
            return when (source[offset]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException(
                    "Unexpected JSON token at offset $offset.",
                )
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            require(depth <= maximumDepth) {
                "JSON nesting exceeds depth $maximumDepth."
            }
            expect('{')
            skipWhitespace()
            if (consume('}')) return emptyMap()

            val result = LinkedHashMap<String, Any?>()
            while (true) {
                skipWhitespace()
                require(offset < source.length && source[offset] == '"') {
                    "JSON object key must be a string at offset $offset."
                }
                val key = parseString()
                require(!result.containsKey(key)) {
                    "JSON object contains duplicate key '${key.describeForError()}'."
                }
                skipWhitespace()
                expect(':')
                result[key] = parseValue(depth + 1)
                skipWhitespace()
                if (consume('}')) return result
                expect(',')
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            require(depth <= maximumDepth) {
                "JSON nesting exceeds depth $maximumDepth."
            }
            expect('[')
            skipWhitespace()
            if (consume(']')) return emptyList()

            val result = ArrayList<Any?>()
            while (true) {
                result += parseValue(depth + 1)
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (offset < source.length) {
                when (val character = source[offset++]) {
                    '"' -> {
                        val value = result.toString()
                        TypeScriptUtf8.encodedByteCount(value, "JSON string")
                        return value
                    }
                    '\\' -> {
                        require(offset < source.length) { "Truncated JSON escape." }
                        when (val escaped = source[offset++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> result.append(parseUnicodeEscape())
                            else -> throw IllegalArgumentException(
                                "Invalid JSON escape '\\$escaped'.",
                            )
                        }
                    }
                    else -> {
                        require(character.code >= 0x20) {
                            "JSON string contains an unescaped control character."
                        }
                        result.append(character)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated JSON string.")
        }

        private fun parseUnicodeEscape(): Char {
            require(offset <= source.length - 4) { "Truncated JSON unicode escape." }
            var value = 0
            repeat(4) {
                val character = source[offset++]
                val digit = character.digitToIntOrNull(16)
                    ?: throw IllegalArgumentException(
                        "Invalid JSON unicode escape at offset ${offset - 1}.",
                    )
                value = value * 16 + digit
            }
            return value.toChar()
        }

        private fun parseNumber(): NumberToken {
            val start = offset
            consume('-')
            if (consume('0')) {
                require(offset >= source.length || source[offset] !in '0'..'9') {
                    "JSON number contains a leading zero."
                }
            } else {
                consumeDigits()
            }
            if (consume('.')) consumeDigits()
            if (consume('e') || consume('E')) {
                consume('+') || consume('-')
                consumeDigits()
            }
            return NumberToken(source.substring(start, offset))
        }

        private fun consumeDigits() {
            val start = offset
            while (offset < source.length && source[offset] in '0'..'9') {
                offset++
            }
            require(offset > start) { "Expected a JSON digit at offset $offset." }
        }

        private fun <T> parseLiteral(
            literal: String,
            value: T,
        ): T {
            require(source.startsWith(literal, offset)) {
                "Expected '$literal' at offset $offset."
            }
            offset += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (
                offset < source.length &&
                source[offset] in JSON_WHITESPACE
            ) {
                offset++
            }
        }

        private fun expect(expected: Char) {
            require(consume(expected)) {
                "Expected '$expected' at offset $offset."
            }
        }

        private fun consume(expected: Char): Boolean {
            if (offset >= source.length || source[offset] != expected) return false
            offset++
            return true
        }

        private fun countNode() {
            nodes++
            require(nodes <= maximumNodes) {
                "JSON document contains more than $maximumNodes values."
            }
        }

        private fun String.describeForError(): String =
            if (length <= MAXIMUM_ERROR_VALUE_CHARS) {
                this
            } else {
                take(MAXIMUM_ERROR_VALUE_CHARS) + "... ($length chars)"
            }

        private companion object {
            const val MAXIMUM_ERROR_VALUE_CHARS = 128
        }
    }

    private const val DEFAULT_MAXIMUM_NODES = 32 * 1024
    private val JSON_WHITESPACE = charArrayOf(' ', '\t', '\r', '\n')
}

internal object TypeScriptUtf8 {

    fun encode(
        text: String,
        label: String,
    ): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val encoded = encoder.encode(CharBuffer.wrap(text))
            ByteArray(encoded.remaining()).also(encoded::get)
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("$label is not valid Unicode text.", error)
        }
    }

    fun decode(
        bytes: ByteArray,
        label: String,
    ): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("$label is not valid UTF-8.", error)
        }
    }

    fun encodedByteCount(
        text: String,
        label: String,
    ): Long {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            encoder.encode(CharBuffer.wrap(text)).remaining().toLong()
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("$label is not valid Unicode text.", error)
        }
    }
}
