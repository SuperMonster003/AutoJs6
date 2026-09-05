package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassTest {

    //             root
    //          /        \
    //      a (clickable)   b
    //     /   |   \        |
    //   a0   a1   a2      b0
    //         |
    //        a1x
    private val root = FakeUiNode.root {
        child("a", clickable = true) {
            child("a0")
            child("a1") {
                child("a1x")
            }
            child("a2")
        }
        child("b") {
            child("b0")
        }
    }

    private fun node(name: String) = root.find(name)

    private fun FakeUiNode.compass(compass: CharSequence?) = Compass.navigate(this, compass)

    @Test
    fun passOnAndEmptyCompassReturnTheNodeItself() {
        assertSame(node("a1"), node("a1").compass(null))
        assertSame(node("a1"), node("a1").compass(""))
        assertSame(node("a1"), node("a1").compass(Compass.PASS_ON))
    }

    @Test
    fun parentSegments() {
        assertSame(node("a"), node("a1").compass("p"))
        assertSame(root, node("a1x").compass("p3"))
        assertSame(root, node("a1x").compass("ppp"))
        assertSame(root, node("a1x").compass("pp2"))
        assertSame(node("a"), node("a1x").compass("pp1"))
        assertNull(node("a1x").compass("p4"))
        assertSame(node("a1x"), node("a1x").compass("p0"))
    }

    @Test
    fun childSegments() {
        assertSame(node("a"), root.compass("c0"))
        assertSame(node("b"), root.compass("c-1"))
        assertSame(node("a1x"), root.compass("c0>1>0"))
        assertSame(node("a1x"), root.compass("c0c1c0"))
        assertSame(node("a2"), root.compass("c0>-1"))
        assertNull(root.compass("c2"))
        assertNull(root.compass("c-3"))
        assertNull(node("a0").compass("c0"))
    }

    @Test
    fun siblingSegments() {
        assertSame(node("a2"), node("a0").compass("s2"))
        assertSame(node("a2"), node("a0").compass("s-1"))
        assertSame(node("a1"), node("a0").compass("s>1"))
        assertSame(node("a0"), node("a2").compass("s<2"))
        assertSame(node("a0"), node("a0").compass("s0"))
        assertNull(node("a2").compass("s>1"))
        assertNull(node("a0").compass("s<1"))
        assertNull(node("a0").compass("s3"))
        assertNull(root.compass("s0"))
    }

    @Test
    fun clickableSegmentsClimbToTheNearestClickableAncestor() {
        assertSame(node("a"), node("a").compass("k"))
        assertSame(node("a"), node("a1").compass("k"))
        assertSame(node("a"), node("a1x").compass("k2"))
        // Nothing clickable within range yields null, whether or not the walk reaches the root.
        assertNull(node("a1x").compass("k1"))
        assertNull(node("b0").compass("k2"))
        assertNull(node("b0").compass("k0"))
        assertNull(root.compass("k"))
    }

    @Test
    fun combinedSegments() {
        assertSame(node("b0"), node("a1x").compass("p2s>1c0"))
        assertSame(node("a"), node("a1x").compass("pk"))
        assertSame(node("a2"), node("a1x").compass("ps-1"))
    }

    @Test
    fun parsedStepsCanBeWalkedDirectly() {
        val steps = CompassParser.parse("p2s>1c0")
        assertSame(node("b0"), Compass.navigate(node("a1x"), steps))
        assertSame(node("a1x"), Compass.navigate(node("a1x"), emptyList()))
    }

    @Test
    fun invalidCompassIsReportedWithItsUnparsableRest() {
        val e = assertThrows(CompassSyntaxException::class.java) { root.compass("c0x1") }
        assertEquals("x1", e.rest)
    }

    @Test
    fun isCompassAcceptsPassOnEmptyAndGrammarStringsOnly() {
        assertTrue(Compass.isCompass(Compass.PASS_ON))
        assertTrue(Compass.isCompass(""))
        assertTrue(Compass.isCompass("p2s>1c0k"))
        assertEquals(false, Compass.isCompass("x"))
        assertEquals(false, Compass.isCompass(null))
        assertEquals(false, Compass.isCompass(1))
    }

}
