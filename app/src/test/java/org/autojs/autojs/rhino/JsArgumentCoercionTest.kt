package org.autojs.autojs.rhino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigInteger

class JsArgumentCoercionTest {

    @Test
    fun jsNumbersRoundToIntegralTargetsAndNarrowToFloat() {
        assertEquals(3, JsArgumentCoercion.coerce(Int::class.java, 2.5))
        assertEquals(2, JsArgumentCoercion.coerce(Int::class.javaObjectType, 2.4))
        assertEquals(3L, JsArgumentCoercion.coerce(Long::class.java, 2.5))
        assertEquals(1.5f, JsArgumentCoercion.coerce(Float::class.java, 1.5))
        assertEquals(0.25, JsArgumentCoercion.coerce(Double::class.java, 0.25))
        assertEquals(3.toShort(), JsArgumentCoercion.coerce(Short::class.java, 2.5))
        assertEquals(3.toByte(), JsArgumentCoercion.coerce(Byte::class.java, 2.5))
        assertEquals(2.5, JsArgumentCoercion.coerce(String::class.java, 2.5))
    }

    @Test
    fun nonDoublesPassThroughUntouched() {
        val text = "3"
        assertSame(text, JsArgumentCoercion.coerce(Int::class.java, text))
        assertEquals(null, JsArgumentCoercion.coerce(Int::class.java, null))
        assertEquals(7, JsArgumentCoercion.coerce(Int::class.java, 7))
    }

    @Test
    fun anyNumberBecomesIntOrLongWithRounding() {
        assertEquals(3, JsArgumentCoercion.toInt(2.5))
        assertEquals(2, JsArgumentCoercion.toInt(2.4f))
        assertEquals(9, JsArgumentCoercion.toInt(9))
        assertEquals(12, JsArgumentCoercion.toInt(BigInteger.valueOf(12)))
        assertEquals(3L, JsArgumentCoercion.toLong(2.5))
        assertEquals(12L, JsArgumentCoercion.toLong(BigInteger.valueOf(12)))
    }

}
