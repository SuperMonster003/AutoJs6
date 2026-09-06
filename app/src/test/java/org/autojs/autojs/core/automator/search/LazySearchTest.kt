package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LazySearch] (roadmap P5.3, `findIterator()`): the order of [DFS] / [BFS], laziness, what is
 * released when, and the end of the walk.
 * zh-CN: [LazySearch] (Roadmap P5.3, `findIterator()`): 与 [DFS] / [BFS] 一致的顺序, 惰性, 何时释放什么, 以及遍历的结束.
 */
class LazySearchTest {

    private val lifecycle = NodeLifecycle(NodeRecyclePolicy.RECYCLE)

    private val nonEmpty = object : Filter {
        override fun filter(node: UiNode<*>) = node.text().isNotEmpty()
    }

    private fun tree() = FakeUiNode.root("root") {
        child("a") {
            text = "A"
            child("a1")
            child("a2") { text = "A2" }
        }
        child("b") { text = "B" }
        child("c") {
            child("c1") { text = "C1" }
        }
    }

    @Test
    fun producesTheNodesOfDfsInDfsOrder() {
        val expected = DFS.search(tree(), nonEmpty, Int.MAX_VALUE, lifecycle).map { it.name }
        val actual = LazySearch(listOf(tree()), nonEmpty, lifecycle = lifecycle).drain().map { it.name }
        assertEquals(listOf("a", "a2", "b", "c1"), expected)
        assertEquals(expected, actual)
    }

    @Test
    fun breadthFirstFollowsBfs() {
        val expected = BFS.search(tree(), nonEmpty, Int.MAX_VALUE, lifecycle).map { it.name }
        val actual = LazySearch(listOf(tree()), nonEmpty, breadthFirst = true, lifecycle = lifecycle).drain().map { it.name }
        assertEquals(listOf("a", "b", "a2", "c1"), expected)
        assertEquals(expected, actual)
    }

    @Test
    fun theWalkAdvancesOnlyAsFarAsTheConsumerAsks() {
        val root = tree()
        val search = LazySearch(listOf(root), nonEmpty, lifecycle = lifecycle)
        assertEquals(0, search.visited)
        assertEquals("a", search.next().name)
        assertEquals(2, search.visited)
        assertEquals(1, search.produced)
        assertFalse(root.find("a1").isRecycled)
        assertFalse(root.find("c").isRecycled)
        assertEquals("a2", search.next().name)
        assertEquals(4, search.visited)
        assertTrue(root.find("a1").isRecycled)
        assertFalse(root.find("b").isRecycled)
        assertFalse(search.isClosed)
    }

    @Test
    fun closeReleasesWhatIsPendingAndEndsTheWalk() {
        val root = tree()
        val search = LazySearch(listOf(root), nonEmpty, lifecycle = lifecycle)
        search.next()
        search.close()
        assertTrue(search.isClosed)
        assertFalse(search.hasNext())
        assertThrows(NoSuchElementException::class.java) { search.next() }
        assertTrue(root.find("b").isRecycled)
        assertTrue(root.find("c").isRecycled)
        assertTrue(root.find("a1").isRecycled)
        assertFalse(root.find("c1").isRecycled)
        assertFalse(root.find("a").isRecycled)
        assertFalse(root.isRecycled)
    }

    @Test
    fun rejectedNodesAreReleasedAsTheWalkPassesThemAndRootsNever() {
        val root = tree()
        val found = LazySearch(listOf(root), nonEmpty, lifecycle = lifecycle).drain().map { it.name }
        assertEquals(listOf("a", "a2", "b", "c1"), found)
        assertTrue(root.find("a1").isRecycled)
        assertTrue(root.find("c").isRecycled)
        assertFalse(root.isRecycled)
        assertFalse(root.find("a").isRecycled)
        assertFalse(root.find("c1").isRecycled)
    }

    @Test
    fun drainTakesTheRestAndExhaustsTheWalk() {
        val search = LazySearch(listOf(tree()), nonEmpty, lifecycle = lifecycle)
        assertEquals("a", search.next().name)
        assertEquals(listOf("a2", "b", "c1"), search.drain().map { it.name })
        assertTrue(search.isClosed)
        assertEquals(4, search.produced)
        assertEquals(7, search.visited)
        assertEquals(emptyList<FakeUiNode>(), search.drain())
    }

    @Test
    fun severalRootsAreWalkedInOrderAndNoneIsReleased() {
        val first = FakeUiNode.root("first") { text = "F"; child("f1") { text = "F1" } }
        val second = FakeUiNode.root("second") { child("s1") { text = "S1" } }
        val search = LazySearch(listOf(first, second), nonEmpty, lifecycle = lifecycle)
        assertEquals(listOf("first", "f1", "s1"), search.drain().map { it.name })
        assertFalse(first.isRecycled)
        assertFalse(second.isRecycled)
    }

    @Test
    fun noRootsMeansAnEmptyWalk() {
        val search = LazySearch(emptyList<FakeUiNode>(), nonEmpty, lifecycle = lifecycle)
        assertFalse(search.hasNext())
        assertTrue(search.isClosed)
        assertEquals(0, search.visited)
    }

    @Test
    fun aNoOpLifecycleReleasesNothing() {
        val root = tree()
        LazySearch(listOf(root), nonEmpty, lifecycle = NodeLifecycle(NodeRecyclePolicy.NO_OP)).drain()
        assertTrue(root.descendantsAndSelf().none { it.isRecycled })
    }

}
