package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import org.autojs.autojs.ui.edit.editor.CodeEditor

object EditorEngineFactory {

    @JvmStatic
    fun create(context: Context, preferredType: EditorEngineType = EditorEngineType.NATIVE): ScriptEditorEngine {
        return when (preferredType) {
            EditorEngineType.NATIVE -> createNative(context)
            EditorEngineType.ACE -> createNative(context)
        }
    }

    @JvmStatic
    fun createNative(context: Context): ScriptEditorEngine {
        return NativeCodeEditorEngine(CodeEditor(context))
    }
}
