package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PythonHostSelectorBoundsTest {

    @Test
    fun platformBoundsPreserveValidAxesAndCollapseInvertedAxes() {
        assertEquals(
            PythonHostSelectorBounds(left = 1, top = 2, right = 101, bottom = 202),
            PythonHostSelectorBounds.fromPlatform(left = 1, top = 2, right = 101, bottom = 202),
        )
        assertEquals(
            PythonHostSelectorBounds(left = 221, top = 2372, right = 612, bottom = 2372),
            PythonHostSelectorBounds.fromPlatform(
                left = 221,
                top = 2372,
                right = 612,
                bottom = 2361,
            ),
        )
        assertEquals(
            PythonHostSelectorBounds(left = 50, top = 60, right = 50, bottom = 60),
            PythonHostSelectorBounds.fromPlatform(left = 50, top = 60, right = 40, bottom = 30),
        )
    }

    @Test
    fun directConstructionRejectsInvertedProtocolBounds() {
        try {
            PythonHostSelectorBounds(left = 10, top = 20, right = 9, bottom = 21)
            fail("Expected inverted selector bounds to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Python selector bounds must not be inverted", expected.message)
        }
    }
}
