package org.autojs.autojs.runtime.api

import android.content.Context
import androidx.core.graphics.createBitmap
import com.benjaminwan.ocrlibrary.RapidOcrEngine
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.image.ImageWrapper
import java.io.File

class OcrRapid {

    fun detect(image: ImageWrapper?): List<OcrResult> {
        return RapidOcrEmbeddedEngine.detect(AutoJs.instance.applicationContext, image)
    }

    fun recognizeText(image: ImageWrapper?): List<String> {
        return detect(image).map { it.text }
    }

}

private object RapidOcrEmbeddedEngine {

    private const val DEFAULT_MAX_SIDE_LEN = 1024
    private const val DEFAULT_PADDING = 50
    private const val DEFAULT_BOX_SCORE_THRESH = 0.5f
    private const val DEFAULT_BOX_THRESH = 0.3f
    private const val DEFAULT_UNCLIP_RATIO = 2.0f
    private const val DEFAULT_DO_ANGLE = false
    private const val DEFAULT_MOST_ANGLE = false

    private val requiredNativeLibraries = listOf(
        "libRapidOcr.so",
        "libonnxruntime.so",
    )

    private val lock = Any()
    private var engine: RapidOcrEngine? = null

    fun detect(context: Context, image: ImageWrapper?): List<OcrResult> {
        image ?: return emptyList()
        val bitmap = image.bitmap
        if (bitmap.isRecycled) return emptyList()

        ensureNativeLibrariesAvailable(context)
        val output = newEmptyOutputBitmap()
        return try {
            val rawResult = synchronized(lock) {
                getEngine(context).detect(
                    bitmap,
                    output,
                    DEFAULT_PADDING,
                    DEFAULT_MAX_SIDE_LEN,
                    DEFAULT_BOX_SCORE_THRESH,
                    DEFAULT_BOX_THRESH,
                    DEFAULT_UNCLIP_RATIO,
                    DEFAULT_DO_ANGLE,
                    DEFAULT_MOST_ANGLE,
                )
            }
            convertResult(rawResult)
        } catch (e: Throwable) {
            throw wrapFailure(e)
        } finally {
            output.recycle()
        }
    }

    private fun getEngine(context: Context): RapidOcrEngine {
        engine?.let { return it }
        return RapidOcrEngine(context.applicationContext)
            .also { engine = it }
    }

    private fun convertResult(rawResult: RapidOcrEngine.Result?): List<OcrResult> {
        rawResult ?: return emptyList()
        return rawResult.textBlocks.map { block ->
            OcrResult(
                text = block.text,
                confidence = block.confidence,
                bounds = block.bounds,
            )
        }
    }

    private fun ensureNativeLibrariesAvailable(context: Context) {
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        val libraries = nativeLibraryDir.list()?.toSet().orEmpty()
        val missing = requiredNativeLibraries.filterNot { it in libraries }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Rapid OCR embedded native libraries are unavailable: ${missing.joinToString()}")
        }
    }

    private fun wrapFailure(cause: Throwable): RuntimeException {
        if (cause is RuntimeException) return cause
        if (cause is Error) throw cause
        return IllegalStateException("Rapid OCR embedded engine failed", cause)
    }

    private fun newEmptyOutputBitmap() = createBitmap(1, 1)

}
