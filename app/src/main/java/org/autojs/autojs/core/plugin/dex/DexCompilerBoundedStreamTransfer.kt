package org.autojs.autojs.core.plugin.dex

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class DexCompilerStreamSummary(
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(sizeBytes >= 0L)
        require(SHA256.matches(sha256))
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

internal class DexCompilerStreamLimitExceededException(
    val limitBytes: Long,
) : IllegalArgumentException("DEX compiler stream exceeds its $limitBytes byte limit")

internal class DexCompilerStreamCancelledException : IllegalStateException("DEX compiler stream transfer was cancelled")

/**
 * Copies one descriptor stream without ever writing more than [maximumBytes].
 *
 * The streams remain caller-owned. Cancellation is checked before every blocking read and before
 * every write. A byte beyond the quota is consumed only to prove overflow; it is never written to
 * host-private staging.
 */
internal object DexCompilerBoundedStreamTransfer {

    fun copy(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Long,
        isCancelled: () -> Boolean = { false },
    ): DexCompilerStreamSummary {
        require(maximumBytes > 0L)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            ensureActive(isCancelled)
            val remaining = maximumBytes - total
            val requested = if (remaining >= buffer.size.toLong()) {
                buffer.size
            } else {
                (remaining + 1L).toInt()
            }
            val count = input.read(buffer, 0, requested)
            if (count < 0) break
            if (count == 0) continue

            val accepted = minOf(count.toLong(), remaining).toInt()
            if (accepted > 0) {
                ensureActive(isCancelled)
                output.write(buffer, 0, accepted)
                digest.update(buffer, 0, accepted)
                total = Math.addExact(total, accepted.toLong())
            }
            if (accepted != count) throw DexCompilerStreamLimitExceededException(maximumBytes)
        }

        ensureActive(isCancelled)
        return DexCompilerStreamSummary(total, digest.digest().toHex())
    }

    private fun ensureActive(isCancelled: () -> Boolean) {
        if (isCancelled()) throw DexCompilerStreamCancelledException()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
