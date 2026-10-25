package org.autojs.autojs.runtime.api.modules.threads

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * The object of `threads.disposable()` (appendix C Q52 of docs/augment/ROADMAP.md): a wait returns once a value is set,
 * whether it was set before the wait began or during it, a wakeup without a value does not end an unbounded wait, and an
 * interrupt ends the wait of `blockedGet` with a ScriptInterruptedException. Waits run on worker threads; a worker is known
 * to be waiting when the lock's condition has a waiter (not after a guessed delay), and a wait that never returns fails the
 * case after five seconds instead of hanging it. The cases with the engine and its threads run on the device
 * (`VolatileDisposeTest.json`).
 *
 * zh-CN: threads.disposable() 返回的对象 (docs/augment/ROADMAP.md 附录 C Q52): 值在等待前或等待中设置, 等待都会返回; 没有值的唤醒不结束不限时的等待;
 * 中断以 ScriptInterruptedException 结束 blockedGet 的等待. 等待在工作线程上进行; 以锁的条件上有等待者判定工作线程已在等待 (而不是猜一个延迟),
 * 永不返回的等待在 5 秒后使用例失败而不是卡住. 涉及引擎与其线程的用例在真机运行 (VolatileDisposeTest.json).
 */
class VolatileDisposeNativeObjectTest {

    private lateinit var scope: VarScope
    private val workers = mutableListOf<Thread>()

    @Before
    fun enterContext() {
        scope = Context.enter().initStandardObjects()
    }

    @After
    fun exitContext() {
        // A wait left blocked by a failed case ends here.
        workers.forEach { it.interrupt() }
        workers.forEach { it.join(5_000) }
        Context.exit()
    }

    // Defect (a): an unbounded wait that begins after the value was set returns it.
    @Test(timeout = 20_000)
    fun unboundedWaitAfterSetReturnsTheValue() {
        val d = newDisposable()
        setAndNotify(d, "a")
        assertEquals("a", result(onWorker { blockedGet(d) }))
    }

    // Defect (b): a wakeup without a value (a spurious one, simulated by signalling the condition) keeps waiting.
    @Test(timeout = 20_000)
    fun unboundedWaitIgnoresWakeupWithoutValue() {
        val d = newDisposable()
        val waiting = onWorker { blockedGet(d) }
        awaitWaiter(d)
        withLock(d) { condition(d).signalAll() }
        // Either the wait ended (the defect) or it waits again.
        while (!waiting.isDone && !hasWaiter(d)) Thread.yield()
        setAndNotify(d, "b")
        assertEquals("b", result(waiting))
    }

    // Defect (c): a bounded wait returns once notified instead of waiting out its timeout.
    @Test(timeout = 20_000)
    fun boundedWaitReturnsWhenNotified() {
        val d = newDisposable()
        val waiting = onWorker { blockedGet(d, 60_000) }
        awaitWaiter(d)
        setAndNotify(d, "c")
        assertEquals("c", result(waiting))
    }

    @Test(timeout = 20_000)
    fun boundedWaitAfterSetReturnsAtOnce() {
        val d = newDisposable()
        setAndNotify(d, "c2")
        assertEquals("c2", result(onWorker { blockedGet(d, 60_000) }))
    }

    @Test(timeout = 20_000)
    fun blockedGetOrThrowReturnsWhenNotified() {
        val d = newDisposable()
        val waiting = onWorker { blockedGetOrThrow(d, IllegalStateException::class.java, 60_000, "default") }
        awaitWaiter(d)
        setAndNotify(d, "c3")
        assertEquals("c3", result(waiting))
    }

    // Defect (d): an interrupt ends the wait with a ScriptInterruptedException, bounded or not.
    @Test(timeout = 20_000)
    fun interruptedUnboundedWaitThrowsScriptInterruptedException() {
        assertInterrupted(ScriptInterruptedException::class.java) { d -> blockedGet(d) }
    }

    @Test(timeout = 20_000)
    fun interruptedBoundedWaitThrowsScriptInterruptedException() {
        assertInterrupted(ScriptInterruptedException::class.java) { d -> blockedGet(d, 60_000) }
    }

    // blockedGetOrThrow throws its own exception when interrupted, as before.
    @Test(timeout = 20_000)
    fun interruptedBlockedGetOrThrowThrowsTheGivenException() {
        assertInterrupted(IllegalStateException::class.java) { d -> blockedGetOrThrow(d, IllegalStateException::class.java) }
    }

    // Unchanged: a bounded wait without a value takes its whole timeout and gives null, or the default of blockedGetOrThrow.
    @Test(timeout = 20_000)
    fun boundedWaitWithoutValueTimesOut() {
        val d = newDisposable()
        val start = System.nanoTime()
        assertNull(blockedGet(d, 50))
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(50))
        assertEquals("default", blockedGetOrThrow(d, IllegalStateException::class.java, 50, "default"))
    }

    // Unchanged: a null value also ends the waits (the default of blockedGetOrThrow stands for it), every waiter wakes, and
    // the last value set is the one returned.
    @Test(timeout = 20_000)
    fun nullValueEndsTheWait() {
        val d = newDisposable()
        val waiting = onWorker { blockedGet(d) }
        awaitWaiter(d)
        setAndNotify(d, null)
        assertNull(result(waiting))
        assertEquals("default", blockedGetOrThrow(d, IllegalStateException::class.java, 60_000, "default"))
    }

    @Test(timeout = 20_000)
    fun everyWaiterWakes() {
        val d = newDisposable()
        val first = onWorker { blockedGet(d) }
        val second = onWorker { blockedGet(d, 60_000) }
        while (lock(d).let { it.lock(); try { it.getWaitQueueLength(condition(d)) } finally { it.unlock() } } < 2) Thread.yield()
        setAndNotify(d, "both")
        assertEquals("both", result(first))
        assertEquals("both", result(second))
    }

    @Test(timeout = 20_000)
    fun lastValueSetIsReturned() {
        val d = newDisposable()
        setAndNotify(d, 1)
        setAndNotify(d, "last")
        assertEquals("last", result(onWorker { blockedGet(d) }))
    }

    @Test
    fun deviceCases() {
        DEVICE.add(DEVICE_CASES)
    }

    private fun newDisposable() = VolatileDisposeNativeObject(scope)

    // The functions as scripts call them (arguments are unwrapped, so numbers come back as doubles).
    private fun blockedGet(d: VolatileDisposeNativeObject, vararg args: Any?) = withContext { cx ->
        VolatileDisposeNativeObject.blockedGet(cx, d, arrayOf(*args), FUNCTION)
    }

    private fun blockedGetOrThrow(d: VolatileDisposeNativeObject, vararg args: Any?) = withContext { cx ->
        VolatileDisposeNativeObject.blockedGetOrThrow(cx, d, arrayOf(*args), FUNCTION)
    }

    private fun setAndNotify(d: VolatileDisposeNativeObject, value: Any?) = withContext { cx ->
        VolatileDisposeNativeObject.setAndNotify(cx, d, arrayOf(value), FUNCTION)
    }

    private fun <T> withContext(block: (Context) -> T): T {
        val cx = Context.enter()
        return try {
            block(cx)
        } finally {
            Context.exit()
        }
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

    private fun assertInterrupted(expected: Class<out Throwable>, wait: (VolatileDisposeNativeObject) -> Any?) {
        val d = newDisposable()
        val waiting = onWorker { wait(d) }
        awaitWaiter(d)
        workers.last().interrupt()
        val thrown = try {
            future(waiting)
            fail("The interrupted wait returned")
        } catch (e: ExecutionException) {
            e.cause
        }
        assertTrue("Expected ${expected.simpleName}, got $thrown", expected.isInstance(thrown))
    }

    private fun future(future: CompletableFuture<Any?>) = try {
        future.get(5, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        fail("The interrupted wait did not end within 5 seconds")
    }

    /** Blocks until a thread waits on the condition of [d]. */
    private fun awaitWaiter(d: VolatileDisposeNativeObject) {
        while (!hasWaiter(d)) Thread.yield()
    }

    private fun hasWaiter(d: VolatileDisposeNativeObject) = withLock(d) { lock(d).hasWaiters(condition(d)) }

    private fun <T> withLock(d: VolatileDisposeNativeObject, block: () -> T): T {
        val lock = lock(d)
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun lock(d: VolatileDisposeNativeObject) = field(d, "lock") as ReentrantLock

    private fun condition(d: VolatileDisposeNativeObject) = field(d, "ready") as Condition

    private fun field(d: VolatileDisposeNativeObject, name: String): Any =
        VolatileDisposeNativeObject::class.java.getDeclaredField(name).apply { isAccessible = true }.get(d)

    companion object {

        private val FUNCTION = BaseFunction()

        private val DEVICE = DeviceCases("VolatileDisposeTest", "threads.disposable() (docs/augment/ROADMAP.md Q52)")

        private const val CONCURRENT = "java.util.concurrent"

        /** Runs [body] (which sets `out`) on a `threads.start` thread after [setup] and returns `out`, or "timeout" after 5 seconds. */
        private fun onWorker(setup: String, body: String) =
            "(function () { $setup var latch = new $CONCURRENT.CountDownLatch(1); var out = 'timeout'; " +
                "threads.start(function () { try { $body } catch (e) { out = 'failed ' + e; } latch.countDown(); }); " +
                "latch.await(5, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        /** Interrupts a `threads.start` thread waiting with [wait] on `d` and returns the simple name of what it threw, "returned" or "timeout". */
        private fun interrupted(wait: String) =
            "(function () { var d = threads.disposable(), started = new $CONCURRENT.CountDownLatch(1), done = new $CONCURRENT.CountDownLatch(1), out = 'timeout'; " +
                "var t = threads.start(function () { started.countDown(); " +
                "try { $wait; out = 'returned'; } catch (e) { out = e.javaException ? String(e.javaException.getClass().getSimpleName()) : String(e); } done.countDown(); }); " +
                "started.await(5, $CONCURRENT.TimeUnit.SECONDS); t.interrupt(); done.await(5, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        /** Returns the value of [wait] on `d` and whether it took less than 2 seconds (the timeouts are 3 seconds). */
        private fun quick(setup: String, wait: String) =
            "(function () { var d = threads.disposable(); $setup var t0 = Date.now(); var v = $wait; return v + ':' + (Date.now() - t0 < 2000); })()"

        private val DEVICE_CASES = listOf(
            // (a) Set before an unbounded wait.
            onWorker("var d = threads.disposable(); d.setAndNotify('a');", "out = d.blockedGet();") to "\"a\"",
            // (c) Set before a bounded wait, or notified during it: returns at once.
            quick("d.setAndNotify('b');", "d.blockedGet(3000)") to "\"b:true\"",
            quick("threads.start(function () { sleep(100); d.setAndNotify('c'); });", "d.blockedGet(3000)") to "\"c:true\"",
            quick("threads.start(function () { sleep(100); d.setAndNotify('e'); });", "d.blockedGetOrThrow(java.lang.IllegalStateException, 3000, 'default')") to "\"e:true\"",
            // Unchanged: a bounded wait without a value takes its timeout, then gives null or the default.
            "(function () { var d = threads.disposable(); var t0 = Date.now(); var v = d.blockedGet(100); return String(v) + ':' + (Date.now() - t0 >= 90); })()" to "\"null:true\"",
            "threads.disposable().blockedGetOrThrow(java.lang.IllegalStateException, 50, 'default')" to "\"default\"",
            // Unchanged: a null value ends an unbounded wait.
            onWorker("var d = threads.disposable(); threads.start(function () { sleep(100); d.setAndNotify(null); });", "out = String(d.blockedGet());") to "\"null\"",
            // (d) An interrupt ends blockedGet with a ScriptInterruptedException; blockedGetOrThrow throws its own exception, as before.
            interrupted("d.blockedGet()") to "\"ScriptInterruptedException\"",
            interrupted("d.blockedGet(60000)") to "\"ScriptInterruptedException\"",
            interrupted("d.blockedGetOrThrow(java.lang.IllegalStateException)") to "\"IllegalStateException\"",
        )

    }

}
