package org.autojs.autojs.concurrent

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.management.ManagementFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The Java VolatileDispose the host waits on (`runtime.threads.disposable()` of ui.run and of the bootstrap ResultAdapter,
 * blocking dialogs, the clipboard, floaty windows, gestures; appendix C Q55 of docs/augment/ROADMAP.md): a wait returns once
 * a value is set, a null one included, whether it was set before the wait began or during it, a wakeup without a value does
 * not end it, every waiter wakes, and an interrupt ends the wait of `blockedGet` with a ScriptInterruptedException. Waits run
 * on worker threads; a worker is known to be in its nth wait from its thread state and wait count (ThreadInfo.getWaitedCount,
 * counted before the monitor is released, so a notification sent afterwards reaches it), not after a guessed delay, and a wait
 * that never returns fails the case after five seconds instead of hanging it. The cases with the engine and its threads run
 * on the device (`HostVolatileDisposeTest.json`).
 *
 * zh-CN: 宿主等待用的 Java VolatileDispose (ui.run 与 bootstrap ResultAdapter 的 runtime.threads.disposable(), 阻塞式对话框, 剪贴板, 悬浮窗, 手势;
 * docs/augment/ROADMAP.md 附录 C Q55): 值 (含 null) 在等待前或等待中设置, 等待都会返回; 没有值的唤醒不结束等待; 全部等待者都被唤醒;
 * 中断以 ScriptInterruptedException 结束 blockedGet 的等待. 等待在工作线程上进行; 以线程状态与等待次数 (ThreadInfo.getWaitedCount, 在释放监视器之前计数,
 * 之后发出的通知必能送达) 判定工作线程已进入第 n 次等待, 而不是猜一个延迟; 永不返回的等待在 5 秒后使用例失败而不是卡住.
 * 涉及引擎与其线程的用例在真机运行 (HostVolatileDisposeTest.json).
 */
class VolatileDisposeTest {

    private val workers = mutableListOf<Thread>()

    @After
    fun stopWorkers() {
        // A wait left blocked by a failed case ends here.
        workers.forEach { it.interrupt() }
        workers.forEach { it.join(5_000) }
    }

    // Defect (a): a null value counts as set, so a wait that begins after it returns at once.
    @Test(timeout = 20_000)
    fun unboundedWaitsAfterNullReturnAtOnce() {
        val d = VolatileDispose<Any?>()
        d.setAndNotify(null)
        assertNull(result(onWorker { d.blockedGet() }))
        assertNull(result(onWorker { d.blockedGetOrThrow(IllegalStateException::class.java) }))
    }

    @Test(timeout = 20_000)
    fun boundedWaitsAfterNullReturnAtOnce() {
        val d = VolatileDispose<Any?>()
        d.setAndNotify(null)
        assertNull(result(onWorker { d.blockedGet(60_000) }))
        assertEquals("default", result(onWorker { d.blockedGetOrThrow(IllegalStateException::class.java, 60_000, "default") }))
    }

    // Defect (b): a wakeup without a value (a spurious one, simulated by notifying the monitor) does not end a wait.
    @Test(timeout = 20_000)
    fun blockedGetIgnoresWakeupWithoutValue() {
        assertWakeupIgnored { d -> d.blockedGet() }
        assertWakeupIgnored { d -> d.blockedGet(60_000) }
    }

    @Test(timeout = 20_000)
    fun blockedGetOrThrowIgnoresWakeupWithoutValue() {
        assertWakeupIgnored { d -> d.blockedGetOrThrow(IllegalStateException::class.java) }
        assertWakeupIgnored { d -> d.blockedGetOrThrow(IllegalStateException::class.java, 60_000, "default") }
    }

    // Defect (c): setAndNotify wakes every waiter, not one of them.
    @Test(timeout = 20_000)
    fun everyWaiterWakes() {
        val d = VolatileDispose<Any?>()
        val waits = listOf(
            onWorker { d.blockedGet() },
            onWorker { d.blockedGet(60_000) },
            onWorker { d.blockedGetOrThrow(IllegalStateException::class.java) },
            onWorker { d.blockedGetOrThrow(IllegalStateException::class.java, 60_000, "default") },
        )
        waits.forEachIndexed { i, waiting -> awaitWait(workers[i], 1, waiting) }
        d.setAndNotify("all")
        waits.forEach { assertEquals("all", result(it)) }
    }

    // Defect (d): an interrupt ends the wait of blockedGet with a ScriptInterruptedException, bounded or not.
    @Test(timeout = 20_000)
    fun interruptedUnboundedWaitThrowsScriptInterruptedException() {
        assertInterrupted(ScriptInterruptedException::class.java) { d -> d.blockedGet() }
    }

    @Test(timeout = 20_000)
    fun interruptedBoundedWaitThrowsScriptInterruptedException() {
        assertInterrupted(ScriptInterruptedException::class.java) { d -> d.blockedGet(60_000) }
    }

    // Unchanged: blockedGetOrThrow throws the given exception when interrupted.
    @Test(timeout = 20_000)
    fun interruptedBlockedGetOrThrowThrowsTheGivenException() {
        assertInterrupted(IllegalStateException::class.java) { d -> d.blockedGetOrThrow(IllegalStateException::class.java) }
        assertInterrupted(IllegalStateException::class.java) { d -> d.blockedGetOrThrow(IllegalStateException::class.java, 60_000, "default") }
    }

    // Unchanged: a value set before a wait or during it is returned at once, the last one set when there were several.
    @Test(timeout = 20_000)
    fun valueSetBeforeOrDuringTheWaitIsReturned() {
        val d = VolatileDispose<Any?>()
        d.setAndNotify(1)
        d.setAndNotify("last")
        assertEquals("last", result(onWorker { d.blockedGet() }))
        assertEquals("last", result(onWorker { d.blockedGet(60_000) }))
        val e = VolatileDispose<Any?>()
        val waiting = onWorker { e.blockedGetOrThrow(IllegalStateException::class.java, 60_000, "default") }
        awaitWait(workers.last(), 1, waiting)
        e.setAndNotify("during")
        assertEquals("during", result(waiting))
    }

    // Unchanged: a bounded wait without a value takes its whole timeout and gives null, or the default of blockedGetOrThrow.
    @Test(timeout = 20_000)
    fun boundedWaitWithoutValueTimesOut() {
        val d = VolatileDispose<Any?>()
        val start = System.nanoTime()
        assertNull(d.blockedGet(50))
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(50))
        assertEquals("default", d.blockedGetOrThrow(IllegalStateException::class.java, 50, "default"))
    }

    // Unchanged: the timeout follows Object.wait, where 0 waits without a bound and a negative one is rejected (while no value is set).
    @Test(timeout = 20_000)
    fun timeoutFollowsObjectWait() {
        val d = VolatileDispose<Any?>()
        assertThrows(IllegalArgumentException::class.java) { d.blockedGet(-1) }
        val waiting = onWorker { d.blockedGet(0) }
        awaitWait(workers.last(), 1, waiting)
        d.setAndNotify("z")
        assertEquals("z", result(waiting))
        assertEquals("z", d.blockedGet(-1))
    }

    // Unchanged: a thread already interrupted gets a value that is set without waiting.
    @Test(timeout = 20_000)
    fun interruptedThreadGetsAValueAlreadySet() {
        val d = VolatileDispose<Any?>()
        d.setAndNotify("v")
        Thread.currentThread().interrupt()
        try {
            assertEquals("v", d.blockedGet())
            assertEquals("v", d.blockedGet(60_000))
            assertEquals("v", d.blockedGetOrThrow(IllegalStateException::class.java))
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun deviceCases() {
        DEVICE.add(DEVICE_CASES)
    }

    private fun assertWakeupIgnored(wait: (VolatileDispose<Any?>) -> Any?) {
        val d = VolatileDispose<Any?>()
        val waiting = onWorker { wait(d) }
        val worker = workers.last()
        awaitWait(worker, 1, waiting)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        synchronized(d) { (d as Object).notifyAll() }
        // Either the wait ended (the defect) or the worker waits again.
        awaitWait(worker, 2, waiting)
        d.setAndNotify("b")
        assertEquals("b", result(waiting))
    }

    private fun assertInterrupted(expected: Class<out Throwable>, wait: (VolatileDispose<Any?>) -> Any?) {
        val d = VolatileDispose<Any?>()
        val waiting = onWorker { wait(d) }
        val worker = workers.last()
        awaitWait(worker, 1, waiting)
        worker.interrupt()
        val thrown = try {
            waiting.get(5, TimeUnit.SECONDS)
            fail("The interrupted wait returned")
        } catch (e: TimeoutException) {
            fail("The interrupted wait did not end within 5 seconds")
        } catch (e: ExecutionException) {
            e.cause
        }
        assertTrue("Expected ${expected.simpleName}, got $thrown", expected.isInstance(thrown))
    }

    /** Runs [call] on a new thread; the future completes with its result or its exception. */
    private fun onWorker(call: () -> Any?): CompletableFuture<Any?> {
        val future = CompletableFuture<Any?>()
        val started = CountDownLatch(1)
        workers += Thread {
            started.countDown()
            try {
                future.complete(call())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }.apply { start() }
        started.await()
        return future
    }

    /** The result of [future], or a failure when the wait has not returned within five seconds. */
    private fun result(future: CompletableFuture<Any?>): Any? = try {
        future.get(5, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        fail("The wait did not return within 5 seconds")
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    /** Blocks until [worker] is in its [n]th wait, or until [future] is done (a wait that ended). */
    private fun awaitWait(worker: Thread, n: Long, future: CompletableFuture<*>) {
        while (!future.isDone && !(isWaiting(worker) && waitedCount(worker) >= n)) Thread.yield()
    }

    private fun isWaiting(worker: Thread) = worker.state.let { it == Thread.State.WAITING || it == Thread.State.TIMED_WAITING }

    @Suppress("DEPRECATION")
    private fun waitedCount(worker: Thread) = ManagementFactory.getThreadMXBean().getThreadInfo(worker.id)?.waitedCount ?: 0L

    companion object {

        private val DEVICE = DeviceCases("HostVolatileDisposeTest", "runtime.threads.disposable(), Java VolatileDispose (docs/augment/ROADMAP.md Q55)")

        private const val CONCURRENT = "java.util.concurrent"

        private const val NEW = "var d = runtime.threads.disposable();"

        /** Runs [body] (which sets `out`) on a `threads.start` thread after [setup] and returns `out`, or "timeout" after 5 seconds. */
        private fun onWorker(setup: String, body: String) =
            "(function () { $setup var latch = new $CONCURRENT.CountDownLatch(1); var out = 'timeout'; " +
                "threads.start(function () { try { $body } catch (e) { out = 'failed ' + e; } latch.countDown(); }); " +
                "latch.await(5, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        /** Interrupts a `threads.start` thread waiting with [wait] on `d` and returns the simple name of what it threw, "returned" or "timeout". */
        private fun interrupted(wait: String) =
            "(function () { $NEW var started = new $CONCURRENT.CountDownLatch(1), done = new $CONCURRENT.CountDownLatch(1), out = 'timeout'; " +
                "var t = threads.start(function () { started.countDown(); " +
                "try { $wait; out = 'returned'; } catch (e) { out = e.javaException ? String(e.javaException.getClass().getSimpleName()) : String(e); } done.countDown(); }); " +
                "started.await(5, $CONCURRENT.TimeUnit.SECONDS); t.interrupt(); done.await(5, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        /** Returns the value of [wait] on `d` and whether it took less than 2 seconds (the timeouts are 3 seconds). */
        private fun quick(setup: String, wait: String) =
            "(function () { $NEW $setup var t0 = Date.now(); var v = $wait; return v + ':' + (Date.now() - t0 < 2000); })()"

        /**
         * Starts two `threads.start` threads waiting on `d` without a bound, sets a value once both wait (thread state) and
         * returns how many of them returned within 5 seconds.
         */
        private val EVERY_WAITER =
            "(function () { $NEW var ready = new $CONCURRENT.CountDownLatch(2), woke = new $CONCURRENT.CountDownLatch(2); " +
                "var ts = [0, 1].map(function () { return threads.start(function () { ready.countDown(); d.blockedGet(); woke.countDown(); }); }); " +
                "ready.await(5, $CONCURRENT.TimeUnit.SECONDS); var t0 = Date.now(); " +
                "while (!ts.every(function (t) { return /WAITING/.test(String(t.getState())); }) && Date.now() - t0 < 5000) java.lang.Thread.yield(); " +
                "d.setAndNotify('x'); woke.await(5, $CONCURRENT.TimeUnit.SECONDS); return String(2 - woke.getCount()); })()"

        private val DEVICE_CASES = listOf(
            // (a) A null value set before the wait ends it at once.
            onWorker(NEW + " d.setAndNotify(null);", "out = String(d.blockedGet());") to "\"null\"",
            quick("d.setAndNotify(null);", "d.blockedGet(3000)") to "\"null:true\"",
            quick("d.setAndNotify(null);", "d.blockedGetOrThrow(java.lang.IllegalStateException, 3000, 'default')") to "\"default:true\"",
            // (c) Every waiter wakes.
            EVERY_WAITER to "\"2\"",
            // (d) An interrupt ends blockedGet with a ScriptInterruptedException; blockedGetOrThrow throws its own exception, as before.
            interrupted("d.blockedGet()") to "\"ScriptInterruptedException\"",
            interrupted("d.blockedGet(60000)") to "\"ScriptInterruptedException\"",
            interrupted("d.blockedGetOrThrow(java.lang.IllegalStateException)") to "\"IllegalStateException\"",
            // Unchanged: a value set before a bounded wait, or during it, is returned at once.
            quick("d.setAndNotify('b');", "d.blockedGet(3000)") to "\"b:true\"",
            quick("threads.start(function () { sleep(100); d.setAndNotify('c'); });", "d.blockedGet(3000)") to "\"c:true\"",
            // Unchanged: a bounded wait without a value takes its timeout, then gives null or the default.
            "(function () { $NEW var t0 = Date.now(); var v = d.blockedGet(100); return String(v) + ':' + (Date.now() - t0 >= 90); })()" to "\"null:true\"",
            "String(runtime.threads.disposable().blockedGetOrThrow(java.lang.IllegalStateException, 50, 'default'))" to "\"default\"",
        )

    }

}
