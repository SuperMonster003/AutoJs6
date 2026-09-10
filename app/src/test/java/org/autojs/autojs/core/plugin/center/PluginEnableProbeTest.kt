package org.autojs.autojs.core.plugin.center

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PluginEnableProbeTest {

    @Test
    fun successfulProbeAndOriginalFailureReachCaller() = runBlocking {
        var called = false
        PluginEnableProbe.run("example.plugin") { called = true }
        assertTrue(called)
        val failure = ClassNotFoundException("example.PluginService")
        val error = runCatching { PluginEnableProbe.run("example.plugin") { throw failure } }.exceptionOrNull()
        assertEquals(failure.javaClass, error?.javaClass)
        assertEquals(failure.message, error?.message)
    }

    @Test
    fun timeoutCancelsSuspendedBindingAndReportsHandshakeFailure() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val error = runCatching {
            PluginEnableProbe.run("example.plugin", timeoutMillis = 100L) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }.exceptionOrNull()
        assertTrue(error is TimeoutException)
        assertEquals(PluginErrorCode.HANDSHAKE_TIMEOUT, PluginErrorMapper.fromThrowable(error!!).code)
        withTimeout(2_000L) { cancelled.await() }
    }

    @Test
    fun callerCancellationCancelsProbeWithoutBecomingAnEnableError() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val job = launch {
            PluginEnableProbe.run("example.plugin") {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        withTimeout(2_000L) { started.await() }
        job.cancelAndJoin()
        withTimeout(2_000L) { cancelled.await() }
        assertTrue(job.isCancelled)
    }

    @Test
    fun uninterruptibleRemoteCallDoesNotHoldUpTimeoutOrLateCompletion() = runBlocking {
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        try {
            val error = withTimeout(2_000L) {
                runCatching {
                    PluginEnableProbe.run("example.plugin", timeoutMillis = 100L) {
                        try {
                            release.await(5L, TimeUnit.SECONDS)
                        } finally {
                            returned.countDown()
                        }
                    }
                }.exceptionOrNull()
            }
            assertTrue(error is TimeoutException)
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
        }
        assertTrue(returned.await(2L, TimeUnit.SECONDS))
    }
}
