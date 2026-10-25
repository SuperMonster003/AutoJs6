package org.autojs.autojs.runtime.api.modules.jsox

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.javascript.NativeArray

class ArrayxCompatibilityTest {

    @Test
    fun unionAcceptsScalarOperandsAndUsesSameValueZero() {
        val first = NativeArray(arrayOf<Any?>(1.0, Double.NaN))
        val second = NativeArray(arrayOf<Any?>(2.0, Double.NaN))

        val result = Arrayx.unionValues(arrayOf(first, 3.0, second))

        assertEquals(4, result.size)
        assertEquals(1.0, result[0])
        assertEquals(true, (result[1] as Double).isNaN())
        assertEquals(3.0, result[2])
        assertEquals(2.0, result[3])
    }

    @Test
    fun intersectAcceptsScalarOperandsAndKeepsSourceOrder() {
        val first = NativeArray(arrayOf<Any?>(2.0, 1.0, 2.0, Double.NaN))
        val second = NativeArray(arrayOf<Any?>(Double.NaN, 2.0))

        val arrayResult = Arrayx.intersectValues(arrayOf(first, second))
        val scalarResult = Arrayx.intersectValues(arrayOf(first, 2.0))

        assertEquals(2, arrayResult.size)
        assertEquals(2.0, arrayResult[0])
        assertEquals(true, (arrayResult[1] as Double).isNaN())
        assertEquals(listOf(2.0), scalarResult)
    }

    @Test
    fun intersectionWithoutOtherOperandsIsEmpty() {
        val first = NativeArray(arrayOf<Any?>(1.0, 2.0))

        assertEquals(emptyList<Any?>(), Arrayx.intersectValues(arrayOf(first)))
    }
}
