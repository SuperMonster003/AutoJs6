package org.autojs.autojs.core.plugin.typescript

import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Owns the clear-text transport archives used for one compiler request.
 *
 * A process can be stopped before [close] runs. The next allocation therefore removes archives
 * left by an earlier process before creating a new pair. Active paths are tracked process-wide so
 * concurrent callers cannot remove each other's files.
 */
internal class TypeScriptCompilerTemporaryArchives private constructor(
    val inputFile: File,
    val outputFile: File,
) : Closeable {

    private var closed = false

    @Throws(IOException::class)
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            val failures = listOf(inputFile, outputFile).filterNot(::deleteArchive)
            activePaths.remove(inputFile.canonicalPath)
            activePaths.remove(outputFile.canonicalPath)
            if (failures.isNotEmpty()) {
                throw IOException(
                    "Unable to securely delete TypeScript compiler temporary archive(s): " +
                        failures.joinToString { file -> file.name },
                )
            }
        }
    }

    internal companion object {
        private const val INPUT_PREFIX = "typescript-input-"
        private const val OUTPUT_PREFIX = "typescript-output-"
        private const val ARCHIVE_SUFFIX = ".zip"
        private val lock = Any()
        private val activePaths = mutableSetOf<String>()

        @Throws(IOException::class)
        fun create(cacheDirectory: File): TypeScriptCompilerTemporaryArchives =
            synchronized(lock) {
                val cacheRoot = cacheDirectory.canonicalFile
                if (!cacheRoot.isDirectory) {
                    throw IOException("The application cache directory is unavailable.")
                }
                removeOrphanedArchives(cacheRoot)

                var inputFile: File? = null
                var outputFile: File? = null
                try {
                    inputFile = createArchive(cacheRoot, INPUT_PREFIX)
                    outputFile = createArchive(cacheRoot, OUTPUT_PREFIX)
                    activePaths += inputFile.canonicalPath
                    activePaths += outputFile.canonicalPath
                    TypeScriptCompilerTemporaryArchives(inputFile, outputFile)
                } catch (error: Exception) {
                    inputFile?.let(::deleteArchive)
                    outputFile?.let(::deleteArchive)
                    throw if (error is IOException) error else IOException(
                        "Unable to create TypeScript compiler temporary archives.",
                        error,
                    )
                }
            }

        @Throws(IOException::class)
        private fun removeOrphanedArchives(cacheRoot: File) {
            val children = cacheRoot.listFiles()
                ?: throw IOException("Unable to inspect the application cache directory.")
            children
                .filter { candidate -> isCompilerArchiveName(candidate.name) }
                .forEach { candidate ->
                    val archive = candidate.canonicalFile
                    if (archive.parentFile != cacheRoot) {
                        throw IOException(
                            "A TypeScript compiler temporary archive escaped the cache directory.",
                        )
                    }
                    if (archive.canonicalPath in activePaths) return@forEach
                    if (!archive.isFile || !archive.delete()) {
                        throw IOException(
                            "Unable to securely delete orphaned TypeScript compiler archive: " +
                                candidate.name,
                        )
                    }
                }
        }

        @Throws(IOException::class)
        private fun createArchive(cacheRoot: File, prefix: String): File =
            File.createTempFile(prefix, ARCHIVE_SUFFIX, cacheRoot).canonicalFile.also { archive ->
                if (archive.parentFile != cacheRoot || !archive.isFile) {
                    archive.delete()
                    throw IOException(
                        "A TypeScript compiler temporary archive escaped the cache directory.",
                    )
                }
            }

        private fun isCompilerArchiveName(name: String): Boolean =
            name.endsWith(ARCHIVE_SUFFIX) &&
                (name.startsWith(INPUT_PREFIX) || name.startsWith(OUTPUT_PREFIX))

        private fun deleteArchive(archive: File): Boolean =
            !archive.exists() || archive.isFile && archive.delete()
    }
}
