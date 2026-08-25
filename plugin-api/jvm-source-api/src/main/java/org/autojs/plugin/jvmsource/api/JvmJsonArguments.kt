package org.autojs.plugin.jvmsource.api

import java.lang.reflect.Array
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/**
 * Canonical JSON object profile used for host-owned execution arguments.
 *
 * [encode] accepts only JSON-shaped Java values and takes a bounded snapshot. [decode] returns
 * deeply immutable maps and lists; integral values are represented by [Long] when possible and
 * [BigInteger] otherwise, while non-integral values are represented by [BigDecimal].
 */
object JvmJsonArguments {

    @JvmStatic
    fun encode(arguments: Map<String, *>): String = try {
        Encoder(JvmSourceContract.MAX_ARGS_JSON_BYTES).apply {
            appendRoot(arguments)
        }.toString()
    } catch (error: JvmSourceContractException) {
        throw error
    } catch (error: RuntimeException) {
        throw invalid("JVM source arguments are outside the JSON profile", error)
    }

    @JvmStatic
    fun decode(argumentsJson: String): Map<String, Any?> {
        requireUtf8(argumentsJson)
        return try {
            Parser(argumentsJson).parseArguments()
        } catch (error: JvmSourceContractException) {
            throw error
        } catch (error: RuntimeException) {
            throw invalid("JVM source arguments are not a valid JSON object: ${error.message}", error)
        }
    }

    private class Encoder(private val maximumBytes: Int) {
        private val output = StringBuilder()
        private var byteCount = 0

        fun appendRoot(arguments: Map<String, *>) {
            appendValue(arguments, depth = 0, active = IdentityHashMap())
        }

        private fun appendValue(value: Any?, depth: Int, active: IdentityHashMap<Any, Boolean>) {
            when (value) {
                null -> appendAscii("null")
                is Boolean -> appendAscii(value.toString())
                is Byte, is Short, is Int, is Long, is BigInteger -> appendNumber(value.toString())
                is Float -> {
                    requireProfile(value.isFinite()) { "JVM source arguments contain a non-finite number" }
                    appendNumber(value.toString())
                }
                is Double -> {
                    requireProfile(value.isFinite()) { "JVM source arguments contain a non-finite number" }
                    appendNumber(value.toString())
                }
                is BigDecimal -> appendNumber(value.toString())
                is CharSequence -> appendString(value.toString())
                is Char -> appendString(value.toString())
                is Map<*, *> -> appendContainer(value, depth, active) {
                    requireProfile(value.size <= maximumBytes) {
                        "JVM source arguments contain too many object entries"
                    }
                    val entries = value.entries.mapIndexed { index, entry ->
                        val key = entry.key as? String
                            ?: throw invalid("JVM source argument object key $index is not a string")
                        key to entry.value
                    }.sortedBy { it.first }
                    appendAscii("{")
                    entries.forEachIndexed { index, (key, item) ->
                        if (index > 0) appendAscii(",")
                        appendString(key)
                        appendAscii(":")
                        appendValue(item, depth + 1, active)
                    }
                    appendAscii("}")
                }
                is Iterable<*> -> appendContainer(value, depth, active) {
                    appendAscii("[")
                    value.forEachIndexed { index, item ->
                        if (index > 0) appendAscii(",")
                        appendValue(item, depth + 1, active)
                    }
                    appendAscii("]")
                }
                else -> if (value.javaClass.isArray) {
                    appendContainer(value, depth, active) {
                        appendAscii("[")
                        repeat(Array.getLength(value)) { index ->
                            if (index > 0) appendAscii(",")
                            appendValue(Array.get(value, index), depth + 1, active)
                        }
                        appendAscii("]")
                    }
                } else {
                    throw invalid("JVM source arguments contain unsupported type ${value.javaClass.name}")
                }
            }
        }

        private inline fun appendContainer(
            value: Any,
            depth: Int,
            active: IdentityHashMap<Any, Boolean>,
            block: () -> Unit,
        ) {
            requireProfile(depth < JvmSourceContract.MAX_JSON_DEPTH) {
                "JVM source arguments exceed JSON depth ${JvmSourceContract.MAX_JSON_DEPTH}"
            }
            requireProfile(active.put(value, true) == null) {
                "JVM source arguments contain a reference cycle"
            }
            try {
                block()
            } finally {
                active.remove(value)
            }
        }

        private fun appendNumber(value: String) {
            requireProfile(value.length <= MAX_NUMBER_CHARACTERS && JSON_NUMBER.matches(value)) {
                "JVM source arguments contain an invalid JSON number"
            }
            appendAscii(value)
        }

        private fun appendString(value: String) {
            appendAscii("\"")
            var index = 0
            while (index < value.length) {
                val character = value[index]
                when (character) {
                    '"' -> appendAscii("\\\"")
                    '\\' -> appendAscii("\\\\")
                    '\b' -> appendAscii("\\b")
                    '\u000C' -> appendAscii("\\f")
                    '\n' -> appendAscii("\\n")
                    '\r' -> appendAscii("\\r")
                    '\t' -> appendAscii("\\t")
                    else -> when {
                        character.code < 0x20 -> appendAscii("\\u%04x".format(character.code))
                        character.isHighSurrogate() -> {
                            requireProfile(index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                                "JVM source arguments contain an unpaired UTF-16 surrogate"
                            }
                            appendCodePoint(Character.toCodePoint(character, value[index + 1]))
                            index++
                        }
                        character.isLowSurrogate() -> throw invalid(
                            "JVM source arguments contain an unpaired UTF-16 surrogate",
                        )
                        else -> appendCodePoint(character.code)
                    }
                }
                index++
            }
            appendAscii("\"")
        }

        private fun appendAscii(value: String) {
            check(value.all { it.code <= 0x7f })
            ensureCapacity(value.length)
            output.append(value)
            byteCount += value.length
        }

        private fun appendCodePoint(codePoint: Int) {
            val bytes = when {
                codePoint <= 0x7f -> 1
                codePoint <= 0x7ff -> 2
                codePoint <= 0xffff -> 3
                else -> 4
            }
            ensureCapacity(bytes)
            output.appendCodePoint(codePoint)
            byteCount += bytes
        }

        private fun ensureCapacity(additionalBytes: Int) {
            requireProfile(byteCount <= maximumBytes - additionalBytes) {
                "JVM source arguments exceed $maximumBytes UTF-8 bytes"
            }
        }

        override fun toString(): String = output.toString()
    }

    private class Parser(private val value: String) {
        private var index = 0

        fun parseArguments(): Map<String, Any?> {
            skipWhitespace()
            val parsed = parseValue(depth = 0)
            skipWhitespace()
            if (index != value.length) fail("trailing content at offset $index")
            @Suppress("UNCHECKED_CAST")
            return parsed as? Map<String, Any?> ?: fail("the root value must be an object")
        }

        private fun parseValue(depth: Int): Any? {
            if (index == value.length) fail("missing value at offset $index")
            return when (value[index]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> fail("unexpected token at offset $index")
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            requireDepth(depth)
            index++
            skipWhitespace()
            if (consume('}')) return emptyMap()
            val result = LinkedHashMap<String, Any?>()
            while (true) {
                if (index == value.length || value[index] != '"') {
                    fail("object key must be a string at offset $index")
                }
                val key = parseString()
                if (result.containsKey(key)) fail("duplicate object key at offset $index")
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = parseValue(depth)
                skipWhitespace()
                if (consume('}')) return Collections.unmodifiableMap(result)
                expect(',')
                skipWhitespace()
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            requireDepth(depth)
            index++
            skipWhitespace()
            if (consume(']')) return emptyList()
            val result = ArrayList<Any?>()
            while (true) {
                result += parseValue(depth)
                skipWhitespace()
                if (consume(']')) return Collections.unmodifiableList(result)
                expect(',')
                skipWhitespace()
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < value.length) {
                val character = value[index++]
                when {
                    character == '"' -> return result.toString()
                    character == '\\' -> appendEscape(result)
                    character < '\u0020' -> fail("unescaped control character at offset ${index - 1}")
                    character.isHighSurrogate() -> {
                        if (index == value.length || !value[index].isLowSurrogate()) {
                            fail("unpaired high surrogate at offset ${index - 1}")
                        }
                        result.append(character)
                        result.append(value[index++])
                    }
                    character.isLowSurrogate() -> fail("unpaired low surrogate at offset ${index - 1}")
                    else -> result.append(character)
                }
            }
            fail("unterminated string")
        }

        private fun appendEscape(output: StringBuilder) {
            if (index == value.length) fail("unterminated escape sequence")
            when (val escaped = value[index++]) {
                '"', '\\', '/' -> output.append(escaped)
                'b' -> output.append('\b')
                'f' -> output.append('\u000C')
                'n' -> output.append('\n')
                'r' -> output.append('\r')
                't' -> output.append('\t')
                'u' -> {
                    val first = parseHexCodeUnit()
                    when {
                        first.isHighSurrogate() -> {
                            if (index + 2 > value.length || value[index] != '\\' || value[index + 1] != 'u') {
                                fail("escaped high surrogate is not followed by an escaped low surrogate")
                            }
                            index += 2
                            val second = parseHexCodeUnit()
                            if (!second.isLowSurrogate()) {
                                fail("escaped high surrogate is not followed by an escaped low surrogate")
                            }
                            output.append(first).append(second)
                        }
                        first.isLowSurrogate() -> fail("unpaired escaped low surrogate")
                        else -> output.append(first)
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

        private fun parseNumber(): Number {
            val start = index
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
            var integral = true
            if (consume('.')) {
                integral = false
                requireDigit()
                consumeDigits()
            }
            if (index < value.length && (value[index] == 'e' || value[index] == 'E')) {
                integral = false
                index++
                if (index < value.length && (value[index] == '+' || value[index] == '-')) index++
                requireDigit()
                consumeDigits()
            }
            val lexical = value.substring(start, index)
            if (lexical.length > MAX_NUMBER_CHARACTERS) fail("number is too long at offset $start")
            return try {
                if (integral) lexical.toLongOrNull() ?: BigInteger(lexical) else BigDecimal(lexical)
            } catch (_: NumberFormatException) {
                fail("number is outside the JSON profile at offset $start")
            }
        }

        private fun <T> parseLiteral(literal: String, parsed: T): T {
            if (!value.regionMatches(index, literal, 0, literal.length)) {
                fail("invalid literal at offset $index")
            }
            index += literal.length
            return parsed
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

    private fun requireUtf8(value: String) {
        val bytes = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
                .remaining()
        } catch (error: Exception) {
            throw invalid("JVM source arguments are not valid UTF-8 text", error)
        }
        requireProfile(bytes in 1..JvmSourceContract.MAX_ARGS_JSON_BYTES) {
            "JVM source arguments exceed ${JvmSourceContract.MAX_ARGS_JSON_BYTES} UTF-8 bytes"
        }
    }

    private inline fun requireProfile(value: Boolean, message: () -> String) {
        if (!value) throw invalid(message())
    }

    private fun invalid(message: String, cause: Throwable? = null): JvmSourceContractException =
        JvmSourceContractException(JvmSourceContractViolation.INVALID_VALUE, message, cause)

    private class InvalidJsonException(message: String) : IllegalArgumentException(message)

    private const val MAX_NUMBER_CHARACTERS = 256
    private val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    private val JSON_WHITESPACE = charArrayOf(' ', '\t', '\r', '\n')
}
