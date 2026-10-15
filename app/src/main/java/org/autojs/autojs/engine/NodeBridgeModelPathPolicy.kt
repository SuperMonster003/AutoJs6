package org.autojs.autojs.engine

import java.io.File
import java.io.IOException

/**
 * Where a model-facing plugin session (MCP Server, 3-Stove Agent) may touch files by path (MCP roadmap
 * P11 D26). Reads: the working directory always, and the primary shared storage read-only when the
 * request declares [NodeBridgePermissionManifest.FILES_SHARED_READ], minus `Android/data` and
 * `Android/obb`. Writes: the working directory only. Scripts never go through this policy.
 *
 * Pure Kotlin over `java.io.File`; the Android storage permission is supplied by [hasStorageAccess]
 * so the arithmetic is unit-testable.
 */
internal class NodeBridgeModelPathPolicy(
    workingDirectory: File,
    sharedStorageRoot: File?,
    private val hasStorageAccess: () -> Boolean,
) {

    enum class Location { WORKING_DIRECTORY, SHARED_STORAGE }

    class Resolved(val file: File, val location: Location)

    val workingRoot: File = canonicalize(workingDirectory)
    val sharedRoot: File? = sharedStorageRoot?.let { canonicalize(it) }
    private val hiddenSharedDirectories: List<File> = sharedRoot?.let { root -> HIDDEN_SHARED.map { File(root, it) } }.orEmpty()

    /** Resolves a read target; throws [SecurityException] with the reason the caller should see. */
    fun resolveRead(rawPath: String, permissions: Collection<String>, label: String): Resolved {
        val file = canonicalize(fileOf(rawPath))
        if (file.isInside(workingRoot)) return Resolved(file, Location.WORKING_DIRECTORY)
        val root = sharedRoot
        if (root == null || !file.isInside(root)) {
            throw SecurityException("AutoJs6 $label path is outside the working directory: $rawPath (shared storage is readable only with files.shared_read)")
        }
        if (isHiddenInShared(file)) {
            throw SecurityException("AutoJs6 $label cannot read $rawPath: Android/data and Android/obb are never readable")
        }
        val declared = permissions.map(NodeBridgePermissionManifest::normalizeCapability)
        if (NodeBridgePermissionManifest.FILES_SHARED_READ !in declared) {
            throw SecurityException("AutoJs6 $label reads shared storage only when the request declares files.shared_read: $rawPath")
        }
        if (!hasStorageAccess()) {
            throw SecurityException("AutoJs6 has no all-files access permission; grant it in AutoJs6 settings before reading shared storage: $rawPath")
        }
        return Resolved(file, Location.SHARED_STORAGE)
    }

    /** Resolves a write target; only the working directory is writable for these sessions. */
    fun resolveWrite(rawPath: String, label: String): Resolved {
        val file = canonicalize(fileOf(rawPath))
        if (!file.isInside(workingRoot)) {
            throw SecurityException("AutoJs6 $label can only write inside the working directory: $rawPath")
        }
        return Resolved(file, Location.WORKING_DIRECTORY)
    }

    /** `Android/data` and `Android/obb` under the shared root, and anything below them. */
    fun isHiddenInShared(file: File): Boolean = hiddenSharedDirectories.any { file.isInside(it) }

    /** The forward-slash path of a shared-storage [file] relative to [sharedRoot], `.` for the root itself. */
    fun sharedRelativePath(file: File): String {
        val root = checkNotNull(sharedRoot) { "shared storage root is unavailable" }
        if (file.path == root.path) return "."
        require(file.isInside(root)) { "file is outside shared storage: ${file.path}" }
        return file.path.substring(root.path.trimEnd(File.separatorChar).length + 1).replace(File.separatorChar, '/')
    }

    private fun fileOf(rawPath: String): File {
        val file = File(rawPath)
        return if (file.isAbsolute) file else File(workingRoot, rawPath)
    }

    /**
     * Canonicalizes through the nearest existing ancestor so a symbolic link in the middle still
     * cannot escape, while a not-yet-existing tail (write targets) keeps its literal segments.
     */
    private fun canonicalize(file: File): File {
        var existing: File? = file.absoluteFile
        val tail = ArrayList<String>()
        while (existing != null && !existing.exists()) {
            tail.add(0, existing.name)
            existing = existing.parentFile
        }
        val base = try {
            (existing ?: return file.absoluteFile).canonicalFile
        } catch (error: IOException) {
            throw SecurityException("AutoJs6 cannot resolve ${file.path}: ${error.message}")
        }
        return tail.fold(base) { parent, name -> File(parent, name) }
    }

    private fun File.isInside(root: File): Boolean =
        path == root.path || path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)

    private companion object {
        val HIDDEN_SHARED = listOf("Android${File.separator}data", "Android${File.separator}obb")
    }
}
