package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared walks and the two subtree fingerprints.
 * zh-CN: 共用的遍历与两种子树指纹.
 */
class UiNodesTest {

    //         root
    //       /      \
    //     a          b (scrollable, 100 x 100)
    //   /   \        |
    //  a0   a1      b0 (scrollable, 10 x 10)
    //        |
    //       a1x (clickable)
    private fun tree() = FakeUiNode.root {
        child("a") {
            child("a0")
            child("a1") {
                child("a1x", clickable = true)
            }
        }
        child("b") {
            scrollable = true
            bounds(0, 0, 100, 100)
            child("b0") {
                scrollable = true
                bounds(0, 0, 10, 10)
            }
        }
    }

    @Test
    fun walkIsPreOrderAndStopsOnFalse() {
        val root = tree()
        val seen = ArrayList<String>()
        assertTrue(UiNodes.walk(root) { seen += it.name; true })
        assertEquals(listOf("root", "a", "a0", "a1", "a1x", "b", "b0"), seen)

        seen.clear()
        assertFalse(UiNodes.walk(root) { seen += it.name; it.name != "a1" })
        assertEquals(listOf("root", "a", "a0", "a1"), seen)

        seen.clear()
        assertTrue(UiNodes.walk(root, includeSelf = false) { seen += it.name; true })
        assertEquals(listOf("a", "a0", "a1", "a1x", "b", "b0"), seen)
    }

    @Test
    fun walkIsBoundedByMaxNodes() {
        val seen = ArrayList<String>()
        assertFalse(UiNodes.walk(tree(), maxNodes = 3) { seen += it.name; true })
        assertEquals(listOf("root", "a", "a0"), seen)
    }

    @Test
    fun descendantsAndFirstDescendant() {
        val root = tree()
        assertEquals(listOf("b", "b0"), UiNodes.descendants(root) { it.scrollable() }.map { it.name })
        assertSame(root.find("b"), UiNodes.firstDescendant(root) { it.scrollable() })
        assertSame(root.find("b0"), UiNodes.firstDescendant(root.find("b"), includeSelf = false) { it.scrollable() })
        assertNull(UiNodes.firstDescendant(root) { it.editable() })
    }

    @Test
    fun largestScrollablePrefersAreaThenOrder() {
        val root = tree()
        assertSame(root.find("b"), UiNodes.largestScrollable(root))
        root.find("b0").bounds(0, 0, 200, 200)
        assertSame(root.find("b0"), UiNodes.largestScrollable(root))
        root.find("b0").bounds(0, 0, 100, 100)
        assertSame(root.find("b"), UiNodes.largestScrollable(root))
        assertNull(UiNodes.largestScrollable(root.find("a")))
    }

    @Test
    fun clickableAncestorOrSelfFollowsTheCompassK() {
        val root = tree()
        assertSame(root.find("a1x"), UiNodes.clickableAncestorOrSelf(root.find("a1x"), 0))
        assertNull(UiNodes.clickableAncestorOrSelf(root.find("a0"), 5))
        root.find("a").clickable = true
        assertNull(UiNodes.clickableAncestorOrSelf(root.find("a1x").parent()!!, 0))
        assertSame(root.find("a"), UiNodes.clickableAncestorOrSelf(root.find("a1"), 1))
    }

    @Test
    fun subtreeFingerprintSeesGeometryContentIgnoresIt() {
        val root = tree()
        val subtree = NodeFingerprints.subtree(root)
        val content = NodeFingerprints.content(root)
        root.find("b0").bounds(5, 5, 15, 15)
        assertNotEquals(subtree, NodeFingerprints.subtree(root))
        assertEquals(content, NodeFingerprints.content(root))
    }

    @Test
    fun bothFingerprintsSeeTextAndState() {
        val root = tree()
        val subtree = NodeFingerprints.subtree(root)
        val content = NodeFingerprints.content(root)
        root.find("a0").text = "hello"
        assertNotEquals(subtree, NodeFingerprints.subtree(root))
        assertNotEquals(content, NodeFingerprints.content(root))

        val again = tree()
        again.find("a1x").checked = true
        assertNotEquals(content, NodeFingerprints.content(again))
    }

    @Test
    fun fingerprintsDependOnStructureAndAreStable() {
        assertEquals(NodeFingerprints.subtree(tree()), NodeFingerprints.subtree(tree()))
        assertEquals(NodeFingerprints.content(tree()), NodeFingerprints.content(tree()))
        val flat = FakeUiNode.root {
            child("a")
            child("b")
        }
        val nested = FakeUiNode.root {
            child("a") {
                child("b")
            }
        }
        assertNotEquals(NodeFingerprints.content(flat), NodeFingerprints.content(nested))
    }

    @Test
    fun fingerprintsAreBounded() {
        val wide = FakeUiNode.root {
            repeat(10) { child("c$it") { text = "$it" } }
        }
        val first3 = NodeFingerprints.content(wide, maxNodes = 4)
        wide.find("c9").text = "changed"
        assertEquals(first3, NodeFingerprints.content(wide, maxNodes = 4))
        wide.find("c0").text = "changed"
        assertNotEquals(first3, NodeFingerprints.content(wide, maxNodes = 4))
    }

}
