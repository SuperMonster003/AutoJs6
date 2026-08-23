package org.autojs.autojs.core.plugin.typescript

/** Maps execution-scoped V8 stack locations from generated Node modules back to TypeScript. */
internal object TypeScriptNodeStackTraceFormatter {

    fun format(binding: TypeScriptSourceMapBinding, stack: String): String? {
        if (stack.isBlank()) return null
        var mapped = false
        val rendered = logicalLines(stack).map { line ->
            val frame = parseFrame(line) ?: return@map line
            if (!sameGeneratedFile(frame.sourceName, binding.generatedSourceName)) {
                return@map line
            }
            val original = binding.originalPositionFor(
                frameSourceName = binding.generatedSourceName,
                generatedLineNumber = frame.lineNumber,
                generatedColumnIndex = frame.columnNumber - 1,
            ) ?: return@map line
            mapped = true
            buildString(line.length) {
                append(frame.prefix)
                append(original.sourceName)
                append(':')
                append(original.lineNumber)
                append(':')
                append(original.columnNumber)
                append(frame.suffix)
            }
        }.joinToString("\n")
        return rendered.takeIf { mapped }
    }

    private fun logicalLines(stack: String): Sequence<String> =
        if ('\n' in stack || '\r' in stack) {
            stack.lineSequence()
        } else {
            FLATTENED_FRAME_BOUNDARY.split(stack)
                .asSequence()
                .map(String::trimEnd)
        }

    private fun parseFrame(line: String): Frame? {
        val payloadEnd = if (line.endsWith(')')) line.length - 1 else line.length
        if (payloadEnd <= 0) return null
        val columnSeparator = line.lastIndexOf(':', payloadEnd - 1)
        if (columnSeparator < 0) return null
        val lineSeparator = line.lastIndexOf(':', columnSeparator - 1)
        if (lineSeparator < 0) return null
        val lineNumber = line.substring(lineSeparator + 1, columnSeparator).toIntOrNull()
            ?.takeIf { it > 0 } ?: return null
        val columnNumber = line.substring(columnSeparator + 1, payloadEnd).toIntOrNull()
            ?.takeIf { it > 0 } ?: return null
        val locationStart = if (payloadEnd < line.length) {
            line.lastIndexOf('(', lineSeparator - 1).takeIf { it >= 0 }?.plus(1)
        } else {
            line.lastIndexOf(' ', lineSeparator - 1).takeIf { it >= 0 }?.plus(1)
        } ?: return null
        if (locationStart >= lineSeparator) return null
        val sourceName = line.substring(locationStart, lineSeparator)
        if (sourceName.isBlank()) return null
        return Frame(
            prefix = line.substring(0, locationStart),
            sourceName = sourceName,
            lineNumber = lineNumber,
            columnNumber = columnNumber,
            suffix = line.substring(payloadEnd),
        )
    }

    private fun sameGeneratedFile(frameSourceName: String, generatedSourceName: String): Boolean {
        fun basename(value: String): String = value
            .replace('\\', '/')
            .substringAfterLast('/')
        return basename(frameSourceName) == basename(generatedSourceName)
    }

    private data class Frame(
        val prefix: String,
        val sourceName: String,
        val lineNumber: Int,
        val columnNumber: Int,
        val suffix: String,
    )

    private val FLATTENED_FRAME_BOUNDARY = Regex("(?= {4,}at )")
}
