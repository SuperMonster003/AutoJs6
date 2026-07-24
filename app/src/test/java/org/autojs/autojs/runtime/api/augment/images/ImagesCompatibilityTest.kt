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

    private fun options(name: String, value: String) = NativeObject().apply {
        put(name, this, value)
    }
}
