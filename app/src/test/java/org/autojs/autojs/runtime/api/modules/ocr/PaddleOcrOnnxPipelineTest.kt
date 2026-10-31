package org.autojs.autojs.runtime.api.modules.ocr

import android.graphics.PointF
import org.autojs.plugin.paddle.ocr.onnx.engine.ExecutionProviders
import org.autojs.plugin.paddle.ocr.onnx.model.ModelConfig
import org.autojs.plugin.paddle.ocr.onnx.model.OCRBox
import org.autojs.plugin.paddle.ocr.onnx.postprocess.BoxSorter
import org.autojs.plugin.paddle.ocr.onnx.postprocess.CTCDecoder
import org.autojs.plugin.paddle.ocr.onnx.postprocess.ClsPostprocessor
import org.autojs.plugin.paddle.ocr.onnx.preprocess.TextLineResize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parts of the embedded det -> cls -> rec pipeline, the same cases as the plugin's
 * `TextLinePipelineTest` and `CtcDecoderTest` (PaddleOCR 2.7 behavior).
 * zh-CN: 内嵌 det -> cls -> rec 流水线中的纯逻辑部分, 用例与插件的 `TextLinePipelineTest` 和 `CtcDecoderTest` 相同 (PaddleOCR 2.7 行为).
 */
class PaddleOcrOnnxPipelineTest {

    @Test
    fun `rec batch width starts at 320 and follows the widest crop`() {
        assertEquals(320, TextLineResize.recBatchWidth(listOf(100 to 48)))
        assertEquals(960, TextLineResize.recBatchWidth(listOf(100 to 48, 960 to 48)))
        assertEquals(480, TextLineResize.recBatchWidth(listOf(100 to 10)))
        assertEquals(3200, TextLineResize.recBatchWidth(listOf(10000 to 10)))
    }

    @Test
    fun `resized width rounds up and stops at the tensor width`() {
        assertEquals(100, TextLineResize.resizedWidth(100, 48, 320))
        assertEquals(97, TextLineResize.resizedWidth(101, 50, 320))
        assertEquals(320, TextLineResize.resizedWidth(1000, 48, 320))
        assertEquals(192, TextLineResize.resizedWidth(1000, 48, TextLineResize.CLS_WIDTH))
    }

    @Test
    fun `empty crops are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TextLineResize.resizedWidth(0, 48, 320) }
        assertThrows(IllegalArgumentException::class.java) { TextLineResize.recBatchWidth(listOf(10 to 0)) }
    }

    @Test
    fun `only confident 180 labels rotate`() {
        val output = floatArrayOf(0.95f, 0.05f, 0.02f, 0.98f, 0.15f, 0.85f, 0.5f, 0.5f)
        val decisions = ClsPostprocessor.decode(output, longArrayOf(4, 2), 0.9f)
        assertEquals(listOf(0, 180, 180, 0), decisions.map { it.angle })
        assertEquals(listOf(false, true, false, false), decisions.map { it.rotate })
    }

    @Test
    fun `unexpected classifier output is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClsPostprocessor.decode(FloatArray(6), longArrayOf(2, 3), 0.9f) }
        assertThrows(IllegalArgumentException::class.java) { ClsPostprocessor.decode(FloatArray(2), longArrayOf(2, 2), 0.9f) }
    }

    @Test
    fun `nnapi is requested only from api 27`() {
        assertTrue(ExecutionProviders.shouldRequestNnapi(true, 27))
        assertTrue(ExecutionProviders.shouldRequestNnapi(true, 35))
        assertFalse(ExecutionProviders.shouldRequestNnapi(true, 26))
        assertFalse(ExecutionProviders.shouldRequestNnapi(false, 35))
        assertEquals("cpu", ExecutionProviders.CPU)
        assertEquals("nnapi", ExecutionProviders.NNAPI)
    }

    @Test
    fun `letters are mirrored and Latin runs stay readable`() {
        assertEquals("$TEH$BEH$ALEF", CTCDecoder.toLogicalOrder("$ALEF$BEH$TEH"))
        assertEquals("AutoJs6", CTCDecoder.toLogicalOrder("AutoJs6"))
        assertEquals(" 12:30$BEH$ALEF", CTCDecoder.toLogicalOrder("$ALEF$BEH 12:30"))
        assertEquals("${BEH}v3 $ALEF", CTCDecoder.toLogicalOrder("${ALEF}v3 $BEH"))
        assertEquals("", CTCDecoder.toLogicalOrder(""))
    }

    @Test
    fun `decode only reverses when the model asks for it`() {
        // Three time steps of three classes (blank, ALEF, BEH): ALEF, blank, BEH.
        val output = floatArrayOf(0.1f, 0.8f, 0.1f, 0.8f, 0.1f, 0.1f, 0.1f, 0.1f, 0.8f)
        val shape = longArrayOf(1, 3, 3)
        val dictionary = listOf("$ALEF", "$BEH")
        assertEquals("$ALEF$BEH", CTCDecoder.decode(output, shape, dictionary).single().first)
        assertEquals("$BEH$ALEF", CTCDecoder.decode(output, shape, dictionary, reverse = true).single().first)
    }

    @Test
    fun `only the key above the dictionary turns reversing on`() {
        assertTrue(ModelConfig.readReverse("PostProcess:\n  name: CTCLabelDecode\n  reverse: true\n  character_dict:\n    - 'a'\n"))
        assertTrue(ModelConfig.readReverse("PostProcess:\r\n  reverse: true\r\n  character_dict:\r\n"))
        assertFalse(ModelConfig.readReverse("PostProcess:\n  name: CTCLabelDecode\n  character_dict:\n    - 'a'\n"))
        assertFalse(ModelConfig.readReverse("PostProcess:\n  character_dict:\n    - 'reverse: true'\n  reverse: true\n"))
    }

    @Test
    fun `boxes of a row sort along the reading direction`() {
        val boxes = listOf(box(100f, 10f), box(0f, 40f), box(0f, 12f), box(50f, 11f))
        assertEquals(listOf(0f, 50f, 100f, 0f), BoxSorter.sortInReadingOrder(boxes).map { it.points[0].x })
        assertEquals(listOf(100f, 50f, 0f, 0f), BoxSorter.sortInReadingOrder(boxes, rightToLeft = true).map { it.points[0].x })
    }

    private fun box(x: Float, y: Float) = OCRBox(List(4) { point(x, y) })

    // android.graphics.PointF is a stub in JVM tests, so its constructors cannot run.
    // zh-CN: JVM 测试里 android.graphics.PointF 是桩, 不能调用构造函数.
    private fun point(x: Float, y: Float): PointF = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let { field ->
        field.isAccessible = true
        ((field.get(null) as sun.misc.Unsafe).allocateInstance(PointF::class.java) as PointF).also {
            it.x = x
            it.y = y
        }
    }

    private companion object {
        const val ALEF = '\u0627'
        const val BEH = '\u0628'
        const val TEH = '\u062A'
    }

}
