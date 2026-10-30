package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.engine.toMergedOcrResultsJson
import org.autojs.autojs.runtime.api.OcrResultExtras
import org.autojs.autojs.runtime.api.modules.ocr.OcrLineMerger.Box
import org.autojs.autojs.runtime.api.modules.ocr.OcrLineMerger.Segment
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line merge shared by Rhino's `mergeLine` and the Node.js and Bun bridge.
 *
 * zh-CN: Rhino 的 mergeLine 与 Node.js / Bun 桥共用的行合并.
 */
class OcrLineMergerTest {

    private fun segment(text: String, box: Box, confidence: Float = 1f, extras: Map<String, Any?> = emptyMap()) = Segment(text, confidence, box, extras)

    private fun merge(vararg segments: Segment<Map<String, Any?>>) = OcrLineMerger.merge(segments.toList(), OcrResultExtras::mergeCommonValues)

    @Test
    fun segmentsOfALineJoinInReadingOrder() {
        val lines = merge(
            segment("second", Box(0, 40, 50, 60)),
            segment("world", Box(60, 2, 100, 22)),
            segment("hello ", Box(0, 0, 50, 20)),
        )
        assertEquals(listOf("hello world", "second"), lines.map { it.text })
        assertEquals(Box(0, 0, 100, 22), lines[0].box)
        assertEquals(Box(0, 40, 50, 60), lines[1].box)
    }

    @Test
    fun theLineConfidenceIsWeightedByTextLength() {
        val line = merge(
            segment("abc", Box(0, 0, 30, 20), confidence = 1f),
            segment("d", Box(40, 0, 50, 20), confidence = 0.2f),
        ).single()
        assertEquals(0.8f, line.confidence, 1e-6f)
    }

    @Test
    fun aSegmentOffTheCenterBandStartsANewLine() {
        // Half of the taller height is the band: 10 apart is the same line at height 22, a new line at height 20.
        assertEquals(1, merge(segment("a", Box(0, 0, 10, 22)), segment("b", Box(20, 10, 30, 32))).size)
        assertEquals(2, merge(segment("a", Box(0, 0, 10, 20)), segment("b", Box(20, 10, 30, 30))).size)
    }

    @Test
    fun anEmptyBoxAddsNothingToTheLine() {
        val line = merge(segment("a", Box(10, 10, 30, 30)), segment("b", Box(40, 20, 40, 20))).single()
        assertEquals("ab", line.text)
        assertEquals(Box(10, 10, 30, 30), line.box)
    }

    @Test
    fun aLineWithoutTextIsDropped() {
        assertEquals(listOf("b"), merge(segment("", Box(0, 0, 10, 10)), segment("b", Box(0, 40, 10, 50))).map { it.text })
    }

    @Test
    fun aSingleResultStaysAsItIs() {
        val only = listOf(segment("", Box(0, 0, 10, 10)))
        assertSame(only, OcrLineMerger.merge(only, OcrResultExtras::mergeCommonValues))
    }

    @Test
    fun aLineOfManySegmentsKeepsOnlyTheEqualExtras() {
        val line = merge(
            segment("a", Box(0, 0, 10, 20), extras = mapOf("quad" to listOf(0.0, 0.0), "executionProvider" to "cpu", "same" to listOf(mapOf("a" to 1)), "first" to 1)),
            segment("b", Box(20, 0, 30, 20), extras = mapOf("quad" to listOf(20.0, 0.0), "executionProvider" to "cpu", "same" to listOf(mapOf("a" to 1)))),
        ).single()
        assertEquals(mapOf("executionProvider" to "cpu", "same" to listOf(mapOf("a" to 1))), line.extras)
    }

    @Test
    fun theBridgeSendsMergedLinesWithTheRegionOffset() {
        val results = JSONArray(
            listOf(
                segment("a", Box(0, 0, 10, 20), 0.5f, mapOf("quad" to listOf(0.0, 0.0, 10.0, 20.0), "executionProvider" to "cpu")),
                segment("b", Box(20, 0, 30, 20), 0.5f, mapOf("quad" to listOf(20.0, 0.0, 30.0, 20.0), "executionProvider" to "cpu")),
                segment("c", Box(0, 40, 10, 60), 0.5f, mapOf("quad" to listOf(0.0, 40.0, 10.0, 60.0), "executionProvider" to "cpu")),
            ).toMergedOcrResultsJson(100, 200),
        )
        assertEquals(2, results.length())

        val merged = results.getJSONObject(0)
        assertEquals("ab", merged.getString("text"))
        assertEquals(0.5, merged.getDouble("confidence"), 0.0)
        assertEquals(listOf(100, 200, 130, 220), merged.getJSONObject("bounds").let { b -> listOf("left", "top", "right", "bottom").map(b::getInt) })
        assertFalse(merged.getJSONObject("extras").has("quad"))
        assertEquals("cpu", merged.getJSONObject("extras").getString("executionProvider"))

        // A line of one segment keeps its quad, moved like its bounds.
        val single = results.getJSONObject(1)
        val quad = single.getJSONObject("extras").getJSONArray("quad")
        assertEquals(listOf(100.0, 240.0, 110.0, 260.0), (0 until quad.length()).map(quad::getDouble))
        assertEquals(240, single.getJSONObject("bounds").getInt("top"))
    }

    @Test
    fun aMergedConfidenceThatIsNotFiniteIsSentAsNull() {
        val results = JSONArray(listOf(segment("a", Box(0, 0, 10, 20), Float.NaN), segment("b", Box(20, 0, 30, 20))).toMergedOcrResultsJson(0, 0))
        assertTrue(results.getJSONObject(0).isNull("confidence"))
    }

}
