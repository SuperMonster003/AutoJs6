package org.autojs.autojs.ui.edit.editor.service

import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.LuaScriptSource

/** Selects the temporary source suffix used to route an editor-buffer execution. */
internal object EditorScriptRunPolicy {

    fun temporaryExtensionWithDot(
        documentDisplayName: String?,
        uriPath: String?,
        editorName: String?,
    ): String {
        val candidate = sequenceOf(documentDisplayName, uriPath, editorName)
            .firstOrNull { !it.isNullOrBlank() }
            ?: return DEFAULT_EXTENSION_WITH_DOT
        return recognizedExtensionWithDot(candidate) ?: DEFAULT_EXTENSION_WITH_DOT
    }

    private fun recognizedExtensionWithDot(candidate: String): String? {
        val fileName = candidate
            .substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .substringAfterLast('/')
            .takeIf(String::isNotBlank)
            ?: return null
        if (fileName.endsWith(NODE_JS_EXTENSION_WITH_DOT, ignoreCase = true)) {
            return NODE_JS_EXTENSION_WITH_DOT
        }
        if (LuaScriptSource.isLuaFileName(fileName)) {
            return LUA_EXTENSION_WITH_DOT
        }
        if (JavaScriptSource.isJavaScriptFileName(fileName)) {
            return "." + fileName.substringAfterLast('.', "js")
        }
        return null
    }

    private const val DEFAULT_EXTENSION_WITH_DOT = ".js"
    private const val NODE_JS_EXTENSION_WITH_DOT = ".node.js"
    private const val LUA_EXTENSION_WITH_DOT = ".lua"
}
