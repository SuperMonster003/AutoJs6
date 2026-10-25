package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter
import org.autojs.autojs.core.automator.query.PollPolicy
import java.util.concurrent.ConcurrentHashMap

/**
 * The framework fast path of a selector search (docs/selector/ROADMAP.md P2.3, appendix C Q1 / Q2 / Q6 / Q11). A
 * selector with an exact `id` (the full name, or the entry name queried as `<root package>:id/<entry>` and
 * `android:id/<entry>`) asks the framework for its candidates first, one `findAccessibilityNodeInfosByViewId` per root.
 * An exact `text` takes no fast path (appendix C Q11): the text lookup of the framework misses most texts of a WebView
 * and some of ordinary views (measured on the devices of P2.3, appendix A):
 *
 * - no candidate: in an intermediate attempt of a polled query the attempt finds nothing without walking the tree; the
 *   first and the last attempt of the query, and a verification attempt at least every [VERIFY_INTERVAL] ms of a long
 *   query, walk the tree all the same, and a node found that way after an empty lookup is a mismatch (counted in
 *   `auto.stats`; the miss of the lookup is then no longer trusted for the package of that root in the script, see
 *   [Ledger]). The first attempt walking the tree keeps a node already on the screen from waiting for the last attempt
 *   when the lookup does not see it (an HTML id of a WebView, or an id of another package, for ones);
 * - exactly one candidate that passes the selector, for a single result searched depth first: it is the result;
 * - anything else (several candidates, all results, breadth first, a candidate that does not pass): the full search,
 *   so the order and the number of results never depend on the lookups.
 *
 * Single searches (`find()`, `findOnce()`, `exists()`, `pickup`) always walk the tree (Q1). Android-free: the lookups
 * are behind [Lookup], so the dispatch is unit tested over `FakeUiNode`.
 *
 * zh-CN: 选择器搜索的框架快路径 (P2.3, 附录 C Q1 / Q2 / Q6 / Q11). 含精确 `id` (全名, 或以 `<根节点包名>:id/<条目>` 与 `android:id/<条目>` 查询的条目名)
 * 的选择器先向框架取候选, 每个根节点一次 `findAccessibilityNodeInfosByViewId`. 精确 `text` 不走快路径 (附录 C Q11): 框架的文本查找漏掉 WebView
 * 中的大部分文本与普通控件中的部分文本 (P2.3 的真机实测, 附录 A):
 * 没有候选时, 轮询查询的中间尝试不遍历树即判定未找到; 查询的第一次与最后一次尝试, 以及长查询中至少每 [VERIFY_INTERVAL] ms 一次的校验尝试仍遍历整棵树,
 * 查找为空而遍历找到节点即为不一致 (计入 `auto.stats`; 此后该脚本对该根节点所属包不再信任查找为空的结论, 见 [Ledger]). 第一次尝试遍历整棵树,
 * 使查找看不到的节点 (例如 WebView 的 HTML id, 其他包的 id) 已在屏幕上时不必等到最后一次尝试;
 * 单个结果且深度优先时, 恰好一个候选并通过选择器即为结果; 其余情况 (多个候选, 全部结果, 广度优先, 候选未通过) 走完整搜索,
 * 结果的顺序与数量因此从不取决于查找. 单次搜索 (`find()`, `findOnce()`, `exists()`, `pickup`) 总是遍历整棵树 (Q1).
 * Android-free: 查找藏在 [Lookup] 之后, 分派逻辑以 `FakeUiNode` 做单元测试.
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
object FastPath {

    /** The longest stretch of a polled query without a full search, in milliseconds (appendix C Q11). zh-CN: 轮询查询中两次完整搜索之间的最长间隔 (毫秒, 附录 C Q11). */
    const val VERIFY_INTERVAL = 1000L

    /** What the framework is asked for. zh-CN: 向框架查找的内容. */
    sealed class Key(val kind: String) {

        /** `id("pkg:id/entry")`. zh-CN: 完整 id. */
        class ViewId(val name: String) : Key(KIND_ID)

        /** `id("entry")`: the entry name in the package of the root, or in `android`. zh-CN: 条目名: 根节点所属包或 `android` 中的同名 id. */
        class IdEntry(val entry: String) : Key(KIND_ID)

        override fun toString() = when (this) {
            is ViewId -> "id($name)"
            is IdEntry -> "id($entry)"
        }

    }

    /** What one search attempt lets the fast path decide; [looksUp]: whether it asks the framework first. zh-CN: 一次搜索尝试允许快路径决定的内容; [looksUp]: 是否先向框架查找. */
    enum class Attempt(val looksUp: Boolean) {

        /** A single search: the full search (Q1). zh-CN: 单次搜索: 完整搜索 (Q1). */
        SINGLE(false),

        /** A polled attempt between the first and the last: no candidate means nothing found. zh-CN: 第一次与最后一次之间的轮询尝试: 没有候选即未找到. */
        INTERMEDIATE(true),

        /**
         * A polled attempt after the first that walks the tree without asking the framework, the last one included: the
         * fast path of the query costs no less than its walk ([Query], appendix C Q15 / Q17).
         * zh-CN: 第一次之后不向框架查找而直接遍历整棵树的轮询尝试, 含最后一次: 该查询的快路径不比遍历省 ([Query], 附录 C Q15 / Q17).
         */
        WALK(false),

        /** The first or the last polled attempt, or a verification: no candidate still walks the tree. zh-CN: 第一次或最后一次轮询尝试, 或校验: 没有候选时仍遍历整棵树. */
        FULL(true),

    }

    /**
     * What one search attempt cost, -1 for a part it did not do: [lookup] the framework lookups, [walk] the walk of the
     * roots. On a device the process CPU time in milliseconds (appendix C Q17), any one unit in the tests. Filled by the
     * search, read by [Query.record].
     * zh-CN: 一次搜索尝试的开销, 未做的部分为 -1: [lookup] 框架查找, [walk] 遍历各根节点. 设备上为进程 CPU 时间 (毫秒, 附录 C Q17), 测试中为任一单位.
     * 由搜索填写, 由 [Query.record] 读取.
     */
    class Costs {

        var lookup = -1L

        var walk = -1L

        fun reset() {
            lookup = -1L
            walk = -1L
        }

    }

    /**
     * The framework lookups over the trees of [N], the candidates being platform handles [C] until one is attached.
     * zh-CN: [N] 树上的框架查找, 候选在被挂接之前是平台句柄 [C].
     */
    interface Lookup<N : UiNode<N>, C : Any> {

        /** The candidates under [root] for [key], or null when the lookup is not available (the full search runs). zh-CN: [root] 之下 [key] 的候选, 查找不可用时为 null (改走完整搜索). */
        fun candidates(root: N, key: Key): List<C>?

        /** [candidate] as a node at its depth below [root], or null when it cannot be placed there (the full search runs). zh-CN: 位于 [root] 之下相应深度的 [candidate] 节点, 无法定位时为 null (改走完整搜索). */
        fun attach(root: N, candidate: C): N?

        /** Gives back a candidate that is not part of the result. zh-CN: 归还不属于结果的候选. */
        fun release(candidate: C)

        /** Gives back an attached node that is not part of the result. zh-CN: 归还不属于结果的已挂接节点. */
        fun releaseNode(node: N)

    }

    /** What the fast path made of an attempt. zh-CN: 快路径对一次尝试的结论. */
    sealed class Outcome<out N> {

        /** The single result. zh-CN: 唯一的结果. */
        class Hit<N>(val node: N) : Outcome<N>()

        /** Nothing found, without a full search. zh-CN: 未找到, 不做完整搜索. */
        object Miss : Outcome<Nothing>()

        /**
         * The full search decides; [emptyLookup]: the lookups found no candidate, so whatever it finds is a mismatch.
         * zh-CN: 由完整搜索决定; [emptyLookup]: 查找没有候选, 因此完整搜索找到的都是不一致.
         */
        class Search(val emptyLookup: Boolean) : Outcome<Nothing>()

    }

    /**
     * The key of the filters of a selector: the first exact `id` among them; null when there is none (the fast path
     * does not apply). zh-CN: 选择器各过滤器的键: 其中首个精确 `id`; 没有时为 null (快路径不适用).
     */
    fun keyOf(filters: List<Filter>): Key? {
        for (filter in filters) {
            if (filter !is StringPropertyFilter) continue
            val exact = filter.matcher.exact?.takeIf { it.isNotEmpty() } ?: continue
            when (filter.property) {
                // The framework resolves a full name only (`pkg:id/entry`); an id of another form is left to the search.
                // zh-CN: 框架只解析完整名称 (`pkg:id/entry`); 其他形式的 id 留给搜索.
                StringProperty.ID -> if (ID_IDENTIFIER in exact) return Key.ViewId(exact)
                StringProperty.ID_ENTRY -> return Key.IdEntry(exact)
                else -> Unit
            }
        }
        return null
    }

    /**
     * Decides an attempt over [roots] for [key]: [filter] is the whole selector, [single] whether one result is wanted
     * from a depth-first search. Candidates that are not returned are released.
     * zh-CN: 针对 [key] 在 [roots] 上判定一次尝试: [filter] 为整个选择器, [single] 表示是否要求深度优先搜索的单个结果. 未返回的候选会被释放.
     */
    fun <N : UiNode<N>, C : Any> decide(roots: List<N>, key: Key, filter: Filter, single: Boolean, attempt: Attempt, lookup: Lookup<N, C>): Outcome<N> {
        val found = ArrayList<Pair<N, C>>()
        for (root in roots) {
            val candidates = lookup.candidates(root, key)
            if (candidates == null) {
                found.forEach { lookup.release(it.second) }
                return Outcome.Search(false)
            }
            candidates.forEach { found += root to it }
        }
        if (found.isEmpty()) {
            return if (attempt == Attempt.INTERMEDIATE) Outcome.Miss else Outcome.Search(true)
        }
        if (single && found.size == 1) {
            val (root, candidate) = found[0]
            val node = lookup.attach(root, candidate)
            if (node == null) {
                lookup.release(candidate)
                return Outcome.Search(false)
            }
            if (filter.filter(node)) return Outcome.Hit(node)
            lookup.releaseNode(node)
            return Outcome.Search(false)
        }
        found.forEach { lookup.release(it.second) }
        return Outcome.Search(false)
    }

    /**
     * The attempts of one polled query (appendix C Q1 / Q11): the last attempt and the verifications walk the tree.
     * An attempt is taken as the last when the remaining time does not exceed the sleep before the next one (that
     * attempt or the next one then ends after the timeout), or when it is the last by count. [clock] is the uptime.
     *
     * Adaptive per query (appendix C Q15): a full attempt measures the walk, the first intermediate attempt after it
     * measures the fast path (its lookups, and its walk when they fall back); when the fast path costs no less than the
     * walk, the other attempts until the next full attempt, the last one included, walk the tree without lookups
     * ([Attempt.WALK]: a walk is as conclusive as a full attempt), and every full attempt (the verifications included)
     * measures and compares anew. The costs are the process CPU time (appendix C Q17): the elapsed times of the lookups
     * and the walk are close on the screens of appendix A while what they cost the process is not (22120RN86C: the lookups
     * 7.0 ms against a walk of 12.9 ms on the main screen of the host, 9.3 ms against 8.7 ms on a floaty window). The
     * results stay the same, the fast path deciding only misses and single candidates.
     *
     * zh-CN: 一次轮询查询的各次尝试 (附录 C Q1 / Q11): 最后一次尝试与校验遍历整棵树. 剩余时间不超过下一次之前的休眠时 (这次或下一次尝试
     * 将在超时之后结束), 或按次数为最后一次时, 视为最后一次尝试. [clock] 为 uptime.
     * 按查询自适应 (附录 C Q15): 完整尝试测量遍历的开销, 其后的第一次中间尝试测量快路径的开销 (查找, 以及回退时的遍历); 快路径不比遍历省的,
     * 到下一次完整尝试为止的其余尝试 (含最后一次) 不查找而直接遍历 ([Attempt.WALK]: 遍历与完整尝试同样可下结论), 每次完整尝试 (含校验) 重新测量并比较.
     * 开销为进程 CPU 时间 (附录 C Q17): 附录 A 各屏幕上查找与遍历的墙钟耗时接近, 对进程的开销却不同 (22120RN86C: 宿主主界面查找 7.0 ms 对遍历 12.9 ms,
     * 悬浮窗 9.3 ms 对 8.7 ms). 结果不变, 快路径只判定未命中与单候选.
     */
    class Query(private val policy: PollPolicy, private val clock: () -> Long) {

        private var lastFull = clock()

        /** The walk of the latest full attempt ([Costs]); -1 when it did not walk. zh-CN: 最近一次完整尝试的遍历开销 ([Costs]); 未遍历时为 -1. */
        private var walkCost = -1L

        /** The fast path of the first intermediate attempt since then ([Costs]); -1 when not measured yet. zh-CN: 此后第一次中间尝试的快路径开销 ([Costs]); 尚未测量时为 -1. */
        private var fastCost = -1L

        /** Whether the fast path of the query costs no less than its walk. zh-CN: 本查询的快路径是否不比遍历省. */
        private val walks get() = walkCost >= 0 && fastCost >= walkCost

        /** The attempt number [round] (from 1) with [remaining] milliseconds before the timeout. zh-CN: 第 [round] 次 (从 1 计) 尝试, 距超时 [remaining] 毫秒. */
        fun attempt(round: Long, remaining: Long): Attempt {
            if (policy.times == 1L) return Attempt.SINGLE
            val now = clock()
            val last = policy.times != PollPolicy.UNBOUNDED && round >= policy.times
                    || policy.timeout != PollPolicy.UNBOUNDED && remaining <= policy.intervalAfter(round - 1)
            return when {
                round <= 1 || now - lastFull >= VERIFY_INTERVAL -> Attempt.FULL.also { lastFull = now }
                walks -> Attempt.WALK
                last -> Attempt.FULL.also { lastFull = now }
                else -> Attempt.INTERMEDIATE
            }
        }

        /** What an attempt cost, [attempt] being what [Query.attempt] gave for it. zh-CN: 一次尝试的耗时, [attempt] 为 [Query.attempt] 为它给出的值. */
        fun record(attempt: Attempt, costs: Costs) {
            when (attempt) {
                Attempt.FULL -> {
                    walkCost = costs.walk
                    fastCost = -1L
                }
                Attempt.INTERMEDIATE -> if (fastCost < 0 && costs.lookup >= 0) {
                    fastCost = costs.lookup + costs.walk.coerceAtLeast(0L)
                }
                else -> Unit
            }
        }

    }

    /**
     * The packages whose roots gave an empty lookup where the full search then found nodes, by [Key.kind]: there the
     * fast path no longer decides misses for the script (the bridge keeps one ledger). Thread-safe.
     * zh-CN: 查找为空而完整搜索随后找到节点的根节点所属包, 按 [Key.kind] 区分: 在这些包上快路径不再为该脚本判定未命中 (每个 bridge 一份). 线程安全.
     */
    class Ledger {

        private val unreliable: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun markUnreliable(packageName: String?, kind: String) {
            unreliable += "$kind:${packageName.orEmpty()}"
        }

        fun isUnreliable(packageName: String?, kind: String) = unreliable.isNotEmpty() && "$kind:${packageName.orEmpty()}" in unreliable

        fun clear() = unreliable.clear()

    }

    private const val ID_IDENTIFIER = ":id/"

    const val KIND_ID = "id"


}
