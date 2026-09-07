package org.autojs.autojs.engine

import org.autojs.autojs.engine.NodeBridgeConsoleTail.Entry
import org.autojs.autojs.engine.NodeBridgeConsoleTail.LEVEL_DEBUG
import org.autojs.autojs.engine.NodeBridgeConsoleTail.LEVEL_ERROR
import org.autojs.autojs.engine.NodeBridgeConsoleTail.LEVEL_INFO
import org.autojs.autojs.engine.NodeBridgeConsoleTail.LEVEL_VERBOSE
import org.autojs.autojs.engine.NodeBridgeConsoleTail.LEVEL_WARN
import org.autojs.autojs.engine.NodeBridgeConsoleTail.NO_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeBridgeConsoleTailTest {

    private fun entries(count: Int, firstId: Int = 0, level: Int = LEVEL_INFO, text: (Int) -> String = { "line $it" }): List<Entry> {
        return (0 until count).map { Entry(firstId + it, level, text(firstId + it)) }
    }

    @Test
    fun parseLevelAcceptsNamesInitialsAndPriorities() {
        assertEquals(LEVEL_VERBOSE, NodeBridgeConsoleTail.parseLevel("verbose"))
        assertEquals(LEVEL_DEBUG, NodeBridgeConsoleTail.parseLevel(" D "))
        assertEquals(LEVEL_WARN, NodeBridgeConsoleTail.parseLevel("warning"))
        assertEquals(LEVEL_ERROR, NodeBridgeConsoleTail.parseLevel(6))
        assertEquals(LEVEL_ERROR, NodeBridgeConsoleTail.parseLevel("6"))
        assertEquals(NodeBridgeConsoleTail.LEVEL_ASSERT, NodeBridgeConsoleTail.parseLevel("wtf"))
        assertNull(NodeBridgeConsoleTail.parseLevel(null))
        assertNull(NodeBridgeConsoleTail.parseLevel(1))
        assertNull(NodeBridgeConsoleTail.parseLevel("loud"))
        assertNull(NodeBridgeConsoleTail.parseLevel(""))
        assertEquals("warn", NodeBridgeConsoleTail.levelName(LEVEL_WARN))
        assertEquals("unknown", NodeBridgeConsoleTail.levelName(42))
    }

    @Test
    fun selectKeepsTheNewestLinesInIdOrder() {
        val selection = NodeBridgeConsoleTail.select(entries(10), lines = 3)
        assertEquals(listOf(7, 8, 9), selection.entries.map { it.id })
        assertTrue(selection.truncated)
        assertFalse(selection.evicted)
        assertEquals(7, selection.firstId)
        assertEquals(9, selection.lastId)

        val all = NodeBridgeConsoleTail.select(entries(10), lines = NodeBridgeConsoleTail.MAX_LINES)
        assertEquals(10, all.entries.size)
        assertFalse(all.truncated)

        val empty = NodeBridgeConsoleTail.select(emptyList())
        assertEquals(NO_ID, empty.firstId)
        assertEquals(NO_ID, empty.lastId)
        assertFalse(empty.evicted)
    }

    @Test
    fun sinceUntilAndLevelFilterTheWindow() {
        val mixed = listOf(
            Entry(0, LEVEL_VERBOSE, "v"),
            Entry(1, LEVEL_INFO, "i"),
            Entry(2, LEVEL_WARN, "w"),
            Entry(3, LEVEL_ERROR, "e"),
            Entry(4, LEVEL_DEBUG, "d"),
        )
        assertEquals(listOf(2, 3, 4), NodeBridgeConsoleTail.select(mixed, sinceId = 1).entries.map { it.id })
        assertEquals(listOf(1, 2), NodeBridgeConsoleTail.select(mixed, sinceId = 0, untilId = 2).entries.map { it.id })
        assertEquals(listOf(2, 3), NodeBridgeConsoleTail.select(mixed, minLevel = LEVEL_WARN).entries.map { it.id })
        assertEquals(emptyList<Int>(), NodeBridgeConsoleTail.select(mixed, sinceId = 4).entries.map { it.id })
    }

    @Test
    fun evictedFlagsAGapAfterSinceId() {
        val retained = entries(5, firstId = 100)
        assertTrue(NodeBridgeConsoleTail.select(retained, sinceId = 10).evicted)
        assertFalse(NodeBridgeConsoleTail.select(retained, sinceId = 99).evicted)
        assertFalse(NodeBridgeConsoleTail.select(retained, sinceId = 102).evicted)
        assertFalse(NodeBridgeConsoleTail.select(retained).evicted)
    }

    @Test
    fun byteBudgetDropsOldestEntriesOrCutsALoneEntry() {
        val lines = entries(4, text = { "x".repeat(10) })
        val fit = NodeBridgeConsoleTail.select(lines, maxBytes = 25)
        assertEquals(listOf(2, 3), fit.entries.map { it.id })
        assertTrue(fit.truncated)
        assertEquals(20, fit.bytes)

        val huge = listOf(Entry(0, LEVEL_INFO, "short"), Entry(1, LEVEL_INFO, "你好世界"))
        val cut = NodeBridgeConsoleTail.select(huge, maxBytes = 7)
        assertEquals(1, cut.entries.size)
        assertEquals("你好", cut.entries.single().text)
        assertTrue(cut.truncated)
        assertEquals(6, cut.bytes)
    }

    @Test
    fun utf8HelpersCountAndCutOnCodePointBoundaries() {
        assertEquals(0, NodeBridgeConsoleTail.utf8Length(""))
        assertEquals(5, NodeBridgeConsoleTail.utf8Length("hello"))
        assertEquals(12, NodeBridgeConsoleTail.utf8Length("你好世界"))
        val emoji = String(Character.toChars(0x1F600))
        assertEquals(4, NodeBridgeConsoleTail.utf8Length(emoji))
        assertEquals("a" to true, NodeBridgeConsoleTail.capUtf8("a$emoji", 4))
        assertEquals("a$emoji" to false, NodeBridgeConsoleTail.capUtf8("a$emoji", 5))
        assertEquals("你" to true, NodeBridgeConsoleTail.capUtf8("你好", 5))
    }
}
