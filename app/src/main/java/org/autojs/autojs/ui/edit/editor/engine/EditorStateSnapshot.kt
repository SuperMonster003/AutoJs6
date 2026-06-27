package org.autojs.autojs.ui.edit.editor.engine

data class EditorStateSnapshot(
    val text: String,
    val dirty: Boolean,
    val cursorLine: Int,
    val cursorColumn: Int,
    val selection: EditorSelection?,
    val firstVisibleLine: Int?,
    val firstVisibleColumn: Int?,
    val breakpoints: List<EditorBreakpoint>,
    val debuggingLine: Int,
    val readOnly: Boolean,
    val fontSizeSp: Float,
    val themeKey: String?,
    val searchQuery: String?,
    val searchOptions: EditorSearchOptions?,
)
