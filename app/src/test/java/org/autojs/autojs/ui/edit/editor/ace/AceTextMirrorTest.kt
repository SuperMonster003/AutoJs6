package org.autojs.autojs.ui.edit.editor.ace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceTextMirrorTest {

    @Test
    fun insertSingleLine() {
        val mirror = AceTextMirror("hello\nworld")

        assertTrue(
            mirror.applyDelta(
                AceTextDelta(
                    action = AceTextDelta.Action.INSERT,
                    start = AceTextDelta.Position(0, 5),
                    end = AceTextDelta.Position(0, 9),
                    lines = listOf(" ACE"),
                ),
            ),
        )

        assertEquals("hello ACE\nworld", mirror.text)
    }

    @Test
    fun insertMultipleLines() {
        val mirror = AceTextMirror("a\nb")

        mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.INSERT,
                start = AceTextDelta.Position(0, 1),
                end = AceTextDelta.Position(1, 1),
                lines = listOf("X", "Y"),
            ),
        )

        assertEquals("aX\nY\nb", mirror.text)
    }

    @Test
    fun removeSingleLine() {
        val mirror = AceTextMirror("hello\nworld")

        mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.REMOVE,
                start = AceTextDelta.Position(0, 1),
                end = AceTextDelta.Position(0, 4),
                lines = listOf("ell"),
            ),
        )

        assertEquals("ho\nworld", mirror.text)
    }

    @Test
    fun removeMultipleLines() {
        val mirror = AceTextMirror("abc\ndef\nghi")

        mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.REMOVE,
                start = AceTextDelta.Position(0, 1),
                end = AceTextDelta.Position(2, 2),
                lines = listOf("bc", "def", "gh"),
            ),
        )

        assertEquals("ai", mirror.text)
    }

    @Test
    fun mixedEditsMatchExpectedText() {
        val mirror = AceTextMirror("const a = 1;\nconsole.log(a);\n")

        mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.INSERT,
                start = AceTextDelta.Position(0, 5),
                end = AceTextDelta.Position(0, 10),
                lines = listOf(" value"),
            ),
        )
        mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.REMOVE,
                start = AceTextDelta.Position(1, 8),
                end = AceTextDelta.Position(1, 14),
                lines = listOf("log(a)"),
            ),
        )
        mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.INSERT,
                start = AceTextDelta.Position(1, 8),
                end = AceTextDelta.Position(1, 19),
                lines = listOf("warn(value)"),
            ),
        )

        assertEquals("const value a = 1;\nconsole.warn(value);\n", mirror.text)
    }

    @Test
    fun resetReplacesTextAndAdvancesRevision() {
        val mirror = AceTextMirror("old")
        val revision = mirror.revision

        mirror.reset("new")

        assertEquals("new", mirror.text)
        assertTrue(mirror.revision > revision)
    }

    @Test
    fun invalidRemoveRangeDoesNotChangeText() {
        val mirror = AceTextMirror("abc")

        val applied = mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.REMOVE,
                start = AceTextDelta.Position(0, 2),
                end = AceTextDelta.Position(0, 1),
                lines = listOf("b"),
            ),
        )

        assertFalse(applied)
        assertEquals("abc", mirror.text)
    }

    @Test
    fun crlfInsertPreservesDocumentLineSeparator() {
        val mirror = AceTextMirror("a\r\nb")

        val applied = mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.INSERT,
                start = AceTextDelta.Position(0, 1),
                end = AceTextDelta.Position(1, 1),
                lines = listOf("X", "Y"),
            ),
        )

        assertTrue(applied)
        assertEquals("aX\r\nY\r\nb", mirror.text)
    }

    @Test
    fun mismatchedRemovalTriggersResyncWithoutChangingMirror() {
        val mirror = AceTextMirror("hello\nworld")

        val applied = mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.REMOVE,
                start = AceTextDelta.Position(0, 1),
                end = AceTextDelta.Position(0, 4),
                lines = listOf("wrong"),
            ),
        )

        assertFalse(applied)
        assertEquals("hello\nworld", mirror.text)
    }

    @Test
    fun outOfBoundsPositionDoesNotClampOrChangeMirror() {
        val mirror = AceTextMirror("abc")

        val applied = mirror.applyDelta(
            AceTextDelta(
                action = AceTextDelta.Action.INSERT,
                start = AceTextDelta.Position(3, 99),
                end = AceTextDelta.Position(3, 100),
                lines = listOf("x"),
            ),
        )

        assertFalse(applied)
        assertEquals("abc", mirror.text)
    }
}
