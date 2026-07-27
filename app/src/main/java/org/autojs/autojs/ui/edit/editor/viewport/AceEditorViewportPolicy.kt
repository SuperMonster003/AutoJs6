package org.autojs.autojs.ui.edit.editor.viewport

import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

class AceEditorViewportPolicy(
    private val editor: ScriptEditorEngine,
) : EditorViewportPolicy {

    override fun onImeInsetsChanged(
        visible: Boolean,
        bottomInset: Int,
        editorBottomOverlap: Int,
    ) {
        val view = editor.view
        if (view.paddingBottom != 0) {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
        }
        editor.onImeInsetsChanged(visible, bottomInset)
    }
}
