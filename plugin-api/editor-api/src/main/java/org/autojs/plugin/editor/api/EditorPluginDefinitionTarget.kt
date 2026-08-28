package org.autojs.plugin.editor.api

import java.nio.charset.StandardCharsets

/**
 * Contract-3 request to navigate to a definition admitted by the current immutable project view.
 *
 * This is deliberately not an arbitrary URI. The plugin identifies one project-relative target,
 * its current content, and the inventory that authorized it. The host must independently resolve
 * and validate those fields before it changes the active document.
 */
data class EditorPluginDefinitionTarget(
    val kind: EditorPluginDefinitionTargetKind,
    val projectRootPath: String,
    val relativePath: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column,
    val contentSha256: String,
    val projectSourceInventoryFingerprint: String? = null,
    val dependencyInventoryFingerprint: String? = null,
)

enum class EditorPluginDefinitionTargetKind {
    PROJECT_SOURCE,
    DEPENDENCY_DECLARATION,
}

object EditorPluginDefinitionTargetContract {
    const val MAX_PROJECT_ROOT_PATH_BYTES = 4_096
    const val MAX_RELATIVE_PATH_BYTES = EditorPluginProjectSnapshotContract.MAX_RELATIVE_PATH_BYTES
    const val MAX_LINE = 10_000_000
    const val MAX_COLUMN = 1_000_000

    fun isSupported(target: EditorPluginDefinitionTarget): Boolean {
        if (!isBoundedPath(target.projectRootPath, MAX_PROJECT_ROOT_PATH_BYTES)) return false
        if (!isCanonicalRelativePath(target.relativePath)) return false
        if (!isLowerSha256(target.contentSha256)) return false
        if (!isValidRange(target)) return false

        val segments = target.relativePath.split('/')
        return when (target.kind) {
            EditorPluginDefinitionTargetKind.PROJECT_SOURCE ->
                segments.none { segment -> segment.equals("node_modules", ignoreCase = true) } &&
                    isLowerSha256(target.projectSourceInventoryFingerprint) &&
                    target.dependencyInventoryFingerprint == null

            EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION ->
                segments.firstOrNull() == "node_modules" &&
                    segments.size >= 3 &&
                    isDeclarationPath(target.relativePath) &&
                    target.projectSourceInventoryFingerprint == null &&
                    isLowerSha256(target.dependencyInventoryFingerprint)
        }
    }

    private fun isBoundedPath(path: String, maximumBytes: Int): Boolean =
        path.isNotBlank() &&
            path.toByteArray(StandardCharsets.UTF_8).size <= maximumBytes &&
            path.none { character -> Character.isISOControl(character.code) }

    private fun isCanonicalRelativePath(path: String): Boolean {
        if (!isBoundedPath(path, MAX_RELATIVE_PATH_BYTES)) return false
        if (path.startsWith('/') || '\\' in path) return false
        return path.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." &&
                segment.none { character -> Character.isISOControl(character.code) }
        }
    }

    private fun isValidRange(target: EditorPluginDefinitionTarget): Boolean {
        if (target.line !in 0..MAX_LINE || target.endLine !in 0..MAX_LINE) return false
        if (target.column !in 0..MAX_COLUMN || target.endColumn !in 0..MAX_COLUMN) return false
        return target.endLine > target.line ||
            target.endLine == target.line && target.endColumn >= target.column
    }

    private fun isDeclarationPath(path: String): Boolean {
        val folded = path.lowercase()
        return folded.endsWith(".d.ts") || folded.endsWith(".d.mts") || folded.endsWith(".d.cts")
    }

    private fun isLowerSha256(value: String?): Boolean =
        value?.length == 64 && value.all { character ->
            character in '0'..'9' || character in 'a'..'f'
        }
}
