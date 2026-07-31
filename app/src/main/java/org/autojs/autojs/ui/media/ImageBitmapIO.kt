package org.autojs.autojs.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

internal object ImageBitmapIO {

    enum class OutputFormat(
        val displayName: String,
        val extension: String,
    ) {
        JPEG("JPEG", "jpg"),
        PNG("PNG", "png"),
        WEBP("WebP", "webp"),
    }

    fun decodeForEditing(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unable to decode image bounds")
        }
        var sampleSize = 1
        while (
            bounds.outWidth.toLong() / sampleSize *
            (bounds.outHeight.toLong() / sampleSize) > MAX_EDIT_PIXELS
        ) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw IOException("Unable to decode image")
        return applyExifOrientation(file, decoded)
    }

    fun decodeForConversion(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unable to decode image bounds")
        }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (pixels > MAX_CONVERSION_PIXELS) {
            throw IOException("Image is too large to convert without changing its resolution")
        }
        val decoded = try {
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to convert the image at its original resolution", error)
        } ?: throw IOException("Unable to decode image")
        return try {
            applyExifOrientation(file, decoded)
        } catch (error: OutOfMemoryError) {
            decoded.recycle()
            throw IOException("Not enough memory to orient the converted image", error)
        }
    }

    fun transform(source: Bitmap, rotation: Float = 0f, flipX: Boolean = false, flipY: Boolean = false): Bitmap {
        val matrix = Matrix().apply {
            if (flipX || flipY) postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
            if (rotation != 0f) postRotate(rotation)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun saveEdited(source: Bitmap, original: File): File {
        val format = when (original.extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg", "jpe", "jfif" -> OutputFormat.JPEG
            "png" -> OutputFormat.PNG
            "webp" -> OutputFormat.WEBP
            else -> OutputFormat.PNG
        }
        // Editing works on a bounded in-memory decode. Always publish a sibling
        // so a very large original can never be silently replaced by its sampled edit.
        val target = uniqueSibling(original, "edited", format.extension)
        writeAtomically(source, target, format)
        return target
    }

    fun convert(source: Bitmap, original: File, format: OutputFormat): File {
        val target = uniqueSibling(original, "converted", format.extension)
        writeAtomically(source, target, format)
        return target
    }

    private fun writeAtomically(bitmap: Bitmap, target: File, format: OutputFormat) {
        val directory = target.parentFile ?: throw IOException("Image has no parent directory")
        if (!directory.isDirectory || !directory.canWrite()) throw IOException("Image directory is not writable")
        val temporary = File(directory, ".${target.name}.${System.nanoTime()}.tmp")
        val encodedBitmap = if (format == OutputFormat.JPEG && bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flattened ->
                Canvas(flattened).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        } else {
            bitmap
        }
        try {
            FileOutputStream(temporary).use { output ->
                if (!encodedBitmap.compress(format.compressFormat(), DEFAULT_QUALITY, output)) {
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
        if (!target.exists()) {
            if (!temporary.renameTo(target)) throw IOException("Unable to publish image")
            return
        }
        val backup = File(target.parentFile, ".${target.name}.${System.nanoTime()}.bak")
        if (!target.renameTo(backup)) throw IOException("Unable to stage the original image")
        try {
            if (!temporary.renameTo(target)) throw IOException("Unable to publish image")
            backup.delete()
        } catch (error: Throwable) {
            if (!target.exists()) backup.renameTo(target)
            throw error
        }
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
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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

    @Suppress("DEPRECATION")
    private fun OutputFormat.compressFormat(): Bitmap.CompressFormat = when (this) {
        OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        OutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }

    private const val MAX_EDIT_PIXELS = 16_000_000L
    private const val MAX_CONVERSION_PIXELS = 40_000_000L
    private const val DEFAULT_QUALITY = 92
}
