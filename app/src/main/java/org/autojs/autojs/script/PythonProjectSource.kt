package org.autojs.autojs.script

import java.io.File
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/**
 * A strictly admitted Python project. [projectEntryFile] is the semantic entry on disk, while
 * [file] may be an immutable editor overlay transported as SOURCE. [projectRoot] is snapshotted
 * into the workspace descriptor without duplicating the semantic entry.
 */
class PythonProjectSource private constructor(
    val projectRoot: File,
    entryFile: File,
    sourceFile: File,
    val entryPoint: String,
) : PythonFileSource(sourceFile, entryFile) {

    val projectEntryFile: File
        get() = semanticFile

    companion object {
        @JvmStatic
        fun create(projectRoot: File, entryFile: File): PythonProjectSource {
            return createWithSource(projectRoot, entryFile, entryFile)
        }

        internal fun createWithSource(
            projectRoot: File,
            entryFile: File,
            sourceFile: File,
        ): PythonProjectSource {
            val root = projectRoot.canonicalFile
            val entry = entryFile.canonicalFile
            val source = sourceFile.canonicalFile
            require(root.isDirectory) { "Python project root must be a directory" }
            require(entry.isFile) { "Python project entry point must be a regular file" }
            require(source.isFile) { "Python project source snapshot must be a regular file" }
            val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
            val ignoreCase = WINDOWS_ABSOLUTE_PATH.matches(root.path)
            require(entry.path.startsWith(rootPrefix, ignoreCase = ignoreCase)) {
                "Python project entry point escapes the project root"
            }
            val relative = entry.path
                .substring(rootPrefix.length)
                .replace(File.separatorChar, '/')
            validateEntryPoint(relative)
            return PythonProjectSource(root, entry, source, relative).apply {
                if (source != entry) overriddenFullPath = entry.path
            }
        }

        private fun validateEntryPoint(value: String) {
            require(value.isNotBlank()) { "Python project entry point must not be blank" }
            require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_ENTRY_POINT_BYTES) {
                "Python project entry point exceeds the protocol limit"
            }
            require(!value.startsWith('/') && '\\' !in value && value.none(Character::isISOControl)) {
                "Python project entry point must be a normalized relative path"
            }
            require(Normalizer.normalize(value, Normalizer.Form.NFC) == value) {
                "Python project entry point must use Unicode NFC normalization"
            }
            require(value.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                "Python project entry point contains an unsafe path segment"
            }
            require(value.endsWith(".py", ignoreCase = true)) {
                "Python project entry point must use the .py extension"
            }
        }

        private const val MAX_ENTRY_POINT_BYTES = 4 * 1024
        private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[\\\\/].*")
    }
}
