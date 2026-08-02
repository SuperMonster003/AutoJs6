package org.autojs.autojs.core.plugin.explorer.output

import org.autojs.autojs.core.plugin.explorer.ExplorerActionOutputPolicy
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal enum class ExplorerActionOutputCopyViolation {
    DECLARED_LIMIT_EXCEEDED,
    HOST_LIMIT_EXCEEDED,
    STORAGE_RESERVE_EXHAUSTED,
}

internal class ExplorerActionOutputCopyException(
    val violation: ExplorerActionOutputCopyViolation,
) : IOException(
    when (violation) {
        ExplorerActionOutputCopyViolation.DECLARED_LIMIT_EXCEEDED ->
            "Explorer Action output exceeded its declared byte limit"
        ExplorerActionOutputCopyViolation.HOST_LIMIT_EXCEEDED ->
            "Explorer Action output exceeded the host byte limit"
        ExplorerActionOutputCopyViolation.STORAGE_RESERVE_EXHAUSTED ->
            "Explorer Action output exhausted the host storage reserve"
    },
)

internal data class ExplorerActionBoundedOutputPolicy(
    val declaredMaxBytes: Long,
    val hostMaxBytes: Long = HOST_MAX_BYTES,
    val minimumUsableBytes: Long = MINIMUM_USABLE_BYTES,
) {

    init {
        require(declaredMaxBytes > 0L) { "Declared output byte limit must be positive" }
        require(hostMaxBytes > 0L) { "Host output byte limit must be positive" }
        require(minimumUsableBytes >= 0L) { "Minimum usable storage cannot be negative" }
    }

    fun violationBeforeWrite(
        copiedBytes: Long,
        nextWriteBytes: Int,
        usableBytes: Long,
    ): ExplorerActionOutputCopyViolation? {
        require(copiedBytes >= 0L) { "Copied output byte count cannot be negative" }
        require(nextWriteBytes >= 0) { "Next output write size cannot be negative" }
        val nextTotal = copiedBytes + nextWriteBytes.toLong()
        if (nextTotal < copiedBytes || nextTotal > declaredMaxBytes) {
            return ExplorerActionOutputCopyViolation.DECLARED_LIMIT_EXCEEDED
        }
        if (nextTotal > hostMaxBytes) {
            return ExplorerActionOutputCopyViolation.HOST_LIMIT_EXCEEDED
        }
        if (
            usableBytes < minimumUsableBytes ||
            usableBytes - minimumUsableBytes < nextWriteBytes.toLong()
        ) {
            return ExplorerActionOutputCopyViolation.STORAGE_RESERVE_EXHAUSTED
        }
        return null
    }

    companion object {
        const val HOST_MAX_BYTES = ExplorerActionOutputPolicy.MAX_HOST_OUTPUT_BYTES
        const val MINIMUM_USABLE_BYTES = 32L * 1024L * 1024L
    }
}

internal class ExplorerActionBoundedOutputCopier(
    private val policy: ExplorerActionBoundedOutputPolicy,
    private val usableBytes: () -> Long,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {

    init {
        require(bufferSize > 0) { "Output copy buffer size must be positive" }
    }

    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(bufferSize)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return copied
            if (count == 0) continue
            policy.violationBeforeWrite(copied, count, usableBytes())?.let { violation ->
                throw ExplorerActionOutputCopyException(violation)
            }
            output.write(buffer, 0, count)
            copied += count
        }
    }
}
