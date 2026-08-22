package org.autojs.autojs.core.plugin.jvm

import org.autojs.autojs.script.JvmSourceKind
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class JvmSourceR1Layout(
    val sourceFileName: String,
    val entryClassName: String,
)

/** Shared M4 host policy for the two independent single-language providers. */
internal object JvmSourceR1Policy {
    const val ENTRY_SIMPLE_NAME = JvmSourceR1JavaPolicy.ENTRY_SIMPLE_NAME

    fun language(kind: JvmSourceKind): JvmSourceLanguage = when (kind) {
        JvmSourceKind.JAVA -> JvmSourceLanguage.JAVA
        JvmSourceKind.KOTLIN -> JvmSourceLanguage.KOTLIN
    }

    fun normalizedSourceFileName(kind: JvmSourceKind): String =
        "$ENTRY_SIMPLE_NAME.${kind.canonicalExtension}"

    fun requireSupportedFileName(kind: JvmSourceKind, fileName: String) {
        when (kind) {
            JvmSourceKind.JAVA -> JvmSourceR1JavaPolicy.requireSupportedFileName(fileName)
            JvmSourceKind.KOTLIN -> {
                require(fileName.length in 4..255 && fileName.endsWith(".kt", ignoreCase = true)) {
                    "JVM source execution requires a .kt source file"
                }
                require('/' !in fileName && '\\' !in fileName && '\u0000' !in fileName) {
                    "JVM source file name must be a single safe path segment"
                }
            }
        }
    }

    fun inspect(kind: JvmSourceKind, source: ByteArray): JvmSourceR1Layout = when (kind) {
        JvmSourceKind.JAVA -> JvmSourceR1JavaPolicy.inspect(source).let {
            JvmSourceR1Layout(it.sourceFileName, it.entryClassName)
        }
        JvmSourceKind.KOTLIN -> inspectKotlin(source)
    }

    private fun inspectKotlin(bytes: ByteArray): JvmSourceR1Layout {
        val decoded = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("Kotlin source is not strict UTF-8", error)
        }
        require(decoded.isNotEmpty() && '\u0000' !in decoded) {
            "Kotlin source is empty or contains NUL"
        }
        val lexical = eraseCommentsAndLiterals(decoded)
        val packageKeywords = PACKAGE_KEYWORD.findAll(lexical).toList()
        val packageDeclarations = PACKAGE_DECLARATION.findAll(lexical).toList()
        require(packageKeywords.size == packageDeclarations.size && packageDeclarations.size <= 1) {
            "Kotlin source has an unsupported package declaration"
        }
        val packageName = packageDeclarations.singleOrNull()
            ?.groupValues
            ?.get(1)
            ?.replace(WHITESPACE, "")
            .orEmpty()
        return JvmSourceR1Layout(
            sourceFileName = normalizedSourceFileName(JvmSourceKind.KOTLIN),
            entryClassName = if (packageName.isEmpty()) {
                ENTRY_SIMPLE_NAME
            } else {
                "$packageName.$ENTRY_SIMPLE_NAME"
            },
        )
    }

    private fun eraseCommentsAndLiterals(source: String): String {
        val result = StringBuilder(source.length)
        var index = 0
        var state = State.CODE
        var blockDepth = 0
        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            val third = source.getOrNull(index + 2)
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
                        blockDepth = 1
                        state = State.BLOCK_COMMENT
                        continue
                    }
                    char == '"' && next == '"' && third == '"' -> {
                        result.append("   ")
                        index += 3
                        state = State.RAW_STRING
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
                State.BLOCK_COMMENT -> when {
                    char == '/' && next == '*' -> {
                        result.append("  ")
                        index += 2
                        blockDepth++
                        continue
                    }
                    char == '*' && next == '/' -> {
                        result.append("  ")
                        index += 2
                        blockDepth--
                        if (blockDepth == 0) state = State.CODE
                        continue
                    }
                    else -> result.append(if (char == '\n' || char == '\r') char else ' ')
                }
                State.STRING, State.CHAR -> {
                    result.append(if (char == '\n' || char == '\r') char else ' ')
                    if (char == '\\' && next != null) {
                        result.append(if (next == '\n' || next == '\r') next else ' ')
                        index += 2
                        continue
                    }
                    if ((state == State.STRING && char == '"') || (state == State.CHAR && char == '\'')) {
                        state = State.CODE
                    }
                }
                State.RAW_STRING -> {
                    if (char == '"' && next == '"' && third == '"') {
                        result.append("   ")
                        index += 3
                        state = State.CODE
                        continue
                    }
                    result.append(if (char == '\n' || char == '\r') char else ' ')
                }
            }
            index++
        }
        require(state == State.CODE || state == State.LINE_COMMENT) {
            "Kotlin source contains an unterminated comment or literal"
        }
        return result.toString()
    }

    private enum class State {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        RAW_STRING,
        CHAR,
    }

    private val PACKAGE_KEYWORD = Regex("(?m)^\\s*package(?:\\s|$)")
    private val PACKAGE_DECLARATION = Regex(
        "(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\s*\\.\\s*[A-Za-z_][A-Za-z0-9_]*)*)\\s*;?\\s*$",
    )
    private val WHITESPACE = Regex("\\s+")
}
