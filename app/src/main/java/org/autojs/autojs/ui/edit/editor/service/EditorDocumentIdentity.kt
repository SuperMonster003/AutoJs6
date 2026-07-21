package org.autojs.autojs.ui.edit.editor.service

import android.net.Uri
import java.io.File

/** Stable document identity shared by IO locks and lifecycle draft persistence. */
fun Uri.editorDocumentIdentity(): String {
    if (scheme.equals("file", ignoreCase = true)) {
        val rawPath = path ?: return normalizeScheme().toString()
        return runCatching { File(rawPath).canonicalPath }
            .getOrElse { File(rawPath).absolutePath }
    }
    return normalizeScheme().toString()
}
