package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.autojs.plugin.editor.api.EditorPluginDefinitionTarget
import org.autojs.plugin.editor.api.EditorPluginDefinitionTargetContract
import org.autojs.plugin.editor.api.EditorPluginDefinitionTargetKind
import org.autojs.plugin.editor.api.EditorPluginProjectSnapshotContract

internal object TypeScriptEditorDefinitionTargetResolver {

    data class ResolvedTarget(
        val documentPath: String,
        val targetPath: String,
        val line: Int,
        val column: Int,
        val endLine: Int,
        val endColumn: Int,
        val kind: EditorPluginDefinitionTargetKind,
    ) {
        val isCurrentDocument: Boolean
            get() = documentPath == targetPath
    }

    fun resolve(
        documentPath: String,
        target: EditorPluginDefinitionTarget,
        currentDocumentText: String? = null,
    ): ResolvedTarget? =
        runCatching { resolveRequired(documentPath, target, currentDocumentText) }.getOrNull()

    internal fun resolveRequired(
        documentPath: String,
        target: EditorPluginDefinitionTarget,
        currentDocumentText: String? = null,
    ): ResolvedTarget? {
        if (!EditorPluginDefinitionTargetContract.isSupported(target)) return null
        val snapshot = TypeScriptEditorProjectSnapshotProvider.captureRequired(documentPath) ?: return null
        if (target.projectRootPath != snapshot.projectRootPath) return null
        val root = File(snapshot.projectRootPath).normalizedCanonicalFile()
        if (!root.isDirectory || root.path != snapshot.projectRootPath) return null

        val authorizedSha256 = when (target.kind) {
            EditorPluginDefinitionTargetKind.PROJECT_SOURCE -> {
                if (
                    target.projectSourceInventoryFingerprint !=
                    snapshot.sourceInventoryFingerprint
                ) {
                    return null
                }
                snapshot.sourceFiles.firstOrNull { source ->
                    source.relativePath == target.relativePath
                }?.sha256 ?: return null
            }

            EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION -> {
                val dependencySnapshot = TypeScriptEditorDependencySnapshotProvider.captureRequired(
                    snapshot.projectRootPath,
                ) ?: return null
                if (
                    dependencySnapshot.projectRootPath != snapshot.projectRootPath ||
                    target.dependencyInventoryFingerprint !=
                    dependencySnapshot.inventoryFingerprint
                ) {
                    return null
                }
                dependencySnapshot.contentSha256ByRelativePath[target.relativePath] ?: return null
            }
        }
        if (authorizedSha256 != target.contentSha256) return null

        val requestedTarget = File(root, target.relativePath.replace('/', File.separatorChar))
        val targetFile = requestedTarget.normalizedCanonicalFile()
        if (
            !targetFile.isFile ||
            !targetFile.isInside(root) ||
            requestedTarget.absoluteFile.normalize().path != targetFile.path
        ) {
            return null
        }
        if (target.kind == EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION) {
            val requestedDependencyRoot = File(root, NODE_MODULES_DIRECTORY)
            val dependencyRoot = requestedDependencyRoot.normalizedCanonicalFile()
            if (
                !dependencyRoot.isDirectory ||
                requestedDependencyRoot.absoluteFile.normalize().path != dependencyRoot.path ||
                !targetFile.isInside(dependencyRoot)
            ) {
                return null
            }
        }
        val maximumBytes = when (target.kind) {
            EditorPluginDefinitionTargetKind.PROJECT_SOURCE ->
                EditorPluginProjectSnapshotContract.MAX_SINGLE_SOURCE_BYTES
            EditorPluginDefinitionTargetKind.DEPENDENCY_DECLARATION ->
                MAX_DEPENDENCY_TARGET_BYTES
        }
        val bytes = readBounded(targetFile, maximumBytes) ?: return null
        try {
            if (sha256(bytes) != target.contentSha256) return null
            val text = TypeScriptUtf8.decode(bytes, "TypeScript editor definition target")
            if (!containsRange(text, target)) return null
        } finally {
            bytes.fill(0)
        }
        val canonicalDocument = localDocumentFile(snapshot.documentPath)
            ?.normalizedCanonicalFile()
            ?: return null
        if (canonicalDocument.path != snapshot.documentPath) return null
        if (
            targetFile.path == canonicalDocument.path &&
            !matchesAuthorizedContent(currentDocumentText, target.contentSha256)
        ) {
            return null
        }
        return ResolvedTarget(
            documentPath = canonicalDocument.path,
            targetPath = targetFile.path,
            line = target.line,
            column = target.column,
            endLine = target.endLine,
            endColumn = target.endColumn,
            kind = target.kind,
        )
    }

    private fun containsRange(text: String, target: EditorPluginDefinitionTarget): Boolean {
        val lines = text.split('\n')
        if (target.line !in lines.indices || target.endLine !in lines.indices) return false
        val startLength = lines[target.line].removeSuffix("\r").length
        val endLength = lines[target.endLine].removeSuffix("\r").length
        return target.column <= startLength && target.endColumn <= endLength
    }

    private fun readBounded(file: File, maximumBytes: Long): ByteArray? {
        if (file.length() !in 0..maximumBytes) return null
        val bytes = file.readBytes()
        if (bytes.size.toLong() > maximumBytes) {
            bytes.fill(0)
            return null
        }
        return bytes
    }

    private fun localDocumentFile(documentPath: String): File? =
        File(documentPath).takeIf(File::isAbsolute)

    private fun matchesAuthorizedContent(text: String?, expectedSha256: String): Boolean {
        val bytes = text?.toByteArray(StandardCharsets.UTF_8) ?: return false
        return try {
            sha256(bytes) == expectedSha256
        } finally {
            bytes.fill(0)
        }
    }

    private fun File.isInside(root: File): Boolean = startsWith(root)

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
        }

    private const val NODE_MODULES_DIRECTORY = "node_modules"
    private const val MAX_DEPENDENCY_TARGET_BYTES = 8L * 1024L * 1024L
}
