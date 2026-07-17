package org.autojs.autojs.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class IntentUtilsNodeShutdownTest {

    @Test
    fun completedShutdownResultIsReturned() {
        val task = FutureTask { true }.apply { run() }

        assertTrue(IntentUtils.App.awaitNodeShutdownBeforeExit(task, 100L))
    }

    @Test
    fun waitUsesBoundedPositiveTimeout() {
        var observedTimeout = -1L
        var observedUnit: TimeUnit? = null
        val task = object : StubFuture() {
            override fun get(timeout: Long, unit: TimeUnit): Boolean {
                observedTimeout = timeout
                observedUnit = unit
                throw TimeoutException("not complete")
            }
        }

        assertFalse(IntentUtils.App.awaitNodeShutdownBeforeExit(task, 0L))
        assertTrue(observedTimeout == 1L)
        assertTrue(observedUnit == TimeUnit.MILLISECONDS)
    }

    @Test
    fun interruptedWaitRestoresInterruptFlag() {
        val task = object : StubFuture() {
            override fun get(timeout: Long, unit: TimeUnit): Boolean {
                throw InterruptedException("interrupted")
            }
        }

        try {
            assertFalse(IntentUtils.App.awaitNodeShutdownBeforeExit(task, 100L))
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private abstract class StubFuture : Future<Boolean> {
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = false
        override fun get(): Boolean = error("Unbounded get must not be used")
    }
}
