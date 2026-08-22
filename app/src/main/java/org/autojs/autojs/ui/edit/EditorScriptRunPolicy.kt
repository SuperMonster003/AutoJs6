package org.autojs.autojs.ui.edit

import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs6.BuildConfig

internal object EditorScriptRunPolicy {

    fun isRunEnabledForDocument(fileNameOrPath: String?): Boolean {
        return when (JvmSourceKind.fromFileName(fileNameOrPath)) {
            // Kotlin has no packaged provider yet (Roadmap v2 M4); keep its run entry hidden.
            JvmSourceKind.KOTLIN -> false
            JvmSourceKind.JAVA -> BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED
            null -> !JvmSourceKind.isUnsupportedJvmFileName(fileNameOrPath)
        }
    }

    fun temporaryScriptExtensionWithDot(fileNameOrPath: String?): String {
        JvmSourceKind.recognizedExtension(fileNameOrPath)?.let { return ".$it" }
        val fileName = fileNameOrPath
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf(String::isNotBlank)
            ?: return ".js"
        if (fileName.endsWith(".node.js", ignoreCase = true)) {
            return ".node.js"
        }
        if (!JavaScriptSource.isJavaScriptFileName(fileName)) {
            return ".js"
        }
        return "." + fileName.substringAfterLast('.', "js")
    }
}
