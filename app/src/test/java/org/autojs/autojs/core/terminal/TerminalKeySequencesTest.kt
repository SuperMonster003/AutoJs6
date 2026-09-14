package org.autojs.autojs.core.terminal

import org.autojs.autojs.core.terminal.TerminalKeySequences.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalKeySequencesTest {

    private val esc = Char(27).toString()

    @Test
    fun cursorKeysFollowNormalAndApplicationModes() {
        assertEquals("$esc[A", TerminalKeySequences.sequence(Key.UP))
        assertEquals("$esc[B", TerminalKeySequences.sequence(Key.DOWN))
        assertEquals("$esc[C", TerminalKeySequences.sequence(Key.RIGHT))
        assertEquals("$esc[D", TerminalKeySequences.sequence(Key.LEFT))
        assertEquals("${esc}OA", TerminalKeySequences.sequence(Key.UP, applicationCursorKeys = true))
        assertEquals("${esc}OD", TerminalKeySequences.sequence(Key.LEFT, applicationCursorKeys = true))
    }

    @Test
    fun editingKeysUseXtermSequences() {
        assertEquals(esc, TerminalKeySequences.sequence(Key.ESC))
        assertEquals("\t", TerminalKeySequences.sequence(Key.TAB))
        assertEquals("$esc[H", TerminalKeySequences.sequence(Key.HOME))
        assertEquals("$esc[F", TerminalKeySequences.sequence(Key.END))
        assertEquals("$esc[5~", TerminalKeySequences.sequence(Key.PAGE_UP))
        assertEquals("$esc[6~", TerminalKeySequences.sequence(Key.PAGE_DOWN))
        assertEquals("$esc[3~", TerminalKeySequences.sequence(Key.DELETE))
    }

    @Test
    fun controlFoldsLettersAndPunctuationIntoC0Range() {
        assertEquals(Char(1), TerminalKeySequences.control('a'))
        assertEquals(Char(1), TerminalKeySequences.control('A'))
        assertEquals(Char(3), TerminalKeySequences.control('c'))
        assertEquals(Char(26), TerminalKeySequences.control('z'))
        assertEquals(Char(0), TerminalKeySequences.control('@'))
        assertEquals(Char(0), TerminalKeySequences.control(' '))
        assertEquals(Char(27), TerminalKeySequences.control('['))
        assertEquals(Char(28), TerminalKeySequences.control('\\'))
        assertEquals(Char(29), TerminalKeySequences.control(']'))
        assertEquals(Char(30), TerminalKeySequences.control('^'))
        assertEquals(Char(31), TerminalKeySequences.control('_'))
        assertEquals(Char(127), TerminalKeySequences.control('?'))
        assertNull(TerminalKeySequences.control('1'))
        assertNull(TerminalKeySequences.control('.'))
    }

    @Test
    fun modifiersApplyOnlyWhereTheyMakeSense() {
        assertEquals(Char(3).toString(), TerminalKeySequences.applyModifiers("c", ctrl = true, alt = false))
        assertEquals("cd", TerminalKeySequences.applyModifiers("cd", ctrl = true, alt = false))
        assertEquals("1", TerminalKeySequences.applyModifiers("1", ctrl = true, alt = false))
        assertEquals("${esc}b", TerminalKeySequences.applyModifiers("b", ctrl = false, alt = true))
        assertEquals("${esc}ls", TerminalKeySequences.applyModifiers("ls", ctrl = false, alt = true))
        assertEquals(esc + Char(4), TerminalKeySequences.applyModifiers("d", ctrl = true, alt = true))
        assertEquals("", TerminalKeySequences.applyModifiers("", ctrl = true, alt = true))
        assertEquals("x", TerminalKeySequences.applyModifiers("x", ctrl = false, alt = false))
    }

}
