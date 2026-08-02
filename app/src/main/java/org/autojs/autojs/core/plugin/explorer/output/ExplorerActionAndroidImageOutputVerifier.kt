package org.autojs.autojs.core.plugin.explorer.output

import android.graphics.BitmapFactory
import java.io.File

/** Adds a bounded real Android decode as an independent check before publication. */
internal object ExplorerActionAndroidImageOutputVerifier {

    fun matches(
        file: File,
        validation: ExplorerActionImageOutputValidationResult,
    ): Boolean = runCatching {
        val expectedMimeType = validation.mimeType ?: return false
        val expectedWidth = validation.width ?: return false
        val expectedHeight = validation.height ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (
            bounds.outWidth != expectedWidth ||
            bounds.outHeight != expectedHeight ||
            bounds.outMimeType != expectedMimeType
        ) {
            return false
        }
        var sampleSize = 1
        while (
            sampledDimension(expectedWidth, sampleSize).toLong() *
                sampledDimension(expectedHeight, sampleSize).toLong() > MAX_DECODE_PIXEL_COUNT
        ) {
            if (sampleSize > Int.MAX_VALUE / 2) return false
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            },
        ) ?: return false
        try {
            decoded.width > 0 &&
                decoded.height > 0 &&
                decoded.width <= expectedWidth &&
                decoded.height <= expectedHeight
        } finally {
            decoded.recycle()
        }
    }.getOrDefault(false)

    private fun sampledDimension(value: Int, sampleSize: Int): Int =
        ((value.toLong() + sampleSize - 1L) / sampleSize).coerceAtLeast(1L).toInt()

    private const val MAX_DECODE_PIXEL_COUNT = 8_000_000L
}
