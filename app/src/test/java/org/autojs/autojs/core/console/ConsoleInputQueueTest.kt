package org.autojs.autojs.core.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConsoleInputQueueTest {

    @Test
    fun submitServesTheOldestPendingRequest() {
        val queue = ConsoleInputQueue()
        val first = ConsoleInputQueue.newRequest("a")
        val second = ConsoleInputQueue.newRequest("b")
        queue.add(first)
        queue.add(second)
        assertEquals(2, queue.pendingCount)

        assertSame(first, queue.submit("one"))
        assertTrue(first.isDone)
        assertEquals("one", first.await())
        assertEquals(1, queue.pendingCount)

        assertSame(second, queue.submit("two"))
        assertEquals("two", second.await())
        assertNull(queue.submit("three"))
        assertFalse(queue.hasPending)
    }

    @Test
    fun completedRequestsAreSkippedUntilRemoved() {
        val queue = ConsoleInputQueue()
        val stale = ConsoleInputQueue.newRequest("a")
        val fresh = ConsoleInputQueue.newRequest("a")
        queue.add(stale)
        queue.add(fresh)
        stale.complete("already")
        assertSame(fresh, queue.submit("line"))
        queue.remove(stale)
        queue.remove(fresh)
        assertEquals(0, queue.pendingCount)
    }

    @Test
    fun cancelAllOnlyTouchesTheOwner() {
        val queue = ConsoleInputQueue()
        val mine = ConsoleInputQueue.newRequest("mine")
        val other = ConsoleInputQueue.newRequest("other")
        queue.add(mine)
        queue.add(other)
        queue.cancelAll("mine")
        assertTrue(mine.isDone)
        assertFalse(other.isDone)
        assertEquals(1, queue.pendingCount)
        try {
            mine.await()
            fail("cancelled request must not return a value")
        } catch (_: CancellationException) {
            /* Expected. */
        }
        queue.cancelAll()
        assertEquals(0, queue.pendingCount)
    }

    @Test
    fun awaitBlocksUntilCompleted() {
        val queue = ConsoleInputQueue()
        val request = ConsoleInputQueue.newRequest(null)
        queue.add(request)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<String> { request.await() }
            Thread.sleep(50)
            assertFalse(future.isDone)
            assertSame(request, queue.submit("typed"))
            assertEquals("typed", future.get(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

}
