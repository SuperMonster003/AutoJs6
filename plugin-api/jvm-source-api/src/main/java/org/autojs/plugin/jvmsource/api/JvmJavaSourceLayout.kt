package org.autojs.plugin.jvmsource.api

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Canonical single-file Java layout derived from the exact source snapshot. */
data class JvmJavaSourceLayout(
    val sourceFileName: String,
    val entryClassName: String,
)

/**
 * Strict, bounded lexical inspection shared by the host and Java provider.
 *
 * Java Unicode escapes are rejected because the Java compiler translates them before tokenization;
 * accepting them here would let source text disagree with the package seen by ECJ. Imports are
 * deliberately left to ECJ. M1 keeps the entry simple name explicit (`Main` in the host), while the
 * package declaration determines the fully-qualified entry class name.
 */
object JvmJavaSourceLayoutPolicy {
    const val DEFAULT_ENTRY_SIMPLE_NAME = "Main"

    @JvmStatic
    @JvmOverloads
    fun inspect(
        sourceBytes: ByteArray,
        entrySimpleName: String = DEFAULT_ENTRY_SIMPLE_NAME,
    ): Inspection {
        require(sourceBytes.isNotEmpty()) { "Java source snapshot is empty" }
        require(isAsciiIdentifier(entrySimpleName)) { "Java entry simple name is invalid" }
        val normalizedSource = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(sourceBytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (error: Throwable) {
            throw IllegalArgumentException("Java source is not valid UTF-8", error)
        }
        require('\u0000' !in normalizedSource) { "Java source contains a NUL character" }
        require(!JAVA_UNICODE_ESCAPE.containsMatchIn(normalizedSource)) {
            "Java Unicode escapes are not admitted before lexical package inspection"
        }

        val lexical = eraseCommentsAndLiterals(normalizedSource)
        val packageOffsets = keywordOffsets(lexical, PACKAGE_KEYWORD)
        require(packageOffsets.size <= 1) { "Java source contains multiple package declarations" }
        val packageName = packageOffsets.singleOrNull()?.let { offset ->
            require(lexical.substring(0, offset).all(Char::isWhitespace)) {
                "Java package declaration must be the first source declaration"
            }
            parsePackageName(lexical, offset + PACKAGE_KEYWORD.length)
        }
        val entryClassName = if (packageName == null) {
            entrySimpleName
        } else {
            "$packageName.$entrySimpleName"
        }
        require(entryClassName.toByteArray(StandardCharsets.UTF_8).size <=
            JvmSourceContract.MAX_ENTRY_CLASS_NAME_BYTES
        ) {
            "Java package makes the entry class name too long"
        }
        return Inspection(
            normalizedSource = normalizedSource,
            layout = JvmJavaSourceLayout(
                sourceFileName = "$entrySimpleName.java",
                entryClassName = entryClassName,
            ),
        )
    }

    class Inspection internal constructor(
        val normalizedSource: String,
        val layout: JvmJavaSourceLayout,
    )

    private fun parsePackageName(source: String, afterKeyword: Int): String {
        var cursor = afterKeyword
        require(cursor < source.length && source[cursor].isWhitespace()) {
            "Java package declaration is malformed"
        }
        cursor = skipWhitespace(source, cursor)
        val segments = mutableListOf<String>()
        while (true) {
            require(cursor < source.length && isAsciiIdentifierStart(source[cursor])) {
                "Java package name must use ASCII Java identifiers"
            }
            val start = cursor++
            while (cursor < source.length && isAsciiIdentifierPart(source[cursor])) cursor++
            segments += source.substring(start, cursor)
            cursor = skipWhitespace(source, cursor)
            when (source.getOrNull(cursor)) {
                '.' -> {
                    cursor = skipWhitespace(source, cursor + 1)
                }
                ';' -> return segments.joinToString(".")
                else -> throw IllegalArgumentException("Java package declaration is malformed")
            }
        }
    }

    private fun skipWhitespace(source: String, start: Int): Int {
        var cursor = start
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
        return cursor
    }

    private fun keywordOffsets(source: String, keyword: String): List<Int> {
        val result = mutableListOf<Int>()
        var cursor = 0
        while (cursor <= source.length - keyword.length) {
            if (source.regionMatches(cursor, keyword, 0, keyword.length) &&
                (cursor == 0 || !Character.isJavaIdentifierPart(source[cursor - 1])) &&
                (cursor + keyword.length == source.length ||
                    !Character.isJavaIdentifierPart(source[cursor + keyword.length]))
            ) {
                result += cursor
                cursor += keyword.length
            } else {
                cursor++
            }
        }
        return result
    }

    private fun eraseCommentsAndLiterals(source: String): String {
        val result = StringBuilder(source.length)
        var index = 0
        var state = State.CODE
        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            when (state) {
                State.CODE -> when {
                    char == '/' && next == '/' -> {
                        result.append("  ")
                        index += 2
                        state = State.LINE_COMMENT
                        continue
                    }
                    char == '/' && next == '*' -> {
                        result.append("  ")
                        index += 2
                        state = State.BLOCK_COMMENT
                        continue
                    }
                    char == '"' -> {
                        result.append(' ')
                        state = State.STRING
                    }
                    char == '\'' -> {
                        result.append(' ')
                        state = State.CHAR
                    }
                    else -> result.append(char)
                }
                State.LINE_COMMENT -> {
                    result.append(if (char == '\n' || char == '\r') char else ' ')
                    if (char == '\n' || char == '\r') state = State.CODE
                }
                State.BLOCK_COMMENT -> {
                    if (char == '*' && next == '/') {
                        result.append("  ")
                        index += 2
                        state = State.CODE
                        continue
                    }
                    result.append(if (char == '\n' || char == '\r') char else ' ')
                }
                State.STRING, State.CHAR -> {
                    result.append(if (char == '\n' || char == '\r') char else ' ')
                    if (char == '\\' && next != null) {
                        result.append(if (next == '\n' || next == '\r') next else ' ')
                        index += 2
                        continue
                    }
                    if ((state == State.STRING && char == '"') ||
                        (state == State.CHAR && char == '\'')
                    ) {
                        state = State.CODE
                    }
                }
            }
            index++
        }
        return result.toString()
    }

    private fun isAsciiIdentifier(value: String): Boolean =
        value.isNotEmpty() && isAsciiIdentifierStart(value[0]) && value.drop(1).all(::isAsciiIdentifierPart)

    private fun isAsciiIdentifierStart(value: Char): Boolean =
        value in 'A'..'Z' || value in 'a'..'z' || value == '_' || value == '$'

    private fun isAsciiIdentifierPart(value: Char): Boolean =
        isAsciiIdentifierStart(value) || value in '0'..'9'

    private enum class State { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR }

    private const val PACKAGE_KEYWORD = "package"
    private val JAVA_UNICODE_ESCAPE = Regex("\\\\u+[0-9A-Fa-f]{4}")
}
