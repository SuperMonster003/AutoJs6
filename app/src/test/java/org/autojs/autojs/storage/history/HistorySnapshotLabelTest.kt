package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistorySnapshotLabelTest {

    @Test
    fun blankBecomesNull() {
        assertNull(HistorySnapshotLabel.normalizeOrNull(null))
        assertNull(HistorySnapshotLabel.normalizeOrNull(""))
        assertNull(HistorySnapshotLabel.normalizeOrNull(" \n\t "))
    }

    @Test
    fun whitespaceIsCollapsed() {
        assertEquals("before refactor", HistorySnapshotLabel.normalizeOrNull("  before \n\n  refactor \t"))
    }

    @Test
    fun lengthIsCapped() {
        val long = "x".repeat(HistorySnapshotLabel.MAX_LENGTH + 20)
        assertEquals(HistorySnapshotLabel.MAX_LENGTH, HistorySnapshotLabel.normalizeOrNull(long)!!.length)

        val trailingSpaceAtCut = "a".repeat(HistorySnapshotLabel.MAX_LENGTH - 1) + " b"
        assertEquals("a".repeat(HistorySnapshotLabel.MAX_LENGTH - 1), HistorySnapshotLabel.normalizeOrNull(trailingSpaceAtCut))
    }
}
