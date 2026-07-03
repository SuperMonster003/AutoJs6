package org.autojs.autojs.runtime.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.image.ImageWrapper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

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

    private val lock = Any()
    private var engine: Any? = null
    private var detectMethod: Method? = null

    fun detect(context: Context, image: ImageWrapper?): List<OcrResult> {
        image ?: return emptyList()
        val bitmap = image.bitmap
        if (bitmap.isRecycled) return emptyList()

        val output = newEmptyOutputBitmap()
        return try {
            val rawResult = synchronized(lock) {
                getDetectMethod(context).invoke(
                    getEngine(context),
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
        } catch (e: ReflectiveOperationException) {
            throw wrapFailure(e)
        } finally {
            output.recycle()
        }
    }

    private fun getEngine(context: Context): Any {
        engine?.let { return it }
        val engineClass = Class.forName("com.benjaminwan.ocrlibrary.OcrEngine")
        return engineClass.getConstructor(Context::class.java)
            .newInstance(context.applicationContext)
            .also { engine = it }
    }

    private fun getDetectMethod(context: Context): Method {
        detectMethod?.let { return it }
        return getEngine(context).javaClass.getMethod(
            "detect",
            Bitmap::class.java,
            Bitmap::class.java,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE,
        ).also { detectMethod = it }
    }

    private fun convertResult(rawResult: Any?): List<OcrResult> {
        rawResult ?: return emptyList()
        val blocks = rawResult.javaClass.getMethod("getTextBlocks").invoke(rawResult) as? Iterable<*>
            ?: return emptyList()
        return blocks.mapNotNull { block ->
            block ?: return@mapNotNull null
            val blockClass = block.javaClass
            val text = blockClass.getMethod("getText").invoke(block) as? String ?: ""
            val confidence = (blockClass.getMethod("getBoxScore").invoke(block) as? Number)?.toFloat() ?: 0f
            val points = blockClass.getMethod("getBoxPoint").invoke(block) as? Iterable<*>
            OcrResult(
                text = text,
                confidence = confidence,
                bounds = boundsOf(points),
            )
        }
    }

    private fun boundsOf(points: Iterable<*>?): Rect {
        val coordinates = points
            ?.mapNotNull { point ->
                point ?: return@mapNotNull null
                pointInt(point, "getX") to pointInt(point, "getY")
            }
            .orEmpty()
        if (coordinates.isEmpty()) return Rect()

        val left = coordinates.minOf { it.first }
        val top = coordinates.minOf { it.second }
        val right = coordinates.maxOf { it.first }
        val bottom = coordinates.maxOf { it.second }
        return Rect(left, top, right, bottom)
    }

    private fun pointInt(point: Any, methodName: String): Int {
        return (point.javaClass.getMethod(methodName).invoke(point) as? Number)?.toInt() ?: 0
    }

    private fun wrapFailure(error: ReflectiveOperationException): RuntimeException {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        if (cause is RuntimeException) return cause
        if (cause is Error) throw cause
        return IllegalStateException("Rapid OCR embedded engine failed", cause)
    }

    private fun newEmptyOutputBitmap() = createBitmap(1, 1)

}
