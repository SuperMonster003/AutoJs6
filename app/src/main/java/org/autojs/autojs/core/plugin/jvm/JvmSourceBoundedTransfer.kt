package org.autojs.autojs.core.plugin.jvm

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal class JvmSourceStreamLimitExceededException(
    val limitBytes: Long,
) : IllegalArgumentException("JVM source stream exceeds its $limitBytes byte limit")

internal data class JvmSourceStreamSummary(
    val sizeBytes: Long,
    val sha256Hex: String,
)

internal object JvmSourceBoundedTransfer {
    fun copy(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Long,
    ): JvmSourceStreamSummary {
        require(maximumBytes >= 0L)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val remaining = maximumBytes - total
            val requested = if (remaining >= buffer.size.toLong()) {
                buffer.size
            } else {
                (remaining + 1L).toInt()
            }
            val count = input.read(buffer, 0, requested)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > remaining) throw JvmSourceStreamLimitExceededException(maximumBytes)
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            total = Math.addExact(total, count.toLong())
        }
        return JvmSourceStreamSummary(total, digest.digest().toHex())
    }

    fun read(input: InputStream, maximumBytes: Long): JvmSourceCapturedOutput {
        require(maximumBytes >= 0L)
        val output = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE.toLong()).toInt())
        val summary = copy(input, output, maximumBytes)
        return JvmSourceCapturedOutput(output.toByteArray(), summary.sha256Hex)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
