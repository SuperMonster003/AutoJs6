package org.autojs.autojs.ui.edit.editor.service

import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeAction
import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeActionContract
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Replays a contract-4 request against the host-owned active buffer before one-shot approval. */
object EditorCurrentDocumentCodeActionAuthorizer {

    fun isAuthorized(
        action: EditorPluginCurrentDocumentCodeAction,
        currentText: String,
        readOnly: Boolean,
    ): Boolean {
        if (readOnly) return false
        if (!EditorPluginCurrentDocumentCodeActionContract.isSupported(action)) return false
        if (currentText.length > EditorPluginCurrentDocumentCodeActionContract.MAX_DOCUMENT_UTF16_LENGTH) {
            return false
        }
        if (sha256(currentText) != action.baseContentSha256) return false

        val result = StringBuilder(currentText)
        action.edits.asReversed().forEach { edit ->
            if (edit.endOffset > currentText.length) return false
            if (!isUtf16Boundary(currentText, edit.startOffset)) return false
            if (!isUtf16Boundary(currentText, edit.endOffset)) return false
            result.replace(edit.startOffset, edit.endOffset, edit.newText)
        }
        if (result.length > EditorPluginCurrentDocumentCodeActionContract.MAX_DOCUMENT_UTF16_LENGTH) {
            return false
        }
        val resultText = result.toString()
        return resultText != currentText && sha256(resultText) == action.resultContentSha256
    }

    private fun isUtf16Boundary(text: String, offset: Int): Boolean =
        offset in 0..text.length &&
            (offset == 0 || offset == text.length ||
                !Character.isHighSurrogate(text[offset - 1]) ||
                !Character.isLowSurrogate(text[offset]))

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
