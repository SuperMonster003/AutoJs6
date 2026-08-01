package org.autojs.autojs.ui.media

import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal enum class ImageOutputFormat(
    val displayName: String,
    val extension: String,
    val supportsQuality: Boolean,
) {
    JPEG("JPEG", "jpg", true),
    PNG("PNG", "png", false),
    WEBP("WebP", "webp", true),
}

internal enum class ImageResizeMode {
    ORIGINAL,
    PERCENTAGE,
    CUSTOM,
}

internal data class ImagePixelSize(
    val width: Int,
    val height: Int,
) {
    val pixelCount: Long get() = width.toLong() * height.toLong()
}

internal data class ImageResizeRequest(
    val mode: ImageResizeMode,
    val percentage: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

internal enum class ImageResizeError {
    INVALID_SOURCE,
    INVALID_PERCENTAGE,
    INVALID_WIDTH,
    INVALID_HEIGHT,
    DIMENSION_TOO_LARGE,
    PIXEL_COUNT_TOO_LARGE,
    MEMORY_BUDGET_EXCEEDED,
}

internal data class ImageResizeResult(
    val size: ImagePixelSize? = null,
    val error: ImageResizeError? = null,
) {
    val isValid: Boolean get() = size != null && error == null
}

internal data class ImageConversionOptions(
    val format: ImageOutputFormat,
    val quality: Int = DEFAULT_QUALITY,
    val targetSize: ImagePixelSize,
    val jpegBackgroundColor: Int = DEFAULT_JPEG_BACKGROUND_COLOR,
) {
    init {
        require(quality in MIN_QUALITY..MAX_QUALITY) { "Quality must be between $MIN_QUALITY and $MAX_QUALITY" }
    }

    companion object {
        const val MIN_QUALITY = 1
        const val MAX_QUALITY = 100
        const val DEFAULT_QUALITY = 92
        const val DEFAULT_JPEG_BACKGROUND_COLOR = -0x1
    }
}

internal object ImageConversionSizing {

    const val MIN_PERCENTAGE = 1
    const val MAX_PERCENTAGE = 1000
    const val MAX_DIMENSION = 16_384
    const val MAX_PIXEL_COUNT = 40_000_000L

    fun resolve(
        source: ImagePixelSize,
        request: ImageResizeRequest,
        maxPixelCount: Long = MAX_PIXEL_COUNT,
    ): ImageResizeResult {
        if (source.width <= 0 || source.height <= 0) {
            return ImageResizeResult(error = ImageResizeError.INVALID_SOURCE)
        }
        val target = when (request.mode) {
            ImageResizeMode.ORIGINAL -> source
            ImageResizeMode.PERCENTAGE -> {
                val percentage = request.percentage
                    ?: return ImageResizeResult(error = ImageResizeError.INVALID_PERCENTAGE)
                if (percentage !in MIN_PERCENTAGE..MAX_PERCENTAGE) {
                    return ImageResizeResult(error = ImageResizeError.INVALID_PERCENTAGE)
                }
                ImagePixelSize(
                    width = scaledDimension(source.width, percentage),
                    height = scaledDimension(source.height, percentage),
                )
            }
            ImageResizeMode.CUSTOM -> {
                val width = request.width
                    ?: return ImageResizeResult(error = ImageResizeError.INVALID_WIDTH)
                val height = request.height
                    ?: return ImageResizeResult(error = ImageResizeError.INVALID_HEIGHT)
                if (width <= 0) return ImageResizeResult(error = ImageResizeError.INVALID_WIDTH)
                if (height <= 0) return ImageResizeResult(error = ImageResizeError.INVALID_HEIGHT)
                ImagePixelSize(width, height)
            }
        }
        if (target.width > MAX_DIMENSION || target.height > MAX_DIMENSION) {
            return ImageResizeResult(error = ImageResizeError.DIMENSION_TOO_LARGE)
        }
        if (target.pixelCount > maxPixelCount) {
            return ImageResizeResult(error = ImageResizeError.PIXEL_COUNT_TOO_LARGE)
        }
        return ImageResizeResult(size = target)
    }

    fun heightForWidth(source: ImagePixelSize, width: Int): Int? {
        if (source.width <= 0 || source.height <= 0 || width <= 0) return null
        return proportionalDimension(width, source.height, source.width)
    }

    fun widthForHeight(source: ImagePixelSize, height: Int): Int? {
        if (source.width <= 0 || source.height <= 0 || height <= 0) return null
        return proportionalDimension(height, source.width, source.height)
    }

    fun estimateEncodedBytes(options: ImageConversionOptions): Long {
        val bytesPerPixel = when (options.format) {
            ImageOutputFormat.JPEG -> 0.10 + options.quality * 0.006
            ImageOutputFormat.PNG -> 1.75
            ImageOutputFormat.WEBP -> 0.08 + options.quality * 0.0045
        }
        return (options.targetSize.pixelCount * bytesPerPixel)
            .roundToLongSafely()
            .coerceAtLeast(1L)
    }

    private fun scaledDimension(source: Int, percentage: Int): Int {
        val scaled = (source.toLong() * percentage + 50L) / 100L
        return scaled.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun proportionalDimension(value: Int, numerator: Int, denominator: Int): Int? {
        val result = value.toDouble() * numerator.toDouble() / denominator.toDouble()
        if (!result.isFinite() || result > Int.MAX_VALUE) return null
        return result.roundToInt().coerceAtLeast(1)
    }

    private fun Double.roundToLongSafely(): Long = when {
        !isFinite() || this >= Long.MAX_VALUE -> Long.MAX_VALUE
        this <= 0.0 -> 0L
        else -> this.roundToLong()
    }
}
