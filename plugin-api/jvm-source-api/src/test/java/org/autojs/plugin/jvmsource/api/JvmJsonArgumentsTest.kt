package org.autojs.plugin.jvmsource.api

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmJsonArgumentsTest {

    @Test
    fun canonicalizesSupportedHostValuesAndDecodesDeeplyImmutableJavaShapes() {
        val encoded = JvmJsonArguments.encode(
            linkedMapOf(
                "z" to "你好",
                "a" to listOf(true, null, 7, 1.25, intArrayOf(2, 3)),
                "big" to BigInteger("123456789012345678901234567890"),
            ),
        )

        assertEquals(
            "{\"a\":[true,null,7,1.25,[2,3]],\"big\":123456789012345678901234567890,\"z\":\"你好\"}",
            encoded,
        )
        val decoded = JvmJsonArguments.decode(encoded)
        assertEquals("你好", decoded["z"])
        assertEquals(BigInteger("123456789012345678901234567890"), decoded["big"])
        val values = decoded["a"] as List<*>
        assertEquals(7L, values[2])
        assertEquals(BigDecimal("1.25"), values[3])
        assertEquals(listOf(2L, 3L), values[4])

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (decoded as MutableMap<String, Any?>)["new"] = true
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (values as MutableList<Any?>).add(false)
        }
    }

    @Test
    fun enforcesTheSharedByteAndDepthLimitsBeforeCreatingARequest() {
        val exact = JvmJsonArguments.encode(
            mapOf("value" to "x".repeat(JvmSourceContract.MAX_ARGS_JSON_BYTES - 12)),
        )
        assertEquals(JvmSourceContract.MAX_ARGS_JSON_BYTES, exact.toByteArray(Charsets.UTF_8).size)

        assertInvalid {
            JvmJsonArguments.encode(
                mapOf("value" to "x".repeat(JvmSourceContract.MAX_ARGS_JSON_BYTES - 11)),
            )
        }
        var nested: Any? = null
        repeat(JvmSourceContract.MAX_JSON_DEPTH + 1) { nested = listOf(nested) }
        assertInvalid { JvmJsonArguments.encode(mapOf("value" to nested)) }
    }

    @Test
    fun rejectsAmbiguousOrNonJsonHostValuesAndMalformedWireObjects() {
        val cycle = arrayListOf<Any?>()
        cycle.add(cycle)
        val unpairedSurrogate = String(charArrayOf('\uD800'))
        listOf<Map<String, *>>(
            mapOf("value" to cycle),
            mapOf("value" to Double.NaN),
            mapOf("value" to Any()),
            mapOf("value" to unpairedSurrogate),
            @Suppress("UNCHECKED_CAST")
            (mapOf(1 to "not-a-string-key") as Map<String, *>),
        ).forEach { arguments -> assertInvalid { JvmJsonArguments.encode(arguments) } }

        listOf(
            "[]",
            "{\"a\":1,\"a\":2}",
            "{\"value\":NaN}",
            "{\"value\":\"\\uD800\"}",
        ).forEach { encoded -> assertInvalid { JvmJsonArguments.decode(encoded) } }
    }

    private fun assertInvalid(block: () -> Unit) {
        val failure = assertThrows(JvmSourceContractException::class.java) { block() }
        assertEquals(JvmSourceContractViolation.INVALID_VALUE, failure.violation)
        assertTrue(failure.message.orEmpty().isNotBlank())
    }
}
