package org.autojs.autojs.core.automator.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [A11yStats]: searches and polled queries aggregate overall and per selector (slowest first),
 * Flow steps by name (by total time), the bounded selector table, reset and the text report.
 * zh-CN: [A11yStats]: 搜索与轮询查询在总体与每个选择器上聚合 (最慢者在前), Flow 步骤按名字 (按总耗时), 有上限的选择器表, 重置与文本报告.
 */
class A11yStatsTest {

    private var now = 1_000L

    private val stats = A11yStats { now }

    @Test
    fun searchesAndQueriesAggregate() {
        stats.recordSearch("a", 10)
        stats.recordSearch("a", 30)
        stats.recordSearch("b", 5)
        stats.recordQuery("a", 45, 2, true)
        stats.recordQuery("b", 500, 5, false)
        now = 3_500
        val s = stats.snapshot()
        assertEquals(1_000, s.since)
        assertEquals(2_500, s.elapsed)
        assertEquals(A11yStats.Timing(3, 45, 30), s.searches)
        assertEquals(15.0, s.searches.averageMillis, 0.0)
        assertEquals(A11yStats.QueryTiming(2, 1, 7, 545, 500), s.queries)
        assertEquals(1, s.queries.missed)
        assertEquals(272.5, s.queries.averageMillis, 0.0)
        assertEquals(listOf("a", "b"), s.selectors.map { it.selector })
        assertEquals("a", s.slowest!!.selector)
        assertEquals(A11yStats.Timing(2, 40, 30), s.slowest!!.searches)
        assertEquals(A11yStats.Timing(1, 45, 45), s.slowest!!.queries)
        assertEquals(1, s.slowest!!.found)
        assertEquals(0, s.selectors[1].found)
        assertTrue(s.steps.isEmpty())
    }

    @Test
    fun stepsAreSortedByTotalTime() {
        stats.recordStep("click", 10, false)
        stats.recordStep("wait", 100, false)
        stats.recordStep("wait", 50, true)
        val steps = stats.snapshot().steps
        assertEquals(listOf("wait", "click"), steps.map { it.name })
        assertEquals(A11yStats.Timing(2, 150, 100), steps[0].runs)
        assertEquals(1, steps[0].rejected)
        assertEquals(0, steps[1].rejected)
    }

    @Test
    fun resetStartsOver() {
        stats.recordSearch("a", 10)
        stats.recordQuery("a", 10, 1, true)
        stats.recordStep("wait", 10, false)
        now = 2_000
        stats.reset()
        val s = stats.snapshot()
        assertEquals(2_000, s.since)
        assertEquals(A11yStats.Timing.NONE, s.searches)
        assertEquals(A11yStats.QueryTiming(0, 0, 0, 0, 0), s.queries)
        assertTrue(s.selectors.isEmpty())
        assertTrue(s.steps.isEmpty())
        assertNull(s.slowest)
        assertEquals(0.0, s.searches.averageMillis, 0.0)
    }

    @Test
    fun theSelectorTableIsBounded() {
        for (i in 0 until A11yStats.MAX_SELECTORS + 44) {
            stats.recordSearch("selector$i", 1)
        }
        val selectors = stats.snapshot().selectors
        assertEquals(A11yStats.MAX_SELECTORS + 1, selectors.size)
        val others = selectors.first { it.selector == A11yStats.OTHERS }
        assertEquals(44, others.searches.count)
        stats.recordSearch("selector0", 9)
        assertEquals(A11yStats.MAX_SELECTORS + 1, stats.snapshot().selectors.size)
        assertEquals("selector0", stats.snapshot().slowest!!.selector)
    }

    @Test
    fun negativeDurationsCountAsZero() {
        stats.recordSearch("a", -5)
        stats.recordQuery("a", -5, -1, false)
        stats.recordStep("wait", -5, false)
        val s = stats.snapshot()
        assertEquals(A11yStats.Timing(1, 0, 0), s.searches)
        assertEquals(A11yStats.QueryTiming(1, 0, 0, 0, 0), s.queries)
        assertEquals(A11yStats.Timing(1, 0, 0), s.steps[0].runs)
    }

    @Test
    fun theReportNamesTheKeyNumbers() {
        stats.recordSearch("text(\"a\")", 10)
        stats.recordSearch("text(\"a\")", 30)
        stats.recordSearch("id(\"b\")", 5)
        stats.recordQuery("text(\"a\")", 45, 2, true)
        stats.recordQuery("id(\"b\")", 500, 5, false)
        stats.recordStep("wait", 100, false)
        stats.recordStep("wait", 50, true)
        now = 36_200
        val text = stats.snapshot().render()
        val lines = text.lines()
        assertTrue(lines[0], lines[0].startsWith("a11y stats since ") && lines[0].endsWith(", elapsed 35.2 s"))
        assertEquals("searches: 3, total 45 ms, average 15.0 ms, max 30 ms", lines[1])
        assertEquals("queries: 2 (found 1, missed 1, attempts 7), total 545 ms, average 272.5 ms, max 500 ms", lines[2])
        assertEquals("slowest selectors (by max search time):", lines[3])
        assertEquals("  1. text(\"a\")  searches 2, total 40 ms, average 20.0 ms, max 30 ms; queries 1, found 1", lines[4])
        assertEquals("  2. id(\"b\")  searches 1, total 5 ms, average 5.0 ms, max 5 ms; queries 1, found 0", lines[5])
        assertEquals("flow steps (by total time):", lines[6])
        assertEquals("  wait  runs 2, total 150 ms, average 75.0 ms, max 100 ms, rejected 1", lines[7])
        assertEquals(8, lines.size)
        assertEquals(text, stats.snapshot().toString())
        val empty = A11yStats { now }.snapshot().render().lines()
        assertEquals(3, empty.size)
        assertEquals("searches: 0, total 0 ms, average 0.0 ms, max 0 ms", empty[1])
    }

    @Test
    fun theReportListsAtMostFiveSelectors() {
        for (i in 0 until 8) {
            stats.recordSearch("s$i", i.toLong())
        }
        val text = stats.snapshot().render()
        assertTrue(text, text.contains("  5. s3  "))
        assertTrue(text, !text.contains("  6. "))
        assertTrue(text, text.lines()[4].startsWith("  1. s7  "))
    }

    /** docs/selector/ROADMAP.md P2.4 / appendix C Q15: the fast path counters, their line in the report (only once used) and reset. */
    @Test
    fun fastPathCounters() {
        assertEquals(A11yStats.FastPathCounts.NONE, stats.snapshot().fastPath)
        assertTrue(!stats.snapshot().render().contains("fast path"))
        repeat(3) { stats.recordFastPath(A11yStats.FastPathEvent.MISS) }
        repeat(2) { stats.recordFastPath(A11yStats.FastPathEvent.FALLBACK) }
        stats.recordFastPath(A11yStats.FastPathEvent.HIT)
        val s = stats.snapshot()
        assertEquals(A11yStats.FastPathCounts(hits = 1, misses = 3, fallbacks = 2, mismatches = 0), s.fastPath)
        assertEquals("fast path: hits 1, misses 3, fallbacks 2, mismatches 0, walks 0", s.render().lines()[3])
        stats.recordFastPath(A11yStats.FastPathEvent.MISMATCH)
        assertEquals(1, stats.snapshot().fastPath.mismatches)
        // Appendix C Q15: the intermediate attempts that walked because the lookups of their query cost more.
        // zh-CN: 附录 C Q15: 因所属查询的查找更费时而直接遍历的中间尝试.
        repeat(4) { stats.recordFastPath(A11yStats.FastPathEvent.WALK) }
        assertEquals(A11yStats.FastPathCounts(hits = 1, misses = 3, fallbacks = 2, mismatches = 1, walks = 4), stats.snapshot().fastPath)
        stats.reset()
        assertEquals(A11yStats.FastPathCounts.NONE, stats.snapshot().fastPath)
    }

}
