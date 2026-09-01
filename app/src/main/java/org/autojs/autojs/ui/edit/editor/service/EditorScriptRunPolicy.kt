package org.autojs.autojs.ui.edit.editor.service

import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs6.BuildConfig

/** Selects the temporary source suffix used to route an editor-buffer execution. */
internal object EditorScriptRunPolicy {

    fun isRunEnabledForDocument(
        documentDisplayName: String?,
        uriPath: String?,
        editorName: String?,
    ): Boolean {
        val classification = sequenceOf(documentDisplayName, uriPath, editorName)
            .filterNotNull()
            .filter(String::isNotBlank)
            .mapNotNull { candidate ->
                when (JvmSourceKind.fromFileName(candidate)) {
                    JvmSourceKind.JAVA,
                    JvmSourceKind.KOTLIN,
                    -> JvmDocumentClass.JVM_SOURCE
                    null -> if (JvmSourceKind.isUnsupportedJvmFileName(candidate)) {
                        JvmDocumentClass.BLOCKED
                    } else {
                        null
                    }
                }
            }
            .firstOrNull()
        return when (classification) {
            JvmDocumentClass.JVM_SOURCE -> BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED
            JvmDocumentClass.BLOCKED -> false
            null -> true
        }
    }

    fun temporaryExtensionWithDot(
        documentDisplayName: String?,
        uriPath: String?,
        editorName: String?,
    ): String {
        return sequenceOf(documentDisplayName, uriPath, editorName)
            .filterNotNull()
            .filter(String::isNotBlank)
            .mapNotNull(::recognizedExtensionWithDot)
            .firstOrNull()
            ?: DEFAULT_EXTENSION_WITH_DOT
    }

    private fun recognizedExtensionWithDot(candidate: String): String? {
        val fileName = candidate
            .substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .substringAfterLast('/')
            .takeIf(String::isNotBlank)
            ?: return null
        JvmSourceKind.recognizedExtension(fileName)?.let { return ".$it" }
        if (fileName.endsWith(PYTHON_EXTENSION_WITH_DOT, ignoreCase = true)) {
            return PYTHON_EXTENSION_WITH_DOT
        }
        if (fileName.endsWith(NODE_JS_EXTENSION_WITH_DOT, ignoreCase = true)) {
            return NODE_JS_EXTENSION_WITH_DOT
        }
        BUN_EXTENSIONS_WITH_DOT.firstOrNull { extension ->
            fileName.endsWith(extension, ignoreCase = true)
        }?.let { return it }
        if (LuaScriptSource.isLuaFileName(fileName)) {
            return LUA_EXTENSION_WITH_DOT
        }
        if (JavaScriptSource.isJavaScriptFileName(fileName)) {
            return "." + fileName.substringAfterLast('.', "js")
        }
        return null
    }

    private const val DEFAULT_EXTENSION_WITH_DOT = ".js"
    private const val PYTHON_EXTENSION_WITH_DOT = ".py"
    private const val NODE_JS_EXTENSION_WITH_DOT = ".node.js"
    private val BUN_EXTENSIONS_WITH_DOT = listOf(".bun.js", ".bun.ts", ".bun.tsx")
    private const val LUA_EXTENSION_WITH_DOT = ".lua"

    private enum class JvmDocumentClass {
        JVM_SOURCE,
        BLOCKED,
    }
}
