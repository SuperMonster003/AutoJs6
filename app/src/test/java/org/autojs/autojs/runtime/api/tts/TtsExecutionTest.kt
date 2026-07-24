package org.autojs.autojs.runtime.api.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TtsExecutionTest {

    @Test
    fun nativeRunnerRejectsInsteadOfGrowingPastItsProcessWideBound() {
        val started = CountDownLatch(AndroidTtsBlockingCallRunner.MAX_IN_FLIGHT_CALLS)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(AndroidTtsBlockingCallRunner.MAX_IN_FLIGHT_CALLS)
        val cleanupFinished = CountDownLatch(1)
        try {
            repeat(AndroidTtsBlockingCallRunner.MAX_IN_FLIGHT_CALLS) {
                assertTrue(
                    AndroidTtsBlockingCallRunner.execute {
                        started.countDown()
                        try {
                            release.await()
                        } finally {
                            finished.countDown()
                        }
                    },
                )
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(
                AndroidTtsBlockingCallRunner.executeCleanup(cleanupFinished::countDown),
            )
            assertFalse(AndroidTtsBlockingCallRunner.execute {})
        } finally {
            release.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(cleanupFinished.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun deadlineSchedulerUsesAnIndependentCancellableLane() {
        val scheduler = DefaultTtsDeadlineScheduler()
        val fired = CountDownLatch(1)
        val threadName = AtomicReference<String>()
        try {
            scheduler.schedule(0L) {
                threadName.set(Thread.currentThread().name)
                fired.countDown()
            }
            assertTrue(fired.await(5, TimeUnit.SECONDS))
            assertTrue(threadName.get().startsWith("AutoJs6-TTS-Deadline-"))

            val cancelled = CountDownLatch(1)
            scheduler.schedule(60_000L, cancelled::countDown).cancel()
            assertFalse(cancelled.await(50, TimeUnit.MILLISECONDS))
        } finally {
            scheduler.shutdown()
        }
    }
}
