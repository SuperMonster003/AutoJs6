package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SelectorExplainer] over an in-memory tree: the per-filter tallies alone and cumulatively, the
 * failing step with its near misses, the capped matches, the empty selector, the node budget and
 * the text report.
 * zh-CN: [SelectorExplainer] 在内存节点树上: 逐过滤器的单独与叠加计数, 失败步骤及其 "差一点" 的节点, 有上限的匹配列表, 空选择器,
 * 节点预算与文本报告.
 */
class SelectorExplainerTest {

    private val root = FakeUiNode.root("root") {
        className = "android.widget.FrameLayout"
        child("a") {
            className = "android.widget.TextView"
            text = "Wi-Fi"
        }
        child("b", clickable = true) {
            className = "android.widget.TextView"
            text = "Wi-Fi"
        }
        child("c", clickable = true) {
            className = "android.widget.Button"
            text = "OK"
        }
    }

    private fun filter(name: String, predicate: (UiNode<*>) -> Boolean): Filter = object : Filter {
        override fun filter(node: UiNode<*>): Boolean = predicate(node)
        override fun toString(): String = name
    }

    private val wifi = filter("text(\"Wi-Fi\")") { it.text() == "Wi-Fi" }
    private val clickable = filter("clickable()") { it.clickable() }
    private val checkable = filter("checkable()") { it.checkable() }

    @Test
    fun stepsCountAloneAndCumulatively() {
        val explanation = SelectorExplainer.explain(listOf(root), listOf(wifi, clickable))
        assertEquals("text(\"Wi-Fi\").clickable()", explanation.selector)
        assertEquals(1, explanation.roots)
        assertEquals(4, explanation.nodes)
        assertEquals(listOf(2, 2), explanation.steps.map { it.matched })
        assertEquals(listOf(2, 1), explanation.steps.map { it.cumulative })
        assertTrue(explanation.found)
        assertEquals(1, explanation.count)
        assertEquals(-1, explanation.failingIndex)
        assertEquals(listOf("b"), explanation.matches.map { it.name })
        assertTrue(explanation.nearMisses.isEmpty())
        val text = explanation.render { it.name }
        assertEquals(
            listOf(
                "selector text(\"Wi-Fi\").clickable(): 1 match(es) (4 node(s) in 1 root(s))",
                "  1. text(\"Wi-Fi\")  matched 2, cumulative 2",
                "  2. clickable()    matched 2, cumulative 1",
                "matches:",
                "  - b",
            ).joinToString("\n"),
            text,
        )
    }

    @Test
    fun theFailingStepIsNamedWithItsNearMisses() {
        val explanation = SelectorExplainer.explain(listOf(root), listOf(wifi, checkable))
        assertFalse(explanation.found)
        assertEquals(0, explanation.count)
        assertEquals(1, explanation.failingIndex)
        assertEquals("checkable()", explanation.failingFilter)
        assertEquals(listOf("a", "b"), explanation.nearMisses.map { it.name })
        assertTrue(explanation.matches.isEmpty())
        val text = explanation.render { it.name }
        assertEquals(
            listOf(
                "selector text(\"Wi-Fi\").checkable(): no match (4 node(s) in 1 root(s))",
                "  1. text(\"Wi-Fi\")  matched 2, cumulative 2",
                "  2. checkable()    matched 0, cumulative 0  <- nothing left",
                "near misses (pass step 1, fail step 2):",
                "  - a",
                "  - b",
            ).joinToString("\n"),
            text,
        )
        val three = SelectorExplainer.explain(listOf(root), listOf(wifi, clickable, checkable)).render { it.name }
        assertTrue(three, three.contains("near misses (pass steps 1..2, fail step 3):\n  - b"))
    }

    @Test
    fun aFailingFirstStepHasNoNearMisses() {
        val explanation = SelectorExplainer.explain(listOf(root), listOf(filter("text(\"nope\")") { it.text() == "nope" }, clickable))
        assertEquals(0, explanation.failingIndex)
        assertTrue(explanation.nearMisses.isEmpty())
        assertEquals(listOf(0, 0), explanation.steps.map { it.cumulative })
        assertEquals(2, explanation.steps[1].matched)
        val text = explanation.render()
        assertTrue(text, text.startsWith("selector text(\"nope\").clickable(): no match"))
        assertFalse(text, text.contains("near misses"))
    }

    @Test
    fun noFilterCountsEveryNode() {
        val explanation = SelectorExplainer.explain(listOf(root), emptyList())
        assertEquals("selector()", explanation.selector)
        assertEquals(4, explanation.count)
        assertTrue(explanation.found)
        assertEquals(listOf("root", "a", "b", "c"), explanation.matches.map { it.name })
        assertTrue(explanation.render(), explanation.render().startsWith("selector selector(): 4 node(s), no filter (4 node(s) in 1 root(s))"))
    }

    @Test
    fun keepCapsTheListedNodes() {
        val explanation = SelectorExplainer.explain(listOf(root), listOf(wifi), keep = 1)
        assertEquals(2, explanation.count)
        assertEquals(listOf("a"), explanation.matches.map { it.name })
        assertTrue(explanation.render { it.name }.contains("matches (first 1 of 2):\n  - a"))
        val misses = SelectorExplainer.explain(listOf(root), listOf(wifi, checkable), keep = 1)
        assertEquals(listOf("a"), misses.nearMisses.map { it.name })
    }

    @Test
    fun theBudgetTruncatesTheWalk() {
        val explanation = SelectorExplainer.explain(listOf(root, root), listOf(wifi), maxNodes = 5)
        assertTrue(explanation.truncated)
        assertEquals(5, explanation.nodes)
        assertEquals(2, explanation.roots)
        assertTrue(explanation.render().contains("(5 node(s) in 2 root(s), walk truncated)"))
        val whole = SelectorExplainer.explain(listOf(root, root), listOf(wifi))
        assertFalse(whole.truncated)
        assertEquals(8, whole.nodes)
        assertEquals(4, whole.count)
    }

    @Test
    fun theDefaultDescriptionIsTheNodeLine() {
        val explanation = SelectorExplainer.explain(listOf(root), listOf(clickable))
        assertTrue(explanation.render().contains("  - TextView [0,0][0,0] text=\"Wi-Fi\" clickable"))
        assertEquals(explanation.render(), explanation.toString())
    }

}
