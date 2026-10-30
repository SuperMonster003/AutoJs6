package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.plugin.paddle.ocr.onnx.engine.ExecutionProviders
import org.autojs.plugin.paddle.ocr.onnx.postprocess.ClsPostprocessor
import org.autojs.plugin.paddle.ocr.onnx.preprocess.TextLineResize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parts of the embedded det -> cls -> rec pipeline, the same cases as the plugin's
 * `TextLinePipelineTest` (PaddleOCR 2.7 behavior).
 * zh-CN: 内嵌 det -> cls -> rec 流水线中的纯逻辑部分, 用例与插件的 `TextLinePipelineTest` 相同 (PaddleOCR 2.7 行为).
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

}
