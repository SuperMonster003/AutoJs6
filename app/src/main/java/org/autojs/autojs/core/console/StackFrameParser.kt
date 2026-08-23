package org.autojs.autojs.core.console

object StackFrameParser {

    private val compilationDiagnosticPattern = Regex(
        """(?m)^[\t ]*((?:file:)?[^\r\n]+?(?:\.node\.js|\.(?:mjs|cjs|mts|cts|tsx?|js))):(\d+):(\d+)[\t ]+-[\t ]+(?:error|warning|suggestion|message)[\t ]+\S+:""",
        RegexOption.IGNORE_CASE,
    )

    private val stackFramePattern = Regex(
        """(^|[\s(])((?:file:)?(?:/[^\s)]+?|[^\s():]+?)(?:\.node\.js|\.(?:mjs|cjs|mts|cts|tsx?|js))):(\d+)(?::(\d+))?""",
        RegexOption.IGNORE_CASE,
    )

    @JvmStatic
    fun parse(text: CharSequence?): List<StackFrameLink> {
        if (text.isNullOrEmpty()) {
            return emptyList()
        }
        val diagnosticLinks = compilationDiagnosticPattern.findAll(text).mapNotNull { match ->
            match.toStackFrameLink(sourceGroup = 1, lineGroup = 2, columnGroup = 3)
        }.toList()
        val stackFrameLinks = stackFramePattern.findAll(text).mapNotNull { match ->
            match.toStackFrameLink(sourceGroup = 2, lineGroup = 3, columnGroup = 4)
        }.filter { candidate ->
            diagnosticLinks.none { diagnostic ->
                candidate.start < diagnostic.end && diagnostic.start < candidate.end
            }
        }
        return (diagnosticLinks + stackFrameLinks)
            .distinctBy { link ->
                listOf(
                    link.sourceName,
                    link.lineIndex,
                    link.columnNumber,
                    link.start,
                    link.end,
                )
            }
            .sortedBy(StackFrameLink::start)
            .toList()
    }

    private fun MatchResult.toStackFrameLink(
        sourceGroup: Int,
        lineGroup: Int,
        columnGroup: Int,
    ): StackFrameLink? {
        val sourceName = groupValues[sourceGroup]
        val sourceRange = groups[sourceGroup]?.range ?: return null
        val lineNumber = groupValues[lineGroup].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val parsedColumn = groups[columnGroup]?.value?.toIntOrNull()
        if (groups[columnGroup] != null && (parsedColumn == null || parsedColumn <= 0)) {
            return null
        }
        val locationEnd = groups[columnGroup]?.range?.last
            ?: groups[lineGroup]?.range?.last
            ?: return null
        return StackFrameLink(
            sourceName = sourceName,
            lineIndex = lineNumber - 1,
            columnNumber = parsedColumn?.minus(1) ?: 0,
            start = sourceRange.first,
            end = locationEnd + 1,
        )
    }

    data class StackFrameLink(
        val sourceName: String,
        val lineIndex: Int,
        val columnNumber: Int,
        val start: Int,
        val end: Int,
    )
}
