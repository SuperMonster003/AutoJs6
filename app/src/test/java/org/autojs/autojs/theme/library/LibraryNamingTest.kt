package org.autojs.autojs.theme.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryNamingTest {

    @Test
    fun normalizeTrimsCollapsesAndLimits() {
        assertNull(LibraryNaming.normalize(null))
        assertNull(LibraryNaming.normalize("   \n\t "))
        assertEquals("Warm and cool", LibraryNaming.normalize("  Warm \n and\tcool  "))
        val long = "a".repeat(LibraryNaming.MAX_LENGTH + 10)
        assertEquals(LibraryNaming.MAX_LENGTH, LibraryNaming.normalize(long)!!.length)
        // The cut lands on a space, which is trimmed. zh-CN: 截断处恰好是空格, 会被去除.
        val cutAtSpace = "a".repeat(LibraryNaming.MAX_LENGTH - 1) + " b"
        assertEquals("a".repeat(LibraryNaming.MAX_LENGTH - 1), LibraryNaming.normalize(cutAtSpace))
    }

    @Test
    fun uniqueAppendsTheFirstFreeIndex() {
        assertEquals("Brand", LibraryNaming.unique("Brand", listOf("Other")))
        assertEquals("Brand (2)", LibraryNaming.unique("Brand", listOf("brand")))
        assertEquals("Brand (4)", LibraryNaming.unique("Brand", listOf("Brand", "Brand (2)", "BRAND (3)")))
        assertEquals("Brand (copy) (2)", LibraryNaming.unique("Brand (copy)", listOf("Brand (copy)")))
    }

}
