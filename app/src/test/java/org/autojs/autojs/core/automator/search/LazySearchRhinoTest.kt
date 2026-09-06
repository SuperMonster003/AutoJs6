package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/**
 * A [LazySearch] seen from JavaScript under a bare Rhino context: `for...of` walks it lazily and
 * `break` leaves the rest untouched; `hasNext()` / `next()` work by hand.
 * zh-CN: 裸 Rhino 上下文下从 JavaScript 看到的 [LazySearch]: `for...of` 惰性遍历, `break` 后其余部分不受影响; `hasNext()` / `next()` 可手动使用.
 */
class LazySearchRhinoTest {

    private lateinit var cx: Context
    private lateinit var scope: VarScope

    private val lifecycle = NodeLifecycle(NodeRecyclePolicy.RECYCLE)

    private val nonEmpty = object : Filter {
        override fun filter(node: UiNode<*>) = node.text().isNotEmpty()
    }

    private val root = FakeUiNode.root("root") {
        child("a") { text = "A" }
        child("b") {
            text = "B"
            child("b1") { text = "B1" }
        }
        child("c") { text = "C" }
    }

    @Before
    fun setUp() {
        cx = Context.enter()
        cx.languageVersion = Context.VERSION_ES6
        scope = cx.initStandardObjects()
    }

    @After
    fun tearDown() = Context.exit()

    private fun js(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    @Test
    fun forOfWalksLazilyAndBreakStopsTheWalk() {
        val search = LazySearch(listOf(root), nonEmpty, lifecycle = lifecycle)
        ScriptableObject.putProperty(scope, "search", Context.javaToJS(search, scope))
        val result = js("var out = []; for (let node of search) { out.push(node.text()); if (out.length == 2) break; } out.join(',')")
        assertEquals("A,B", Context.toString(result))
        assertEquals(2, search.produced)
        assertEquals(3, search.visited)
        assertFalse(search.isClosed)
        assertFalse(root.find("c").isRecycled)
        assertEquals("B1,C", js("out = []; for (let node of search) out.push(node.text()); out.join(',')").let { Context.toString(it) })
        assertTrue(search.isClosed)
    }

    @Test
    fun hasNextAndNextWorkByHand() {
        val search = LazySearch(listOf(root), nonEmpty, lifecycle = lifecycle)
        ScriptableObject.putProperty(scope, "search", Context.javaToJS(search, scope))
        assertEquals("A", Context.toString(js("search.hasNext() ? search.next().text() : 'none'")))
        js("search.close()")
        assertEquals(false, js("search.hasNext()"))
        assertTrue(search.isClosed)
    }

}
