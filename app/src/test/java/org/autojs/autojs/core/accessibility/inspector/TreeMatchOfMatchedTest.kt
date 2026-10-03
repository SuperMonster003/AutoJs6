package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/** Temporary code-generation previews retain exactly the resolved nodes and their in-tree ancestors. */
class TreeMatchOfMatchedTest {

    private val root = FakeUiNode.root {
        child("a") {
            child("a0")
            child("a1")
        }
        child("b") {
            child("b0") { child("b00") }
        }
        child("c")
    }

    private fun node(name: String) = root.find(name)

    @Test
    fun hitsAreDeduplicatedAndOrderedByTheTree() {
        val input = mutableListOf(node("b00"), node("a1"), node("a1"))
        val match = TreeMatch.ofMatched(root, input)
        input.clear()

        assertEquals(listOf(node("a1"), node("b00")), match.matches)
        assertEquals(2, match.count)
        assertTrue(match.isMatched(node("a1")))
        assertTrue(match.isMatched(node("b00")))
        assertFalse(match.isMatched(node("a0")))
        assertFalse(match.isMatched(root))
        assertSame(node("b00"), match.next(node("a1")))
        assertSame(node("a1"), match.next(node("b00")))
        assertEquals(0, match.indexOf(node("a1")))
    }

    @Test
    fun keptNodesAreExactlyTheHitsAndTheirAncestors() {
        val match = TreeMatch.ofMatched(root, listOf(node("a1"), node("b00")))

        for (name in listOf("root", "a", "a1", "b", "b0", "b00")) {
            assertTrue(name, match.isKept(node(name)))
        }
        for (name in listOf("a0", "c")) assertFalse(name, match.isKept(node(name)))
    }

    @Test
    fun emptyInputKeepsNothingAndDoesNotBecomeUnfiltered() {
        val match = TreeMatch.ofMatched(root, emptyList())

        assertTrue(match.isEmpty)
        assertFalse(match.isUnfiltered)
        assertEquals(0, match.count)
        assertFalse(match.isMatched(root))
        assertFalse(match.isKept(root))
        assertNull(match.next(null))
        assertNull(match.previous(node("a")))
    }

    @Test
    fun outsideNodesAreIgnoredAndAncestorsStopAtTheSubtreeRoot() {
        val subtree = node("b")
        val outsider = FakeUiNode.root { child("b00") }
        val match = TreeMatch.ofMatched(subtree, listOf(root, node("a1"), node("b00"), outsider.find("b00")))

        assertEquals(listOf(node("b00")), match.matches)
        assertTrue(match.isKept(subtree))
        assertFalse(match.isKept(root))
        assertFalse(match.isMatched(outsider.find("b00")))
        assertTrue(TreeMatch.ofMatched(subtree, listOf(outsider)).isEmpty)
    }

    @Test
    fun rootCanBeMatchedWithoutKeepingItsChildrenAndMatchesCannotBeMutated() {
        val match = TreeMatch.ofMatched(root, listOf(root))

        assertEquals(listOf(root), match.matches)
        assertTrue(match.isMatched(root))
        assertTrue(match.isKept(root))
        assertFalse(match.isKept(node("a")))
        assertThrows(UnsupportedOperationException::class.java) {
            (match.matches as MutableList<FakeUiNode>).clear()
        }
    }

    @Test
    fun freshWrappersCompareByTheirUnderlyingNodeIdentity() {
        val match = TreeMatch.ofMatched(Rewrapped(root), listOf(Rewrapped(node("a1")), Rewrapped(node("a1"))))

        assertEquals(1, match.count)
        assertEquals(listOf(Rewrapped(node("a1"))), match.matches)
        assertTrue(match.isMatched(Rewrapped(node("a1"))))
        assertTrue(match.isKept(Rewrapped(node("a"))))
        assertTrue(match.isKept(Rewrapped(root)))
        assertFalse(match.isMatched(Rewrapped(node("a0"))))
    }

    @Test
    fun deepTreesDoNotRequireRecursiveTraversalOrAncestorWalks() {
        val deepRoot = FakeUiNode.root {}
        var leaf = deepRoot
        repeat(4999) { leaf = leaf.child("level$it") }

        val match = TreeMatch.ofMatched(deepRoot, listOf(leaf))

        assertEquals(listOf(leaf), match.matches)
        assertTrue(match.isKept(deepRoot))
        assertFalse(match.isMatched(deepRoot))
    }

    /** Only parent/child have the self type; all scalar properties are delegated unchanged. */
    @Suppress("UNCHECKED_CAST")
    private class Rewrapped(private val delegate: FakeUiNode) : UiNode<Rewrapped> by (delegate as UiNode<Rewrapped>) {
        override fun parent(): Rewrapped? = delegate.parent()?.let(::Rewrapped)
        override fun child(i: Int): Rewrapped? = delegate.child(i)?.let(::Rewrapped)
        override fun equals(other: Any?): Boolean = other is Rewrapped && other.delegate === delegate
        override fun hashCode(): Int = System.identityHashCode(delegate)
    }
}
