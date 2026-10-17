package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A private, unlinked file transfers pixels without putting the data in the Binder transaction. */
internal object NodeImageByteTransport {
    data class Encoded(val descriptor: ParcelFileDescriptor, val size: Long)

    fun encode(context: Context, bitmap: Bitmap, format: String, quality: Int = 100, region: Rect? = null): Encoded {
        require(format in setOf("png", "jpg", "jpeg", "webp", "rgba")) { "Image byte format must be png, jpg, jpeg, webp or rgba." }
        require(quality in 0..100) { "Image quality must be in 0..100." }
        val rect = region ?: Rect(0, 0, bitmap.width, bitmap.height)
        require(rect.left >= 0 && rect.top >= 0 && rect.width() > 0 && rect.height() > 0 && rect.right <= bitmap.width && rect.bottom <= bitmap.height) { "Image region must fit inside the image." }
        val file = File.createTempFile("node-image-bytes-", ".bin", context.cacheDir)
        try {
            file.outputStream().use { stream ->
                if (format != "rgba") {
                    @Suppress("DEPRECATION")
                    val encoding = when (format) {
                        "png" -> Bitmap.CompressFormat.PNG
                        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                        else -> Bitmap.CompressFormat.WEBP
                    }
                    val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                    try { check(cropped.compress(encoding, quality, stream)) { "Image encoding failed." } }
                    finally { if (cropped !== bitmap) cropped.recycle() }
                } else {
                    // getPixels returns straight-alpha ARGB even when Android stores premultiplied pixels.
                    val pixels = IntArray(Math.multiplyExact(rect.width(), rect.height()))
                    bitmap.getPixels(pixels, 0, rect.width(), rect.left, rect.top, rect.width(), rect.height())
                    for (index in pixels.indices) {
                        val argb = pixels[index]
                        pixels[index] = (argb and 0xff00ff00.toInt()) or ((argb ushr 16) and 255) or ((argb and 255) shl 16)
                    }
                    val buffer = ByteBuffer.allocate(Math.multiplyExact(pixels.size, 4)).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.asIntBuffer().put(pixels)
                    stream.write(buffer.array())
                }
            }
            return Encoded(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), file.length())
        } finally {
            file.delete()
        }
    }
}
