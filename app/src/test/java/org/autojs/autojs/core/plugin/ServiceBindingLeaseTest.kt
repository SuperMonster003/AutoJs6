package org.autojs.autojs.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ServiceBindingLeaseTest {

    @Test
    fun registeredLeaseConcurrentCloseUnbindsExactlyOnce() {
        val unbindCount = AtomicInteger()
        val lease = ServiceBindingLease(unbindCount::incrementAndGet)
        lease.markRegistered()
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)
        val done = CountDownLatch(16)
        val executor = Executors.newFixedThreadPool(16)
        repeat(16) {
            executor.execute {
                ready.countDown()
                start.await()
                lease.close()
                done.countDown()
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertTrue(lease.isClosed)
        assertEquals(1, unbindCount.get())
    }

    @Test
    fun closeBeforeBindRegistrationUnbindsWhenLateBindSucceeds() {
        val unbindCount = AtomicInteger()
        val lease = ServiceBindingLease(unbindCount::incrementAndGet)

        lease.close()
        assertEquals(0, unbindCount.get())
        lease.markRegistered()
        lease.markRegistered()
        lease.close()

        assertEquals(1, unbindCount.get())
    }

    @Test
    fun failedBindThatNeverRegistersDoesNotUnbind() {
        val unbindCount = AtomicInteger()
        val lease = ServiceBindingLease(unbindCount::incrementAndGet)

        lease.close()

        assertTrue(lease.isClosed)
        assertEquals(0, unbindCount.get())
    }

    @Test
    fun registeredLeaseRemainsOpenUntilItsOwnerClosesIt() {
        val unbindCount = AtomicInteger()
        val lease = ServiceBindingLease(unbindCount::incrementAndGet)
        lease.markRegistered()

        assertFalse(lease.isClosed)
        assertEquals(0, unbindCount.get())

        lease.close()
        assertEquals(1, unbindCount.get())
    }

    @Test
    fun concurrentLeasesReleaseOnlyTheirOwnBindings() {
        val firstUnbindCount = AtomicInteger()
        val secondUnbindCount = AtomicInteger()
        val first = ServiceBindingLease(firstUnbindCount::incrementAndGet).apply { markRegistered() }
        val second = ServiceBindingLease(secondUnbindCount::incrementAndGet).apply { markRegistered() }

        first.close()
        first.close()
        assertEquals(1, firstUnbindCount.get())
        assertEquals(0, secondUnbindCount.get())

        second.close()
        assertEquals(1, secondUnbindCount.get())
    }

    @Test
    fun normalExceptionCancelAndDeathPathsEachReleaseTheirLease() {
        ReleasePath.entries.forEach { path ->
            val unbindCount = AtomicInteger()
            val lease = ServiceBindingLease(unbindCount::incrementAndGet)
            lease.markRegistered()

            when (path) {
                ReleasePath.NORMAL -> lease.close()
                ReleasePath.EXCEPTION -> runCatching {
                    lease.use { throw IOException("expected") }
                }
                ReleasePath.CANCEL -> lease.close()
                ReleasePath.BINDER_DEATH -> lease.close()
            }

            assertTrue("Path $path did not close its lease", lease.isClosed)
            assertEquals("Path $path did not unbind exactly once", 1, unbindCount.get())
        }
    }

    private enum class ReleasePath {
        NORMAL,
        EXCEPTION,
        CANCEL,
        BINDER_DEATH,
    }
}
