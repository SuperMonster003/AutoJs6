package org.autojs.autojs.ui.edit.editor.viewport

import android.view.View
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

object EditorViewportPolicyFactory {

    fun create(editor: ScriptEditorEngine, hostView: View): EditorViewportPolicy {
        return when (editor.engineType) {
            EditorEngineType.NATIVE -> NativeEditorViewportPolicy(editor, hostView)
            EditorEngineType.ACE -> AceEditorViewportPolicy(editor)
        }
    }
}
