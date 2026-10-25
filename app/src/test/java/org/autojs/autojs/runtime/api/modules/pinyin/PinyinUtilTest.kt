package org.autojs.autojs.runtime.api.modules.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinUtilTest {

    @Test
    fun compareUsesNestedArrayStringificationAndLocaleOrdering() {
        assertEquals(0, Util.compare(listOf(listOf("a"), listOf("b")), listOf(listOf("a", "b"))))
        assertTrue(Util.compare(listOf(listOf("a")), listOf(listOf("b"))) < 0)
        assertTrue(Util.compare(listOf(listOf("b")), listOf(listOf("a"))) > 0)
    }

    @Test
    fun compactBuildsTheUpstreamCartesianProduct() {
        assertEquals(
            listOf(
                listOf("a", "1"),
                listOf("a", "2"),
                listOf("b", "1"),
                listOf("b", "2"),
            ),
            Util.compact(listOf(listOf("a", "b"), listOf("1", "2"))),
        )
    }

    @Test
    fun compactPreservesExistingBoundHelperEmptyRowSemantics() {
        assertEquals(emptyList<List<String>>(), Util.compact(emptyList()))
        assertEquals(listOf(emptyList<String>()), Util.compact(listOf(emptyList())))
        assertEquals(
            listOf(listOf("x")),
            Util.compact(listOf(emptyList(), listOf("x"))),
        )
        assertEquals(
            listOf(listOf("x")),
            Util.compact(listOf(listOf("x"), emptyList())),
        )
    }
}
