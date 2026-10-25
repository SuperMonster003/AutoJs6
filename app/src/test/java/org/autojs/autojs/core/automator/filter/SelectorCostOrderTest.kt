package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.search.BFS
import org.autojs.autojs.core.automator.search.DFS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/selector/ROADMAP.md P2.2: a [Selector] evaluates its filters cheapest first (a stable sort by [Filter.cost]),
 * while the list, `toString` and the result of the AND keep the declared order and value; the sorted snapshot follows
 * the list as it changes. Also the cost tiers of the filters the registry builds.
 *
 * zh-CN: P2.2: [Selector] 按开销从低到高求值其过滤器 (按 [Filter.cost] 稳定排序), 而列表, `toString` 与 AND 的结果保持声明顺序与取值; 排序后的快照
 * 随列表变化而更新. 另验证注册表所建过滤器的开销分级.
 */
class SelectorCostOrderTest {

    private class Probe(val label: String, override val cost: Int, private val log: MutableList<String>, private val pass: (UiNode<*>) -> Boolean = { true }) : Filter {
        override fun filter(node: UiNode<*>): Boolean {
            log += label
            return pass(node)
        }

        override fun toString() = label
    }

    private val node = FakeUiNode.root()

    @Test
    fun cheapestFirstStableAndDeclaredText() {
        val log = ArrayList<String>()
        val selector = Selector()
        listOf(
            Probe("script", Filter.COST_SCRIPT, log),
            Probe("regex", Filter.COST_REGEX, log),
            Probe("bounds", Filter.COST_BOUNDS, log),
            Probe("bool1", Filter.COST_PROPERTY, log),
            Probe("string", Filter.COST_STRING, log),
            Probe("unknown", Filter.COST_UNKNOWN, log),
            Probe("actions", Filter.COST_ACTIONS, log),
            Probe("bool2", Filter.COST_PROPERTY, log),
        ).forEach { selector.add(it) }
        assertTrue(selector.filter(node))
        assertEquals(listOf("bool1", "bool2", "string", "bounds", "regex", "script", "actions", "unknown"), log)
        assertEquals("script.regex.bounds.bool1.string.unknown.actions.bool2", selector.toString())
        assertEquals(listOf("script", "regex", "bounds", "bool1", "string", "unknown", "actions", "bool2"), selector.filters.map { it.toString() })
        assertEquals(Filter.COST_UNKNOWN, selector.cost)
    }

    @Test
    fun cheapRejectionSparesTheCostlyFilters() {
        val log = ArrayList<String>()
        val selector = Selector().apply {
            add(Probe("script", Filter.COST_SCRIPT, log))
            add(Probe("regex", Filter.COST_REGEX, log))
            add(Probe("bool", Filter.COST_PROPERTY, log) { false })
        }
        assertFalse(selector.filter(node))
        assertEquals(listOf("bool"), log)
    }

    @Test
    fun snapshotFollowsTheList() {
        val log = ArrayList<String>()
        val selector = Selector().apply { add(Probe("regex", Filter.COST_REGEX, log)) }
        val before = selector.snapshot()
        assertSame(before, selector.snapshot())
        assertEquals("regex", selector.toString())
        selector.add(Probe("bool", Filter.COST_PROPERTY, log) { false })
        assertEquals("regex.bool", selector.toString())
        assertFalse(selector.filter(node))
        assertEquals(listOf("bool"), log)
        // A snapshot taken earlier keeps the filters of its time. zh-CN: 早先取得的快照保留当时的过滤器.
        log.clear()
        assertTrue(before.filter(node))
        assertEquals(listOf("regex"), log)
        selector.filters.removeAt(1)
        log.clear()
        assertTrue(selector.filter(node))
        assertEquals(listOf("regex"), log)
        assertEquals("selector()", Selector().toString())
        assertTrue(Selector().filter(node))
    }

    @Test
    fun nestedSelectorTextIsNotKept() {
        val inner = Selector().apply { add(StringPropertyFilter(StringProperty.TEXT, StringMatcher.equals("a"))) }
        val outer = Selector().apply { add(inner) }
        assertEquals("text(\"a\")", outer.toString())
        inner.add(BooleanFilter(BooleanFilter.CLICKABLE))
        assertEquals("text(\"a\").clickable()", outer.toString())
    }

    /** The result of a search does not depend on the order: the same nodes as the declared order gives. */
    @Test
    fun searchResultsUnchanged() {
        // A search recycles the nodes it rejects, so each search walks a tree of its own.
        // zh-CN: 搜索会回收它否决的节点, 因此每次搜索使用各自的树.
        fun tree() = FakeUiNode.root {
            child("a") {
                text = "Login"
                clickable = true
                bounds(0, 0, 100, 50)
                child("a0", clickable = true) { text = "Log" }
            }
            child("b") {
                text = "Login"
                bounds(0, 60, 100, 110)
                child("b0") { desc = "Login" }
            }
            child("c", clickable = true) { text = "login" }
        }
        val calls = intArrayOf(0)
        val declared = listOf(
            object : Filter {
                override fun filter(node: UiNode<*>) = true.also { calls[0]++ }
                override val cost get() = Filter.COST_SCRIPT
            },
            StringPropertyFilter(StringProperty.TEXT, StringMatcher.matches("^[Ll]og.*")),
            BooleanFilter(BooleanFilter.CLICKABLE),
        )
        val selector = Selector().apply { declared.forEach { add(it) } }
        val inOrder = object : Filter {
            override fun filter(node: UiNode<*>) = declared.all { it.filter(node) }
        }
        val expected = DFS.search(tree(), inOrder).map { it.name }
        calls[0] = 0
        assertEquals(expected, DFS.search(tree(), selector.snapshot()).map { it.name })
        assertEquals(listOf("a", "a0", "c"), expected)
        // The script function only sees the nodes the cheaper filters let through. zh-CN: 脚本函数只看到更廉价的过滤器放行的节点.
        assertEquals(3, calls[0])
        assertEquals(expected, DFS.search(tree(), selector).map { it.name })
        assertEquals(BFS.search(tree(), inOrder).map { it.name }, BFS.search(tree(), selector.snapshot()).map { it.name })
    }

    @Test
    fun costTiers() {
        val text = StringPropertyFilter(StringProperty.TEXT, StringMatcher.equals("a"))
        val prefix = StringPropertyFilter(StringProperty.DESC, StringMatcher.startsWith("a"))
        val contains = StringPropertyFilter(StringProperty.CONTENT, StringMatcher.contains("a"))
        val className = ClassNameFilter.equals("Button")
        val classPrefix = ClassNameFilter.startsWith("android.")
        val regex = StringPropertyFilter(StringProperty.TEXT, StringMatcher.matches("a+"))
        val match = StringPropertyFilter(StringProperty.TEXT, StringMatcher.match("a"))
        val idHex = StringPropertyFilter(StringProperty.ID_HEX, StringMatcher.equals("0x7f"))
        assertEquals(Filter.COST_PROPERTY, BooleanFilter(BooleanFilter.CLICKABLE).cost)
        assertEquals(Filter.COST_PROPERTY, IntFilter(IntFilter.Property.DEPTH, 2).cost)
        assertEquals(Filter.COST_PROPERTY, className.cost)
        assertEquals(Filter.COST_STRING, classPrefix.cost)
        assertEquals(Filter.COST_STRING, text.cost)
        assertEquals(Filter.COST_STRING, prefix.cost)
        assertEquals(Filter.COST_STRING, contains.cost)
        assertEquals(Filter.COST_STRING, IdFilter.equals("login").cost)
        assertEquals(Filter.COST_BOUNDS, BoundsFilter(0.0, 0.0, 1.0, 1.0, BoundsFilter.TYPE_INSIDE).cost)
        assertEquals(Filter.COST_BOUNDS, MetricsFilter(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Min(1.0)).cost)
        assertEquals(Filter.COST_BOUNDS, ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X).cost)
        assertEquals(Filter.COST_BOUNDS, DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE).cost)
        assertEquals(Filter.COST_REGEX, regex.cost)
        assertEquals(Filter.COST_REGEX, match.cost)
        assertEquals(Filter.COST_RESOURCE, idHex.cost)
        assertEquals(Filter.COST_ACTIONS, ActionFilter(arrayOf("ACTION_CLICK")).cost)
        assertEquals(Filter.COST_UNKNOWN, object : Filter {
            override fun filter(node: UiNode<*>) = true
        }.cost)
        val tiers = listOf(Filter.COST_PROPERTY, Filter.COST_STRING, Filter.COST_BOUNDS, Filter.COST_REGEX, Filter.COST_RESOURCE, Filter.COST_SCRIPT, Filter.COST_ACTIONS, Filter.COST_UNKNOWN)
        assertEquals(tiers.sorted(), tiers)
        assertEquals("a", text.matcher.exact)
        assertEquals(null, prefix.matcher.exact)
        assertEquals(null, regex.matcher.exact)
        assertTrue(regex.matcher.isPattern && !text.matcher.isPattern)
    }

}
