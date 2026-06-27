package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import org.autojs.autojs.ui.edit.editor.ace.AceCodeEditor
import org.autojs.autojs.ui.edit.editor.CodeEditor

object EditorEngineFactory {

    @JvmStatic
    fun create(context: Context, preferredType: EditorEngineType = EditorEngineType.ACE): ScriptEditorEngine {
        return when (preferredType) {
            EditorEngineType.NATIVE -> createNativeCodeEditorEngine(context)
            EditorEngineType.ACE -> createAceOrFallback(context)
        }
    }

    @JvmStatic
    fun createNativeCodeEditorEngine(context: Context): NativeCodeEditorEngine {
        return NativeCodeEditorEngine(CodeEditor(context))
    }

    @JvmStatic
    fun createAceOrFallback(context: Context): ScriptEditorEngine {
        return if (AceCodeEditor.hasRequiredAssets(context)) {
            AceScriptEditorEngine(context)
        } else {
            createNativeCodeEditorEngine(context)
        }
    }
}
