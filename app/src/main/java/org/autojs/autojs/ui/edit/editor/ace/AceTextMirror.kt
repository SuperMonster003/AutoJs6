package org.autojs.autojs.ui.edit.editor.ace

class AceTextMirror(initialText: String = "") {

    private var builder = StringBuilder(initialText)
    private var lineSeparator = detectLineSeparator(initialText)

    var revision: Long = 0L
        private set

    val text: String
        get() = builder.toString()

    val length: Int
        get() = builder.length

    fun reset(text: String) {
        builder = StringBuilder(text)
        lineSeparator = detectLineSeparator(text)
        revision++
    }

    fun applyDelta(delta: AceTextDelta): Boolean {
        return runCatching {
            val applied = when (delta.action) {
                AceTextDelta.Action.INSERT -> applyInsert(delta)
                AceTextDelta.Action.REMOVE -> applyRemove(delta)
            }
            if (applied) {
                revision++
            }
            applied
        }.getOrDefault(false)
    }

    private fun applyInsert(delta: AceTextDelta): Boolean {
        val startOffset = offsetForPosition(delta.start) ?: return false
        builder.insert(startOffset, deltaText(delta))
        return true
    }

    private fun applyRemove(delta: AceTextDelta): Boolean {
        val startOffset = offsetForPosition(delta.start) ?: return false
        val endOffset = offsetForPosition(delta.end) ?: return false
        if (endOffset < startOffset) {
            return false
        }
        if (builder.substring(startOffset, endOffset) != deltaText(delta)) {
            return false
        }
        builder.delete(startOffset, endOffset)
        return true
    }

    private fun deltaText(delta: AceTextDelta): String {
        return delta.lines.joinToString(lineSeparator)
    }

    private fun offsetForPosition(position: AceTextDelta.Position): Int? {
        if (position.row < 0 || position.column < 0) {
            return null
        }
        val targetRow = position.row
        val targetColumn = position.column
        var offset = 0
        var row = 0

        while (row < targetRow) {
            val nextLine = builder.indexOf("\n", offset)
            if (nextLine < 0) {
                return null
            }
            offset = nextLine + 1
            row++
        }

        val lineEnd = builder.indexOf("\n", offset).let { index ->
            if (index < 0) builder.length else index
        }
        val logicalLineEnd = if (lineEnd > offset && builder[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
        if (targetColumn > logicalLineEnd - offset) {
            return null
        }
        return offset + targetColumn
    }

    companion object {
        const val SMALL_TEXT_CALIBRATION_LIMIT = 512 * 1024

        private fun detectLineSeparator(text: String): String {
            return if (text.contains("\r\n")) "\r\n" else "\n"
        }
    }
}
