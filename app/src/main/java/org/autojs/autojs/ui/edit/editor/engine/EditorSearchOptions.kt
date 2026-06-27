package org.autojs.autojs.ui.edit.editor.engine

data class EditorSearchOptions(
    val usingRegex: Boolean = false,
    val caseSensitive: Boolean = true,
    val wholeWord: Boolean = false,
)
