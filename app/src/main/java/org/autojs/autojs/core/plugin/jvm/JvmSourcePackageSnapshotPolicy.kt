package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayoutPolicy
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourcePackageCodec
import org.autojs.plugin.jvmsource.api.JvmSourcePackagePathPolicy
import java.io.File
import java.util.Locale

internal data class JvmSourcePackageSnapshotData(
    val archiveBytes: ByteArray,
    val sourceFileName: String,
    val entryClassName: String,
    val sourceFileCount: Int,
    val sourceContentBytes: Long,
)

/** Host-side source-tree admission. Provider validation intentionally repeats every content check. */
internal object JvmSourcePackageSnapshotPolicy {
    enum class NodeType { DIRECTORY, REGULAR_FILE, SYMBOLIC_LINK, OTHER, MISSING }

    interface Operations {
        fun typeNoFollow(file: File): NodeType
        fun children(directory: File): List<File>?
        fun readRegularFile(file: File, maximumBytes: Long): ByteArray
    }

    fun collect(
        sourceRoot: File,
        entryFile: File,
        operations: Operations,
        entrySimpleName: String = JvmSourceR1Policy.ENTRY_SIMPLE_NAME,
    ): JvmSourcePackageSnapshotData {
        JvmSourceR1JavaPolicy.requireEntrySimpleName(entrySimpleName)
        val root = sourceRoot.absoluteFile
        require(root.canonicalFile == root && operations.typeNoFollow(root) == NodeType.DIRECTORY) {
            "Java source package root must be an ordinary canonical directory"
        }
        val entry = entryFile.absoluteFile
        val rootPrefix = root.path + File.separator
        require(entry.canonicalFile == entry && entry.path.startsWith(rootPrefix)) {
            "Java source package entry escaped its root"
        }

        val raw = linkedMapOf<String, ByteArray>()
        val physicalFiles = linkedMapOf<String, File>()
        collectDirectory(root, root, "", operations, raw, physicalFiles)
        require(raw.size in 2..JvmSourceContract.MAX_SOURCE_FILES) {
            "Java source package must contain between 2 and ${JvmSourceContract.MAX_SOURCE_FILES} files"
        }
        val entryPath = physicalFiles.entries.singleOrNull { (_, file) -> file == entry }?.key
            ?: throw IllegalArgumentException("Java source package entry was not an admitted source file")

        val normalized = linkedMapOf<String, ByteArray>()
        var entryClassName: String? = null
        raw.forEach { (path, bytes) ->
            val simpleName = path.substringAfterLast('/').removeSuffix(".java")
            val inspection = JvmJavaSourceLayoutPolicy.inspect(bytes, simpleName)
            val expectedPath = JvmSourcePackagePathPolicy.sourcePathForClassName(
                inspection.layout.entryClassName,
            )
            require(path == expectedPath) {
                "Java source package path '$path' differs from its declared package"
            }
            if (path == entryPath) {
                require(simpleName == entrySimpleName) {
                    "Java source package entry file must match the selected entry simple name"
                }
                entryClassName = inspection.layout.entryClassName
            }
            normalized[path] = inspection.normalizedSource.toByteArray(Charsets.UTF_8)
        }
        val selectedEntryClass = requireNotNull(entryClassName)
        val selectedEntryPath = JvmSourcePackagePathPolicy.sourcePathForClassName(selectedEntryClass)
        require(selectedEntryPath == entryPath) { "Java source package entry layout is inconsistent" }
        val archive = JvmSourcePackageCodec.encode(selectedEntryPath, normalized)
        return JvmSourcePackageSnapshotData(
            archiveBytes = archive,
            sourceFileName = "$entrySimpleName.java",
            entryClassName = selectedEntryClass,
            sourceFileCount = normalized.size,
            sourceContentBytes = normalized.values.sumOf { it.size.toLong() },
        )
    }

    private fun collectDirectory(
        root: File,
        directory: File,
        relativeDirectory: String,
        operations: Operations,
        sources: LinkedHashMap<String, ByteArray>,
        physicalFiles: LinkedHashMap<String, File>,
    ) {
        val children = operations.children(directory)
            ?: throw IllegalArgumentException("Unable to enumerate Java source package directory")
        require(children.isNotEmpty()) { "Java source package contains an empty directory" }
        val ordered = children.sortedBy(File::getName)
        require(ordered.map { it.name.lowercase(Locale.ROOT) }.distinct().size == ordered.size) {
            "Java source package contains case-folding sibling collisions"
        }
        ordered.forEach { listed ->
            require(listed.parentFile?.absoluteFile == directory.absoluteFile && listed.name.isNotEmpty()) {
                "Java source package enumeration returned an invalid child"
            }
            val file = listed.absoluteFile
            require(file.canonicalFile == file && file.path.startsWith(root.path + File.separator)) {
                "Java source package child escaped its root or used a link"
            }
            val relative = if (relativeDirectory.isEmpty()) listed.name else "$relativeDirectory/${listed.name}"
            when (operations.typeNoFollow(file)) {
                NodeType.DIRECTORY -> {
                    require(DIRECTORY_SEGMENT.matches(listed.name)) {
                        "Java source package directory name is outside the ASCII identifier profile"
                    }
                    require(relative.count { it == '/' } < JvmSourceContract.MAX_SOURCE_PACKAGE_DEPTH) {
                        "Java source package nesting exceeds the protocol limit"
                    }
                    collectDirectory(root, file, relative, operations, sources, physicalFiles)
                }
                NodeType.REGULAR_FILE -> {
                    JvmSourcePackagePathPolicy.requireSourcePath(relative)
                    require(sources.size < JvmSourceContract.MAX_SOURCE_FILES) {
                        "Java source package contains too many files"
                    }
                    val consumed = sources.values.sumOf { it.size.toLong() }
                    val remaining = JvmSourceContract.MAX_SOURCE_BYTES - consumed
                    require(remaining > 0L) { "Java source package exceeds the source-byte limit" }
                    val bytes = operations.readRegularFile(file, remaining)
                    require(bytes.isNotEmpty() && bytes.size.toLong() <= remaining) {
                        "Java source package file is empty or exceeds the remaining source-byte limit"
                    }
                    sources[relative] = bytes
                    physicalFiles[relative] = file
                }
                NodeType.SYMBOLIC_LINK ->
                    throw IllegalArgumentException("Java source package must not contain symbolic links")
                NodeType.OTHER, NodeType.MISSING ->
                    throw IllegalArgumentException("Java source package contains a non-regular entry")
            }
        }
    }

    private val DIRECTORY_SEGMENT = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
}
