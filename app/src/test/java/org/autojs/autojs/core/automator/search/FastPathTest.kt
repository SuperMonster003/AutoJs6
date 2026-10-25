package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.IdFilter
import org.autojs.autojs.core.automator.filter.IntFilter
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.filter.StringMatcher
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter
import org.autojs.autojs.core.automator.query.PollPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/selector/ROADMAP.md P2.3 (appendix C Q1 / Q11): the dispatch of the framework fast path over `FakeUiNode` trees
 * with the lookup of [FakeUiNode.findByViewId]: the key of a selector (an exact text has none, Q11), an empty lookup in an
 * intermediate and in a full attempt, a single candidate that passes or not, several candidates, all results /
 * breadth first / a negative index (not single), unavailable lookups, the release of the candidates, and the attempt
 * plan of a polled query (the first attempt, the last attempt by time and by count, the verification of long queries).
 *
 * zh-CN: P2.3 (附录 C Q1 / Q11): 以 [FakeUiNode.findByViewId] 查找, 在 `FakeUiNode` 树上验证框架快路径的分派: 选择器的键 (精确文本没有键, Q11),
 * 中间尝试与完整尝试中的空查找, 单候选通过与不通过, 多候选, 全部结果 / 广度优先 / 负数索引 (非单个), 查找不可用, 候选的释放, 以及轮询查询的
 * 尝试计划 (第一次尝试, 按时间与按次数的最后一次尝试, 长查询的校验).
 */
class FastPathTest {

    private class FakeLookup : FastPath.Lookup<FakeUiNode, FakeUiNode> {
        val released = ArrayList<String>()
        override fun candidates(root: FakeUiNode, key: FastPath.Key): List<FakeUiNode>? = when (key) {
            is FastPath.Key.ViewId -> root.findByViewId(key.name)
            is FastPath.Key.IdEntry -> {
                val own = root.findByViewId("${root.packageName}:id/${key.entry}") ?: return null
                val framework = root.findByViewId("android:id/${key.entry}") ?: return null
                own + framework
            }
        }

        override fun attach(root: FakeUiNode, candidate: FakeUiNode): FakeUiNode? = candidate.takeIf { it.descendantsOf(root) }

        override fun release(candidate: FakeUiNode) {
            released += candidate.name
        }

        override fun releaseNode(node: FakeUiNode) {
            released += node.name
        }

        private fun FakeUiNode.descendantsOf(root: FakeUiNode) = root.descendantsAndSelf().any { it === this }
    }

    //  root (com.example)
    //   +- list
    //   |   +- ok      id com.example:id/ok, text "OK", clickable
    //   |   +- title1  id android:id/title, text "Wi-Fi"
    //   |   +- title2  id android:id/title, text "Bluetooth"
    //   +- footer      text "ok, then", desc "Okay"
    private fun tree() = FakeUiNode.root {
        packageName = "com.example"
        child("list") {
            child("ok", clickable = true) {
                id = "com.example:id/ok"
                text = "OK"
            }
            child("title1") {
                id = "android:id/title"
                text = "Wi-Fi"
            }
            child("title2") {
                id = "android:id/title"
                text = "Bluetooth"
            }
        }
        child("footer") {
            text = "ok, then"
            desc = "Okay"
        }
    }

    private fun selector(vararg filters: Filter) = Selector().apply { filters.forEach { add(it) } }

    private fun text(s: String) = StringPropertyFilter(StringProperty.TEXT, StringMatcher.equals(s))

    private fun decide(root: FakeUiNode, selector: Selector, single: Boolean, attempt: FastPath.Attempt, lookup: FakeLookup = FakeLookup()): FastPath.Outcome<FakeUiNode> {
        val key = FastPath.keyOf(selector.filters)!!
        return FastPath.decide(listOf(root), key, selector.snapshot(), single, attempt, lookup)
    }

    @Test
    fun keyOfSelectors() {
        assertEquals("id(com.example:id/ok)", FastPath.keyOf(listOf(IdFilter.equals("com.example:id/ok"))).toString())
        assertTrue(FastPath.keyOf(listOf(IdFilter.equals("ok"))) is FastPath.Key.IdEntry)
        // An exact text takes no fast path (Q11), an id beside it does. zh-CN: 精确文本不走快路径 (Q11), 与之并列的 id 走.
        assertNull(FastPath.keyOf(listOf(text("OK"))))
        assertTrue(FastPath.keyOf(listOf(text("OK"), BooleanFilter(BooleanFilter.CLICKABLE), IdFilter.equals("ok"))) is FastPath.Key.IdEntry)
        assertNull(FastPath.keyOf(listOf(text(""))))
        assertNull(FastPath.keyOf(listOf(StringPropertyFilter(StringProperty.TEXT, StringMatcher.contains("OK")))))
        assertNull(FastPath.keyOf(listOf(StringPropertyFilter(StringProperty.TEXT, StringMatcher.matches("OK")))))
        assertNull(FastPath.keyOf(listOf(StringPropertyFilter(StringProperty.DESC, StringMatcher.equals("OK")))))
        assertNull(FastPath.keyOf(listOf(IdFilter.startsWith("ok"))))
        // An id without `:id/` under the full-id property (not built by `id()`) is left to the search.
        // zh-CN: 完整 id 属性下不含 `:id/` 的 id (并非由 `id()` 构建) 留给搜索.
        assertNull(FastPath.keyOf(listOf(StringPropertyFilter(StringProperty.ID, StringMatcher.equals("ok")))))
        assertNull(FastPath.keyOf(listOf(BooleanFilter(BooleanFilter.CLICKABLE))))
        assertNull(FastPath.keyOf(emptyList()))
    }

    @Test
    fun emptyLookupIsAMissOnlyInAnIntermediateAttempt() {
        val root = tree()
        val none = selector(IdFilter.equals("nothing"))
        assertSame(FastPath.Outcome.Miss, decide(root, none, true, FastPath.Attempt.INTERMEDIATE))
        assertTrue((decide(root, none, true, FastPath.Attempt.FULL) as FastPath.Outcome.Search).emptyLookup)
        assertSame(FastPath.Outcome.Miss, decide(root, selector(IdFilter.equals("none")), false, FastPath.Attempt.INTERMEDIATE))
        // A provider whose lookups find nothing: an intermediate attempt says "nothing", a full attempt walks the tree.
        // zh-CN: 查找什么也找不到的提供方: 中间尝试判定 "未找到", 完整尝试遍历整棵树.
        root.lookup = FakeUiNode.Lookup.EMPTY
        assertSame(FastPath.Outcome.Miss, decide(root, selector(IdFilter.equals("ok")), true, FastPath.Attempt.INTERMEDIATE))
        val last = decide(root, selector(IdFilter.equals("ok")), true, FastPath.Attempt.FULL)
        assertTrue((last as FastPath.Outcome.Search).emptyLookup)
        assertEquals(listOf("ok"), DFS.search(root, selector(IdFilter.equals("ok")).snapshot(), 1).map { it.name })
    }

    @Test
    fun singleCandidateThatPasses() {
        val root = tree()
        val lookup = FakeLookup()
        val hit = decide(root, selector(BooleanFilter(BooleanFilter.CLICKABLE), IdFilter.equals("ok")), true, FastPath.Attempt.INTERMEDIATE, lookup)
        assertEquals("ok", (hit as FastPath.Outcome.Hit).node.name)
        assertEquals(2, hit.node.depth())
        assertEquals(emptyList<String>(), lookup.released)
        // Also in a full attempt: a hit needs no correction. zh-CN: 完整尝试也一样: 命中无需校正.
        assertEquals("ok", (decide(root, selector(IdFilter.equals("com.example:id/ok")), true, FastPath.Attempt.FULL) as FastPath.Outcome.Hit).node.name)
        // The candidate is the same node DFS finds first. zh-CN: 候选就是 DFS 首个找到的节点.
        assertSame(hit.node, DFS.search(root, selector(IdFilter.equals("ok")).snapshot(), 1).single())
        // A filter on the depth sees the depth below the root. zh-CN: 深度过滤器看到的是根节点之下的深度.
        val deep = decide(root, selector(IdFilter.equals("ok"), IntFilter(IntFilter.Property.DEPTH, 2)), true, FastPath.Attempt.INTERMEDIATE)
        assertTrue(deep is FastPath.Outcome.Hit)
    }

    @Test
    fun singleCandidateThatDoesNotPass() {
        val lookup = FakeLookup()
        val outcome = decide(tree(), selector(IdFilter.equals("ok"), BooleanFilter(BooleanFilter.CHECKED)), true, FastPath.Attempt.INTERMEDIATE, lookup)
        assertTrue(!(outcome as FastPath.Outcome.Search).emptyLookup)
        assertEquals(listOf("ok"), lookup.released)
    }

    @Test
    fun severalCandidates() {
        val titles = FakeLookup()
        assertTrue(decide(tree(), selector(IdFilter.equals("title")), true, FastPath.Attempt.INTERMEDIATE, titles) is FastPath.Outcome.Search)
        assertEquals(listOf("title1", "title2"), titles.released)
    }

    /** All results (`find()`, `untilFind()`), breadth first and a negative index all take the full search. */
    @Test
    fun notSingle() {
        val lookup = FakeLookup()
        val outcome = decide(tree(), selector(IdFilter.equals("ok")), false, FastPath.Attempt.INTERMEDIATE, lookup)
        assertTrue(!(outcome as FastPath.Outcome.Search).emptyLookup)
        assertEquals(listOf("ok"), lookup.released)
    }

    @Test
    fun unavailableLookup() {
        val root = tree().apply { lookup = FakeUiNode.Lookup.UNAVAILABLE }
        val outcome = decide(root, selector(IdFilter.equals("nothing")), true, FastPath.Attempt.INTERMEDIATE)
        assertTrue(!(outcome as FastPath.Outcome.Search).emptyLookup)
        // With several roots, the candidates of the roots before the failing one are released.
        // zh-CN: 有多个根节点时, 失败根节点之前各根节点的候选被释放.
        val fake = FakeLookup()
        val second = tree().apply { lookup = FakeUiNode.Lookup.UNAVAILABLE }
        val key = FastPath.keyOf(listOf(IdFilter.equals("ok")))!!
        assertTrue(FastPath.decide(listOf(tree(), second), key, selector(IdFilter.equals("ok")).snapshot(), true, FastPath.Attempt.INTERMEDIATE, fake) is FastPath.Outcome.Search)
        assertEquals(listOf("ok"), fake.released)
    }

    @Test
    fun candidateOutsideTheRoot() {
        val other = tree()
        val lookup = object : FastPath.Lookup<FakeUiNode, FakeUiNode> {
            override fun candidates(root: FakeUiNode, key: FastPath.Key) = listOf(other.find("ok"))
            override fun attach(root: FakeUiNode, candidate: FakeUiNode): FakeUiNode? = null
            override fun release(candidate: FakeUiNode) = Unit
            override fun releaseNode(node: FakeUiNode) = Unit
        }
        val key = FastPath.keyOf(listOf(IdFilter.equals("ok")))!!
        assertTrue(FastPath.decide(listOf(tree()), key, selector(IdFilter.equals("ok")).snapshot(), true, FastPath.Attempt.INTERMEDIATE, lookup) is FastPath.Outcome.Search)
    }

    @Test
    fun attemptPlanOfABoundedQuery() {
        var now = 0L
        val query = FastPath.Query(PollPolicy.within(600)) { now }
        // findOne(600): attempts every 50 ms; the last ones are those with no more than 50 ms left.
        // zh-CN: findOne(600): 每 50 ms 一次尝试; 剩余不超过 50 ms 的尝试为最后一次.
        val attempts = (1L..13L).map { round ->
            now = (round - 1) * 50
            query.attempt(round, (600 - now).coerceAtLeast(0))
        }
        // The first attempt walks the tree too, for a node already on the screen. zh-CN: 第一次尝试也遍历整棵树, 以应对已在屏幕上的节点.
        assertEquals(listOf(FastPath.Attempt.FULL) + List(10) { FastPath.Attempt.INTERMEDIATE } + listOf(FastPath.Attempt.FULL, FastPath.Attempt.FULL), attempts)
        assertSame(FastPath.Attempt.SINGLE, FastPath.Query(PollPolicy.ONCE) { 0L }.attempt(1, PollPolicy.UNBOUNDED))
        val counted = FastPath.Query(PollPolicy(times = 3)) { 0L }
        assertEquals(listOf(FastPath.Attempt.FULL, FastPath.Attempt.INTERMEDIATE, FastPath.Attempt.FULL), (1L..3L).map { counted.attempt(it, PollPolicy.UNBOUNDED) })
    }

    @Test
    fun attemptPlanOfAnUnboundedQuery() {
        var now = 1_000_000L
        val query = FastPath.Query(PollPolicy.FOREVER) { now }
        val fulls = ArrayList<Long>()
        for (round in 1L..100L) {
            now = 1_000_000L + (round - 1) * 50
            if (query.attempt(round, PollPolicy.UNBOUNDED) == FastPath.Attempt.FULL) fulls += now - 1_000_000L
        }
        // A full search at least every VERIFY_INTERVAL: an unreliable lookup cannot hide a node for longer.
        // zh-CN: 至少每 VERIFY_INTERVAL 一次完整搜索: 不可靠的查找不能把节点藏得更久.
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L), fulls)
    }

    @Test
    fun ledger() {
        val ledger = FastPath.Ledger()
        assertTrue(!ledger.isUnreliable("com.example", "other"))
        ledger.markUnreliable("com.example", "other")
        assertTrue(ledger.isUnreliable("com.example", "other"))
        assertTrue(!ledger.isUnreliable("com.example", FastPath.KIND_ID))
        assertTrue(!ledger.isUnreliable("com.other", "other"))
        ledger.markUnreliable(null, FastPath.KIND_ID)
        assertTrue(ledger.isUnreliable(null, FastPath.KIND_ID))
        ledger.clear()
        assertTrue(!ledger.isUnreliable("com.example", "other"))
    }

    private class Recording(private val inner: Filter, private val log: MutableList<String>) : Filter {
        override fun filter(node: UiNode<*>) = inner.filter(node).also { log += (node as FakeUiNode).name }
    }

    /** The single candidate is checked against the whole selector, not only the key. */
    @Test
    fun singleCandidateIsFilteredByTheWholeSelector() {
        val log = ArrayList<String>()
        val filter = Recording(selector(IdFilter.equals("ok"), BooleanFilter(BooleanFilter.CLICKABLE)).snapshot(), log)
        val key = FastPath.keyOf(listOf(IdFilter.equals("ok")))!!
        val outcome = FastPath.decide(listOf(tree()), key, filter, true, FastPath.Attempt.INTERMEDIATE, FakeLookup())
        assertTrue(outcome is FastPath.Outcome.Hit)
        assertEquals(listOf("ok"), log)
    }

}
