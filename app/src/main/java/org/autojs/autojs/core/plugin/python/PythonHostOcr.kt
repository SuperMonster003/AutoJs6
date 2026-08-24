package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Fixed result bounds for Python calls into the Host-selected OCR engine. */
internal object PythonHostOcrPolicy {
    const val MAX_LINES = 256
    const val MAX_LINE_BYTES = 4 * 1024
    const val MAX_TOTAL_TEXT_BYTES = 48 * 1024

    fun validate(lines: List<*>): List<String> {
        if (lines.size > MAX_LINES) throw PythonHostCapabilityResultLimitException()
        var totalBytes = 0
        return lines.map { candidate ->
            val line = candidate as? String ?: throw PythonHostCapabilityOcrFailedException()
            val lineBytes = strictUtf8Size(line)
            if (lineBytes > MAX_LINE_BYTES) throw PythonHostCapabilityResultLimitException()
            totalBytes += lineBytes
            if (totalBytes > MAX_TOTAL_TEXT_BYTES) throw PythonHostCapabilityResultLimitException()
            line
        }
    }

    private fun strictUtf8Size(value: String): Int = try {
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
            .remaining()
    } catch (error: OutOfMemoryError) {
        throw PythonHostCapabilityResultLimitException(error)
    } catch (error: Throwable) {
        throw PythonHostCapabilityOcrFailedException(error)
    }
}

/** Bridges one execution-local decoded image into AutoJs6's configured external OCR plugin. */
internal object PythonHostOcr {
    fun recognize(
        context: Context,
        image: PythonHostDecodedImageTemplate,
    ): List<String> {
        val bitmap = try {
            Bitmap.createBitmap(
                image.pixels,
                image.width,
                image.height,
                Bitmap.Config.ARGB_8888,
            )
        } catch (error: OutOfMemoryError) {
            throw PythonHostCapabilityResultLimitException(error)
        } catch (error: Throwable) {
            throw PythonHostCapabilityOcrFailedException(error)
        } finally {
            image.pixels.fill(0)
        }
        return try {
            val lines = runBlocking(Dispatchers.IO) {
                val target = try {
                    OcrPluginHost.selectOrThrow(
                        context = context,
                        moduleLabel = "Python OCR",
                    )
                } catch (error: OutOfMemoryError) {
                    throw PythonHostCapabilityResultLimitException(error)
                } catch (error: Throwable) {
                    throw PythonHostCapabilityOcrUnavailableException(error)
                }
                try {
                    OcrPluginHost.recognizeText(
                        context = context,
                        target = target,
                        bitmap = bitmap,
                        options = OcrOptions(),
                    )
                } catch (error: OutOfMemoryError) {
                    throw PythonHostCapabilityResultLimitException(error)
                } catch (error: Throwable) {
                    throw PythonHostCapabilityOcrFailedException(error)
                }
            }
            PythonHostOcrPolicy.validate(lines)
        } finally {
            runCatching { bitmap.eraseColor(Color.TRANSPARENT) }
            bitmap.recycle()
        }
    }
}

internal class PythonHostCapabilityOcrUnavailableException(
    cause: Throwable? = null,
) : RuntimeException(cause)

internal class PythonHostCapabilityOcrFailedException(
    cause: Throwable? = null,
) : RuntimeException(cause)
