package org.autojs.autojs.ui.edit.editor.viewport

import android.view.View
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

class NativeEditorViewportPolicy(
    private val editor: ScriptEditorEngine,
    private val hostView: View,
) : EditorViewportPolicy {

    override fun onImeInsetsChanged(visible: Boolean, bottomInset: Int) {
        val view = editor.view
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
        editor.onImeInsetsChanged(visible, bottomInset)
        if (visible) {
            hostView.post { editor.ensureCursorVisibleIfPossible() }
        }
    }
}
