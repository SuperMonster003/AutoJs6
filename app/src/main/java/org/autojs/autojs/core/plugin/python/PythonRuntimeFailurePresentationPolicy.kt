package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionError

/** Pure console presentation policy. The structured provider error remains unchanged. */
internal object PythonRuntimeFailurePresentationPolicy {

    fun messageFor(source: PythonFileSource, error: PythonExecutionError): String {
        val traceback = error.traceback.joinToString(separator = "\n") { frame ->
            "  at ${frame.fileName}:${frame.lineNumber} in ${frame.functionName}" +
                frame.sourceLine?.let { "\n    $it" }.orEmpty()
        }
        return buildString {
            append(error.code.name).append('/').append(error.phase.name).append(": ").append(error.message)
            error.exceptionType?.let { append(" [").append(it).append(']') }
            error.exceptionMessage?.let { append(' ').append(it) }
            if (traceback.isNotEmpty()) append('\n').append(traceback)
            if (shouldSuggestExplicitProject(source, error)) {
                append('\n').append(MISSING_LOCAL_MODULE_HINT)
            }
        }
    }

    private fun shouldSuggestExplicitProject(
        source: PythonFileSource,
        error: PythonExecutionError,
    ): Boolean =
        source !is PythonProjectSource &&
            error.code == PythonErrorCode.PYTHON_EXCEPTION &&
            error.exceptionType == MODULE_NOT_FOUND_ERROR

    private const val MODULE_NOT_FOUND_ERROR = "ModuleNotFoundError"
    internal const val MISSING_LOCAL_MODULE_HINT =
        "Hint: standalone Python files include only the selected source. " +
            "To import sibling or other local modules, run an explicit Python project " +
            "(`project.json` with `type: python`)."
}
