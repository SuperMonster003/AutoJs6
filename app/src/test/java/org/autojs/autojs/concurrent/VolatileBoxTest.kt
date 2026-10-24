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
 * The Java VolatileBox (the global `VolatileBox` of scripts and `com.stardust.concurrent.VolatileBox` of Auto.js 4 scripts;
 * the host itself only keeps a value in it; appendix C Q130 of docs/augment/ROADMAP.md): a notification sent while threads
 * wait wakes all of them, one sent while none waits is kept for the next wait (once), a wakeup without a notification does
 * not end a wait, and an interrupt ends the wait of `block` / `blockedGet` with a ScriptInterruptedException. Waits run on
 * worker threads, known to be in their nth wait from the thread state and wait count (as in [VolatileDisposeTest]); a wait
 * that never returns fails the case after five seconds instead of hanging it. The cases through the script engine and its
 * threads run on the device (`HostVolatileBoxTest.json`).
 *
 * zh-CN: Java VolatileBox (脚本的全局 VolatileBox 与 Auto.js 4 脚本的 com.stardust.concurrent.VolatileBox; 宿主自身只用它存值; docs/augment/ROADMAP.md
 * 附录 C Q130): 有线程等待时发出的通知唤醒全部等待者, 无人等待时发出的通知留给下一次等待 (一次), 没有通知的唤醒不结束等待, 中断以
 * ScriptInterruptedException 结束 block / blockedGet 的等待. 等待在工作线程上进行, 以线程状态与等待次数判定已进入第 n 次等待 (同 [VolatileDisposeTest]);
 * 永不返回的等待在 5 秒后使用例失败而不是卡住. 经脚本引擎与其线程的用例在真机运行 (HostVolatileBoxTest.json).
 */
class VolatileBoxTest {

    private val workers = mutableListOf<Thread>()

    @After
    fun stopWorkers() {
        // A wait left blocked by a failed case ends here.
        workers.forEach { it.interrupt() }
        workers.forEach { it.join(5_000) }
    }

    // A notification sent before the wait began is not lost: the next wait returns at once, bounded or not.
    @Test(timeout = 20_000)
    fun notificationBeforeTheWaitIsKept() {
        val b = VolatileBox<Any?>()
        b.setAndNotify("v")
        assertEquals("v", result(onWorker { b.blockedGet() }))
        b.unblock()
        assertEquals("v", result(onWorker { b.blockedGetOrThrow(IllegalStateException::class.java) }))
        b.unblock("u")
        assertEquals("u", result(onWorker { b.blockedGet(60_000) }))
        b.unblock()
        assertEquals("done", result(onWorker { b.block(); "done" }))
    }

    // It is kept for one wait: the wait after it waits for a notification of its own, so a box can be used again.
    @Test(timeout = 20_000)
    fun aKeptNotificationEndsOneWait() {
        val b = VolatileBox<Any?>()
        b.setAndNotify(1)
        b.setAndNotify(2)
        assertEquals(2, b.blockedGet(60_000))
        val start = System.nanoTime()
        assertEquals(2, b.blockedGet(50))
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(50))
        repeat(3) { round ->
            val waiting = onWorker { b.blockedGet() }
            awaitWait(workers.last(), 1, waiting)
            b.setAndNotify("round $round")
            assertEquals("round $round", result(waiting))
        }
    }

    // A wakeup without a notification (a spurious one, simulated by notifying the monitor) does not end a wait.
    @Test(timeout = 20_000)
    fun wakeupWithoutNotificationIsIgnored() {
        assertWakeupIgnored { b -> b.blockedGet() }
        assertWakeupIgnored { b -> b.blockedGet(60_000) }
        assertWakeupIgnored { b -> b.blockedGetOrThrow(IllegalStateException::class.java) }
        assertWakeupIgnored { b -> b.blockedGetOrThrow(60_000, IllegalStateException::class.java) }
    }

    // A notification wakes every waiter, not one of them.
    @Test(timeout = 20_000)
    fun everyWaiterWakes() {
        val b = VolatileBox<Any?>()
        val waits = listOf(
            onWorker { b.block(); "block" },
            onWorker { b.blockedGet() },
            onWorker { b.blockedGet(60_000) },
            onWorker { b.blockedGetOrThrow(IllegalStateException::class.java) },
        )
        waits.forEachIndexed { i, waiting -> awaitWait(workers[i], 1, waiting) }
        b.setAndNotify("all")
        assertEquals(listOf("block", "all", "all", "all"), waits.map { result(it) })
    }

    // Concurrent setting and waiting: two threads hand a counter back and forth through two boxes, each setting the box
    // the other one waits on, whether that one already waits or not; a lost notification would stop the exchange.
    @Test(timeout = 20_000)
    fun concurrentHandOffsLoseNoNotification() {
        val ping = VolatileBox<Int>()
        val pong = VolatileBox<Int>()
        val rounds = 2_000
        val partner = onWorker {
            repeat(rounds) { pong.setAndNotify(ping.blockedGet()!! + 1) }
            "partner"
        }
        var n = 0
        repeat(rounds) {
            ping.setAndNotify(n)
            n = pong.blockedGet(5_000) ?: throw AssertionError("No answer after round $it")
        }
        assertEquals(rounds, n)
        assertEquals("partner", result(partner))
    }

    // Timeouts: a bounded wait without a notification takes its whole timeout and gives the value as it is (a value set
    // without a notification does not end a wait); 0 waits without a bound, a negative one is rejected (as Object.wait).
    @Test(timeout = 20_000)
    fun boundedWaitWithoutNotificationTimesOut() {
        val b = VolatileBox<Any?>()
        val start = System.nanoTime()
        assertNull(b.blockedGet(50))
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(50))
        b.set("plain")
        assertEquals("plain", b.blockedGetOrThrow(50, IllegalStateException::class.java))
        assertThrows(IllegalArgumentException::class.java) { b.block(-1) }
        val waiting = onWorker { b.blockedGet(0) }
        awaitWait(workers.last(), 1, waiting)
        b.setAndNotify("z")
        assertEquals("z", result(waiting))
        b.unblock()
        assertEquals("z", b.blockedGet(-1))
    }

    // An interrupt ends the wait of block / blockedGet with a ScriptInterruptedException, bounded or not.
    @Test(timeout = 20_000)
    fun interruptedWaitThrowsScriptInterruptedException() {
        assertInterrupted(ScriptInterruptedException::class.java) { b -> b.block() }
        assertInterrupted(ScriptInterruptedException::class.java) { b -> b.blockedGet() }
        assertInterrupted(ScriptInterruptedException::class.java) { b -> b.blockedGet(60_000) }
    }

    // Unchanged: blockedGetOrThrow throws the given exception when interrupted.
    @Test(timeout = 20_000)
    fun interruptedBlockedGetOrThrowThrowsTheGivenException() {
        assertInterrupted(IllegalStateException::class.java) { b -> b.blockedGetOrThrow(IllegalStateException::class.java) }
        assertInterrupted(IllegalStateException::class.java) { b -> b.blockedGetOrThrow(60_000, IllegalStateException::class.java) }
    }

    // A thread already interrupted gets a kept notification without waiting.
    @Test(timeout = 20_000)
    fun interruptedThreadGetsAKeptNotification() {
        val b = VolatileBox<Any?>()
        Thread.currentThread().interrupt()
        try {
            b.setAndNotify("v")
            assertEquals("v", b.blockedGet())
            b.unblock()
            assertEquals("v", b.blockedGetOrThrow(IllegalStateException::class.java))
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun deviceCases() {
        DEVICE.add(DEVICE_CASES)
    }

    private fun assertWakeupIgnored(wait: (VolatileBox<Any?>) -> Any?) {
        val b = VolatileBox<Any?>()
        val waiting = onWorker { wait(b) }
        val worker = workers.last()
        awaitWait(worker, 1, waiting)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        synchronized(b) { (b as Object).notifyAll() }
        // Either the wait ended (the defect) or the worker waits again.
        awaitWait(worker, 2, waiting)
        b.setAndNotify("b")
        assertEquals("b", result(waiting))
    }

    private fun assertInterrupted(expected: Class<out Throwable>, wait: (VolatileBox<Any?>) -> Any?) {
        val b = VolatileBox<Any?>()
        val waiting = onWorker { wait(b) }
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

        private val DEVICE = DeviceCases("HostVolatileBoxTest", "global VolatileBox, Java VolatileBox (docs/augment/ROADMAP.md Q130)")

        private const val CONCURRENT = "java.util.concurrent"

        private const val NEW = "var b = new VolatileBox();"

        /** Interrupts a `threads.start` thread waiting with [wait] on `b` and returns the simple name of what it threw, "returned" or "timeout". */
        private fun interrupted(wait: String) =
            "(function () { $NEW var started = new $CONCURRENT.CountDownLatch(1), done = new $CONCURRENT.CountDownLatch(1), out = 'timeout'; " +
                "var t = threads.start(function () { started.countDown(); " +
                "try { $wait; out = 'returned'; } catch (e) { out = e.javaException ? String(e.javaException.getClass().getSimpleName()) : String(e); } done.countDown(); }); " +
                "started.await(5, $CONCURRENT.TimeUnit.SECONDS); var t0 = Date.now(); " +
                "while (!/WAITING/.test(String(t.getState())) && Date.now() - t0 < 5000) java.lang.Thread.yield(); " +
                "t.interrupt(); done.await(5, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        /** Returns the value of [wait] on `b` and whether it took less than 2 seconds (the timeouts are 3 seconds). */
        private fun quick(setup: String, wait: String) =
            "(function () { $NEW $setup var t0 = Date.now(); var v = $wait; return v + ':' + (Date.now() - t0 < 2000); })()"

        /**
         * Starts two `threads.start` threads waiting on `b` (bounded by 8 seconds, so that a thread the old box left waiting
         * does not keep the script alive), notifies once both wait (thread state) and returns how many of them returned
         * within 4 seconds.
         */
        private val EVERY_WAITER =
            "(function () { $NEW var ready = new $CONCURRENT.CountDownLatch(2), woke = new $CONCURRENT.CountDownLatch(2); " +
                "var ts = [0, 1].map(function () { return threads.start(function () { ready.countDown(); b.block(8000); woke.countDown(); }); }); " +
                "ready.await(5, $CONCURRENT.TimeUnit.SECONDS); var t0 = Date.now(); " +
                "while (!ts.every(function (t) { return /WAITING/.test(String(t.getState())); }) && Date.now() - t0 < 5000) java.lang.Thread.yield(); " +
                "b.unblock(); woke.await(4, $CONCURRENT.TimeUnit.SECONDS); return String(2 - woke.getCount()); })()"

        /**
         * Hands a counter back and forth 200 times between the script thread and a `threads.start` thread through two boxes;
         * a lost notification shows as a round of a second ("slow at" the round).
         */
        private val HAND_OFFS =
            "(function () { var ping = new VolatileBox(), pong = new VolatileBox(), n = 0, done = false; " +
                "threads.start(function () { for (var i = 0; i < 200 && !done; i += 1) pong.setAndNotify(ping.blockedGet(1000) + 1); }); " +
                "for (var j = 0; j < 200; j += 1) { var t0 = Date.now(); ping.setAndNotify(n); n = pong.blockedGet(1000); if (Date.now() - t0 >= 900) { done = true; return 'slow at ' + j; } } " +
                "return String(Number(n)); })()"

        private val DEVICE_CASES = listOf(
            // typeof of the bare name is "undefined" in both states (the name still resolves), that of global.VolatileBox "function".
            "typeof global.VolatileBox + ':' + (new VolatileBox() instanceof Packages.org.autojs.autojs.concurrent.VolatileBox)" to "\"function:true\"",
            // A notification sent before the wait is kept for it, once.
            quick("b.setAndNotify('k');", "b.blockedGet(3000)") to "\"k:true\"",
            quick("b.unblock('u');", "b.blockedGetOrThrow(3000, java.lang.IllegalStateException)") to "\"u:true\"",
            "(function () { $NEW b.setAndNotify('k'); b.blockedGet(3000); var t0 = Date.now(); var v = b.blockedGet(100); return v + ':' + (Date.now() - t0 >= 90); })()" to "\"k:true\"",
            // Every waiter wakes; no notification is lost while two threads hand a value back and forth.
            EVERY_WAITER to "\"2\"",
            HAND_OFFS to "\"200\"",
            // An interrupt ends block / blockedGet with a ScriptInterruptedException; blockedGetOrThrow throws its own exception, as before.
            interrupted("b.block()") to "\"ScriptInterruptedException\"",
            interrupted("b.blockedGet(60000)") to "\"ScriptInterruptedException\"",
            interrupted("b.blockedGetOrThrow(java.lang.IllegalStateException)") to "\"IllegalStateException\"",
            // Unchanged: a notification during a bounded wait ends it; a value set without one does not, the wait takes its timeout.
            quick("threads.start(function () { sleep(100); b.setAndNotify('c'); });", "b.blockedGet(3000)") to "\"c:true\"",
            "(function () { $NEW b.set('s'); var t0 = Date.now(); var v = b.blockedGet(100); return v + ':' + (Date.now() - t0 >= 90); })()" to "\"s:true\"",
        )

    }

}
