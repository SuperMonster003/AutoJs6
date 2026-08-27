package org.autojs.autojs.core.plugin.r8

import java.io.Closeable
import java.io.File
import java.util.UUID

internal class R8RetraceTransactionWorkspace private constructor(
    private val directory: File,
) : Closeable {
    val inputBundleFile = child(INPUT_BUNDLE)
    val outputStackTraceFile = child(OUTPUT_STACK)

    override fun close() {
        var failure: Throwable? = null
        val children = directory.listFiles().orEmpty()
        children.forEach { child ->
            if (child.canonicalFile.parentFile != directory || child.name !in CHILDREN ||
                child.isDirectory || !child.delete()
            ) {
                failure = failure ?: IllegalStateException("Unable to clean R8 retrace workspace")
            }
        }
        if (directory.exists() && !directory.delete()) {
            failure = failure ?: IllegalStateException("Unable to delete R8 retrace workspace")
        }
        failure?.let { throw it }
    }

    private fun child(name: String): File = File(directory, name).canonicalFile.also {
        require(it.parentFile == directory)
    }

    companion object {
        private const val INPUT_BUNDLE = "input.bundle"
        private const val OUTPUT_STACK = "output.txt"
        private val CHILDREN = setOf(INPUT_BUNDLE, OUTPUT_STACK)
        private val ATTEMPT = Regex("attempt-[0-9a-f-]{36}")

        fun create(rootDirectory: File): R8RetraceTransactionWorkspace {
            val root = rootDirectory.canonicalFile
            if (!root.exists()) check(root.mkdirs()) { "Unable to create R8 retrace transaction root" }
            require(root.isDirectory) { "R8 retrace transaction root is not a directory" }
            repeat(16) {
                val directory = File(root, "attempt-${UUID.randomUUID()}").canonicalFile
                require(directory.parentFile == root)
                if (directory.mkdir()) return R8RetraceTransactionWorkspace(directory)
            }
            error("Unable to reserve an R8 retrace workspace")
        }

        fun recoverStale(rootDirectory: File) {
            val root = rootDirectory.canonicalFile
            if (!root.exists()) {
                check(root.mkdirs()) { "Unable to create R8 retrace transaction root" }
                return
            }
            require(root.isDirectory) { "R8 retrace transaction root is not a directory" }
            root.listFiles().orEmpty().forEach { directory ->
                require(directory.canonicalFile.parentFile == root && directory.isDirectory &&
                    ATTEMPT.matches(directory.name)
                ) { "Unexpected R8 retrace transaction entry" }
                directory.listFiles().orEmpty().forEach { child ->
                    require(child.canonicalFile.parentFile == directory.canonicalFile &&
                        child.isFile && child.name in CHILDREN
                    ) { "Unexpected R8 retrace workspace child" }
                    check(child.delete()) { "Unable to recover R8 retrace staging file" }
                }
                check(directory.delete()) { "Unable to recover R8 retrace workspace" }
            }
        }
    }
}
