package org.autojs.autojs.core.automator.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The per-script tallies behind `auto.stats`: every selector search (one look at the window
 * roots), every polled query (`findOne(timeout)`, `untilFind()`, `waitFor()` and the like: its
 * attempts and whether it found anything), the per-selector timings (bounded at [MAX_SELECTORS]
 * distinct selectors, the rest pooled under [OTHERS]) and the Flow steps by name. Thread-safe;
 * [snapshot] is an immutable copy and [reset] starts over.
 * zh-CN: `auto.stats` 背后的每脚本计数: 每次选择器搜索 (对窗口根的一次查看), 每次轮询查询 (`findOne(timeout)`, `untilFind()`,
 * `waitFor()` 等: 其尝试次数与是否找到), 每个选择器的耗时 (至多 [MAX_SELECTORS] 个不同选择器, 其余并入 [OTHERS]), 以及按名字
 * 统计的 Flow 步骤. 线程安全; [snapshot] 为不可变副本, [reset] 从头开始.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class A11yStats(private val clock: () -> Long = System::currentTimeMillis) {

    /** A count with its total and maximum duration. zh-CN: 计数及其总耗时与最大耗时. */
    data class Timing(val count: Long, val totalMillis: Long, val maxMillis: Long) {

        val averageMillis: Double get() = if (count == 0L) 0.0 else totalMillis.toDouble() / count

        companion object {

            val NONE = Timing(0, 0, 0)

        }

    }

    /** The polled queries: how many found something and how many attempts they took in all. zh-CN: 轮询查询: 多少次找到了, 总共尝试了多少次. */
    data class QueryTiming(val count: Long, val found: Long, val attempts: Long, val totalMillis: Long, val maxMillis: Long) {

        val missed: Long get() = count - found

        val averageMillis: Double get() = if (count == 0L) 0.0 else totalMillis.toDouble() / count

    }

    data class SelectorStats(val selector: String, val searches: Timing, val queries: Timing, val found: Long)

    /**
     * The framework fast path of the polled queries (docs/selector/ROADMAP.md P2.3 / P2.4): [hits] attempts answered by
     * a single candidate, [misses] attempts answered "nothing" by an empty lookup, [fallbacks] attempts with a key that
     * walked the tree all the same (several candidates, the last attempt, a verification), [mismatches] full searches
     * that found nodes after an empty lookup (appendix C Q1: expected to stay 0).
     * zh-CN: 轮询查询的框架快路径 (P2.3 / P2.4): [hits] 由唯一候选作答的尝试, [misses] 由空查找判定未找到的尝试, [fallbacks] 有键但仍遍历整棵树的尝试
     * (多个候选, 最后一次尝试, 校验), [mismatches] 查找为空而完整搜索找到节点的次数 (附录 C Q1: 应保持为 0).
     */
    data class FastPathCounts(val hits: Long, val misses: Long, val fallbacks: Long, val mismatches: Long) {

        val isEmpty: Boolean get() = hits == 0L && misses == 0L && fallbacks == 0L && mismatches == 0L

        companion object {

            val NONE = FastPathCounts(0, 0, 0, 0)

        }

    }

    /** One event of the fast path, see [FastPathCounts]. zh-CN: 快路径的一次事件, 见 [FastPathCounts]. */
    enum class FastPathEvent { HIT, MISS, FALLBACK, MISMATCH }

    data class StepStats(val name: String, val runs: Timing, val rejected: Long)

    /**
     * An immutable copy: [selectors] slowest first (by maximum search time, then total),
     * [steps] by total time.
     * zh-CN: 不可变副本: [selectors] 最慢者在前 (按最大搜索耗时, 其次总耗时), [steps] 按总耗时.
     */
    class Snapshot(
        val since: Long,
        val now: Long,
        val searches: Timing,
        val queries: QueryTiming,
        val selectors: List<SelectorStats>,
        val steps: List<StepStats>,
        val fastPath: FastPathCounts = FastPathCounts.NONE,
    ) {

        val elapsed: Long get() = now - since

        val slowest: SelectorStats? get() = selectors.firstOrNull()

        /** The text report, at most [MAX_RENDERED_SELECTORS] selectors. zh-CN: 文本报告, 至多列出 [MAX_RENDERED_SELECTORS] 个选择器. */
        fun render(): String = buildString {
            append("a11y stats since ").append(SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(since)))
            append(", elapsed ").append(seconds(elapsed))
            append("\nsearches: ").append(searches.count).append(", ").append(timing(searches))
            append("\nqueries: ").append(queries.count).append(" (found ").append(queries.found).append(", missed ").append(queries.missed)
            append(", attempts ").append(queries.attempts).append("), ").append(timing(Timing(queries.count, queries.totalMillis, queries.maxMillis)))
            if (!fastPath.isEmpty) {
                append("\nfast path: hits ").append(fastPath.hits).append(", misses ").append(fastPath.misses)
                append(", fallbacks ").append(fastPath.fallbacks).append(", mismatches ").append(fastPath.mismatches)
            }
            if (selectors.isNotEmpty()) {
                append("\nslowest selectors (by max search time):")
                selectors.take(MAX_RENDERED_SELECTORS).forEachIndexed { index, s ->
                    append("\n  ").append(index + 1).append(". ").append(s.selector)
                    append("  searches ").append(s.searches.count).append(", ").append(timing(s.searches))
                    if (s.queries.count > 0) append("; queries ").append(s.queries.count).append(", found ").append(s.found)
                }
            }
            if (steps.isNotEmpty()) {
                append("\nflow steps (by total time):")
                for (s in steps) {
                    append("\n  ").append(s.name).append("  runs ").append(s.runs.count).append(", ").append(timing(s.runs))
                    if (s.rejected > 0) append(", rejected ").append(s.rejected)
                }
            }
        }

        override fun toString(): String = render()

        private fun timing(t: Timing): String = "total ${t.totalMillis} ms, average ${String.format(Locale.ROOT, "%.1f", t.averageMillis)} ms, max ${t.maxMillis} ms"

        private fun seconds(millis: Long): String = String.format(Locale.ROOT, "%.1f s", millis / 1000.0)

    }

    private class Bucket {

        var count = 0L

        var total = 0L

        var max = 0L

        fun add(millis: Long) {
            count += 1
            total += millis
            if (millis > max) max = millis
        }

        fun timing() = Timing(count, total, max)

    }

    private class SelectorBucket {

        val searches = Bucket()

        val queries = Bucket()

        var found = 0L

    }

    private class StepBucket {

        val runs = Bucket()

        var rejected = 0L

    }

    private val searches = Bucket()

    private val queries = Bucket()

    private var found = 0L

    private var attempts = 0L

    private val fastPath = LongArray(FastPathEvent.entries.size)

    private val selectors = LinkedHashMap<String, SelectorBucket>()

    private val steps = LinkedHashMap<String, StepBucket>()

    private var since = clock()

    /** One search of [selector] over the roots that took [elapsed] ms. zh-CN: 对各根的一次 [selector] 搜索, 耗时 [elapsed] ms. */
    @Synchronized
    fun recordSearch(selector: String, elapsed: Long) {
        val millis = elapsed.coerceAtLeast(0)
        searches.add(millis)
        bucket(selector).searches.add(millis)
    }

    /** One polled query of [selector]: [attempts] searches over [elapsed] ms, [found] or given up. zh-CN: 一次对 [selector] 的轮询查询: [elapsed] ms 内 [attempts] 次搜索, [found] 或已放弃. */
    @Synchronized
    fun recordQuery(selector: String, elapsed: Long, attempts: Long, found: Boolean) {
        val millis = elapsed.coerceAtLeast(0)
        queries.add(millis)
        this.attempts += attempts.coerceAtLeast(0)
        if (found) this.found += 1
        val bucket = bucket(selector)
        bucket.queries.add(millis)
        if (found) bucket.found += 1
    }

    /** One [event] of the fast path. zh-CN: 快路径的一次 [event]. */
    @Synchronized
    fun recordFastPath(event: FastPathEvent) {
        fastPath[event.ordinal] += 1
    }

    /** One settled Flow step named [name]. zh-CN: 一个已结算的名为 [name] 的 Flow 步骤. */
    @Synchronized
    fun recordStep(name: String, elapsed: Long, rejected: Boolean) {
        val bucket = steps.getOrPut(name) { StepBucket() }
        bucket.runs.add(elapsed.coerceAtLeast(0))
        if (rejected) bucket.rejected += 1
    }

    @Synchronized
    fun reset() {
        searches.apply { count = 0; total = 0; max = 0 }
        queries.apply { count = 0; total = 0; max = 0 }
        found = 0
        attempts = 0
        fastPath.fill(0)
        selectors.clear()
        steps.clear()
        since = clock()
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        since = since,
        now = clock(),
        searches = searches.timing(),
        queries = QueryTiming(queries.count, found, attempts, queries.total, queries.max),
        selectors = selectors.map { (selector, b) -> SelectorStats(selector, b.searches.timing(), b.queries.timing(), b.found) }
            .sortedWith(compareByDescending<SelectorStats> { it.searches.maxMillis }.thenByDescending { it.searches.totalMillis }),
        steps = steps.map { (name, b) -> StepStats(name, b.runs.timing(), b.rejected) }
            .sortedWith(compareByDescending<StepStats> { it.runs.totalMillis }.thenByDescending { it.runs.count }),
        fastPath = FastPathCounts(
            hits = fastPath[FastPathEvent.HIT.ordinal],
            misses = fastPath[FastPathEvent.MISS.ordinal],
            fallbacks = fastPath[FastPathEvent.FALLBACK.ordinal],
            mismatches = fastPath[FastPathEvent.MISMATCH.ordinal],
        ),
    )

    private fun bucket(selector: String): SelectorBucket = selectors[selector] ?: when {
        selectors.size < MAX_SELECTORS -> SelectorBucket().also { selectors[selector] = it }
        else -> selectors.getOrPut(OTHERS) { SelectorBucket() }
    }

    companion object {

        const val MAX_SELECTORS = 256

        const val OTHERS = "(others)"

        const val MAX_RENDERED_SELECTORS = 5

    }

}
