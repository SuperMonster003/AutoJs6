package org.autojs.plugin.apkbuilder.template.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RemoteBuildRequestInputCloserTest {

    @Test
    fun close_isIdempotentIdentityDeduplicatedAndBestEffort() {
        val first = RecordingCloseable()
        val failing = RecordingCloseable(fail = true)
        val last = RecordingCloseable()
        val failures = AtomicInteger()
        val closer = RemoteBuildRequestInputCloser(
            closeables = listOf(first, failing, first, null, last),
            onCloseFailure = { failures.incrementAndGet() },
        )

        closer.close()
        closer.close()

        assertEquals(1, first.closeCount.get())
        assertEquals(1, failing.closeCount.get())
        assertEquals(1, last.closeCount.get())
        assertEquals(1, failures.get())
    }

    @Test
    fun close_isIdempotentAcrossConcurrentCallers() {
        val closeable = RecordingCloseable()
        val closer = RemoteBuildRequestInputCloser(listOf(closeable))
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        val executor = Executors.newFixedThreadPool(8)
        repeat(8) {
            executor.execute {
                ready.countDown()
                start.await()
                closer.close()
                done.countDown()
            }
        }

        ready.await(5, TimeUnit.SECONDS)
        start.countDown()
        done.await(5, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertEquals(1, closeable.closeCount.get())
    }

    private class RecordingCloseable(
        private val fail: Boolean = false,
    ) : Closeable {

        val closeCount = AtomicInteger()

        override fun close() {
            closeCount.incrementAndGet()
            if (fail) {
                throw IOException("expected")
            }
        }
    }
}
