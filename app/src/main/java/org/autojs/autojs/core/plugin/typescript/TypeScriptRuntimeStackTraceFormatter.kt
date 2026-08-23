package org.autojs.autojs.core.plugin.typescript

import org.autojs.autojs.engine.JavaScriptEngine
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.ScriptStackElement

/** Rewrites only execution-scoped TypeScript frames; unrelated Rhino frames stay untouched. */
internal object TypeScriptRuntimeStackTraceFormatter {

    @JvmStatic
    fun format(engine: JavaScriptEngine, error: Throwable): String? {
        val binding = engine.getTag(TypeScriptExecutionCompiler.SOURCE_MAP_ENGINE_TAG)
            as? TypeScriptSourceMapBinding ?: return null
        val rhinoError = error as? RhinoException ?: return null
        return runCatching {
            format(
                binding = binding,
                details = rhinoError.details(),
                frames = rhinoError.scriptStack.toList(),
                originSourceName = rhinoError.sourceName(),
                originLineNumber = rhinoError.lineNumber(),
            )
        }.getOrNull()
    }

    internal fun format(
        binding: TypeScriptSourceMapBinding,
        details: String,
        frames: List<ScriptStackElement>,
        originSourceName: String? = null,
        originLineNumber: Int = 0,
    ): String? {
        var mappedAnyFrame = false
        val renderedFrames = frames.map { frame ->
            val original = binding.originalPositionFor(
                frameSourceName = frame.fileName,
                generatedLineNumber = frame.lineNumber,
            )
            if (original == null) {
                RenderedFrame(frame, mapped = false)
            } else {
                mappedAnyFrame = true
                RenderedFrame(
                    ScriptStackElement(
                        original.sourceName,
                        frame.functionName,
                        original.lineNumber,
                    ),
                    mapped = true,
                )
            }
        }.toMutableList()

        if (!mappedAnyFrame && !originSourceName.isNullOrBlank() && originLineNumber > 0) {
            binding.originalPositionFor(originSourceName, originLineNumber)?.let { original ->
                mappedAnyFrame = true
                renderedFrames.add(
                    0,
                    RenderedFrame(
                        ScriptStackElement(original.sourceName, null, original.lineNumber),
                        mapped = true,
                    ),
                )
            }
        }
        if (!mappedAnyFrame) {
            return null
        }

        return buildString {
            appendLine(details)
            renderedFrames.forEach { rendered ->
                if (rendered.mapped) {
                    renderMappedFrame(rendered.frame)
                } else {
                    rendered.frame.renderV8Style(this)
                }
                appendLine()
            }
        }.trimEnd()
    }

    /** Rhino renders its unavailable column as `:0`; mapped TypeScript frames omit it. */
    private fun StringBuilder.renderMappedFrame(frame: ScriptStackElement) {
        append("    at ")
        val functionName = frame.functionName
        if (functionName == null || functionName == "anonymous" || functionName == "undefined") {
            append(frame.fileName)
            append(':')
            append(frame.lineNumber)
            return
        }
        append(functionName)
        append(" (")
        append(frame.fileName)
        append(':')
        append(frame.lineNumber)
        append(')')
    }

    private data class RenderedFrame(
        val frame: ScriptStackElement,
        val mapped: Boolean,
    )
}
