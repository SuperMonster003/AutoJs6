package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import org.autojs.autojs.ui.edit.editor.CodeEditor

object EditorEngineFactory {

    @JvmStatic
    fun create(context: Context, preferredType: EditorEngineType = EditorEngineType.NATIVE): ScriptEditorEngine {
        return when (preferredType) {
            EditorEngineType.NATIVE -> createNativeCodeEditorEngine(context)
            EditorEngineType.ACE -> createNativeCodeEditorEngine(context)
        }
    }

    @JvmStatic
    fun createNativeCodeEditorEngine(context: Context): NativeCodeEditorEngine {
        return NativeCodeEditorEngine(CodeEditor(context))
    }
}
