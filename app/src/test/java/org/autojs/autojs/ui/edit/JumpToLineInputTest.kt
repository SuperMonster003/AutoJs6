package org.autojs.autojs.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JumpToLineInputTest {

    @Test
    fun returnsMaximumOnlyWhenNumericInputExceedsIt() {
        assertNull(jumpToLineUpperBoundCorrection("", 120))
        assertNull(jumpToLineUpperBoundCorrection("119", 120))
        assertNull(jumpToLineUpperBoundCorrection("120", 120))
        assertNull(jumpToLineUpperBoundCorrection("00099", 120))
        assertNull(jumpToLineUpperBoundCorrection("12x", 120))
        assertEquals("120", jumpToLineUpperBoundCorrection("121", 120))
        assertEquals("120", jumpToLineUpperBoundCorrection("999999999999999999999999", 120))
        assertEquals("1", jumpToLineUpperBoundCorrection("2", 1))
    }
}
