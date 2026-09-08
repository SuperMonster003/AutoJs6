package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A private, unlinked file transfers pixels without putting the data in the Binder transaction. */
internal object NodeImageByteTransport {
    data class Encoded(val descriptor: ParcelFileDescriptor, val size: Long)

    fun encode(context: Context, bitmap: Bitmap, format: String): Encoded {
        require(format == "png" || format == "rgba") { "Image byte format must be png or rgba." }
        val file = File.createTempFile("node-image-bytes-", ".bin", context.cacheDir)
        try {
            file.outputStream().use { stream ->
                if (format == "png") {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoding failed." }
                } else {
                    // getPixels returns straight-alpha ARGB even when Android stores premultiplied pixels.
                    val pixels = IntArray(Math.multiplyExact(bitmap.width, bitmap.height))
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
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
