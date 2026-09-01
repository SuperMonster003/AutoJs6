package org.autojs.autojs.apkbuilder.template

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal object RemoteBuildOutputTransport {

    const val MAX_OUTPUT_BYTES = 2L * 1_024L * 1_024L * 1_024L
    private const val BUFFER_SIZE = 256 * 1_024

    data class CopyResult(
        val bytesCopied: Long,
        val sha256: String,
    )

    /**
     * Receives and validates an output APK in a same-directory temporary file, then publishes it
     * with a single rename. The caller-visible output is therefore left untouched on cancellation,
     * transfer failure, size mismatch, digest mismatch, or staged-file validation failure.
     */
    fun copyToFile(
        input: InputStream,
        outputFile: File,
        declaredSizeBytes: Long,
        expectedSha256: String? = null,
        maxBytes: Long = MAX_OUTPUT_BYTES,
        isCancelled: () -> Boolean = { false },
        validateTemporaryFile: (File) -> Unit = {},
    ): CopyResult {
        require(maxBytes > 0L) { "Remote output size limit must be positive." }
        validateDeclaredSize(declaredSizeBytes, maxBytes)
        ensureActive(isCancelled)

        val absoluteOutput = outputFile.absoluteFile
        val parent = absoluteOutput.parentFile
            ?: throw IOException("Remote output path has no parent directory: ${absoluteOutput.path}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create remote output directory: ${parent.path}")
        }
        if (absoluteOutput.exists() && !absoluteOutput.isFile) {
            throw IOException("Remote output path is not a file: ${absoluteOutput.path}")
        }

        val temporary = File.createTempFile(
            temporaryPrefix(absoluteOutput.name),
            ".tmp",
            parent,
        )
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = FileOutputStream(temporary, false).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    ensureActive(isCancelled)
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    if (read == 0) {
                        continue
                    }
                    val readBytes = read.toLong()
                    if (totalBytes > maxBytes - readBytes) {
                        throw IOException("Remote output APK exceeds the $maxBytes-byte transfer limit.")
                    }
                    if (declaredSizeBytes > 0L && totalBytes > declaredSizeBytes - readBytes) {
                        throw IOException(
                            "Remote output APK exceeds its declared size: declared=$declaredSizeBytes"
                        )
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalBytes += readBytes
                }
                ensureActive(isCancelled)
                output.flush()
                output.fd.sync()
                totalBytes
            }
            if (copied <= 0L) {
                throw IOException("Remote output APK is empty.")
            }
            if (declaredSizeBytes > 0L && copied != declaredSizeBytes) {
                throw IOException(
                    "Remote output APK size mismatch: declared=$declaredSizeBytes actual=$copied"
                )
            }
            val actualSha256 = digest.digest().toHexString()
            if (!expectedSha256.isNullOrBlank() && !expectedSha256.equals(actualSha256, ignoreCase = true)) {
                throw IOException(
                    "Remote output APK SHA-256 mismatch: expected=$expectedSha256 actual=$actualSha256"
                )
            }
            ensureActive(isCancelled)
            validateTemporaryFile(temporary)
            ensureActive(isCancelled)
            replaceAtomically(temporary, absoluteOutput)
            return CopyResult(copied, actualSha256)
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    private fun validateDeclaredSize(declaredSizeBytes: Long, maxBytes: Long) {
        if (declaredSizeBytes < 0L) {
            throw IOException("Remote output APK declares a negative size: $declaredSizeBytes")
        }
        if (declaredSizeBytes > maxBytes) {
            throw IOException(
                "Remote output APK exceeds the $maxBytes-byte transfer limit: declared=$declaredSizeBytes"
            )
        }
    }

    private fun ensureActive(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw CancellationException("Remote output APK transfer was cancelled.")
        }
    }

    private fun temporaryPrefix(outputName: String): String {
        val boundedName = outputName.take(48)
        return ".$boundedName.".takeIf { it.length >= 3 } ?: ".apk."
    }

    /**
     * android.system.Os.rename is available below minSdk 24 and provides POSIX atomic replacement
     * for same-directory paths. The File fallback keeps local JVM tests and unusual file systems
     * functional while preserving the previous output if publication cannot complete.
     */
    private fun replaceAtomically(temporary: File, output: File) {
        try {
            Os.rename(temporary.absolutePath, output.absolutePath)
            return
        } catch (osError: Exception) {
            replaceWithFileFallback(temporary, output, osError)
        }
    }

    @Synchronized
    private fun replaceWithFileFallback(
        temporary: File,
        output: File,
        osError: Exception,
    ) {
        if (temporary.renameTo(output)) {
            return
        }
        if (!output.exists()) {
            throw IOException("Failed to publish remote output APK: ${output.path}", osError)
        }

        val backup = File(
            output.parentFile,
            ".${output.name}.${System.nanoTime()}.publish-backup",
        )
        if (backup.exists() || !output.renameTo(backup)) {
            throw IOException("Failed to preserve existing output APK before publication: ${output.path}", osError)
        }

        var published = false
        try {
            if (!temporary.renameTo(output)) {
                throw IOException("Failed to publish remote output APK: ${output.path}", osError)
            }
            published = true
        } finally {
            if (published) {
                backup.delete()
            } else if (!backup.renameTo(output)) {
                throw IOException(
                    "Failed to restore existing output APK after publication failure: ${output.path}",
                    osError,
                )
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
