package org.autojs.autojs.core.console

object StackFrameParser {

    private val stackFramePattern = Regex(
        """(^|[\s(])((?:file:)?(?:/[^\s)]+?|[^\s():]+?)(?:\.node\.js|\.(?:mjs|cjs|mts|cts|tsx?|js))):(\d+)(?::(\d+))?""",
        RegexOption.IGNORE_CASE,
    )

    @JvmStatic
    fun parse(text: CharSequence?): List<StackFrameLink> {
        if (text.isNullOrEmpty()) {
            return emptyList()
        }
        return stackFramePattern.findAll(text)
            .mapNotNull { match ->
                val sourceName = match.groupValues[2]
                val sourceRange = match.groups[2]?.range ?: return@mapNotNull null
                val lineNumber = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                if (lineNumber <= 0) {
                    return@mapNotNull null
                }
                val columnNumber = match.groups[4]?.value?.toIntOrNull() ?: 0
                StackFrameLink(
                    sourceName = sourceName,
                    lineIndex = lineNumber - 1,
                    columnNumber = columnNumber,
                    start = sourceRange.first,
                    end = match.range.last + 1,
                )
            }
            .toList()
    }

    data class StackFrameLink(
        val sourceName: String,
        val lineIndex: Int,
        val columnNumber: Int,
        val start: Int,
        val end: Int,
    )
}
