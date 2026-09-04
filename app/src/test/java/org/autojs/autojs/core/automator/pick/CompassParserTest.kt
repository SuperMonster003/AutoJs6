package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.automator.pick.CompassStep.Sibling.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassParserTest {

    @Test
    fun emptyCompassHasNoSteps() {
        assertEquals(emptyList<CompassStep>(), CompassParser.parse(""))
    }

    @Test
    fun parentSegmentsAreSummedAcrossDigitsAndRepeats() {
        assertEquals(listOf(CompassStep.Up(1)), CompassParser.parse("p"))
        assertEquals(listOf(CompassStep.Up(2)), CompassParser.parse("p2"))
        assertEquals(listOf(CompassStep.Up(3)), CompassParser.parse("ppp"))
        assertEquals(listOf(CompassStep.Up(3)), CompassParser.parse("pp2"))
        assertEquals(listOf(CompassStep.Up(19)), CompassParser.parse("p4ppp12p"))
    }

    @Test
    fun childSegmentsAcceptBothSeparators() {
        assertEquals(listOf(CompassStep.Children(listOf(3))), CompassParser.parse("c3"))
        assertEquals(listOf(CompassStep.Children(listOf(-2))), CompassParser.parse("c-2"))
        assertEquals(listOf(CompassStep.Children(listOf(0, 2, 0, 1))), CompassParser.parse("c0c2c0c1"))
        assertEquals(listOf(CompassStep.Children(listOf(0, 2, 0, 1))), CompassParser.parse("c0>2>0>1"))
        assertEquals(listOf(CompassStep.Children(listOf(1, 2, 3, 0, -1, 1))), CompassParser.parse("c1>2>3>0>-1>1"))
        assertEquals(listOf(CompassStep.Children(listOf(-3, 2, -1))), CompassParser.parse("c-3c2c-1"))
    }

    @Test
    fun siblingSegmentsDistinguishAbsoluteAndRelativeForms() {
        assertEquals(listOf(CompassStep.Sibling(Mode.ABSOLUTE, 2)), CompassParser.parse("s2"))
        assertEquals(listOf(CompassStep.Sibling(Mode.ABSOLUTE, -2)), CompassParser.parse("s-2"))
        assertEquals(listOf(CompassStep.Sibling(Mode.FORWARD, 3)), CompassParser.parse("s>3"))
        assertEquals(listOf(CompassStep.Sibling(Mode.BACKWARD, 1)), CompassParser.parse("s<1"))
    }

    @Test
    fun clickableSegmentsDefaultToOneLevel() {
        assertEquals(listOf(CompassStep.Clickable(1)), CompassParser.parse("k"))
        assertEquals(listOf(CompassStep.Clickable(1)), CompassParser.parse("k1"))
        assertEquals(listOf(CompassStep.Clickable(2)), CompassParser.parse("k2"))
        assertEquals(listOf(CompassStep.Clickable(5)), CompassParser.parse("k2k3"))
    }

    @Test
    fun combinedCompassKeepsSegmentOrder() {
        assertEquals(
            listOf(
                CompassStep.Up(4),
                CompassStep.Children(listOf(0, 1, 1, 0)),
                CompassStep.Sibling(Mode.ABSOLUTE, 0),
                CompassStep.Clickable(2),
                CompassStep.Up(1),
            ),
            CompassParser.parse("p4c0>1>1>0s0k2p"),
        )
    }

    @Test
    fun unparsableRemainderIsReported() {
        assertEquals("c", assertThrows(CompassSyntaxException::class.java) { CompassParser.parse("p2c") }.rest)
        assertEquals(">1", assertThrows(CompassSyntaxException::class.java) { CompassParser.parse(">1") }.rest)
        assertEquals("-1", assertThrows(CompassSyntaxException::class.java) { CompassParser.parse("p-1") }.rest)
        assertEquals("x", assertThrows(CompassSyntaxException::class.java) { CompassParser.parse("x") }.rest)
        assertThrows(CompassSyntaxException::class.java) { CompassParser.parse("p99999999999") }
    }

    @Test
    fun lexicalCheckMatchesLegacyRule() {
        listOf("", "p", "p2", "c-1", "c0>1>1>0", "s<1", "s>1", "k2", "p4c0>1>1>0s0", "c", ">1").forEach {
            assertTrue("\"$it\" should be a compass", CompassParser.isCompass(it))
        }
        listOf("x", "p 2", "c1-1", "%", "P2").forEach {
            assertFalse("\"$it\" should not be a compass", CompassParser.isCompass(it))
        }
    }

}
