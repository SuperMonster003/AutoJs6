package org.autojs.autojs.runtime.api.modules.terminal

import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException

class TerminalBlockingWaitTest {
    @Test fun synchronousWaitDrainsQueuedEventsOnItsOriginThreadBeforeReturning() {
        val owner = Thread.currentThread()
        val release = AtomicInteger()
        val events = mutableListOf<String>()
        val dispatcher = ScriptAsyncDispatcher(
            valueMapper = { assertSame(owner, Thread.currentThread()); it },
            scheduler = { }, releaseWait = { release.incrementAndGet() },
        )
        val wait = TerminalBlockingWait<String>()
        val worker = thread {
            wait.complete(runCatching {
                dispatcher.dispatchValues(arrayOf("writing")) { events += it[0] as String }
                dispatcher.terminalValues(arrayOf("complete")) { events += it[0] as String }
                assertFalse(dispatcher.dispatchValues(arrayOf("late")) { events += "late" })
                "result"
            })
        }
        assertEquals("result", bounded(wait) { dispatcher.drainOnOriginatingThread() })
        worker.join(1000)
        assertFalse(worker.isAlive)
        assertEquals(listOf("writing", "complete"), events)
        assertEquals(1, release.get())
    }

    @Test fun stoppingTheScriptSuppressesAlreadyQueuedTerminalDelivery() {
        val release = AtomicInteger()
        val delivered = AtomicInteger()
        val dispatcher = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = {}, releaseWait = { release.incrementAndGet() })
        dispatcher.terminalValues { delivered.incrementAndGet() }
        assertTrue(dispatcher.cancelSilently())
        dispatcher.drainOnOriginatingThread()
        assertEquals(0, delivered.get())
        assertEquals(1, release.get())
        try {
            TerminalBlockingWait<Unit>().await(stopped = { true }, pump = { fail("stopped script must not pump callbacks") })
            fail("expected cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun aForeignThreadCannotDrainAnotherScriptsQueue() {
        val dispatcher = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = {}, releaseWait = {})
        val rejection = AtomicReference<Throwable?>()
        val worker = thread {
            try { dispatcher.drainOnOriginatingThread() } catch (error: Throwable) { rejection.set(error) }
        }
        worker.join(1000)
        assertTrue(rejection.get() is IllegalStateException)
        dispatcher.cancelSilently()
    }

    @Test fun completionWakesTheConditionAndOnlyTheFirstOutcomeWins() {
        val wait = TerminalBlockingWait<Int>()
        val pumping = CountDownLatch(1)
        val worker = thread {
            wait.complete(runCatching { check(pumping.await(1, TimeUnit.SECONDS)); 42 })
            wait.complete(Result.failure(IllegalStateException("late")))
        }
        assertEquals(42, bounded(wait) { pumping.countDown() })
        worker.join(1000)
        assertFalse(worker.isAlive)
    }

    @Test fun aListenerFailureEscapesTheWaitWithoutLeavingItsConditionLocked() {
        val wait = TerminalBlockingWait<Int>()
        val failure = IllegalStateException("listener failed")
        try { wait.await { throw failure }; fail("expected listener failure") } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
        val worker = thread { wait.complete(Result.success(12)) }
        worker.join(1000)
        assertFalse(worker.isAlive)
        assertEquals(12, bounded(wait) {})
    }

    private fun <T> bounded(wait: TerminalBlockingWait<T>, pump: () -> Unit): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        return wait.await(stopped = { System.nanoTime() >= deadline }, pump = pump)
    }
}
