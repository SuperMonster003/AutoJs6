package org.autojs.plugin.editor.api

import java.nio.charset.StandardCharsets

/**
 * Contract-5 request for one bounded project-symbol rename transaction.
 *
 * The plugin supplies only canonical project-relative paths, immutable source-inventory identity
 * and replayable UTF-16 edits. The host must recapture the project, verify every base/result hash,
 * show its own preview and publish or roll back the complete transaction. Absolute target paths,
 * dependency files, commands, file creation and file deletion cannot be represented by this model.
 */
data class EditorPluginProjectRenameRequest(
    val projectRootPath: String,
    val projectSourceInventoryFingerprint: String,
    val documentRelativePath: String,
    val symbolName: String,
    val newName: String,
    val files: List<EditorPluginProjectRenameFile>,
)

data class EditorPluginProjectRenameFile(
    val relativePath: String,
    val baseContentSha256: String,
    val resultContentSha256: String,
    val edits: List<EditorPluginTextEdit>,
)

object EditorPluginProjectRenameContract {
    const val MAX_PROJECT_ROOT_PATH_BYTES = 4_096
    const val MAX_IDENTIFIER_BYTES = 256
    const val MAX_FILE_COUNT = 128
    const val MAX_EDIT_COUNT_PER_FILE = 512
    const val MAX_TOTAL_EDIT_COUNT = 4_096
    const val MAX_DOCUMENT_UTF16_LENGTH = 1_048_576
    const val MAX_TOTAL_REPLACEMENT_TEXT_BYTES = 1_048_576

    fun isSupported(request: EditorPluginProjectRenameRequest): Boolean {
        if (!isBoundedProjectRoot(request.projectRootPath)) return false
        if (!isLowerSha256(request.projectSourceInventoryFingerprint)) return false
        if (!isCanonicalRelativePath(request.documentRelativePath)) return false
        if (!isIdentifier(request.symbolName) || !isIdentifier(request.newName)) return false
        if (request.symbolName == request.newName) return false
        if (request.files.size !in 2..MAX_FILE_COUNT) return false

        var previousPath: String? = null
        var totalEdits = 0
        var totalReplacementBytes = 0L
        var containsDocument = false
        val foldedPaths = HashSet<String>(request.files.size)
        request.files.forEach { file ->
            if (!isCanonicalRelativePath(file.relativePath)) return false
            val precedingPath = previousPath
            if (precedingPath != null && file.relativePath <= precedingPath) return false
            previousPath = file.relativePath
            if (!foldedPaths.add(file.relativePath.asciiCaseFold())) return false
            if (file.relativePath == request.documentRelativePath) containsDocument = true
            if (!isLowerSha256(file.baseContentSha256)) return false
            if (!isLowerSha256(file.resultContentSha256)) return false
            if (file.baseContentSha256 == file.resultContentSha256) return false
            if (file.edits.size !in 1..MAX_EDIT_COUNT_PER_FILE) return false

            var previousStart = -1
            var previousEnd = -1
            file.edits.forEach { edit ->
                if (edit.startOffset !in 0..MAX_DOCUMENT_UTF16_LENGTH) return false
                if (edit.endOffset !in edit.startOffset..MAX_DOCUMENT_UTF16_LENGTH) return false
                if (edit.endOffset - edit.startOffset != request.symbolName.length) return false
                if (edit.newText != request.newName) return false
                if (previousStart >= 0) {
                    if (edit.startOffset <= previousStart) return false
                    if (edit.startOffset < previousEnd) return false
                }
                previousStart = edit.startOffset
                previousEnd = edit.endOffset
            }

            totalEdits += file.edits.size
            if (totalEdits > MAX_TOTAL_EDIT_COUNT) return false
            totalReplacementBytes += request.newName.toByteArray(StandardCharsets.UTF_8).size.toLong() *
                file.edits.size
            if (totalReplacementBytes > MAX_TOTAL_REPLACEMENT_TEXT_BYTES) return false
        }
        return containsDocument
    }

    private fun isBoundedProjectRoot(path: String): Boolean =
        path.isNotBlank() &&
            path.toByteArray(StandardCharsets.UTF_8).size <= MAX_PROJECT_ROOT_PATH_BYTES &&
            path.none { character -> Character.isISOControl(character.code) }

    private fun isIdentifier(value: String): Boolean =
        value.isNotEmpty() &&
            value.toByteArray(StandardCharsets.UTF_8).size <= MAX_IDENTIFIER_BYTES &&
            IDENTIFIER.matches(value)

    private fun isCanonicalRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
        if (
            path.toByteArray(StandardCharsets.UTF_8).size >
            EditorPluginProjectSnapshotContract.MAX_RELATIVE_PATH_BYTES
        ) {
            return false
        }
        return path.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." &&
                segment.none { character -> Character.isISOControl(character.code) }
        }
    }

    private fun isLowerSha256(value: String): Boolean =
        value.length == 64 && value.all { character ->
            character in '0'..'9' || character in 'a'..'f'
        }

    private fun String.asciiCaseFold(): String = buildString(length) {
        this@asciiCaseFold.forEach { character ->
            append(if (character in 'A'..'Z') character + ('a' - 'A') else character)
        }
    }

    private val IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
}
