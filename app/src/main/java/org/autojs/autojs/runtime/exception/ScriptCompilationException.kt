package org.autojs.autojs.runtime.exception

import java.util.Locale

/** A source location and message reported by a script-language compiler. */
data class ScriptCompilationDiagnostic(
    val sourceName: String?,
    val lineNumber: Int?,
    val columnNumber: Int?,
    val severity: ScriptCompilationDiagnosticSeverity,
    val code: String,
    val message: String,
    val relatedInformation: List<ScriptCompilationDiagnostic> = emptyList(),
) {

    init {
        require(sourceName == null || sourceName.isNotBlank())
        require(lineNumber == null || lineNumber > 0)
        require(columnNumber == null || columnNumber > 0)
        require((lineNumber == null) == (columnNumber == null))
        require(code.isNotBlank())
    }

    val consoleLine: String
        get() = buildString {
            sourceName?.let { source ->
                append(source)
                lineNumber?.let { line ->
                    append(':')
                    append(line)
                    append(':')
                    append(columnNumber)
                }
                append(" - ")
            }
            append(severity.name.lowercase(Locale.ROOT))
            append(' ')
            append(code)
            append(": ")
            append(message)
        }
}

enum class ScriptCompilationDiagnosticSeverity {
    ERROR,
    WARNING,
    SUGGESTION,
    MESSAGE,
}

/**
 * Stable host exception for failures reported by a script-language compiler.
 *
 * [message] is a short user-facing summary. [consoleMessage] contains the structured diagnostic
 * locations that the Console can turn into editor links without exposing an implementation stack.
 */
open class ScriptCompilationException(
    message: String,
    diagnostics: List<ScriptCompilationDiagnostic>,
    cause: Throwable? = null,
) : ScriptException(message, cause) {

    val diagnostics: List<ScriptCompilationDiagnostic> = diagnostics.map { it.snapshot() }

    val consoleMessage: String = buildString {
        append(message)
        this@ScriptCompilationException.diagnostics.forEach { diagnostic ->
            append('\n')
            append(diagnostic.consoleLine)
            diagnostic.relatedInformation.forEach { related ->
                append("\n  ")
                append(related.consoleLine)
            }
        }
    }
}

private fun ScriptCompilationDiagnostic.snapshot(): ScriptCompilationDiagnostic = copy(
    relatedInformation = relatedInformation.map { it.snapshot() },
)
