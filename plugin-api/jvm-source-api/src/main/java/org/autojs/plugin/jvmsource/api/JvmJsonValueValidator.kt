package org.autojs.plugin.jvmsource.api

/** A dependency-free, bounded recognizer for one complete RFC 8259 JSON value. */
internal object JvmJsonValueValidator {

    fun validate(value: String, label: String) {
        try {
            Parser(value).parseDocument()
        } catch (error: InvalidJsonException) {
            throw JvmSourceContractException(
                JvmSourceContractViolation.INVALID_VALUE,
                "$label is not a valid JSON value: ${error.message}",
                error,
            )
        }
    }

    private class Parser(private val value: String) {
        private var index = 0

        fun parseDocument() {
            skipWhitespace()
            parseValue(depth = 0)
            skipWhitespace()
            if (index != value.length) fail("trailing content at offset $index")
        }

        private fun parseValue(depth: Int) {
            if (index == value.length) fail("missing value at offset $index")
            when (value[index]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> parseLiteral("true")
                'f' -> parseLiteral("false")
                'n' -> parseLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> fail("unexpected token at offset $index")
            }
        }

        private fun parseObject(depth: Int) {
            requireDepth(depth)
            index++
            skipWhitespace()
            if (consume('}')) return
            while (true) {
                if (index == value.length || value[index] != '"') {
                    fail("object key must be a string at offset $index")
                }
                parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                parseValue(depth)
                skipWhitespace()
                if (consume('}')) return
                expect(',')
                skipWhitespace()
            }
        }

        private fun parseArray(depth: Int) {
            requireDepth(depth)
            index++
            skipWhitespace()
            if (consume(']')) return
            while (true) {
                parseValue(depth)
                skipWhitespace()
                if (consume(']')) return
                expect(',')
                skipWhitespace()
            }
        }

        private fun parseString() {
            expect('"')
            while (index < value.length) {
                val character = value[index++]
                when {
                    character == '"' -> return
                    character == '\\' -> parseEscape()
                    character < '\u0020' -> fail("unescaped control character at offset ${index - 1}")
                    character.isHighSurrogate() -> {
                        if (index == value.length || !value[index].isLowSurrogate()) {
                            fail("unpaired high surrogate at offset ${index - 1}")
                        }
                        index++
                    }
                    character.isLowSurrogate() -> fail("unpaired low surrogate at offset ${index - 1}")
                }
            }
            fail("unterminated string")
        }

        private fun parseEscape() {
            if (index == value.length) fail("unterminated escape sequence")
            when (val escaped = value[index++]) {
                '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                'u' -> {
                    val first = parseHexCodeUnit()
                    when {
                        first.isHighSurrogate() -> {
                            if (index + 2 > value.length || value[index] != '\\' || value[index + 1] != 'u') {
                                fail("escaped high surrogate is not followed by an escaped low surrogate")
                            }
                            index += 2
                            if (!parseHexCodeUnit().isLowSurrogate()) {
                                fail("escaped high surrogate is not followed by an escaped low surrogate")
                            }
                        }
                        first.isLowSurrogate() -> fail("unpaired escaped low surrogate")
                    }
                }
                else -> fail("invalid escape '$escaped' at offset ${index - 1}")
            }
        }

        private fun parseHexCodeUnit(): Char {
            if (index + 4 > value.length) fail("incomplete Unicode escape at offset $index")
            var result = 0
            repeat(4) {
                val digit = value[index++].digitToIntOrNull(16)
                    ?: fail("invalid Unicode escape at offset ${index - 1}")
                result = (result shl 4) or digit
            }
            return result.toChar()
        }

        private fun parseNumber() {
            consume('-')
            if (index == value.length) fail("incomplete number at offset $index")
            if (consume('0')) {
                if (index < value.length && value[index] in '0'..'9') {
                    fail("leading zero in number at offset $index")
                }
            } else {
                requireDigit()
                consumeDigits()
            }
            if (consume('.')) {
                requireDigit()
                consumeDigits()
            }
            if (index < value.length && (value[index] == 'e' || value[index] == 'E')) {
                index++
                if (index < value.length && (value[index] == '+' || value[index] == '-')) index++
                requireDigit()
                consumeDigits()
            }
        }

        private fun parseLiteral(literal: String) {
            if (!value.regionMatches(index, literal, 0, literal.length)) {
                fail("invalid literal at offset $index")
            }
            index += literal.length
        }

        private fun requireDepth(depth: Int) {
            if (depth > JvmSourceContract.MAX_JSON_DEPTH) {
                fail("nesting exceeds ${JvmSourceContract.MAX_JSON_DEPTH}")
            }
        }

        private fun requireDigit() {
            if (index == value.length || value[index] !in '0'..'9') {
                fail("digit expected at offset $index")
            }
        }

        private fun consumeDigits() {
            while (index < value.length && value[index] in '0'..'9') index++
        }

        private fun skipWhitespace() {
            while (index < value.length && value[index] in JSON_WHITESPACE) index++
        }

        private fun consume(expected: Char): Boolean {
            if (index == value.length || value[index] != expected) return false
            index++
            return true
        }

        private fun expect(expected: Char) {
            if (!consume(expected)) fail("'$expected' expected at offset $index")
        }

        private fun fail(message: String): Nothing = throw InvalidJsonException(message)
    }

    private class InvalidJsonException(message: String) : IllegalArgumentException(message)

    private val JSON_WHITESPACE = charArrayOf(' ', '\t', '\r', '\n')
}
