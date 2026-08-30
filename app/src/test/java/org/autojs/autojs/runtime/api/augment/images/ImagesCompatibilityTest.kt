package org.autojs.autojs.runtime.api.augment.images

import org.junit.Assert.assertEquals
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
    fun imageQuantizationOptionsPreserveLegacyDefaults() {
        val parsed = Images.parseImageQuantizationOptions(NativeObject(), 60)

        assertEquals(256, parsed.maxColors)
        assertEquals(8, parsed.speed)
        assertEquals(60, parsed.minQuality)
        assertEquals(60, parsed.maxQuality)
        assertEquals(0.0F, parsed.ditheringLevel, 0.0F)
        assertEquals(0, parsed.posterizeBits)
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
            },
            60,
        )

        assertEquals(16, parsed.maxColors)
        assertEquals(2, parsed.speed)
        assertEquals(75, parsed.minQuality)
        assertEquals(90, parsed.maxQuality)
        assertEquals(0.75F, parsed.ditheringLevel, 0.0F)
        assertEquals(3, parsed.posterizeBits)
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

    private fun options(name: String, value: String) = NativeObject().apply {
        put(name, this, value)
    }
}
