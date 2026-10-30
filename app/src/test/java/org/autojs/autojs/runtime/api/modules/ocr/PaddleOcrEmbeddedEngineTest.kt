package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.runtime.api.modules.ocr.PaddleOcrEmbeddedEngine.Candidate
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.onnx.OnnxPaddleOcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packaged applications pick the embedded Paddle OCR engine from their assets and native libraries:
 * ONNX Runtime (PP-OCRv3 2.0.0 plugin) first, then the Paddle Lite variants (roadmap D8 / D20 / D25).
 * zh-CN: 打包应用按资产与原生库选择内嵌 Paddle OCR 引擎: 先 ONNX Runtime (PP-OCRv3 2.0.0 插件), 再 Paddle Lite 变体 (路线图 D8 / D20 / D25).
 */
class PaddleOcrEmbeddedEngineTest {

    private val onnxAssets = setOf(
        "models/ppocrv3/det/inference.onnx",
        "models/ppocrv3/rec/inference.onnx",
        "models/ppocrv3/rec/inference.yml",
        "models/ppocrv3/cls/inference.onnx",
    )

    private val onnxSlimAssets = setOf(
        "models/ppocrv3-slim/det/inference.onnx",
        "models/ppocrv3-slim/rec/inference.onnx",
    )

    private val onnxLibraries = setOf(
        "libonnxruntime.so",
        "libonnxruntime4j_jni.so",
        "libc++_shared.so",
        "libopencv_java4.so",
    )

    private val liteV3Assets = setOf(
        "labels/ppocr_keys_v1.txt",
        "models/ocr_v3_for_cpu/det_opt.nb",
    )

    private val liteV3Libraries = setOf(
        "libc++_shared.so",
        "libpaddle_light_api_shared.so",
        "libNative.so",
    )

    private val liteV5Assets = setOf(
        "labels/ppocr_keys_ocrv5.txt",
        "models/pp-ocrv5-arm/PP-OCRv5_mobile_det.nb",
    )

    private fun resolve(assets: Set<String>, libraries: Set<String>): Candidate? {
        return PaddleOcrEmbeddedEngine.resolveCandidate(hasAsset = { it in assets }, nativeLibraries = libraries)
    }

    @Test
    fun `onnx runtime assets with both libraries resolve the onnx runtime engine`() {
        assertEquals(Candidate.OnnxRuntime(slimAvailable = false), resolve(onnxAssets, onnxLibraries))
        assertEquals(Candidate.OnnxRuntime(slimAvailable = true), resolve(onnxAssets + onnxSlimAssets, onnxLibraries))
    }

    @Test
    fun `slim models count only when both det and rec are packaged`() {
        val partialSlim = onnxAssets + "models/ppocrv3-slim/det/inference.onnx"
        assertEquals(Candidate.OnnxRuntime(slimAvailable = false), resolve(partialSlim, onnxLibraries))
    }

    @Test
    fun `onnx runtime comes before paddle lite when both are packaged`() {
        val candidate = resolve(onnxAssets + liteV3Assets + liteV5Assets, onnxLibraries + liteV3Libraries)
        assertEquals(Candidate.OnnxRuntime(slimAvailable = false), candidate)
    }

    @Test
    fun `paddle lite variants keep their order`() {
        assertEquals("v3", (resolve(liteV3Assets, liteV3Libraries) as Candidate.PaddleLite).spec.name)
        assertEquals("v5", (resolve(liteV3Assets + liteV5Assets, liteV3Libraries) as Candidate.PaddleLite).spec.name)
    }

    @Test
    fun `onnx runtime assets without libraries report the missing libraries`() {
        assertEquals(
            Candidate.MissingOnnxRuntimeLibraries(listOf("libonnxruntime4j_jni.so")),
            resolve(onnxAssets, onnxLibraries - "libonnxruntime4j_jni.so"),
        )
        assertEquals(
            Candidate.MissingOnnxRuntimeLibraries(listOf("libonnxruntime.so", "libonnxruntime4j_jni.so")),
            resolve(onnxAssets, emptySet()),
        )
    }

    @Test
    fun `paddle lite assets still work when onnx runtime libraries are missing`() {
        val candidate = resolve(onnxAssets + liteV3Assets, liteV3Libraries)
        assertEquals("v3", (candidate as Candidate.PaddleLite).spec.name)
    }

    @Test
    fun `nothing packaged resolves nothing`() {
        assertNull(resolve(emptySet(), emptySet()))
        assertNull(resolve(onnxSlimAssets, onnxLibraries))
    }

    @Test
    fun `options map to sessions and per-call settings`() {
        val defaults = OcrOptions().apply {
            cpuThreadNum = 4
            useSlim = true
            detLongSize = 0
            scoreThreshold = -1f
        }
        assertEquals(
            OnnxPaddleOcrEngine.SessionKey(slim = false, threads = 4, useCls = false),
            OnnxPaddleOcrEngine.sessionKeyOf(defaults, slimAvailable = false),
        )
        assertTrue(OnnxPaddleOcrEngine.sessionKeyOf(defaults, slimAvailable = true).slim)
        assertFalse(OnnxPaddleOcrEngine.sessionKeyOf(defaults.apply { useSlim = false }, slimAvailable = true).slim)
        assertEquals(0, OnnxPaddleOcrEngine.sessionKeyOf(defaults.apply { cpuThreadNum = -2 }, slimAvailable = true).threads)

        val config = OnnxPaddleOcrEngine.runConfigOf(defaults)
        assertEquals(OnnxPaddleOcrEngine.DEFAULT_DET_LIMIT_SIDE_LEN, config.detLimitSideLen)
        assertEquals("max", config.detLimitType)
        assertEquals(0f, config.recScoreThresh)

        val tuned = OnnxPaddleOcrEngine.runConfigOf(defaults.apply {
            detLongSize = 1280
            scoreThreshold = 0.6f
        })
        assertEquals(1280, tuned.detLimitSideLen)
        assertEquals(0.6f, tuned.recScoreThresh)
    }

}
