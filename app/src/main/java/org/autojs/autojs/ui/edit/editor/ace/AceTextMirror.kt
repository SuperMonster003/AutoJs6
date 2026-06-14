package org.autojs.autojs.ui.edit.editor.ace

class AceTextMirror(initialText: String = "") {

    private var builder = StringBuilder(initialText)

    var revision: Long = 0L
        private set

    val text: String
        get() = builder.toString()

    val length: Int
        get() = builder.length

    fun reset(text: String) {
        builder = StringBuilder(text)
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
        builder.insert(offsetForPosition(delta.start), delta.text)
        return true
    }

    private fun applyRemove(delta: AceTextDelta): Boolean {
        val startOffset = offsetForPosition(delta.start)
        val endOffset = offsetForPosition(delta.end)
        if (endOffset < startOffset) {
            return false
        }
        builder.delete(startOffset, endOffset)
        return true
    }

    private fun offsetForPosition(position: AceTextDelta.Position): Int {
        val targetRow = position.row.coerceAtLeast(0)
        val targetColumn = position.column.coerceAtLeast(0)
        var offset = 0
        var row = 0

        while (row < targetRow && offset < builder.length) {
            val nextLine = builder.indexOf("\n", offset)
            if (nextLine < 0) {
                return builder.length
            }
            offset = nextLine + 1
            row++
        }

        val lineEnd = builder.indexOf("\n", offset).let { index ->
            if (index < 0) builder.length else index
        }
        return (offset + targetColumn).coerceAtMost(lineEnd)
    }

    companion object {
        const val SMALL_TEXT_CALIBRATION_LIMIT = 512 * 1024
    }
}
