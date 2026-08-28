package org.autojs.plugin.editor.api

import java.nio.charset.StandardCharsets

/**
 * Contract-2 callback through which an editor obtains one bounded, immutable project-source view.
 *
 * The callback may read the filesystem and must therefore never be invoked on the Android main
 * thread. Returning `null` means that the document is not part of an admitted TypeScript project
 * or that the project exceeds the editor snapshot budget. Editor correctness must not depend on
 * this optional hinting surface.
 */
fun interface EditorPluginProjectSnapshotProvider {
    fun capture(documentPath: String): EditorPluginProjectSnapshot?
}

data class EditorPluginProjectSnapshot(
    val schemaRevision: Int,
    val projectRootPath: String,
    val documentPath: String,
    val documentRelativePath: String,
    val targetProfile: String,
    val sourceFiles: List<EditorPluginProjectSource>,
    val sourceInventoryFingerprint: String,
    val sourceFileCount: Int,
    val sourceByteLength: Long,
)

data class EditorPluginProjectSource(
    val relativePath: String,
    val text: String,
    val utf8ByteLength: Long,
    val sha256: String,
)

object EditorPluginProjectSnapshotContract {
    const val SCHEMA_REVISION = 1
    const val TARGET_PROFILE_RHINO = "rhino"
    const val TARGET_PROFILE_NODE = "node"
    const val MAX_SOURCE_FILES = 512
    const val MAX_SINGLE_SOURCE_BYTES = 512L * 1024L
    const val MAX_SOURCE_BYTES = 8L * 1024L * 1024L
    const val MAX_RELATIVE_PATH_BYTES = 1_024

    val TARGET_PROFILES = setOf(TARGET_PROFILE_RHINO, TARGET_PROFILE_NODE)

    fun isSupported(snapshot: EditorPluginProjectSnapshot): Boolean {
        if (snapshot.schemaRevision != SCHEMA_REVISION) return false
        if (snapshot.projectRootPath.isBlank() || snapshot.documentPath.isBlank()) return false
        if (snapshot.targetProfile !in TARGET_PROFILES) return false
        if (!isCanonicalRelativePath(snapshot.documentRelativePath)) return false
        if (!isLowerSha256(snapshot.sourceInventoryFingerprint)) return false
        if (snapshot.sourceFileCount != snapshot.sourceFiles.size) return false
        if (snapshot.sourceFiles.size !in 1..MAX_SOURCE_FILES) return false
        if (snapshot.sourceByteLength !in 0..MAX_SOURCE_BYTES) return false

        var totalBytes = 0L
        val paths = HashSet<String>(snapshot.sourceFiles.size)
        val foldedPaths = HashSet<String>(snapshot.sourceFiles.size)
        for (source in snapshot.sourceFiles) {
            if (!isCanonicalRelativePath(source.relativePath)) return false
            if (!paths.add(source.relativePath)) return false
            if (!foldedPaths.add(source.relativePath.asciiCaseFold())) return false
            if (source.utf8ByteLength !in 0..MAX_SINGLE_SOURCE_BYTES) return false
            if (source.text.toByteArray(StandardCharsets.UTF_8).size.toLong() != source.utf8ByteLength) {
                return false
            }
            if (!isLowerSha256(source.sha256)) return false
            totalBytes = try {
                Math.addExact(totalBytes, source.utf8ByteLength)
            } catch (_: ArithmeticException) {
                return false
            }
            if (totalBytes > MAX_SOURCE_BYTES) return false
        }
        return totalBytes == snapshot.sourceByteLength &&
            snapshot.sourceFiles.any { source ->
                source.relativePath == snapshot.documentRelativePath
            }
    }

    private fun isCanonicalRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
        if (path.toByteArray(StandardCharsets.UTF_8).size > MAX_RELATIVE_PATH_BYTES) return false
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
}
