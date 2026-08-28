package org.autojs.plugin.editor.api

import java.nio.charset.StandardCharsets

/**
 * Contract-4 request to apply one TypeScript code action to the active document buffer.
 *
 * The model deliberately has no path, URI, file descriptor, command or secondary-file field.
 * The active editor session is the only possible target. [baseContentSha256] binds the request to
 * the exact text on which it was computed, while [resultContentSha256] lets the host independently
 * replay and verify every edit before it grants one-shot approval.
 */
data class EditorPluginCurrentDocumentCodeAction(
    val kind: EditorPluginCurrentDocumentCodeActionKind,
    val title: String,
    val diagnosticCode: Int,
    val baseContentSha256: String,
    val resultContentSha256: String,
    val edits: List<EditorPluginTextEdit>,
)

enum class EditorPluginCurrentDocumentCodeActionKind {
    AUTO_IMPORT,
    SPELLING_CORRECTION,
}

/** UTF-16 offsets into the exact active-document text identified by the action's base hash. */
data class EditorPluginTextEdit(
    val startOffset: Int,
    val endOffset: Int,
    val newText: String,
)

object EditorPluginCurrentDocumentCodeActionContract {
    const val MAX_TITLE_BYTES = 512
    const val MAX_DIAGNOSTIC_CODE = 999_999
    const val MAX_DOCUMENT_UTF16_LENGTH = 1_048_576
    const val MAX_EDIT_COUNT = 32
    const val MAX_REPLACEMENT_TEXT_BYTES = 65_536
    const val MAX_TOTAL_REPLACEMENT_TEXT_BYTES = 131_072

    fun isSupported(action: EditorPluginCurrentDocumentCodeAction): Boolean {
        if (!isBoundedTitle(action.title)) return false
        if (action.diagnosticCode !in 1..MAX_DIAGNOSTIC_CODE) return false
        if (!isLowerSha256(action.baseContentSha256)) return false
        if (!isLowerSha256(action.resultContentSha256)) return false
        if (action.baseContentSha256 == action.resultContentSha256) return false
        if (action.edits.isEmpty() || action.edits.size > MAX_EDIT_COUNT) return false

        var previousStart = -1
        var previousEnd = -1
        var replacementBytes = 0L
        action.edits.forEach { edit ->
            if (edit.startOffset !in 0..MAX_DOCUMENT_UTF16_LENGTH) return false
            if (edit.endOffset !in edit.startOffset..MAX_DOCUMENT_UTF16_LENGTH) return false
            if (edit.startOffset == edit.endOffset && edit.newText.isEmpty()) return false
            if (!isWellFormedUtf16(edit.newText)) return false
            val bytes = edit.newText.toByteArray(StandardCharsets.UTF_8).size
            if (bytes > MAX_REPLACEMENT_TEXT_BYTES) return false
            replacementBytes += bytes
            if (replacementBytes > MAX_TOTAL_REPLACEMENT_TEXT_BYTES) return false

            if (previousStart >= 0) {
                if (edit.startOffset <= previousStart) return false
                if (edit.startOffset < previousEnd) return false
            }
            previousStart = edit.startOffset
            previousEnd = edit.endOffset
        }
        return true
    }

    private fun isBoundedTitle(title: String): Boolean =
        title.isNotBlank() &&
            title.toByteArray(StandardCharsets.UTF_8).size <= MAX_TITLE_BYTES &&
            title.none { character -> Character.isISOControl(character.code) }

    private fun isLowerSha256(value: String): Boolean =
        value.length == 64 && value.all { character ->
            character in '0'..'9' || character in 'a'..'f'
        }

    private fun isWellFormedUtf16(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                Character.isHighSurrogate(character) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                        return false
                    }
                    index += 2
                }
                Character.isLowSurrogate(character) -> return false
                else -> index++
            }
        }
        return true
    }
}
