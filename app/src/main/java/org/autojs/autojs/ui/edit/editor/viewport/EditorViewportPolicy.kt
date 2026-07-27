package org.autojs.autojs.ui.edit.editor.viewport

interface EditorViewportPolicy {

    /**
     * [bottomInset] is the reported host IME height, while [editorBottomOverlap] is only the
     * portion still covering the editor after the host window/root layout has handled the IME.
     */
    fun onImeInsetsChanged(
        visible: Boolean,
        bottomInset: Int,
        editorBottomOverlap: Int = bottomInset,
    )
}
