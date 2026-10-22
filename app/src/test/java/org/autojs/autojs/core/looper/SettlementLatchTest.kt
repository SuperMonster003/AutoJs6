package org.autojs.autojs.core.looper

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.EcmaError
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.math.max

/**
 * The latch of `Promise.prototype.wait()` (appendix C Q26): the waiting thread runs the callbacks of its own timer, where
 * the Promise callbacks may be queued, and the UI thread never blocks. A fake queue stands for the timer on the JVM; the
 * cases with the engine, its timers and the UI thread run on the device (`PromiseWaitTest.json`).
 *
 * zh-CN: Promise.prototype.wait() 的门闩 (附录 C Q26): 等待的线程运行自身计时器上的回调 (Promise 回调可能排在那里), UI 线程从不阻塞. JVM 上用假队列代替计时器;
 * 涉及引擎, 计时器与 UI 线程的用例在真机运行 (PromiseWaitTest.json).
 */
class SettlementLatchTest {

    @Test(timeout = 10_000)
    fun settledBeforeAwaitReturnsAtOnce() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = true)
        latch.settle(5)
        assertEquals(5, latch.await())
        assertEquals(1, queue.endWaits)
    }

    @Test(timeout = 10_000)
    fun dueCallbacksRunInOrderUntilOneSettles() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = true)
        val ran = mutableListOf<String>()
        queue.post { ran += "a" }
        queue.post { ran += "b"; latch.settle("b") }
        queue.post { ran += "c" }
        assertEquals("b", latch.await())
        assertEquals(listOf("a", "b"), ran)
        assertEquals(1, queue.pending())
    }

    @Test(timeout = 10_000)
    fun callbackDueLaterRunsWhenDue() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = true)
        val start = System.nanoTime()
        queue.post(delayMillis = 100) { latch.settle("later") }
        assertEquals("later", latch.await())
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 100)
    }

    @Test(timeout = 10_000)
    fun callbackQueuedByAnotherThreadRunsOnTheWaitingThread() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = true)
        val ranOn = AtomicReference<Thread>()
        val poster = Thread {
            Thread.sleep(50)
            queue.post { ranOn.set(Thread.currentThread()); latch.settle(1) }
        }.apply { start() }
        assertEquals(1, latch.await())
        assertSame(Thread.currentThread(), ranOn.get())
        poster.join(5_000)
    }

    @Test(timeout = 10_000)
    fun settleOnAnotherThreadWakesTheWait() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = true)
        val settler = Thread { Thread.sleep(50); latch.settle(2) }.apply { start() }
        assertEquals(2, latch.await())
        settler.join(5_000)
    }

    @Test(timeout = 10_000)
    fun withoutQueueTheLatchOnlyBlocks() {
        val latch = SettlementLatch(null, mayBlock = true)
        val settler = Thread { Thread.sleep(50); latch.settle(3) }.apply { start() }
        assertEquals(3, latch.await())
        settler.join(5_000)
    }

    @Test(timeout = 10_000)
    fun nestedWaitOnTheSameQueue() {
        val queue = FakeQueue()
        val outer = SettlementLatch(queue, mayBlock = true)
        queue.post {
            val inner = SettlementLatch(queue, mayBlock = true)
            queue.post { inner.settle(6) }
            outer.settle(inner.await() as Int + 1)
        }
        assertEquals(7, outer.await())
        assertEquals(2, queue.endWaits)
        assertNull(queue.waiter)
    }

    @Test(timeout = 10_000)
    fun withoutBlockingDueCallbacksStillRun() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = false)
        queue.post { latch.settle(4) }
        assertEquals(4, latch.await())
    }

    @Test(timeout = 10_000)
    fun withoutBlockingPendingSettlementThrowsTypeError() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = false)
        queue.post(delayMillis = 60_000) { latch.settle(0) }
        val error = assertThrows(EcmaError::class.java) { latch.await() }
        assertEquals("TypeError", error.name)
        assertEquals(
            "Promise.prototype.wait() cannot block the UI thread while the Promise is pending. Use then(), await() in a coroutine, or call wait() in a sub-thread",
            error.errorMessage,
        )
        // The callback due later is left to the timer.
        assertEquals(1, queue.pending())
        assertEquals(1, queue.endWaits)
    }

    @Test(timeout = 10_000)
    fun interruptThrowsScriptInterruptedException() {
        val latch = SettlementLatch(FakeQueue(), mayBlock = true)
        Thread.currentThread().interrupt()
        assertThrows(ScriptInterruptedException::class.java) { latch.await() }
    }

    @Test(timeout = 10_000)
    fun onlyTheFirstSettleCounts() {
        val latch = SettlementLatch(null, mayBlock = true)
        latch.settle(1)
        latch.settle(2)
        assertEquals(1, latch.await())
    }

    @Test(timeout = 10_000)
    fun awaitOnAnotherThreadIsRefused() {
        val latch = SettlementLatch(null, mayBlock = true)
        val error = AtomicReference<Throwable>()
        Thread { error.set(runCatching { latch.await() }.exceptionOrNull()) }.apply { start() }.join(5_000)
        assertTrue(error.get() is IllegalStateException)
    }

    @Test(timeout = 10_000)
    fun throwingCallbackPropagatesAndEndsTheWait() {
        val queue = FakeQueue()
        val latch = SettlementLatch(queue, mayBlock = true)
        queue.post { throw IllegalArgumentException("callback failed") }
        assertThrows(IllegalArgumentException::class.java) { latch.await() }
        assertEquals(1, queue.endWaits)
        assertNull(queue.waiter)
    }

    @Test
    fun deviceCases() {
        DEVICE.add(DEVICE_CASES)
    }

    /** Callbacks due at a time in milliseconds; [post] wakes the waiting thread the way `Timer.postDelayed` does. */
    private class FakeQueue : SettlementLatch.CallbackQueue {

        private val start = System.nanoTime()
        private val callbacks = mutableListOf<Pair<Long, Runnable>>()

        @Volatile
        var waiter: Thread? = null
        var endWaits = 0

        private fun now() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        fun post(delayMillis: Long = 0, callback: Runnable) {
            val waiting = synchronized(this) {
                callbacks += now() + delayMillis to callback
                waiter
            }
            waiting?.let { LockSupport.unpark(it) }
        }

        @Synchronized
        fun pending() = callbacks.size

        @Synchronized
        override fun takeDueCallback(): Runnable? {
            val next = callbacks.minByOrNull { it.first }?.takeIf { it.first <= now() } ?: return null
            callbacks.remove(next)
            return next.second
        }

        @Synchronized
        override fun millisUntilNextCallback(): Long = callbacks.minByOrNull { it.first }?.let { max(it.first - now(), 1L) } ?: -1L

        // Nested waits on the same thread keep the waiter until the outermost one ends.
        private var depth = 0

        @Synchronized
        override fun beginWait() {
            depth++
            waiter = Thread.currentThread()
        }

        @Synchronized
        override fun endWait() {
            endWaits++
            if (--depth == 0) waiter = null
        }

    }

    companion object {

        private val DEVICE = DeviceCases("PromiseWaitTest", "Promise.prototype.wait (docs/augment/ROADMAP.md Q26)")

        private const val CONCURRENT = "java.util.concurrent"

        /** Runs [body] (which sets `out`) in a function posted to the UI thread and returns `out`, or "timeout" after 10 seconds. */
        private fun onUiThread(body: String) =
            "(function () { var latch = new $CONCURRENT.CountDownLatch(1); var out = 'timeout'; " +
                "new android.os.Handler(android.os.Looper.getMainLooper()).post(function () { try { $body } catch (e) { out = 'failed ' + e; } latch.countDown(); }); " +
                "latch.await(10, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        private val DEVICE_CASES = listOf(
            "Promise.resolve(1).wait()" to "1",
            HostModuleTestSupport.waited("new Promise(function (resolve) { setTimeout(function () { resolve(2); }, 20); })") to "2",
            // Settled on a thread without a timer: the callbacks go to the timer of the script thread.
            HostModuleTestSupport.waited("new Promise(function (resolve) { new java.lang.Thread(function () { java.lang.Thread.sleep(20); resolve(3); }).start(); })") to "3",
            "(function () { try { Promise.reject(new Error('x')).wait(); return 'fulfilled'; } catch (e) { return e.message; } })()" to "\"x\"",
            // A wait inside a timer callback that another wait runs.
            HostModuleTestSupport.waited("new Promise(function (resolve) { setTimeout(function () { resolve(Promise.resolve(6).wait() + 1); }, 10); })") to "7",
            "(function () { var latch = new $CONCURRENT.CountDownLatch(1); var out = 'timeout'; " +
                "threads.start(function () { try { out = Promise.resolve(4).wait(); } catch (e) { out = 'failed ' + e; } latch.countDown(); }); " +
                "latch.await(10, $CONCURRENT.TimeUnit.SECONDS); return out; })()" to "4",
            // The UI thread runs the callbacks already due and never blocks: a pending Promise throws.
            onUiThread("out = Promise.resolve(5).wait();") to "5",
            onUiThread("try { new Promise(function () {}).wait(); out = 'returned'; } catch (e) { out = e.name; }") to "\"TypeError\"",
        )

    }

}
