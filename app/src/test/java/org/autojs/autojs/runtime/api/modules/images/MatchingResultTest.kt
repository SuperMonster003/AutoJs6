package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.core.image.TemplateMatching
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.opencv.core.Point

class MatchingResultTest {

    private lateinit var leftTopBest: TemplateMatching.Match
    private lateinit var middle: TemplateMatching.Match
    private lateinit var rightBottomWorst: TemplateMatching.Match

    @Before
    fun setUp() {
        Context.enter()
        leftTopBest = TemplateMatching.Match(Point(10.1, 9.5), 0.92)
        middle = TemplateMatching.Match(Point(10.4, 9.9), 0.91)
        rightBottomWorst = TemplateMatching.Match(Point(11.0, 10.2), 0.90)
    }

    @After
    fun tearDown() {
        Context.exit()
    }

    @Test
    fun directionalAndSimilaritySelectorsReturnTheCorrectMatch() {
        val result = MatchingResult(listOf(middle, rightBottomWorst, leftTopBest))

        assertSame(leftTopBest, result.leftmost())
        assertSame(rightBottomWorst, result.rightmost())
        assertSame(leftTopBest, result.topmost())
        assertSame(rightBottomWorst, result.bottommost())
        assertSame(leftTopBest, result.best())
        assertSame(rightBottomWorst, result.worst())
    }

    @Test
    fun builtInSortUsesPreciseDoubleComparisons() {
        val result = MatchingResult(listOf(middle, rightBottomWorst, leftTopBest))

        assertSame(leftTopBest, result.sortBy("left-top").first())
        assertSame(rightBottomWorst, result.sortBy("right-bottom").first())
    }

    @Test
    fun sizeAndEmptinessReflectTheMatchList() {
        val result = MatchingResult(listOf(middle, leftTopBest))
        assertEquals(2, result.size)
        assertFalse(result.isEmpty())
        assertTrue(result.isNotEmpty())
        assertTrue(MatchingResult(emptyList()).isEmpty())
        assertEquals(0, MatchingResult(emptyList()).size)
    }

    @Test
    fun filterKeepsMatchesForWhichThePredicateIsTruthy() {
        val result = MatchingResult(listOf(middle, rightBottomWorst, leftTopBest))
        /* Rhino hands back JS values, so the predicate result is coerced like a JS boolean. */
        val strong = result.filter { it.similarity > 0.905 }
        assertEquals(2, strong.size)
        assertSame(middle, strong.first())
        assertSame(leftTopBest, strong.last())
        assertEquals(0, result.filter { null }.size)
        assertEquals(3, result.filter { 1 }.size)
        assertEquals(0, result.filter { "" }.size)
    }

    @Test
    fun customComparatorAcceptsJsNumbers() {
        val result = MatchingResult(listOf(middle, rightBottomWorst, leftTopBest))
        /* A JS comparator returns a double such as -0.02; it must not be cast to Int. */
        val ascending = result.sortBy { a, b -> a.similarity - b.similarity }
        assertSame(rightBottomWorst, ascending.first())
        assertSame(leftTopBest, ascending.last())
        val descending = result.sortBy { a, b -> (b.similarity - a.similarity).toString() }
        assertSame(leftTopBest, descending.first())
    }
}
