package org.autojs.autojs.ui.edit.editor.engine

data class EditorSelection(
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
) {
    val isCollapsed: Boolean
        get() = startOffset == endOffset

    companion object {
        val EMPTY = EditorSelection(
            startOffset = 0,
            endOffset = 0,
            startLine = 0,
            startColumn = 0,
            endLine = 0,
            endColumn = 0,
        )
    }
}
