package org.autojs.autojs.runtime.api.modules.global

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined

/**
 * Locks the argument handling of the synchronous global `wait`,
 * which must stay unchanged while the asynchronous `waitAsync` is introduced.
 */
class GlobalWaitCompatibilityTest {

    @Test
    fun defaultsAreTenSecondsTimeoutWithTwoHundredMillisInterval() {
        val policy = Global.resolveWaitPolicy(null, null)

        assertEquals(Long.MAX_VALUE, policy.times)
        assertEquals(10000L, policy.timeout)
        assertEquals(200L, policy.interval)
        assertFalse(policy.isNoop)
    }

    @Test
    fun undefinedAndNonNumericLimitsFallBackToDefaults() {
        assertEquals(10000L, Global.resolveWaitPolicy(Undefined.instance, null).timeout)
        assertEquals(10000L, Global.resolveWaitPolicy("abc", null).timeout)
        assertEquals(500L, Global.resolveWaitPolicy("500", null).timeout)
        assertEquals(200L, Global.resolveWaitPolicy(5000, Undefined.instance).interval)
    }

    @Test
    fun limitBelowOneHundredMeansAttemptsOtherwiseTimeout() {
        val attempts = Global.resolveWaitPolicy(3, null)
        assertEquals(3L, attempts.times)
        assertEquals(Long.MAX_VALUE, attempts.timeout)

        // 99.5 rounds to 100 and therefore becomes a timeout; the default 200 ms interval would collapse it to one attempt.
        val boundary = Global.resolveWaitPolicy(99.5, 50)
        assertEquals(Long.MAX_VALUE, boundary.times)
        assertEquals(100L, boundary.timeout)
        assertEquals(1L, Global.resolveWaitPolicy(99.5, null).times)

        val timeout = Global.resolveWaitPolicy(3000, 50)
        assertEquals(Long.MAX_VALUE, timeout.times)
        assertEquals(3000L, timeout.timeout)
        assertEquals(50L, timeout.interval)
    }

    @Test
    fun zeroAttemptsIsNoop() {
        assertTrue(Global.resolveWaitPolicy(0, null).isNoop)
        assertTrue(Global.resolveWaitPolicy(0.4, null).isNoop)
        assertFalse(Global.resolveWaitPolicy(1, null).isNoop)
    }

    @Test
    fun intervalNotShorterThanTimeoutCollapsesToSingleAttempt() {
        val policy = Global.resolveWaitPolicy(1000, 1000)

        assertEquals(1L, policy.times)
        assertEquals(1000L, policy.timeout)
        assertEquals(1L, Global.resolveWaitPolicy(1000, 5000).times)
        assertEquals(1L, Global.resolveWaitPolicy(1, 200).times)
    }

    @Test
    fun negativeOrInfiniteValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Global.resolveWaitPolicy(-1, null) }
        assertThrows(IllegalArgumentException::class.java) { Global.resolveWaitPolicy(1000, -1) }
        assertThrows(IllegalArgumentException::class.java) { Global.resolveWaitPolicy(1000, Double.POSITIVE_INFINITY) }
    }

    @Test
    fun callbackObjectMayReplaceLimitOrInterval() {
        val callback = NativeObject()
        val condition = "target"

        assertArrayEquals(arrayOf(condition, null, null, callback), Global.normalizeWaitArguments(arrayOf(condition, callback)))
        assertArrayEquals(arrayOf(condition, null, null, callback), Global.normalizeWaitArguments(arrayOf(condition, callback, 100, "extra")))
        assertArrayEquals(arrayOf(condition, 3000, null, callback), Global.normalizeWaitArguments(arrayOf(condition, 3000, callback)))
        assertArrayEquals(arrayOf(condition, 3000, 200, callback), Global.normalizeWaitArguments(arrayOf(condition, 3000, 200, callback)))
        assertArrayEquals(arrayOf(condition, null, null, null), Global.normalizeWaitArguments(arrayOf(condition)))
    }

    @Test
    fun onlyNullishFalseAndNaNFailTheCondition() {
        listOf(null, Undefined.instance, false, Double.NaN).forEach {
            assertFalse("$it should fail", Global.isWaitConditionSatisfied(it))
        }
        listOf(0, 0.0, -0.0, "", true, "text", NativeObject(), emptyList<Any>()).forEach {
            assertTrue("$it should pass", Global.isWaitConditionSatisfied(it))
        }
    }

}
