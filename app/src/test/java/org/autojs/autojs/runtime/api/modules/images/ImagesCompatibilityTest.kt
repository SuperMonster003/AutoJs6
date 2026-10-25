package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.runtime.api.PngQuantBridge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject

class ImagesCompatibilityTest {

    @Test
    fun similarityMetricAcceptsCurrentAndProOptionNames() {
        assertEquals("mssim", Images.parseSimilarityMetric(null))
        assertEquals("hist", Images.parseSimilarityMetric(options("metric", "HIST")))
        assertEquals("mssim", Images.parseSimilarityMetric(options("type", "MSSIM")))
        assertEquals("psnr", Images.parseSimilarityMetric(options("type", "PNSR")))
        assertEquals("psnr", Images.parseSimilarityMetric(options("type", "PSNR")))
    }

    @Test
    fun circleResultsUsePlainObjectFieldsAndAbsoluteRegionCoordinates() {
        assertEquals(
            linkedMapOf(
                "x" to 11.5,
                "y" to 22.5,
                "radius" to 3.5,
            ),
            Images.buildCircleResult(1.5, 2.5, 3.5, 10, 20),
        )
    }

    @Test
    fun imageQuantizationOptionsDefaultToBestEffortMinimumQuality() {
        val parsed = Images.parseImageQuantizationOptions(NativeObject(), 60)

        assertEquals(256, parsed.maxColors)
        assertEquals(8, parsed.speed)
        assertEquals(0, parsed.minQuality)
        assertEquals(60, parsed.maxQuality)
        assertEquals(0.0F, parsed.ditheringLevel, 0.0F)
        assertEquals(0, parsed.posterizeBits)
        assertTrue(parsed.isPreserveAlpha)
        assertEquals(PngQuantBridge.Options.DEFAULT_MAX_PIXELS, parsed.maxPixels)
        assertEquals(PngQuantBridge.Options.DEFAULT_MAX_MEMORY_BYTES, parsed.maxMemoryBytes)
    }

    @Test
    fun imageQuantizationOptionsSupportNamedOverridesAndQualityShortcut() {
        val parsed = Images.parseImageQuantizationOptions(
            NativeObject().apply {
                put("quality", this, 75)
                put("maxQuality", this, 90)
                put("maxColors", this, 16)
                put("speed", this, 2)
                put("ditheringLevel", this, 0.75)
                put("posterizeBits", this, 3)
                put("preserveAlpha", this, false)
                put("maxPixels", this, 1_234_567L)
                put("maxMemoryBytes", this, 67_108_864L)
            },
            60,
        )

        assertEquals(16, parsed.maxColors)
        assertEquals(2, parsed.speed)
        assertEquals(75, parsed.minQuality)
        assertEquals(90, parsed.maxQuality)
        assertEquals(0.75F, parsed.ditheringLevel, 0.0F)
        assertEquals(3, parsed.posterizeBits)
        assertFalse(parsed.isPreserveAlpha)
        assertEquals(1_234_567L, parsed.maxPixels)
        assertEquals(67_108_864L, parsed.maxMemoryBytes)
    }

    @Test
    fun imageQuantizationQualityShortcutUsesLegacyClamping() {
        val parsed = Images.parseImageQuantizationOptions(
            NativeObject().apply { put("quality", this, 120) },
            60,
        )

        assertEquals(100, parsed.minQuality)
        assertEquals(100, parsed.maxQuality)
    }

    @Test
    fun imageQuantizationExplicitMinimumOverridesBestEffortAndQualityShortcut() {
        val bestEffort = Images.parseImageQuantizationOptions(
            NativeObject().apply { put("maxQuality", this, 90) },
            60,
        )
        val strict = Images.parseImageQuantizationOptions(
            NativeObject().apply {
                put("quality", this, 75)
                put("minQuality", this, 50)
            },
            60,
        )

        assertEquals(0, bestEffort.minQuality)
        assertEquals(90, bestEffort.maxQuality)
        assertEquals(50, strict.minQuality)
        assertEquals(75, strict.maxQuality)
    }

    @Test
    fun pngQuantizationResultExposesStableScriptFields() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val result = PngQuantizationResult(PngQuantBridge.Result(bytes, 73, 0.125, 8192L))

        assertArrayEquals(bytes, result.bytes)
        assertEquals(bytes.size, result.size)
        assertEquals(73, result.quality)
        assertEquals(0.125, result.quantizationError, 0.0)
        assertEquals(8192L, result.peakWorkingMemoryBytes)
    }

    @Test
    fun pngQuantizationFileResultExposesStableScriptFields() {
        val result = PngQuantizationFileResult(
            "/tmp/quantized.png",
            PngQuantBridge.OutputResult(4096L, 81, 0.0625, 16384L),
        )

        assertEquals("/tmp/quantized.png", result.path)
        assertEquals(4096L, result.size)
        assertEquals(81, result.quality)
        assertEquals(0.0625, result.quantizationError, 0.0)
        assertEquals(16384L, result.peakWorkingMemoryBytes)
    }

    private fun options(name: String, value: String) = NativeObject().apply {
        put(name, this, value)
    }
}
