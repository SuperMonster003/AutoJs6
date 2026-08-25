package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Flat, host-private staging directory. Only fixed names or generated numeric source names exist. */
internal class R8CompilerTransactionWorkspace private constructor(
    val directory: File,
) : Closeable {
    private val closed = AtomicBoolean(false)

    val inputBundleFile: File = child("input.bundle")
    val outputBundleFile: File = child("output.bundle")

    fun sourceFile(index: Int): File {
        require(index >= 0)
        return child("source-${index.toString().padStart(3, '0')}.bin")
    }

    fun artifactFile(role: R8ArtifactRole): File = child(
        when (role) {
            R8ArtifactRole.DEX_ZIP -> "artifact-dex.zip"
            R8ArtifactRole.MAPPING_TEXT -> "artifact-mapping.txt"
            R8ArtifactRole.SEEDS_TEXT -> "artifact-seeds.txt"
            R8ArtifactRole.USAGE_TEXT -> "artifact-usage.txt"
            R8ArtifactRole.RETRACE_METADATA -> "artifact-retrace.bin"
        },
    )

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null
        directory.listFiles().orEmpty().forEach { child ->
            if (child.parentFile?.canonicalFile != directory.canonicalFile || child.isDirectory) {
                failure = failure ?: IllegalStateException("Unexpected R8 transaction workspace child")
                return@forEach
            }
            if (!child.delete() && child.exists()) {
                failure = failure ?: IllegalStateException("Unable to delete R8 transaction staging file")
            }
        }
        if (!directory.delete() && directory.exists()) {
            failure = failure ?: IllegalStateException("Unable to delete R8 transaction workspace")
        }
        failure?.let { throw it }
    }

    private fun child(name: String): File {
        require(SAFE_CHILD.matches(name))
        return File(directory, name).canonicalFile.also {
            require(it.parentFile == directory.canonicalFile) { "R8 workspace child escaped its root" }
        }
    }

    companion object {
        private val SAFE_CHILD = Regex("[a-z0-9][a-z0-9.-]{0,127}")
        private val SESSION_NAME = Regex("session-[0-9a-f]{32}")

        fun create(rootDirectory: File): R8CompilerTransactionWorkspace {
            val root = rootDirectory.canonicalFile
            if (!root.exists()) check(root.mkdirs()) { "Unable to create R8 transaction root" }
            require(root.isDirectory) { "R8 transaction root is not a directory" }
            repeat(16) {
                val name = "session-${UUID.randomUUID().toString().replace("-", "")}"
                check(SESSION_NAME.matches(name))
                val directory = File(root, name).canonicalFile
                require(directory.parentFile == root)
                if (directory.mkdir()) return R8CompilerTransactionWorkspace(directory)
            }
            error("Unable to reserve a unique R8 transaction workspace")
        }

        /** Startup-only recovery. Active dispatchers must not call this method. */
        fun recoverStale(rootDirectory: File) {
            val root = rootDirectory.canonicalFile
            if (!root.exists()) {
                check(root.mkdirs()) { "Unable to create R8 transaction root" }
                return
            }
            require(root.isDirectory)
            root.listFiles().orEmpty().filter { SESSION_NAME.matches(it.name) }.forEach { directory ->
                val canonical = directory.canonicalFile
                if (canonical.parentFile != root || !canonical.isDirectory) return@forEach
                canonical.listFiles().orEmpty().forEach { child ->
                    if (child.canonicalFile.parentFile == canonical && !child.isDirectory) child.delete()
                }
                canonical.delete()
            }
        }
    }
}
