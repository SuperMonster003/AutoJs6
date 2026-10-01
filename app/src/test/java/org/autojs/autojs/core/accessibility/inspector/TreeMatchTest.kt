package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TreeMatch] over a [FakeUiNode] tree: pre-order of the matches, the kept set of matches plus
 * ancestors, cyclic navigation, the zero-match and unfiltered cases, and [InspectorNodes.preOrder].
 * zh-CN: [TreeMatch] 在 [FakeUiNode] 树上的行为: 匹配项的先序, 匹配项加祖先的保留集合, 循环导航, 零匹配与无筛选两种情形,
 * 以及 [InspectorNodes.preOrder].
 */
class TreeMatchTest {

    /*
     * root
     *  +- a (clickable)
     *  |   +- a0
     *  |   +- a1 (clickable)
     *  +- b
     *  |   +- b0
     *  |       +- b00 (clickable)
     *  +- c
     */
    private val root = FakeUiNode.root {
        child("a", clickable = true) {
            child("a0")
            child("a1", clickable = true)
        }
        child("b") {
            child("b0") {
                child("b00", clickable = true)
            }
        }
        child("c")
    }

    private fun node(name: String) = root.find(name)

    private val clickable = listOf(BooleanFilter(BooleanFilter.CLICKABLE))

    @Test
    fun preOrderListsParentsBeforeChildrenAndSiblingsInIndexOrder() {
        assertEquals(listOf("root", "a", "a0", "a1", "b", "b0", "b00", "c"), InspectorNodes.preOrder(root).map { it.name })
    }

    @Test
    fun matchesArePreOrderedAndKeptSetAddsAncestors() {
        val match = TreeMatch.evaluate(root, clickable)
        assertEquals(listOf("a", "a1", "b00"), match.matches.map { it.name })
        assertEquals(3, match.count)
        assertFalse(match.isUnfiltered)
        assertFalse(match.isEmpty)

        assertTrue(match.isMatched(node("a")))
        assertFalse(match.isMatched(node("a0")))
        assertFalse(match.isMatched(root))

        for (kept in listOf("root", "a", "a1", "b", "b0", "b00")) assertTrue(kept, match.isKept(node(kept)))
        for (dropped in listOf("a0", "c")) assertFalse(dropped, match.isKept(node(dropped)))
    }

    @Test
    fun navigationWrapsAroundAndStartsFromEitherEnd() {
        val match = TreeMatch.evaluate(root, clickable)
        assertSame(node("a"), match.next(null))
        assertSame(node("b00"), match.previous(null))
        assertSame(node("a1"), match.next(node("a")))
        assertSame(node("a"), match.next(node("b00")))
        assertSame(node("b00"), match.previous(node("a")))
        // A current node that is not a match restarts from the first / last match.
        assertSame(node("a"), match.next(node("c")))
        assertSame(node("b00"), match.previous(node("c")))
        assertEquals(1, match.indexOf(node("a1")))
        assertEquals(-1, match.indexOf(node("c")))
        assertEquals(-1, match.indexOf(null))
    }

    @Test
    fun rootItselfCanMatch() {
        val clickableRoot = FakeUiNode.root(clickable = true) { child("x") }
        val match = TreeMatch.evaluate(clickableRoot, clickable)
        assertEquals(listOf(clickableRoot), match.matches)
        assertTrue(match.isKept(clickableRoot))
        assertFalse(match.isKept(clickableRoot.find("x")))
    }

    @Test
    fun zeroMatchesKeepNothing() {
        val match = TreeMatch.evaluate(root, listOf(BooleanFilter(BooleanFilter.SCROLLABLE)))
        assertTrue(match.isEmpty)
        assertEquals(0, match.count)
        assertFalse(match.isKept(root))
        assertNull(match.next(null))
        assertNull(match.previous(node("a")))
    }

    @Test
    fun emptyFilterListIsUnfiltered() {
        val match = TreeMatch.evaluate(root, emptyList())
        assertTrue(match.isUnfiltered)
        assertFalse(match.isEmpty)
        assertEquals(0, match.count)
        assertTrue(match.isMatched(node("c")))
        assertTrue(match.isKept(node("a0")))
        assertNull(match.next(null))
    }

}
