package org.autojs.autojs.core.plugin.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import org.autojs.plugin.yolo.api.YoloContract
import org.autojs.plugin.yolo.api.YoloImageReference
import org.autojs.plugin.yolo.api.YoloPixelFormat
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/** R2's deliberately narrow, regular-file RGBA transport. */
internal object YoloImageTransport {

    fun fromBitmap(context: Context, bitmap: Bitmap): Frame {
        require(!bitmap.isRecycled) { "YOLO image is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "YOLO image dimensions are invalid" }

        val width = bitmap.width
        val height = bitmap.height
        require(width <= YoloContract.MAX_IMAGE_DIMENSION && height <= YoloContract.MAX_IMAGE_DIMENSION) {
            "YOLO image dimensions exceed the protocol limit"
        }
        val rowStride = Math.multiplyExact(width, RGBA_BYTES_PER_PIXEL)
        val sizeBytes = Math.multiplyExact(rowStride.toLong(), height.toLong())
        require(sizeBytes in 1L..YoloContract.MAX_IMAGE_BYTES) {
            "YOLO RGBA image exceeds the protocol limit"
        }

        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply {
            check(isDirectory || mkdirs()) { "Unable to create the YOLO image transport directory" }
        }
        val file = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, directory)
        var descriptor: ParcelFileDescriptor? = null
        try {
            writeRgba(bitmap, file, width, height, rowStride)
            check(file.length() == sizeBytes) { "YOLO RGBA image length changed while materializing" }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            check(descriptor.statSize == sizeBytes) { "YOLO RGBA descriptor length is invalid" }
            check(file.delete()) { "Unable to unlink the temporary YOLO RGBA image" }
            return Frame(
                descriptor = descriptor,
                reference = YoloImageReference(
                    pixelFormat = YoloPixelFormat.RGBA_8888,
                    width = width,
                    height = height,
                    rowStride = rowStride,
                    sizeBytes = sizeBytes,
                ),
            ).also { descriptor = null }
        } finally {
            runCatching { descriptor?.close() }
            if (file.exists()) {
                runCatching { file.delete() }
            }
        }
    }

    private fun writeRgba(
        bitmap: Bitmap,
        file: File,
        width: Int,
        height: Int,
        rowStride: Int,
    ) {
        val pixels = IntArray(width)
        val row = ByteArray(rowStride)
        FileOutputStream(file).use { output ->
            repeat(height) { y ->
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
                pixels.forEachIndexed { x, color ->
                    val offset = x * RGBA_BYTES_PER_PIXEL
                    row[offset] = Color.red(color).toByte()
                    row[offset + 1] = Color.green(color).toByte()
                    row[offset + 2] = Color.blue(color).toByte()
                    row[offset + 3] = Color.alpha(color).toByte()
                }
                output.write(row)
            }
            output.fd.sync()
        }
    }

    internal class Frame(
        val descriptor: ParcelFileDescriptor,
        val reference: YoloImageReference,
    ) : Closeable {
        override fun close() {
            descriptor.close()
        }
    }

    private const val RGBA_BYTES_PER_PIXEL = 4
    private const val CACHE_DIRECTORY = "yolo-ipc"
    private const val FILE_PREFIX = "frame-"
    private const val FILE_SUFFIX = ".rgba"
}
