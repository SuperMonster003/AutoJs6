package org.autojs.plugin.apkbuilder.template.impl

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

internal data class RemoteTransferBudget(
    val label: String,
    val maxBytes: Long,
) {

    init {
        require(label.isNotBlank()) { "Transfer budget label must not be blank." }
        require(maxBytes > 0L) { "Transfer budget maxBytes must be positive." }
    }

    fun validateDeclaredSize(sizeBytes: Long) {
        if (sizeBytes < 0L) {
            throw IOException("Remote build $label declares a negative size: $sizeBytes")
        }
        if (sizeBytes > maxBytes) {
            throw IOException(
                "Remote build $label exceeds the ${formatBytes(maxBytes)} transfer limit: declared=$sizeBytes"
            )
        }
    }
}

internal data class RemoteZipBudget(
    val label: String,
    val maxArchiveBytes: Long,
    val maxEntryCount: Int,
    val maxEntryUncompressedBytes: Long,
    val maxTotalUncompressedBytes: Long,
    val maxCompressionRatio: Long,
    val compressionRatioThresholdBytes: Long,
    val maxPathLength: Int = 1_024,
    val maxPathDepth: Int = 64,
) {

    init {
        require(label.isNotBlank()) { "ZIP budget label must not be blank." }
        require(maxArchiveBytes > 0L) { "ZIP budget maxArchiveBytes must be positive." }
        require(maxEntryCount > 0) { "ZIP budget maxEntryCount must be positive." }
        require(maxEntryUncompressedBytes > 0L) { "ZIP budget maxEntryUncompressedBytes must be positive." }
        require(maxTotalUncompressedBytes >= maxEntryUncompressedBytes) {
            "ZIP budget total uncompressed size must cover a single entry."
        }
        require(maxCompressionRatio > 0L) { "ZIP budget maxCompressionRatio must be positive." }
        require(compressionRatioThresholdBytes >= 0L) {
            "ZIP budget compressionRatioThresholdBytes must not be negative."
        }
        require(maxPathLength > 0) { "ZIP budget maxPathLength must be positive." }
        require(maxPathDepth > 0) { "ZIP budget maxPathDepth must be positive." }
    }
}

internal object RemoteBuildBudgets {

    private const val MEBIBYTE = 1_024L * 1_024L
    private const val GIBIBYTE = 1_024L * MEBIBYTE
    private const val MAX_COMPRESSION_RATIO = 250L
    private const val COMPRESSION_RATIO_THRESHOLD = 4L * MEBIBYTE

    val PROJECT_ARCHIVE_TRANSFER = RemoteTransferBudget(
        label = "project archive",
        maxBytes = 512L * MEBIBYTE,
    )
    val NATIVE_LIBRARIES_ARCHIVE_TRANSFER = RemoteTransferBudget(
        label = "native libraries archive",
        maxBytes = GIBIBYTE,
    )
    val KEY_STORE_TRANSFER = RemoteTransferBudget(
        label = "keystore",
        maxBytes = 64L * MEBIBYTE,
    )

    val PROJECT_ARCHIVE_ZIP = RemoteZipBudget(
        label = "project archive",
        maxArchiveBytes = PROJECT_ARCHIVE_TRANSFER.maxBytes,
        maxEntryCount = 100_000,
        maxEntryUncompressedBytes = 512L * MEBIBYTE,
        maxTotalUncompressedBytes = 2L * GIBIBYTE,
        maxCompressionRatio = MAX_COMPRESSION_RATIO,
        compressionRatioThresholdBytes = COMPRESSION_RATIO_THRESHOLD,
    )
    val NATIVE_LIBRARIES_ARCHIVE_ZIP = RemoteZipBudget(
        label = "native libraries archive",
        maxArchiveBytes = NATIVE_LIBRARIES_ARCHIVE_TRANSFER.maxBytes,
        maxEntryCount = 50_000,
        maxEntryUncompressedBytes = 512L * MEBIBYTE,
        maxTotalUncompressedBytes = 2L * GIBIBYTE,
        maxCompressionRatio = MAX_COMPRESSION_RATIO,
        compressionRatioThresholdBytes = COMPRESSION_RATIO_THRESHOLD,
    )
}

internal object RemoteBoundedStreamCopier {

    private const val BUFFER_SIZE = 256 * 1_024

    fun copy(
        input: InputStream,
        output: OutputStream,
        budget: RemoteTransferBudget,
        isCancelled: () -> Boolean = { false },
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            ensureActive(isCancelled)
            val read = input.read(buffer)
            if (read < 0) {
                return totalBytes
            }
            if (read == 0) {
                continue
            }
            if (totalBytes > budget.maxBytes - read.toLong()) {
                throw IOException(
                    "Remote build ${budget.label} exceeds the ${formatBytes(budget.maxBytes)} transfer limit."
                )
            }
            output.write(buffer, 0, read)
            totalBytes += read
        }
    }

    private fun ensureActive(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw CancellationException("Remote APK build session was cancelled.")
        }
    }
}

private fun formatBytes(bytes: Long): String = "$bytes-byte"
