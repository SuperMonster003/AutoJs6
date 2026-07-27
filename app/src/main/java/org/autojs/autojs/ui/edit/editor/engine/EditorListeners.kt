package org.autojs.autojs.ui.edit.editor.engine

fun interface EditorBreakpointChangeListener {
    fun onBreakpointChange(line: Int, enabled: Boolean)

    fun onAllBreakpointRemoved(count: Int) {
        /* Optional. */
    }
}

fun interface EditorCursorChangeListener {
    fun onCursorChange(lineText: String, line: Int, column: Int)
}

fun interface EditorTextChangeListener {
    fun onTextChanged(text: CharSequence?)
}
