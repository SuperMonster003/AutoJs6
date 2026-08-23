package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import java.io.File
import java.io.FileOutputStream

/** Publishes verified explicit Python results without writing back into the transported workspace. */
internal object PythonRuntimeResultPresenter {
    const val OUTPUT_DIRECTORY_NAME = ".python-artifacts"

    data class Presentation(
        val consoleLines: List<String>,
        val artifactDirectory: File?,
        val artifactFiles: List<File>,
    )

    fun present(
        source: PythonFileSource,
        executionId: Int,
        result: PythonRuntimeExecutionResult,
    ): Presentation = present(outputBaseDirectory(source), executionId, result)

    internal fun present(
        outputBaseDirectory: File,
        executionId: Int,
        result: PythonRuntimeExecutionResult,
    ): Presentation {
        val published = try {
            if (result.outputArtifacts.isEmpty()) null else publishArtifacts(
                outputBaseDirectory,
                executionId,
                result,
            )
        } catch (error: Throwable) {
            if (error is InterruptedException || error is PythonRuntimeHostException) throw error
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.RESULT_PUBLICATION_FAILED,
                "Python result artifacts could not be published",
                error,
            )
        }
        val consoleLines = buildList {
            result.structuredJson?.let { structuredJson -> add("[result] $structuredJson") }
            published?.artifactFiles?.forEach { artifact ->
                add("[artifact] ${artifact.absolutePath}")
            }
        }
        return Presentation(
            consoleLines = consoleLines,
            artifactDirectory = published?.artifactDirectory,
            artifactFiles = published?.artifactFiles.orEmpty(),
        )
    }

    internal fun outputBaseDirectory(source: PythonFileSource): File =
        (source as? PythonProjectSource)?.projectRoot
            ?: requireNotNull(source.semanticFile.parentFile) {
                "Python source has no parent directory for result publication"
            }

    private fun publishArtifacts(
        outputBaseDirectory: File,
        executionId: Int,
        result: PythonRuntimeExecutionResult,
    ): PublishedArtifacts {
        require(executionId >= 0) { "Python execution ID must not be negative" }
        checkNotInterrupted()
        val base = requireCanonicalDirectory(outputBaseDirectory, "Python result base directory")
        val container = File(base, OUTPUT_DIRECTORY_NAME).absoluteFile
        requireDirectChild(base, container, "Python result container")
        val containerCreated = createOrRequireDirectory(container, "Python result container")
        var staging: File? = null
        var published = false
        try {
            val suffix = "execution-$executionId-${result.terminal.requestId}"
            val target = File(container, suffix).absoluteFile
            val stagingDirectory = File(container, ".$suffix.pending").absoluteFile
            staging = stagingDirectory
            requireDirectChild(container, stagingDirectory, "Python result staging directory")
            requireDirectChild(container, target, "Python result directory")
            check(!stagingDirectory.exists()) { "Python result staging directory already exists" }
            check(!target.exists()) { "Python result directory already exists" }
            check(stagingDirectory.mkdir()) { "Python result staging directory could not be created" }
            requireCanonicalDirectory(stagingDirectory, "Python result staging directory")
            val relativePaths = result.outputArtifacts.map { artifact ->
                checkNotInterrupted()
                PythonRuntimeValidation.validateOutputArtifactPath(artifact.path)
                val output = resolveArtifactFile(stagingDirectory, artifact.path)
                check(!output.exists()) { "Python result artifact path already exists" }
                val bytes = artifact.toByteArray()
                try {
                    check(bytes.size.toLong() == artifact.sizeBytes) {
                        "Python result artifact length changed before publication"
                    }
                    check(PythonSha256.digest(bytes) == artifact.sha256) {
                        "Python result artifact digest changed before publication"
                    }
                    FileOutputStream(output).use { stream ->
                        stream.write(bytes)
                        stream.fd.sync()
                    }
                    check(output.isFile && output.length() == artifact.sizeBytes) {
                        "Python result artifact was not written exactly"
                    }
                } finally {
                    bytes.fill(0)
                }
                artifact.path
            }
            checkNotInterrupted()
            check(!target.exists()) { "Python result directory appeared before publication" }
            check(stagingDirectory.renameTo(target)) { "Python result staging directory could not be published" }
            published = true
            return PublishedArtifacts(
                artifactDirectory = target,
                artifactFiles = relativePaths.map { path ->
                    File(target, path.replace('/', File.separatorChar)).absoluteFile
                },
            )
        } finally {
            if (!published) {
                staging?.deleteRecursively()
                if (containerCreated && container.listFiles().orEmpty().isEmpty()) container.delete()
            }
        }
    }

    private fun resolveArtifactFile(staging: File, logicalPath: String): File {
        val segments = logicalPath.split('/')
        var parent = staging
        segments.dropLast(1).forEach { segment ->
            val child = File(parent, segment).absoluteFile
            requireDirectChild(parent, child, "Python result artifact directory")
            createOrRequireDirectory(child, "Python result artifact directory")
            parent = child
        }
        val output = File(parent, segments.last()).absoluteFile
        requireDirectChild(parent, output, "Python result artifact")
        check(output.canonicalFile == output) { "Python result artifact path is non-canonical" }
        return output
    }

    private fun createOrRequireDirectory(directory: File, label: String): Boolean {
        val created = if (directory.exists()) {
            false
        } else {
            check(directory.mkdir()) { "$label could not be created" }
            true
        }
        requireCanonicalDirectory(directory, label)
        return created
    }

    private fun requireCanonicalDirectory(directory: File, label: String): File {
        val absolute = directory.absoluteFile
        val canonical = directory.canonicalFile
        check(absolute == canonical && canonical.isDirectory) {
            "$label must be a canonical, non-symbolic-link directory"
        }
        return canonical
    }

    private fun requireDirectChild(parent: File, child: File, label: String) {
        check(child.parentFile?.canonicalFile == parent.canonicalFile) {
            "$label escapes its parent directory"
        }
    }

    private fun checkNotInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Python result publication was interrupted")
        }
    }

    private data class PublishedArtifacts(
        val artifactDirectory: File,
        val artifactFiles: List<File>,
    )
}
