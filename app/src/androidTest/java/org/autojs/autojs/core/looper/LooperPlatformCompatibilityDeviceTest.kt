package org.autojs.autojs.core.looper

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.lang.ThreadCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LooperPlatformCompatibilityDeviceTest {
    @Test
    fun compatCancellationSurvivesPlatformInterruptClearing() {
        val ordinary = dispatchProbe(false)
        val compat = dispatchProbe(true)
        val expectedClear = InstrumentationRegistry.getArguments().getString("sdk37.clearInterrupt")
            ?.toBooleanStrict() ?: targets37()
        Log.i(TAG, "dispatch: clear=$expectedClear, ordinary=$ordinary, compat=$compat")
        assertTrue(ordinary.before)
        assertTrue(compat.before)
        assertEquals(!expectedClear, ordinary.nativeAfter)
        assertEquals(!expectedClear, compat.nativeAfter)
        assertTrue("Script cancellation must survive the platform clearing the native flag", compat.after)
        assertFalse("Explicit ThreadCompat.interrupted() must clear script cancellation", compat.afterExplicitClear)
    }

    @Test
    fun threadPoolsRunWorkersWhenStartOverrideIsBypassed() {
        val directStarts = AtomicInteger()
        val directRuns = AtomicInteger()
        val direct = object : Thread() {
            override fun start() { directStarts.incrementAndGet(); super.start() }
            override fun run() { directRuns.incrementAndGet() }
        }
        direct.start()
        direct.join(5_000)
        assertFalse(direct.isAlive)
        assertEquals(1, directStarts.get())
        assertEquals(1, directRuns.get())

        val poolStarts = AtomicInteger()
        val poolRuns = AtomicInteger()
        val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { task ->
            object : Thread(task) {
                override fun start() { poolStarts.incrementAndGet(); super.start() }
                override fun run() { poolRuns.incrementAndGet(); super.run() }
            }
        }
        val forkStarts = AtomicInteger()
        val forkRuns = AtomicInteger()
        val forkJoin = ForkJoinPool(1, { pool ->
            object : ForkJoinWorkerThread(pool) {
                override fun start() { forkStarts.incrementAndGet(); super.start() }
                override fun run() { forkRuns.incrementAndGet(); super.run() }
            }
        }, null, false)
        try {
            assertEquals(37, executor.submit<Int> { 37 }.get(5, TimeUnit.SECONDS))
            assertEquals(37, forkJoin.submit<Int> { 37 }.get(5, TimeUnit.SECONDS))
            val expectedStarts = InstrumentationRegistry.getArguments().getString("sdk37.poolStarts")
                ?.toInt() ?: if (targets37()) 0 else 1
            Log.i(TAG, "pool: expectedStarts=$expectedStarts, executor=${poolStarts.get()}/${poolRuns.get()}, forkJoin=${forkStarts.get()}/${forkRuns.get()}")
            assertEquals(expectedStarts, poolStarts.get())
            assertEquals(expectedStarts, forkStarts.get())
            assertEquals(1, poolRuns.get())
            assertEquals(1, forkRuns.get())
        } finally {
            executor.shutdownNow()
            forkJoin.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(forkJoin.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun dispatchProbe(useCompat: Boolean): InterruptSnapshot {
        val snapshot = AtomicReference<InterruptSnapshot>()
        val failure = AtomicReference<Throwable>()
        val looper = AtomicReference<Looper>()
        val finished = CountDownLatch(1)
        val task = Runnable {
            try {
                Looper.prepare()
                val currentLooper = requireNotNull(Looper.myLooper()).also(looper::set)
                val handler = Handler(currentLooper)
                var before = false
                handler.post {
                    Thread.currentThread().interrupt()
                    before = Thread.currentThread().isInterrupted
                }
                handler.post {
                    val after = Thread.currentThread().isInterrupted
                    // Intentional test operation: observe and clear the platform's native flag.
                    val nativeAfter = Thread.interrupted()
                    val stickyAfter = Thread.currentThread().isInterrupted
                    ThreadCompat.interrupted()
                    snapshot.set(InterruptSnapshot(before, nativeAfter, if (useCompat) stickyAfter else after, Thread.currentThread().isInterrupted))
                    currentLooper.quit()
                }
                Looper.loop()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                ThreadCompat.interrupted()
                finished.countDown()
            }
        }
        val thread = if (useCompat) ThreadCompat(task, "sdk37-compat-dispatch") else Thread(task, "sdk37-native-dispatch")
        thread.start()
        try {
            assertTrue("Looper dispatch did not finish", finished.await(5, TimeUnit.SECONDS))
            thread.join(5_000)
            assertFalse(thread.isAlive)
            failure.get()?.let { throw AssertionError("Dispatch probe failed", it) }
            return requireNotNull(snapshot.get())
        } finally {
            looper.get()?.quit()
            if (thread.isAlive) { thread.interrupt(); thread.join(5_000) }
        }
    }

    private fun targets37() = Build.VERSION.SDK_INT >= 37 &&
        InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.targetSdkVersion >= 37

    private data class InterruptSnapshot(val before: Boolean, val nativeAfter: Boolean, val after: Boolean, val afterExplicitClear: Boolean)

    private companion object { const val TAG = "SDK37-Looper" }
}
