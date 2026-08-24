package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonExecutionTimeoutPolicyTest {

    @Test
    fun defaultsToFiveMinutesAndNeverExceedsTheProviderLimit() {
        assertEquals(
            PythonExecutionTimeoutPolicy.DEFAULT_TIMEOUT_MILLIS,
            PythonExecutionTimeoutPolicy.resolve(null, 30L * 60L * 1_000L),
        )
        assertEquals(
            60_000L,
            PythonExecutionTimeoutPolicy.resolve(null, 60_000L),
        )
    }

    @Test
    fun configuredTimeoutIsBoundedByThirtyMinutesAndTheProvider() {
        assertTrue(PythonExecutionTimeoutPolicy.isValidConfiguredTimeout(1L))
        assertTrue(
            PythonExecutionTimeoutPolicy.isValidConfiguredTimeout(
                PythonExecutionTimeoutPolicy.MAX_CONFIGURED_TIMEOUT_MILLIS,
            ),
        )
        assertFalse(PythonExecutionTimeoutPolicy.isValidConfiguredTimeout(0L))
        assertFalse(
            PythonExecutionTimeoutPolicy.isValidConfiguredTimeout(
                PythonExecutionTimeoutPolicy.MAX_CONFIGURED_TIMEOUT_MILLIS + 1L,
            ),
        )
        assertEquals(90_000L, PythonExecutionTimeoutPolicy.resolve(90_000L, 120_000L))
        assertEquals(60_000L, PythonExecutionTimeoutPolicy.resolve(90_000L, 60_000L))
    }

    @Test
    fun invalidConfiguredOrProviderLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PythonExecutionTimeoutPolicy.resolve(0L, 60_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonExecutionTimeoutPolicy.resolve(
                PythonExecutionTimeoutPolicy.MAX_CONFIGURED_TIMEOUT_MILLIS + 1L,
                60_000L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonExecutionTimeoutPolicy.resolve(null, 0L)
        }
    }

    @Test
    fun longRunningModeHasNoDeadlineAndRejectsConfiguredTimeouts() {
        assertEquals(
            0L,
            PythonExecutionTimeoutPolicy.resolve(
                PythonExecutionMode.LONG_RUNNING,
                configuredTimeoutMillis = null,
                providerMaxTimeoutMillis = 1L,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PythonExecutionTimeoutPolicy.resolve(
                PythonExecutionMode.LONG_RUNNING,
                configuredTimeoutMillis = 1L,
                providerMaxTimeoutMillis = 60_000L,
            )
        }
    }
}
