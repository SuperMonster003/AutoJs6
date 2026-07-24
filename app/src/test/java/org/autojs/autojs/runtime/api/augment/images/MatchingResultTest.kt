package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.core.image.TemplateMatching
import org.junit.After
import org.junit.Assert.assertSame
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
}
