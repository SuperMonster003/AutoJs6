package org.autojs.autojs.core.automator

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityNodeIndexTest {

    @Test
    fun `returns sentinel without reading past child count when target disappears`() {
        val children = listOf("remaining-child")

        val index = findIndexInParent(children.size, children::get, "detached-child")

        assertEquals(-1, index)
    }

    @Test
    fun `returns sentinel when child access fails`() {
        val index = findIndexInParent(1, { throw IllegalStateException("stale node") }, "target")

        assertEquals(-1, index)
    }

    @Test
    fun `returns target index when child is present`() {
        val children = listOf("first", "target", "last")

        val index = findIndexInParent(children.size, children::get, "target")

        assertEquals(1, index)
    }
}
