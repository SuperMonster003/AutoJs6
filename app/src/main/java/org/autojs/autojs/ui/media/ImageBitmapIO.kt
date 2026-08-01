package org.autojs.autojs.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

internal object ImageBitmapIO {

    data class SourceInfo(
        val rawSize: ImagePixelSize,
        val displaySize: ImagePixelSize,
        val mimeType: String?,
        val orientation: Int,
    )

    fun inspect(file: File): SourceInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unable to decode image bounds")
        }
        val orientation = readExifOrientation(file)
        val rawSize = ImagePixelSize(bounds.outWidth, bounds.outHeight)
        val displaySize = if (orientation.swapsDimensions()) {
            ImagePixelSize(bounds.outHeight, bounds.outWidth)
        } else {
            rawSize
        }
        return SourceInfo(rawSize, displaySize, bounds.outMimeType, orientation)
    }

    fun decodeForEditing(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unable to decode image bounds")
        }
        val pixelBudget = editingPixelBudget()
        var sampleSize = 1
        while (
            sampledDimension(bounds.outWidth, sampleSize) *
            sampledDimension(bounds.outHeight, sampleSize) > pixelBudget
        ) {
            sampleSize *= 2
        }
        val decoded = try {
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to edit the image", error)
        } ?: throw IOException("Unable to decode image")
        return try {
            applyExifOrientation(file, decoded)
        } catch (error: Throwable) {
            if (!decoded.isRecycled) decoded.recycle()
            throw error
        }
    }

    fun decodeForConversion(file: File): Bitmap {
        val info = inspect(file)
        if (info.displaySize.pixelCount > ImageConversionSizing.MAX_PIXEL_COUNT) {
            throw IOException("Image is too large to convert without changing its resolution")
        }
        return decodeForConversion(file, info.displaySize, info)
    }

    fun decodeForConversion(file: File, targetSize: ImagePixelSize): Bitmap {
        return decodeForConversion(file, targetSize, inspect(file))
    }

    fun isWithinConversionMemoryBudget(info: SourceInfo, targetSize: ImagePixelSize): Boolean {
        return selectSampleSize(info, targetSize) != null
    }

    private fun decodeForConversion(file: File, targetSize: ImagePixelSize, info: SourceInfo): Bitmap {
        val validation = ImageConversionSizing.resolve(
            info.displaySize,
            ImageResizeRequest(
                mode = ImageResizeMode.CUSTOM,
                width = targetSize.width,
                height = targetSize.height,
            ),
        )
        if (!validation.isValid) throw IOException("Invalid conversion dimensions: ${validation.error}")
        val sampleSize = selectSampleSize(info, targetSize)
            ?: throw IOException("Not enough memory to convert the image at the requested resolution")
        val decoded = try {
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to convert the image at the requested resolution", error)
        } ?: throw IOException("Unable to decode image")
        val oriented = try {
            applyExifOrientation(info.orientation, decoded)
        } catch (error: OutOfMemoryError) {
            decoded.recycle()
            throw IOException("Not enough memory to orient the converted image", error)
        }
        if (oriented.width == targetSize.width && oriented.height == targetSize.height) return oriented
        return try {
            Bitmap.createScaledBitmap(oriented, targetSize.width, targetSize.height, true).also {
                if (it !== oriented) oriented.recycle()
            }
        } catch (error: OutOfMemoryError) {
            oriented.recycle()
            throw IOException("Not enough memory to resize the converted image", error)
        }
    }

    private fun selectSampleSize(info: SourceInfo, targetSize: ImagePixelSize): Int? {
        val rawTargetSize = if (info.orientation.swapsDimensions()) {
            ImagePixelSize(targetSize.height, targetSize.width)
        } else {
            targetSize
        }
        val exactRatio = minOf(
            info.rawSize.width.toDouble() / rawTargetSize.width,
            info.rawSize.height.toDouble() / rawTargetSize.height,
        )
        // BitmapFactory rounds non-power-of-two values down on some Android versions,
        // so use explicit powers of two and estimate memory against the actual sample.
        val qualitySample = Integer.highestOneBit(exactRatio.toInt().coerceAtLeast(1))
        // Do not silently decode below the requested resolution and upscale it again. If the
        // quality-preserving sample does not fit, surface the memory validation error instead.
        return qualitySample.takeIf { fitsMemoryBudget(info, targetSize, it) }
    }

    private fun fitsMemoryBudget(info: SourceInfo, targetSize: ImagePixelSize, sampleSize: Int): Boolean {
        val decodedWidth = sampledDimension(info.rawSize.width, sampleSize)
        val decodedHeight = sampledDimension(info.rawSize.height, sampleSize)
        val decodedPixels = decodedWidth * decodedHeight
        val orientationPeak = if (info.orientation.requiresTransform()) decodedPixels * 2L else decodedPixels
        val scalePeak = decodedPixels + targetSize.pixelCount
        // JPEG alpha flattening may temporarily hold two target-sized bitmaps.
        val encodingPeak = targetSize.pixelCount * 2L
        val peakPixels = maxOf(orientationPeak, scalePeak, encodingPeak)
        val bitmapBytes = peakPixels.saturatedMultiply(BYTES_PER_PIXEL)
        val estimatedBytes = if (bitmapBytes > Long.MAX_VALUE - MEMORY_OVERHEAD_BYTES) {
            Long.MAX_VALUE
        } else {
            bitmapBytes + MEMORY_OVERHEAD_BYTES
        }
        val heapBudget = (Runtime.getRuntime().maxMemory() * MAX_HEAP_FRACTION).toLong()
        return estimatedBytes in 1..heapBudget
    }

    private fun editingPixelBudget(): Long {
        val heapBudget = (Runtime.getRuntime().maxMemory() * MAX_EDIT_HEAP_FRACTION).toLong()
        val bitmapBudget = (heapBudget - EDIT_MEMORY_OVERHEAD_BYTES).coerceAtLeast(
            MIN_EDIT_PIXEL_COUNT * BYTES_PER_PIXEL * EDIT_PEAK_BITMAP_COUNT,
        )
        return (bitmapBudget / BYTES_PER_PIXEL / EDIT_PEAK_BITMAP_COUNT)
            .coerceIn(MIN_EDIT_PIXEL_COUNT, MAX_EDIT_PIXELS)
    }

    private fun sampledDimension(dimension: Int, sampleSize: Int): Long =
        (dimension.toLong() + sampleSize - 1L) / sampleSize

    private fun Long.saturatedMultiply(factor: Long): Long {
        return if (this > Long.MAX_VALUE / factor) Long.MAX_VALUE else this * factor
    }

    fun transform(source: Bitmap, rotation: Float = 0f, flipX: Boolean = false, flipY: Boolean = false): Bitmap {
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            // Android post operations are left-multiplied. Rotate before mirroring so the
            // combined matrices match EXIF transpose (5) and transverse (7), rather than
            // accidentally swapping those two diagonal reflections.
            if (flipX || flipY) postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun saveEdited(source: Bitmap, original: File): File {
        val format = when (original.extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg", "jpe", "jfif" -> ImageOutputFormat.JPEG
            "png" -> ImageOutputFormat.PNG
            "webp" -> ImageOutputFormat.WEBP
            else -> ImageOutputFormat.PNG
        }
        // Editing works on a bounded in-memory decode. Always publish a sibling
        // so a very large original can never be silently replaced by its sampled edit.
        return synchronized(OUTPUT_LOCK) {
            val target = uniqueSibling(original, "edited", format.extension)
            writeAtomically(
                bitmap = source,
                target = target,
                options = ImageConversionOptions(format, targetSize = ImagePixelSize(source.width, source.height)),
            )
            target
        }
    }

    fun convert(source: Bitmap, original: File, format: ImageOutputFormat): File {
        return convert(
            source,
            original,
            ImageConversionOptions(format, targetSize = ImagePixelSize(source.width, source.height)),
        )
    }

    fun convert(source: Bitmap, original: File, options: ImageConversionOptions): File {
        require(source.width == options.targetSize.width && source.height == options.targetSize.height) {
            "Bitmap size does not match conversion options"
        }
        return synchronized(OUTPUT_LOCK) {
            val target = uniqueSibling(original, "converted", options.format.extension)
            writeAtomically(source, target, options)
            target
        }
    }

    fun suggestedOutputFile(original: File, format: ImageOutputFormat): File {
        return synchronized(OUTPUT_LOCK) {
            uniqueSibling(original, "converted", format.extension)
        }
    }

    private fun writeAtomically(bitmap: Bitmap, target: File, options: ImageConversionOptions) {
        val directory = target.parentFile ?: throw IOException("Image has no parent directory")
        if (!directory.isDirectory || !directory.canWrite()) throw IOException("Image directory is not writable")
        val temporary = File(directory, ".${target.name}.${System.nanoTime()}.tmp")
        var encodedBitmap = bitmap
        try {
            if (options.format == ImageOutputFormat.JPEG && bitmap.hasAlpha()) {
                encodedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(encodedBitmap).apply {
                    drawColor(options.jpegBackgroundColor)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
            val compressionQuality = when {
                options.format == ImageOutputFormat.WEBP &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                    options.quality == ImageConversionOptions.MAX_QUALITY ->
                    ImageConversionOptions.MAX_QUALITY - 1
                else -> options.quality
            }
            FileOutputStream(temporary).use { output ->
                // Legacy WEBP switches to lossless at quality 100. Clamp it to 99 so the WebP
                // option keeps the same lossy semantics as WEBP_LOSSY on Android 11 and newer.
                if (!encodedBitmap.compress(options.format.compressFormat(), compressionQuality, output)) {
                    throw IOException("Unable to encode image")
                }
                output.fd.sync()
            }
            publishTemporary(temporary, target)
        } finally {
            if (encodedBitmap !== bitmap) encodedBitmap.recycle()
            temporary.delete()
        }
    }

    private fun publishTemporary(temporary: File, target: File) {
        if (target.exists()) throw IOException("Output image already exists")
        if (!temporary.renameTo(target)) throw IOException("Unable to publish image")
    }

    private fun uniqueSibling(original: File, suffix: String, extension: String): File {
        val directory = original.parentFile ?: throw IOException("Image has no parent directory")
        val baseName = original.nameWithoutExtension.ifBlank { original.name }
        var index = 0
        while (true) {
            val indexedSuffix = if (index == 0) suffix else "$suffix-$index"
            val candidate = File(directory, "${baseName}_$indexedSuffix.$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun applyExifOrientation(file: File, source: Bitmap): Bitmap {
        return applyExifOrientation(readExifOrientation(file), source)
    }

    private fun applyExifOrientation(orientation: Int, source: Bitmap): Bitmap {
        val transformed = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform(source, flipX = true)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform(source, rotation = 180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform(source, flipY = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> transform(source, rotation = 90f, flipX = true)
            ExifInterface.ORIENTATION_ROTATE_90 -> transform(source, rotation = 90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> transform(source, rotation = 270f, flipX = true)
            ExifInterface.ORIENTATION_ROTATE_270 -> transform(source, rotation = 270f)
            else -> source
        }
        if (transformed !== source) source.recycle()
        return transformed
    }

    private fun readExifOrientation(file: File): Int = runCatching {
        ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun Int.swapsDimensions(): Boolean = this == ExifInterface.ORIENTATION_TRANSPOSE ||
        this == ExifInterface.ORIENTATION_ROTATE_90 ||
        this == ExifInterface.ORIENTATION_TRANSVERSE ||
        this == ExifInterface.ORIENTATION_ROTATE_270

    private fun Int.requiresTransform(): Boolean = this != ExifInterface.ORIENTATION_UNDEFINED &&
        this != ExifInterface.ORIENTATION_NORMAL

    @Suppress("DEPRECATION")
    private fun ImageOutputFormat.compressFormat(): Bitmap.CompressFormat = when (this) {
        ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
        ImageOutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }

    private const val MAX_EDIT_PIXELS = 16_000_000L
    private const val MIN_EDIT_PIXEL_COUNT = 256L * 256L
    private const val EDIT_PEAK_BITMAP_COUNT = 2L
    private const val BYTES_PER_PIXEL = 4L
    private const val EDIT_MEMORY_OVERHEAD_BYTES = 8L * 1024L * 1024L
    private const val MEMORY_OVERHEAD_BYTES = 16L * 1024L * 1024L
    private const val MAX_EDIT_HEAP_FRACTION = 0.55
    private const val MAX_HEAP_FRACTION = 0.65
    private val OUTPUT_LOCK = Any()
}
