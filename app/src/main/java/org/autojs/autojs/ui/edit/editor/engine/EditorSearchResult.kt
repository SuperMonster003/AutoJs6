package org.autojs.autojs.ui.edit.editor.engine

data class EditorSearchResult(
    val found: Boolean,
    val foundIndex: Int = -1,
    val query: String? = null,
)
