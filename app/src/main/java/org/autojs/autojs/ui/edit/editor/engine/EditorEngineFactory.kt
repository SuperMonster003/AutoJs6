package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import org.autojs.autojs.core.plugin.editor.EditorPluginLoadException
import org.autojs.autojs.ui.edit.editor.CodeEditor

object EditorEngineFactory {

    data class AcePluginFailure(
        val code: String,
        val message: String,
    )

    @Volatile
    private var lastAcePluginFailure: AcePluginFailure? = null

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
        lastAcePluginFailure = null
        return try {
            AceScriptEditorEngine.create(context)
        } catch (error: EditorPluginLoadException) {
            createAceFallback(context, error.code.name, error.message)
        } catch (error: LinkageError) {
            createAceFallback(context, "LINKAGE_ERROR", error.message)
        } catch (error: Exception) {
            createAceFallback(context, error.javaClass.simpleName, error.message)
        }
    }

    fun lastAcePluginFailure(): AcePluginFailure? = lastAcePluginFailure

    private fun createAceFallback(context: Context, code: String, message: String?): ScriptEditorEngine {
        lastAcePluginFailure = AcePluginFailure(
            code = code,
            message = message?.takeIf(String::isNotBlank) ?: "ACE editor plugin is unavailable",
        )
        return createNativeCodeEditorEngine(context)
    }
}
