package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DexCompilerSessionLifecycleTest {

    @Test
    fun startedOccursAtMostOnceAndCompletedRequiresStart() {
        val lifecycle = DexCompilerSessionLifecycle()

        assertFalse(lifecycle.acceptCompleted())
        assertTrue(lifecycle.acceptStarted())
        assertFalse(lifecycle.acceptStarted())
        assertTrue(lifecycle.acceptCompleted())
        assertEquals(DexCompilerSessionLifecycle.CallbackState.COMPLETED, lifecycle.snapshot().callbackState)
    }

    @Test
    fun progressRequiresStartAndStrictlyIncreasingNonNegativeSequence() {
        val lifecycle = DexCompilerSessionLifecycle()

        assertFalse(lifecycle.acceptProgress(0))
        assertTrue(lifecycle.acceptStarted())
        assertFalse(lifecycle.acceptProgress(-1))
        assertTrue(lifecycle.acceptProgress(0))
        assertFalse(lifecycle.acceptProgress(0))
        assertFalse(lifecycle.acceptProgress(-1))
        assertTrue(lifecycle.acceptProgress(4))
        assertFalse(lifecycle.acceptProgress(3))
        assertEquals(4L, lifecycle.snapshot().lastProgressSequence)
    }

    @Test
    fun failureAndCancellationMayBeTerminalBeforeStart() {
        val failed = DexCompilerSessionLifecycle()
        assertTrue(failed.acceptFailed())
        assertFalse(failed.acceptStarted())
        assertFalse(failed.acceptCancelled())

        val cancelled = DexCompilerSessionLifecycle()
        assertTrue(cancelled.acceptCancelled())
        assertFalse(cancelled.acceptFailed())
        assertNull(cancelled.snapshot().lastProgressSequence)
    }

    @Test
    fun everyCallbackIsRejectedAfterAnyTerminalResult() {
        listOf<(DexCompilerSessionLifecycle) -> Boolean>(
            { lifecycle -> lifecycle.apply { acceptStarted() }.acceptCompleted() },
            { lifecycle -> lifecycle.acceptFailed() },
            { lifecycle -> lifecycle.acceptCancelled() },
        ).forEach { terminate ->
            val lifecycle = DexCompilerSessionLifecycle()
            assertTrue(terminate(lifecycle))
            assertFalse(lifecycle.acceptStarted())
            assertFalse(lifecycle.acceptProgress(Long.MAX_VALUE))
            assertFalse(lifecycle.acceptCompleted())
            assertFalse(lifecycle.acceptFailed())
            assertFalse(lifecycle.acceptCancelled())
            assertFalse(lifecycle.requestCancel())
        }
    }

    @Test
    fun cancellationRequestIsIdempotentAndSuppressesNonTerminalAndSuccessCallbacks() {
        val lifecycle = DexCompilerSessionLifecycle()

        assertTrue(lifecycle.requestCancel())
        assertFalse(lifecycle.requestCancel())
        assertFalse(lifecycle.acceptStarted())
        assertFalse(lifecycle.acceptProgress(1))
        assertFalse(lifecycle.acceptCompleted())
        assertTrue(lifecycle.acceptCancelled())
        assertFalse(lifecycle.acceptFailed())
    }

    @Test
    fun failureCanWinRaceAfterCancellationRequest() {
        val lifecycle = DexCompilerSessionLifecycle()
        assertTrue(lifecycle.acceptStarted())
        assertTrue(lifecycle.requestCancel())
        assertTrue(lifecycle.acceptFailed())
        assertFalse(lifecycle.acceptCancelled())
    }

    @Test
    fun closeIsIdempotentAndRejectsLaterCallbacks() {
        val lifecycle = DexCompilerSessionLifecycle()
        assertTrue(lifecycle.acceptStarted())
        assertTrue(lifecycle.close())
        assertFalse(lifecycle.close())
        assertFalse(lifecycle.acceptProgress(1))
        assertFalse(lifecycle.acceptCompleted())
        assertFalse(lifecycle.acceptFailed())
        assertFalse(lifecycle.acceptCancelled())
    }

    @Test
    fun onlyOneConcurrentTerminalCallbackWins() {
        val lifecycle = DexCompilerSessionLifecycle()
        assertTrue(lifecycle.acceptStarted())
        val ready = CountDownLatch(12)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(12)
        val results = (0 until 12).map { index ->
            pool.submit<Boolean> {
                ready.countDown()
                release.await()
                when (index % 3) {
                    0 -> lifecycle.acceptCompleted()
                    1 -> lifecycle.acceptFailed()
                    else -> lifecycle.acceptCancelled()
                }
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        release.countDown()
        assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        pool.shutdownNow()
        assertTrue(lifecycle.snapshot().isTerminal)
    }
}
