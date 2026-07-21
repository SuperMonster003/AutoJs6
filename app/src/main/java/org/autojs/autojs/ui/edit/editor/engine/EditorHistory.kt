package org.autojs.autojs.ui.edit.editor.engine

enum class EditorHistoryDirection {
    UNDO,
    REDO,
}

fun interface EditorHistoryOperationListener {
    fun onHistoryOperation(token: String, direction: EditorHistoryDirection)
}
