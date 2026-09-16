package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LineDiffTest {

    @Test
    fun splitLinesHandlesAllTerminators() {
        assertEquals(emptyList<String>(), LineDiff.splitLines(""))
        assertEquals(listOf("a"), LineDiff.splitLines("a"))
        assertEquals(listOf("a"), LineDiff.splitLines("a\n"))
        assertEquals(listOf("a", "b"), LineDiff.splitLines("a\r\nb"))
        assertEquals(listOf("a", "b"), LineDiff.splitLines("a\rb\n"))
        assertEquals(listOf("a", ""), LineDiff.splitLines("a\n\n"))
        assertEquals(listOf(""), LineDiff.splitLines("\n"))
    }

    @Test
    fun identicalInputsHaveNoChanges() {
        val lines = listOf("x", "y", "z")
        val result = LineDiff.diff(lines, lines)
        assertFalse(result.hasChanges)
        assertEquals(3, result.unchanged)
        assertEquals(listOf(1, 2, 3), result.lines.map { it.leftLineNo })
        assertEquals(listOf(1, 2, 3), result.lines.map { it.rightLineNo })
    }

    @Test
    fun emptySides() {
        val all = LineDiff.diff(emptyList(), listOf("a", "b"))
        assertEquals(listOf(LineDiff.Kind.INSERT, LineDiff.Kind.INSERT), all.lines.map { it.kind })
        assertEquals(listOf(0, 0), all.lines.map { it.leftLineNo })
        assertEquals(listOf(1, 2), all.lines.map { it.rightLineNo })

        val none = LineDiff.diff(listOf("a"), emptyList())
        assertEquals(listOf(LineDiff.Kind.DELETE), none.lines.map { it.kind })
        assertEquals(1, none.deleted)

        assertFalse(LineDiff.diff(emptyList(), emptyList()).hasChanges)
    }

    @Test
    fun middleChangeKeepsPrefixAndSuffix() {
        val left = listOf("head", "old-1", "old-2", "tail")
        val right = listOf("head", "new-1", "tail")
        val result = LineDiff.diff(left, right)
        assertEquals(
            listOf(
                LineDiff.Kind.EQUAL to "head",
                LineDiff.Kind.DELETE to "old-1",
                LineDiff.Kind.DELETE to "old-2",
                LineDiff.Kind.INSERT to "new-1",
                LineDiff.Kind.EQUAL to "tail",
            ),
            result.lines.map { it.kind to it.text },
        )
        assertEquals(1, result.inserted)
        assertEquals(2, result.deleted)
        assertEquals(2, result.unchanged)
    }

    @Test
    fun classicMyersExample() {
        // From the paper: ABCABBA -> CBABAC has edit distance 5.
        val left = "ABCABBA".map { it.toString() }
        val right = "CBABAC".map { it.toString() }
        val result = LineDiff.diff(left, right)
        assertEquals(5, result.inserted + result.deleted)
        assertReconstructs(left, right, result)
    }

    @Test
    fun randomInputsReconstructBothSidesWithMinimalScript() {
        val random = Random(20260916)
        repeat(400) {
            val alphabet = 1 + random.nextInt(4)
            val left = List(random.nextInt(0, 14)) { random.nextInt(alphabet).toString() }
            val right = List(random.nextInt(0, 14)) { random.nextInt(alphabet).toString() }
            val result = LineDiff.diff(left, right)
            assertReconstructs(left, right, result)
            assertEquals("LCS length for $left vs $right", lcsLength(left, right), result.unchanged)
        }
    }

    @Test
    fun largerShiftedInputStaysConsistent() {
        val left = List(500) { "line $it" }
        val right = listOf("inserted at top") + left.filterIndexed { i, _ -> i % 37 != 0 } + listOf("appended")
        val result = LineDiff.diff(left, right)
        assertReconstructs(left, right, result)
        assertEquals(lcsLength(left, right), result.unchanged)
    }

    @Test
    fun limitsAreInclusive() {
        assertFalse(LineDiff.exceedsLimits(10, 10, 100, 100, maxLinesPerSide = 10, maxBytesPerSide = 100))
        assertTrue(LineDiff.exceedsLimits(11, 10, 100, 100, maxLinesPerSide = 10, maxBytesPerSide = 100))
        assertTrue(LineDiff.exceedsLimits(10, 10, 100, 101, maxLinesPerSide = 10, maxBytesPerSide = 100))
    }

    private fun assertReconstructs(left: List<String>, right: List<String>, result: LineDiff.Result) {
        val rebuiltLeft = result.lines.filter { it.kind != LineDiff.Kind.INSERT }.map { it.text }
        val rebuiltRight = result.lines.filter { it.kind != LineDiff.Kind.DELETE }.map { it.text }
        assertEquals(left, rebuiltLeft)
        assertEquals(right, rebuiltRight)

        var expectedLeftNo = 1
        var expectedRightNo = 1
        for (line in result.lines) {
            when (line.kind) {
                LineDiff.Kind.EQUAL -> {
                    assertEquals(expectedLeftNo++, line.leftLineNo)
                    assertEquals(expectedRightNo++, line.rightLineNo)
                }
                LineDiff.Kind.DELETE -> {
                    assertEquals(expectedLeftNo++, line.leftLineNo)
                    assertEquals(0, line.rightLineNo)
                }
                LineDiff.Kind.INSERT -> {
                    assertEquals(0, line.leftLineNo)
                    assertEquals(expectedRightNo++, line.rightLineNo)
                }
            }
        }
        assertEquals(result.lines.count { it.kind == LineDiff.Kind.EQUAL }, result.unchanged)
        assertEquals(result.lines.count { it.kind == LineDiff.Kind.INSERT }, result.inserted)
        assertEquals(result.lines.count { it.kind == LineDiff.Kind.DELETE }, result.deleted)
    }

    private fun lcsLength(left: List<String>, right: List<String>): Int {
        val dp = Array(left.size + 1) { IntArray(right.size + 1) }
        for (i in 1..left.size) {
            for (j in 1..right.size) {
                dp[i][j] = if (left[i - 1] == right[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
        return dp[left.size][right.size]
    }
}
