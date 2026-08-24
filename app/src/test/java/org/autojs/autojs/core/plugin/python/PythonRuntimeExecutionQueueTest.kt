package org.autojs.autojs.core.plugin.python

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeExecutionQueueTest {
    @Test
    fun queuedExecutionsAcquireInFifoOrder() {
        val queue = PythonRuntimeExecutionQueue(maxPendingExecutions = 4)
        val first = queue.acquire()
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val firstQueuedAcquired = CountDownLatch(1)
        val releaseFirstQueued = CountDownLatch(1)
        val secondQueuedAcquired = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val firstQueued = Thread({
            runCatching {
                queue.acquire().use {
                    order += 1
                    firstQueuedAcquired.countDown()
                    releaseFirstQueued.await()
                }
            }.onFailure { error -> failure.compareAndSet(null, error) }
        }, "python-queue-test-first")
        val secondQueued = Thread({
            runCatching {
                queue.acquire().use {
                    order += 2
                    secondQueuedAcquired.countDown()
                }
            }.onFailure { error -> failure.compareAndSet(null, error) }
        }, "python-queue-test-second")

        try {
            firstQueued.start()
            awaitPending(queue, 1)
            secondQueued.start()
            awaitPending(queue, 2)

            first.close()
            assertTrue(firstQueuedAcquired.await(5, TimeUnit.SECONDS))
            assertFalse(secondQueuedAcquired.await(100, TimeUnit.MILLISECONDS))
            releaseFirstQueued.countDown()
            assertTrue(secondQueuedAcquired.await(5, TimeUnit.SECONDS))
            join(firstQueued)
            join(secondQueued)

            failure.get()?.let { throw AssertionError("Queued execution failed", it) }
            assertEquals(listOf(1, 2), order)
            assertEquals(
                PythonRuntimeExecutionQueue.Snapshot(active = false, pendingExecutions = 0),
                queue.snapshot(),
            )
        } finally {
            first.close()
            releaseFirstQueued.countDown()
            firstQueued.interrupt()
            secondQueued.interrupt()
            firstQueued.join(5_000)
            secondQueued.join(5_000)
        }
    }

    @Test
    fun interruptRemovesAQueuedExecutionWithoutLosingTheOwner() {
        val queue = PythonRuntimeExecutionQueue(maxPendingExecutions = 2)
        val owner = queue.acquire()
        val failure = AtomicReference<Throwable?>()
        val waiter = Thread({
            try {
                queue.acquire().close()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }, "python-queue-test-interrupt")

        try {
            waiter.start()
            awaitPending(queue, 1)
            waiter.interrupt()
            join(waiter)

            assertTrue(failure.get() is InterruptedException)
            assertEquals(
                PythonRuntimeExecutionQueue.Snapshot(active = true, pendingExecutions = 0),
                queue.snapshot(),
            )
            owner.close()
            queue.acquire().close()
        } finally {
            owner.close()
            waiter.interrupt()
            waiter.join(5_000)
        }
    }

    @Test
    fun queueCapacityFailsClosedWithoutDisturbingExistingWaiters() {
        val queue = PythonRuntimeExecutionQueue(maxPendingExecutions = 1)
        val owner = queue.acquire()
        val waiter = Thread({
            try {
                queue.acquire().close()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "python-queue-test-capacity")

        try {
            waiter.start()
            awaitPending(queue, 1)
            val error = assertThrows(PythonRuntimeHostException::class.java) {
                queue.acquire()
            }
            assertEquals(PythonRuntimeHostError.BUSY, error.error)
            assertEquals(1, queue.snapshot().pendingExecutions)
        } finally {
            waiter.interrupt()
            join(waiter)
            owner.close()
        }
    }

    @Test
    fun leaseCloseIsIdempotentAndCapacityMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PythonRuntimeExecutionQueue(maxPendingExecutions = 0)
        }

        val queue = PythonRuntimeExecutionQueue(maxPendingExecutions = 1)
        val lease = queue.acquire()
        lease.close()
        lease.close()
        assertFalse(queue.snapshot().active)
        queue.acquire().close()
    }

    private fun awaitPending(queue: PythonRuntimeExecutionQueue, expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (queue.snapshot().pendingExecutions != expected && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assertEquals(expected, queue.snapshot().pendingExecutions)
    }

    private fun join(thread: Thread) {
        thread.join(5_000)
        assertFalse("Thread did not terminate: ${thread.name}", thread.isAlive)
    }
}
