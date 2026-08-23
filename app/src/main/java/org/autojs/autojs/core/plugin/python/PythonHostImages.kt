package org.autojs.autojs.core.plugin.python

import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Fixed bounds for execution-local Python screenshot transfer. */
internal object PythonHostImagePolicy {
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    const val MAX_CHUNK_BYTES = 32 * 1024
    const val MAX_DIMENSION = 8_192
    const val MAX_PIXELS = 16 * 1024 * 1024
    const val MIN_QUALITY = 1
    const val MAX_QUALITY = 100
    const val MAX_IMAGE_ID_BYTES = 64

    val FORMATS = setOf("png", "jpeg")
    val IMAGE_ID = Regex("image-[1-9][0-9]*")
}

internal data class PythonHostCapturedScreenImage(
    val format: String,
    val width: Int,
    val height: Int,
    /** Ownership transfers to [PythonHostScreenImageScope]. */
    val bytes: ByteArray,
)

internal data class PythonHostScreenImageDescriptor(
    val id: String,
    val format: String,
    val width: Int,
    val height: Int,
    val byteLength: Int,
    val sha256: String,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("id", id)
        addProperty("format", format)
        addProperty("width", width)
        addProperty("height", height)
        addProperty("byteLength", byteLength)
        addProperty("sha256", sha256)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-screen-image-v1"
    }
}

internal data class PythonHostScreenImageChunk(
    val imageId: String,
    val offset: Int,
    val data: String,
    val nextOffset: Int,
    val eof: Boolean,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("imageId", imageId)
        addProperty("offset", offset)
        addProperty("data", data)
        addProperty("nextOffset", nextOffset)
        addProperty("eof", eof)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-screen-image-chunk-v1"
    }
}

/**
 * Owns at most one encoded screenshot for a Python execution. Replacing, releasing, or closing the
 * scope overwrites retained bytes before dropping the reference.
 */
internal class PythonHostScreenImageScope(
    private val capture: (format: String, quality: Int) -> PythonHostCapturedScreenImage,
) : AutoCloseable {
    private var retained: RetainedImage? = null
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun capture(format: String, quality: Int): PythonHostScreenImageDescriptor {
        check(!closed) { "Python Host screen image scope is closed" }
        require(format in PythonHostImagePolicy.FORMATS) { "Python Host image format is invalid" }
        require(quality in PythonHostImagePolicy.MIN_QUALITY..PythonHostImagePolicy.MAX_QUALITY) {
            "Python Host image quality is invalid"
        }

        clearRetained()
        generation += 1L
        val captured = capture.invoke(format, quality)
        var retainedOwnership = false
        try {
            check(captured.format == format) { "Python Host image format changed during capture" }
            if (captured.width <= 0 || captured.height <= 0) {
                throw PythonHostCapabilityScreenCaptureFailedException()
            }
            if (
                captured.width > PythonHostImagePolicy.MAX_DIMENSION ||
                captured.height > PythonHostImagePolicy.MAX_DIMENSION ||
                captured.width.toLong() * captured.height > PythonHostImagePolicy.MAX_PIXELS
            ) {
                throw PythonHostCapabilityResultLimitException()
            }
            if (captured.bytes.size !in 1..PythonHostImagePolicy.MAX_IMAGE_BYTES) {
                throw PythonHostCapabilityResultLimitException()
            }

            val id = "image-$generation"
            val descriptor = PythonHostScreenImageDescriptor(
                id = id,
                format = format,
                width = captured.width,
                height = captured.height,
                byteLength = captured.bytes.size,
                sha256 = sha256(captured.bytes),
            )
            retained = RetainedImage(descriptor, captured.bytes)
            retainedOwnership = true
            return descriptor
        } finally {
            if (!retainedOwnership) captured.bytes.fill(0)
        }
    }

    @Synchronized
    fun readChunk(imageId: String, offset: Int): PythonHostScreenImageChunk {
        check(!closed) { "Python Host screen image scope is closed" }
        val image = retained
            ?.takeIf { it.descriptor.id == imageId }
            ?: throw PythonHostCapabilityStaleImageException()
        require(offset in 0 until image.bytes.size) { "Python Host image offset is invalid" }
        val nextOffset = minOf(offset + PythonHostImagePolicy.MAX_CHUNK_BYTES, image.bytes.size)
        val bytes = image.bytes.copyOfRange(offset, nextOffset)
        try {
            return PythonHostScreenImageChunk(
                imageId = imageId,
                offset = offset,
                data = Base64.getEncoder().encodeToString(bytes),
                nextOffset = nextOffset,
                eof = nextOffset == image.bytes.size,
            )
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun release(imageId: String) {
        check(!closed) { "Python Host screen image scope is closed" }
        if (retained?.descriptor?.id != imageId) throw PythonHostCapabilityStaleImageException()
        clearRetained()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        clearRetained()
    }

    private fun clearRetained() {
        retained?.bytes?.fill(0)
        retained = null
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val encoded = CharArray(digest.size * 2)
        digest.forEachIndexed { index, value ->
            val unsigned = value.toInt() and 0xff
            encoded[index * 2] = HEX[unsigned ushr 4]
            encoded[index * 2 + 1] = HEX[unsigned and 0x0f]
        }
        digest.fill(0)
        return String(encoded)
    }

    private data class RetainedImage(
        val descriptor: PythonHostScreenImageDescriptor,
        val bytes: ByteArray,
    )

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}

/** Android 11+ accessibility screenshot capture with a fixed deadline and bounded encoded output. */
internal object PythonHostAccessibilityScreenCapture {
    private const val CAPTURE_TIMEOUT_MILLIS = 4_000L
    private const val RETRY_DELAY_MILLIS = 50L
    private const val MAX_ATTEMPTS = 3
    private const val INTERNAL_FAILURE = -1
    private const val TIMEOUT_FAILURE = -2

    fun capture(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        format: String,
        quality: Int,
    ): PythonHostCapturedScreenImage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw PythonHostCapabilityScreenCaptureUnavailableException()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw PythonHostCapabilityScreenCaptureFailedException()
        }
        return captureApi30(service, format, quality)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureApi30(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        format: String,
        quality: Int,
    ): PythonHostCapturedScreenImage {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CAPTURE_TIMEOUT_MILLIS)
        var bitmap: Bitmap? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) throw PythonHostCapabilityScreenCaptureFailedException()
            when (val result = takeScreenshot(service, remainingNanos)) {
                is ScreenshotResult.Success -> {
                    bitmap = result.bitmap
                    break
                }
                is ScreenshotResult.Failure -> {
                    val canRetry =
                        result.errorCode == AndroidAccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT &&
                            attempt < MAX_ATTEMPTS &&
                            deadlineNanos - System.nanoTime() >
                            TimeUnit.MILLISECONDS.toNanos(RETRY_DELAY_MILLIS)
                    if (!canRetry) throw PythonHostCapabilityScreenCaptureFailedException()
                    try {
                        Thread.sleep(RETRY_DELAY_MILLIS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw PythonHostCapabilityScreenCaptureFailedException()
                    }
                }
            }
        }
        val capturedBitmap = bitmap ?: throw PythonHostCapabilityScreenCaptureFailedException()
        try {
            validateBitmap(capturedBitmap)
            return PythonHostCapturedScreenImage(
                format = format,
                width = capturedBitmap.width,
                height = capturedBitmap.height,
                bytes = encode(capturedBitmap, format, quality),
            )
        } finally {
            capturedBitmap.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshot(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        timeoutNanos: Long,
    ): ScreenshotResult {
        val result = CompletableFuture<ScreenshotResult>()
        val callback = object : AndroidAccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AndroidAccessibilityService.ScreenshotResult) {
                val decoded = runCatching { copySoftwareBitmap(screenshot) }
                    .fold(
                        onSuccess = ScreenshotResult::Success,
                        onFailure = { ScreenshotResult.Failure(INTERNAL_FAILURE) },
                    )
                if (!result.complete(decoded) && decoded is ScreenshotResult.Success) {
                    decoded.bitmap.recycle()
                }
            }

            override fun onFailure(errorCode: Int) {
                result.complete(ScreenshotResult.Failure(errorCode))
            }
        }
        runCatching {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        }.onFailure {
            result.complete(ScreenshotResult.Failure(INTERNAL_FAILURE))
        }
        return try {
            result.get(timeoutNanos, TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            result.complete(ScreenshotResult.Failure(TIMEOUT_FAILURE))
            result.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            result.complete(ScreenshotResult.Failure(TIMEOUT_FAILURE))
            result.getNow(ScreenshotResult.Failure(TIMEOUT_FAILURE))
        } catch (_: ExecutionException) {
            ScreenshotResult.Failure(INTERNAL_FAILURE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun copySoftwareBitmap(screenshot: AndroidAccessibilityService.ScreenshotResult): Bitmap {
        val hardwareBuffer = screenshot.hardwareBuffer
        try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                ?: throw IllegalStateException("Accessibility screenshot could not be decoded")
            try {
                return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw IllegalStateException("Accessibility screenshot could not be copied")
            } finally {
                hardwareBitmap.recycle()
            }
        } finally {
            hardwareBuffer.close()
        }
    }

    private fun validateBitmap(bitmap: Bitmap) {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            throw PythonHostCapabilityScreenCaptureFailedException()
        }
        if (
            bitmap.width > PythonHostImagePolicy.MAX_DIMENSION ||
            bitmap.height > PythonHostImagePolicy.MAX_DIMENSION ||
            bitmap.width.toLong() * bitmap.height > PythonHostImagePolicy.MAX_PIXELS
        ) {
            throw PythonHostCapabilityResultLimitException()
        }
    }

    private fun encode(bitmap: Bitmap, format: String, quality: Int): ByteArray {
        val output = BoundedByteArrayOutputStream(PythonHostImagePolicy.MAX_IMAGE_BYTES)
        try {
            val compression = when (format) {
                "png" -> Bitmap.CompressFormat.PNG
                "jpeg" -> Bitmap.CompressFormat.JPEG
                else -> throw IllegalArgumentException("Python Host image format is invalid")
            }
            val compressed = runCatching { bitmap.compress(compression, quality, output) }
                .getOrElse { error ->
                    if (output.exceeded) throw PythonHostCapabilityResultLimitException()
                    throw PythonHostCapabilityScreenCaptureFailedException(error)
                }
            if (output.exceeded) throw PythonHostCapabilityResultLimitException()
            if (!compressed || output.size() == 0) {
                throw PythonHostCapabilityScreenCaptureFailedException()
            }
            return output.copyBytes()
        } finally {
            output.clearBytes()
        }
    }

    private sealed interface ScreenshotResult {
        data class Success(val bitmap: Bitmap) : ScreenshotResult
        data class Failure(val errorCode: Int) : ScreenshotResult
    }

    private class BoundedByteArrayOutputStream(
        private val maximumBytes: Int,
    ) : ByteArrayOutputStream() {
        var exceeded = false
            private set

        @Synchronized
        override fun write(value: Int) {
            ensureCapacityFor(1)
            super.write(value)
        }

        @Synchronized
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
                "Python Host image encoder produced an invalid write"
            }
            ensureCapacityFor(length)
            super.write(bytes, offset, length)
        }

        @Synchronized
        fun copyBytes(): ByteArray = toByteArray()

        @Synchronized
        fun clearBytes() {
            buf.fill(0, 0, count)
            reset()
        }

        private fun ensureCapacityFor(additionalBytes: Int) {
            if (additionalBytes > maximumBytes - count) {
                exceeded = true
                throw ImageOutputLimitException()
            }
        }
    }

    private class ImageOutputLimitException : RuntimeException()
}

internal class PythonHostCapabilityScreenCaptureUnavailableException : RuntimeException()

internal class PythonHostCapabilityScreenCaptureFailedException(
    cause: Throwable? = null,
) : RuntimeException(cause)

internal class PythonHostCapabilityStaleImageException : RuntimeException()
