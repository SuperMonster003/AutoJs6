package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.FakeUiObject
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAlgorithmTest {

    //        root
    //       /    \
    //     a        b
    //   /  \      /  \
    //  a0  a1   b0   b1
    //             \
    //             b1x
    private fun tree() = FakeUiObject.root {
        child("a") {
            child("a0")
            child("a1")
        }
        child("b") {
            child("b0")
            child("b1") {
                child("b1x")
            }
        }
    }

    private fun named(vararg names: String) = object : Filter {
        override fun filter(node: UiObject) = (node as FakeUiObject).name() in names
    }

    private val all = object : Filter {
        override fun filter(node: UiObject) = true
    }

    private fun names(nodes: List<UiObject>) = nodes.map { (it as FakeUiObject).name() }

    // The recycling assertions below describe platforms that recycle nodes (API < 33); the search
    // is handed that policy explicitly rather than relying on the JVM's Build.VERSION.SDK_INT.
    // zh-CN: 下方的回收断言描述的是需要回收节点的平台 (API < 33); 显式把该策略交给搜索, 而不依赖 JVM 上的 Build.VERSION.SDK_INT.
    private val recycling = NodeLifecycle(NodeRecyclePolicy.RECYCLE)

    private val noRecycling = NodeLifecycle(NodeRecyclePolicy.NO_OP)

    @Test
    fun dfsVisitsInPreOrder() {
        assertEquals(listOf("root", "a", "a0", "a1", "b", "b0", "b1", "b1x"), names(DFS.search(tree(), all)))
    }

    @Test
    fun bfsVisitsLevelByLevel() {
        assertEquals(listOf("root", "a", "b", "a0", "a1", "b0", "b1", "b1x"), names(BFS.search(tree(), all)))
    }

    @Test
    fun limitIsRespectedExactly() {
        listOf(DFS, BFS).forEach { algorithm ->
            assertEquals(algorithm.toString(), 1, algorithm.search(tree(), all, 1).size)
            assertEquals(algorithm.toString(), 3, algorithm.search(tree(), all, 3).size)
            assertEquals(algorithm.toString(), 8, algorithm.search(tree(), all, 8).size)
            assertEquals(algorithm.toString(), 8, algorithm.search(tree(), all, 100).size)
        }
    }

    @Test
    fun zeroLimitFindsNothing() {
        listOf(DFS, BFS).forEach { algorithm ->
            assertTrue(algorithm.toString(), algorithm.search(tree(), all, 0).isEmpty())
        }
    }

    @Test
    fun firstMatchIsTheOneReturnedForLimitOne() {
        assertEquals(listOf("a1"), names(DFS.search(tree(), named("a1", "b1"), 1)))
        assertEquals(listOf("b1"), names(BFS.search(tree(), named("b1x", "b1"), 1)))
    }

    @Test
    fun nonMatchingNodesAreRecycledButRootAndMatchesAreNot() {
        listOf(DFS, BFS).forEach { algorithm ->
            val root = tree()
            val found = algorithm.search(root, named("a1", "b1"), lifecycle = recycling)

            assertEquals(algorithm.toString(), listOf("a1", "b1"), names(found))
            assertFalse(algorithm.toString(), root.isRecycled)
            root.descendantsAndSelf().filter { it !== root }.forEach { node ->
                assertEquals("$algorithm: ${node.name()}", node !in found, node.isRecycled)
            }
        }
    }

    @Test
    fun nodesAlreadyFetchedWhenLimitIsReachedAreRecycled() {
        // DFS has pushed a0, a1 (children of the match) and b (sibling) when `a` completes the search.
        val dfsRoot = tree()
        assertEquals(listOf("a"), names(DFS.search(dfsRoot, named("a", "b"), 1, recycling)))
        assertEquals(setOf("a0", "a1", "b"), recycledNames(dfsRoot))

        // BFS has only queued `b` when `a` completes the search.
        val bfsRoot = tree()
        assertEquals(listOf("a"), names(BFS.search(bfsRoot, named("a", "b"), 1, recycling)))
        assertEquals(setOf("b"), recycledNames(bfsRoot))
    }

    @Test
    fun nothingIsRecycledOnPlatformsWithoutNodeRecycling() {
        listOf(DFS, BFS).forEach { algorithm ->
            val root = tree()
            assertEquals(algorithm.toString(), listOf("a"), names(algorithm.search(root, named("a", "b"), 1, noRecycling)))
            assertEquals(algorithm.toString(), emptySet<String>(), recycledNames(root))
            val unlimited = tree()
            algorithm.search(unlimited, named("a1", "b1"), lifecycle = noRecycling)
            assertEquals(algorithm.toString(), emptySet<String>(), recycledNames(unlimited))
        }
    }

    @Test
    fun aFailingRecycleDoesNotAbortTheSearch() {
        // `a` throws when recycled (as an already recycled framework node would); the search still completes.
        val root = tree()
        (root.child(0) as FakeUiObject).recycle()
        val logs = mutableListOf<String>()
        val found = DFS.search(root, named("b1x"), lifecycle = NodeLifecycle(NodeRecyclePolicy.RECYCLE) { message, _ -> logs += message })
        assertEquals(listOf("b1x"), names(found))
        assertEquals(listOf("Unable to recycle accessibility node"), logs)
    }

    private fun recycledNames(root: FakeUiObject) = root.descendantsAndSelf().filter { it.isRecycled }.map { it.name() }.toSet()

}
