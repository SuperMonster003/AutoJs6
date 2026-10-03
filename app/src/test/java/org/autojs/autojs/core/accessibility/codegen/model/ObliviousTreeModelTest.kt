package org.autojs.autojs.core.accessibility.codegen.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ObliviousTreeModelTest {
    private fun model(
        featureCount: Int = 2,
        depths: ByteArray = byteArrayOf(2, 1),
        splits: IntArray = intArrayOf(0, 2),
        leaves: IntArray = intArrayOf(0, 4),
        indices: IntArray = intArrayOf(0, 1, 0),
        thresholds: FloatArray = floatArrayOf(0f, 1f, -2f),
        values: FloatArray = floatArrayOf(-3f, 5f, 7f, 11f, 13f, -17f),
        bias: Double = 2.5,
        scale: Double = -.5,
    ) = ObliviousTreeModel(featureCount, depths, splits, leaves, indices, thresholds, values, bias, scale)

    @Test
    fun twoTreesUseStrictThresholdsAndLeastSignificantFirstSplitBits() {
        val model = model()
        assertEquals(12.5, model.score(floatArrayOf(0f, 1f)), 0.0)
        assertEquals(-2.5, model.score(floatArrayOf(-2f, 1f)), 0.0)
        assertEquals(5.5, model.score(floatArrayOf(1f, 2f)), 0.0)
        assertEquals(7.5, model.score(floatArrayOf(0f, 2f)), 0.0)
        assertEquals(8.5, model.score(floatArrayOf(1f, 1f)), 0.0)
        assertEquals(2, model.treeCount)
        assertEquals(2, model.maxDepth)
        assertEquals(66L, model.storageBytes)
    }

    @Test
    fun negativeAndSignedZeroBoundariesDoNotTakeTheGreaterBranch() {
        val model = model()
        assertEquals(model.score(floatArrayOf(0f, 1f)), model.score(floatArrayOf(-0f, 1f)), 0.0)
        assertEquals(-2.5, model.score(floatArrayOf(-2f, -100f)), 0.0)
        assertEquals(2.5, model(scale = 0.0).score(floatArrayOf(1f, 2f)), 0.0)
    }

    @Test
    fun constructorDefensivelyCopiesEveryArray() {
        val depths = byteArrayOf(2, 1)
        val splits = intArrayOf(0, 2)
        val leaves = intArrayOf(0, 4)
        val indices = intArrayOf(0, 1, 0)
        val thresholds = floatArrayOf(0f, 1f, -2f)
        val values = floatArrayOf(-3f, 5f, 7f, 11f, 13f, -17f)
        val model = model(depths = depths, splits = splits, leaves = leaves, indices = indices, thresholds = thresholds, values = values)
        depths.fill(0)
        splits.fill(-1)
        leaves.fill(-1)
        indices.fill(100)
        thresholds.fill(Float.NaN)
        values.fill(Float.POSITIVE_INFINITY)
        assertEquals(5.5, model.score(floatArrayOf(1f, 2f)), 0.0)
        assertEquals(66L, model.storageBytes)
    }

    @Test
    fun constructorRejectsMalformedOffsetsShapesAndNonfinitePayloads() {
        val invalid = listOf<() -> Unit>(
            { model(featureCount = 0) }, { model(featureCount = 65536) },
            { model(depths = byteArrayOf()) }, { model(depths = byteArrayOf(0, 1)) },
            { model(depths = byteArrayOf(9, 1)) }, { model(depths = ByteArray(4097) { 1 }) },
            { model(splits = intArrayOf(0)) }, { model(leaves = intArrayOf(0)) },
            { model(splits = intArrayOf(0, 1)) }, { model(leaves = intArrayOf(0, 3)) },
            { model(indices = intArrayOf(0, 2, 0)) }, { model(indices = intArrayOf(0, -1, 0)) },
            { model(thresholds = floatArrayOf(0f)) }, { model(values = FloatArray(5)) },
            { model(values = FloatArray(7)) }, { model(thresholds = floatArrayOf(0f, Float.NaN, -2f)) },
            { model(values = floatArrayOf(-3f, 5f, Float.POSITIVE_INFINITY, 11f, 13f, -17f)) },
            { model(bias = Double.NaN) }, { model(scale = Double.NEGATIVE_INFINITY) },
        )
        invalid.forEach { factory -> assertThrows(IllegalArgumentException::class.java) { factory() } }
    }

    @Test
    fun scoringRejectsWrongFeatureCountsNonfiniteValuesAndOverflow() {
        val model = model()
        for (vector in listOf(floatArrayOf(0f), floatArrayOf(0f, 1f, 2f), floatArrayOf(Float.NaN, 1f),
            floatArrayOf(0f, Float.POSITIVE_INFINITY), floatArrayOf(Float.NEGATIVE_INFINITY, 0f))) {
            assertThrows(IllegalArgumentException::class.java) { model.score(vector) }
        }
        assertThrows(IllegalStateException::class.java) { model(scale = Double.MAX_VALUE).score(floatArrayOf(1f, 2f)) }
    }

    @Test
    fun immutableModelCanScoreConcurrentCallers() {
        val model = model()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll(List(8) { Callable {
                val vector = floatArrayOf(1f, 2f)
                var result = 0.0
                repeat(1000) { result = model.score(vector) }
                result
            } })
            results.forEach { assertEquals(5.5, it.get(), 0.0) }
        } finally {
            executor.shutdownNow()
        }
    }
}
