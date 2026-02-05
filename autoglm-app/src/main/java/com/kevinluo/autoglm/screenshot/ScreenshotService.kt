package com.kevinluo.autoglm.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import com.kevinluo.autoglm.IUserService
import com.kevinluo.autoglm.util.ErrorHandler
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Data class representing a captured screenshot.
 *
 * Contains the base64-encoded image data along with metadata about the capture.
 * The image is typically in WebP format for optimal compression.
 *
 * @property base64Data Base64-encoded image data (typically WebP format)
 * @property width Width of the screenshot in pixels (after scaling)
 * @property height Height of the screenshot in pixels (after scaling)
 * @property originalWidth Original screen width in pixels (before scaling)
 * @property originalHeight Original screen height in pixels (before scaling)
 * @property isSensitive Flag indicating if the screen was sensitive (e.g., password input)
 *
 */
data class Screenshot(
    val base64Data: String,
    val width: Int,
    val height: Int,
    val originalWidth: Int = width,
    val originalHeight: Int = height,
    val isSensitive: Boolean = false,
)

/**
 * Interface for controlling floating window visibility during screenshot capture.
 *
 * The floating window must be hidden before capture to avoid appearing in screenshots.
 * Implementations should handle the visibility state transitions safely.
 *
 */
interface FloatingWindowController {
    /**
     * Hides the floating window from the screen.
     */
    fun hide()

    /**
     * Shows the floating window on the screen.
     */
    fun show()

    /**
     * Shows the floating window and brings it to the front of other windows.
     */
    fun showAndBringToFront()

    /**
     * Checks if the floating window is currently visible.
     *
     * @return true if the window is visible, false otherwise
     */
    fun isVisible(): Boolean
}

/**
 * Service for capturing device screenshots using Shizuku shell commands.
 *
 * Manages floating window visibility during capture to ensure the window
 * doesn't appear in captured screenshots. Screenshots are captured in PNG format,
 * then scaled and converted to WebP for optimal file size.
 *
 * @param userService Shizuku user service for executing shell commands
 * @param floatingWindowControllerProvider Provider function for the floating window controller
 *
 */
class ScreenshotService(
    private val userService: IUserService,
    private val floatingWindowControllerProvider: () -> FloatingWindowController? = { null },
) {
    companion object {
        private const val TAG = "ScreenshotService"
        private const val HIDE_DELAY_MS = 200L
        private const val SHOW_DELAY_MS = 100L
        private const val FALLBACK_WIDTH = 1080
        private const val FALLBACK_HEIGHT = 1920

        // Screenshot compression settings - optimized for API upload
        private const val WEBP_QUALITY = 65 // Reduced from 70 for better compression

        /**
         * Returns the appropriate WebP compress format based on API level.
         * WEBP_LOSSY is only available on API 30+, use deprecated WEBP for older versions.
         */
        @Suppress("DEPRECATION")
        val WEBP_FORMAT: Bitmap.CompressFormat
            get() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }

        // Screenshot scaling settings - use max dimensions instead of fixed scale factor
        // This ensures consistent output size regardless of device resolution
        private const val MAX_WIDTH = 720 // Max width after scaling
        private const val MAX_HEIGHT = 1280 // Max height after scaling

        // Base64 output chunk size for reading (safe for Binder)
        private const val BASE64_CHUNK_SIZE = 500000
    }

    /**
     * Captures the current screen content.
     *
     * Uses try-finally pattern to ensure floating window is always restored,
     * even if an exception occurs during capture. The screenshot is scaled
     * and compressed to WebP format for optimal size.
     *
     * @return Screenshot object containing the captured image data and metadata
     *
     */
    suspend fun capture(): Screenshot = withContext(Dispatchers.IO) {
        val floatingWindowController = floatingWindowControllerProvider()
        val hasFloatingWindow = floatingWindowController != null
        Logger.d(TAG, "Starting screenshot capture, window visible: ${floatingWindowController?.isVisible()}")

        // Hide floating window before capture
        if (hasFloatingWindow) {
            Logger.d(TAG, "Hiding floating window")
            withContext(Dispatchers.Main) {
                floatingWindowController?.hide()
            }
            delay(HIDE_DELAY_MS)
        }

        try {
            // Capture screenshot
            val result = captureScreen()
            Logger.logScreenshot(result.width, result.height, result.isSensitive)
            result
        } catch (e: Exception) {
            val handledError = ErrorHandler.handleScreenshotError(e.message ?: "Unknown error", false, e)
            Logger.e(TAG, ErrorHandler.formatErrorForLog(handledError), e)
            createFallbackScreenshot()
        } finally {
            // Always restore floating window after capture (success or failure)
            if (hasFloatingWindow) {
                delay(SHOW_DELAY_MS)
                Logger.d(TAG, "Restoring floating window")
                withContext(Dispatchers.Main) {
                    floatingWindowController?.showAndBringToFront()
                }
            }
        }
    }

    /**
     * Captures the screen using Shizuku shell command.
     *
     * Executes screencap command, scales down the image if needed,
     * and converts to WebP format for smaller file size.
     *
     * @return Screenshot object with captured image data, or fallback screenshot on failure
     *
     */
    private suspend fun captureScreen(): Screenshot = withContext(Dispatchers.IO) {
        try {
            Logger.d(TAG, "Executing screencap command")

            val pngData = executeScreencapToBytes()

            if (pngData == null || pngData.isEmpty()) {
                Logger.w(TAG, "Failed to capture screenshot, returning fallback")
                return@withContext createFallbackScreenshot()
            }

            Logger.d(TAG, "PNG data captured: ${pngData.size} bytes")

            // Decode PNG to bitmap
            var bitmap = BitmapFactory.decodeByteArray(pngData, 0, pngData.size)
            if (bitmap == null) {
                Logger.w(TAG, "Failed to decode PNG, returning fallback")
                return@withContext createFallbackScreenshot()
            }

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // Calculate scaled dimensions based on max size constraints
            val (scaledWidth, scaledHeight) = calculateOptimalDimensions(originalWidth, originalHeight)

            // Scale bitmap if needed
            if (scaledWidth != originalWidth || scaledHeight != originalHeight) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                bitmap.recycle()
                bitmap = scaledBitmap
                Logger.d(TAG, "Scaled from ${originalWidth}x$originalHeight to ${scaledWidth}x$scaledHeight")
            }

            // Convert to WebP for better compression
            val webpStream = ByteArrayOutputStream()
            bitmap.compress(WEBP_FORMAT, WEBP_QUALITY, webpStream)
            bitmap.recycle()

            val webpData = webpStream.toByteArray()
            val compressionRatio = if (pngData.isNotEmpty()) 100 * webpData.size / pngData.size else 0
            Logger.d(TAG, "Converted to WebP: ${webpData.size} bytes ($compressionRatio% of PNG)")

            val base64Data = encodeToBase64(webpData)
            Logger.d(TAG, "Screenshot captured: ${scaledWidth}x$scaledHeight, base64 length: ${base64Data.length}")

            Screenshot(
                base64Data = base64Data,
                width = scaledWidth,
                height = scaledHeight,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                isSensitive = false,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Screenshot capture failed", e)
            createFallbackScreenshot()
        }
    }

    /**
     * Calculates optimal dimensions based on max size constraints.
     *
     * Maintains aspect ratio while ensuring the image fits within MAX_WIDTH x MAX_HEIGHT.
     * If the original image is already smaller, returns original dimensions.
     *
     * @param originalWidth Original image width in pixels
     * @param originalHeight Original image height in pixels
     * @return Pair of (scaledWidth, scaledHeight) maintaining aspect ratio
     *
     */
    private fun calculateOptimalDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
        // If already within limits, no scaling needed
        if (originalWidth <= MAX_WIDTH && originalHeight <= MAX_HEIGHT) {
            Logger.d(TAG, "Image already within limits: ${originalWidth}x$originalHeight")
            return originalWidth to originalHeight
        }

        // Calculate scale ratios for both dimensions
        val widthRatio = MAX_WIDTH.toFloat() / originalWidth
        val heightRatio = MAX_HEIGHT.toFloat() / originalHeight

        // Use the smaller ratio to ensure both dimensions fit within limits
        val ratio = minOf(widthRatio, heightRatio)

        val scaledWidth = (originalWidth * ratio).toInt()
        val scaledHeight = (originalHeight * ratio).toInt()

        Logger.d(TAG, "Scaling with ratio $ratio: ${originalWidth}x$originalHeight -> ${scaledWidth}x$scaledHeight")
        return scaledWidth to scaledHeight
    }

    /**
     * Executes screencap command and returns raw bytes.
     *
     * Uses sequential chunk reading for large files to avoid Binder buffer overflow.
     * The screenshot is captured as PNG, converted to base64, then decoded back to bytes.
     *
     * @return Raw PNG bytes of the screenshot, or null if capture failed
     *
     */
    private suspend fun executeScreencapToBytes(): ByteArray? = coroutineScope {
        val timestamp = System.currentTimeMillis()
        val pngFile = "/data/local/tmp/screenshot_$timestamp.png"
        val base64File = "$pngFile.b64"

        try {
            Logger.d(TAG, "Attempting screenshot capture")
            val startTime = System.currentTimeMillis()

            // Capture screenshot and pipe to base64
            val captureResult =
                userService.executeCommand(
                    "screencap -p | base64 > $base64File && stat -c %s $base64File",
                )

            val captureTime = System.currentTimeMillis() - startTime
            Logger.d(TAG, "Screenshot capture took ${captureTime}ms")

            // Check for errors
            if (captureResult.contains("Error") || captureResult.contains("permission denied", ignoreCase = true)) {
                Logger.w(TAG, "Screenshot capture failed: $captureResult")
                return@coroutineScope null
            }

            // Parse base64 file size from output
            val base64Size =
                captureResult
                    .lines()
                    .firstOrNull { it.trim().all { c -> c.isDigit() } }
                    ?.trim()
                    ?.toLongOrNull() ?: 0L

            if (base64Size == 0L) {
                Logger.w(TAG, "Base64 file not created or empty")
                return@coroutineScope null
            }

            Logger.d(TAG, "Base64 file size: $base64Size bytes")

            // Read base64 file
            val readStartTime = System.currentTimeMillis()
            val base64Data: String

            if (base64Size <= BASE64_CHUNK_SIZE) {
                // Small file - read in one go
                val result = userService.executeCommand("cat $base64File")
                base64Data =
                    result
                        .lines()
                        .filter { line ->
                            !line.startsWith("[") &&
                                line.isNotBlank() &&
                                !line.contains("exit code", ignoreCase = true)
                        }.joinToString("")
            } else {
                // Large file - read chunks sequentially to avoid Binder buffer overflow
                val chunkCount = ((base64Size + BASE64_CHUNK_SIZE - 1) / BASE64_CHUNK_SIZE).toInt()
                Logger.d(TAG, "Reading $chunkCount chunks sequentially")

                val chunks = mutableListOf<String>()

                for (index in 0 until chunkCount) {
                    val offset = index.toLong() * BASE64_CHUNK_SIZE
                    val remaining = base64Size - offset
                    val currentChunkSize = minOf(BASE64_CHUNK_SIZE.toLong(), remaining)

                    val chunkResult =
                        userService.executeCommand(
                            "tail -c +${offset + 1} $base64File | head -c $currentChunkSize",
                        )

                    val chunkData =
                        chunkResult
                            .lines()
                            .filter { line ->
                                !line.startsWith("[") &&
                                    line.isNotBlank() &&
                                    !line.contains("exit code", ignoreCase = true)
                            }.joinToString("")

                    chunks.add(chunkData)
                }

                base64Data = chunks.joinToString("")
            }

            val readTime = System.currentTimeMillis() - readStartTime
            Logger.d(TAG, "Base64 read took ${readTime}ms, total length: ${base64Data.length}")

            if (base64Data.isBlank()) {
                Logger.w(TAG, "No base64 data read")
                return@coroutineScope null
            }

            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to capture screenshot to bytes", e)
            null
        } finally {
            // Clean up temp files
            try {
                userService.executeCommand("rm -f $pngFile $base64File")
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Creates a fallback black screenshot in WebP format.
     *
     * Used when screenshot capture fails to provide a valid Screenshot object.
     * The fallback is marked as sensitive to indicate capture failure.
     *
     * @return Screenshot object with a black image and isSensitive=true
     *
     */
    private fun createFallbackScreenshot(): Screenshot {
        val bitmap = Bitmap.createBitmap(FALLBACK_WIDTH, FALLBACK_HEIGHT, Bitmap.Config.ARGB_8888)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(WEBP_FORMAT, WEBP_QUALITY, outputStream)
        bitmap.recycle()

        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        return Screenshot(
            base64Data = base64Data,
            width = FALLBACK_WIDTH,
            height = FALLBACK_HEIGHT,
            isSensitive = true,
        )
    }

    /**
     * Encodes byte array to base64 string.
     *
     * @param data Byte array to encode
     * @return Base64-encoded string without line wrapping
     *
     */
    fun encodeToBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    /**
     * Decodes base64 string to byte array.
     *
     * @param base64Data Base64-encoded string to decode
     * @return Decoded byte array
     *
     */
    fun decodeFromBase64(base64Data: String): ByteArray = Base64.decode(base64Data, Base64.DEFAULT)

    /**
     * Decodes base64 screenshot data to a Bitmap.
     *
     * @param base64Data Base64-encoded image data
     * @return Decoded Bitmap, or null if decoding fails
     *
     */
    fun decodeScreenshotToBitmap(base64Data: String): Bitmap? = try {
        val bytes = decodeFromBase64(base64Data)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    /**
     * Encodes a Bitmap to base64 string.
     *
     * @param bitmap Bitmap to encode
     * @param format Compression format (default: WEBP_LOSSY)
     * @param quality Compression quality 0-100 (default: WEBP_QUALITY)
     * @return Base64-encoded string of the compressed image
     *
     */
    fun encodeBitmapToBase64(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = WEBP_FORMAT,
        quality: Int = WEBP_QUALITY,
    ): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, quality, outputStream)
        return encodeToBase64(outputStream.toByteArray())
    }

    /**
     * Creates a Screenshot object from a Bitmap.
     *
     * @param bitmap Source bitmap to convert
     * @param isSensitive Whether the content is sensitive (default: false)
     * @return Screenshot object with encoded image data and dimensions
     *
     */
    fun createScreenshotFromBitmap(bitmap: Bitmap, isSensitive: Boolean = false): Screenshot = Screenshot(
        base64Data = encodeBitmapToBase64(bitmap),
        width = bitmap.width,
        height = bitmap.height,
        isSensitive = isSensitive,
    )
}
