package org.autojs.autojs.core.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.AidlPluginHost.LeasedCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The opening calls of plugin sessions that answer after their caller gave up (docs/memory/ROADMAP.md appendix C Q55 / Q59):
 * a result dropped by the timeout, by the cancellation of the caller or by the interrupt of a stopped Rhino script that waits
 * in `runBlocking` is abandoned and its binding lease closed, even if abandoning it fails; a result returned in time stays
 * with the caller. The blocking in these tests stands for the Binder call, which ignores both the coroutine cancellation and
 * the thread interrupt.
 * zh-CN: 插件会话的打开调用在调用方放弃之后才应答 (docs/memory/ROADMAP.md 附录 C Q55 / Q59): 被超时, 调用方取消, 或在 `runBlocking` 中等待的
 * Rhino 脚本被停止时的中断丢弃的结果会被放弃, 其绑定租约随之关闭, 即使放弃失败也是如此; 按时返回的结果归调用方. 这些测试中的阻塞代表 Binder 调用,
 * 它既不响应协程取消, 也不响应线程中断.
 */
class AidlPluginHostAbandoningLateTest {

    @Test
    fun aLeasedCallAnsweringAfterTheTimeoutIsAbandonedAndItsLeaseClosed() {
        val abandoned = CopyOnWriteArrayList<Int>()
        val unbinds = AtomicInteger()
        val lease = registeredLease { unbinds.incrementAndGet() }

        val failure = runCatching {
            runBlocking(Dispatchers.Default) {
                AidlPluginHost.withTimeoutAbandoningLate(TIMEOUT_MS, abandoned::add) {
                    // The wait for the dedicated binding suspends, then the opening call outlasts the deadline.
                    // zh-CN: 等待专用绑定时挂起, 之后打开调用超过时限.
                    delay(1)
                    blockUninterruptibly(LATE_MS)
                    LeasedCall(7, lease)
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertEquals(listOf(7), abandoned)
        assertTrue(lease.isClosed)
        assertEquals(1, unbinds.get())
    }

    @Test
    fun aLeasedCallReturnedInTimeIsNotTouched() {
        val abandoned = CopyOnWriteArrayList<Int>()
        val lease = registeredLease()

        val call = runBlocking(Dispatchers.Default) {
            AidlPluginHost.withTimeoutAbandoningLate(GENEROUS_MS, abandoned::add) {
                delay(1)
                LeasedCall(7, lease)
            }
        }

        assertEquals(7, call.value)
        assertSame(lease, call.bindingLease)
        assertTrue(abandoned.isEmpty())
        assertFalse(lease.isClosed)
    }

    @Test
    fun aLeasedCallAnsweringAfterTheCallerWasCancelledIsAbandonedAndItsLeaseClosed() {
        val abandoned = CopyOnWriteArrayList<Int>()
        val unbinds = AtomicInteger()
        val lease = registeredLease { unbinds.incrementAndGet() }
        val calling = CountDownLatch(1)
        val answer = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        runBlocking {
            val job = launch(Dispatchers.Default) {
                try {
                    AidlPluginHost.withTimeoutAbandoningLate(GENEROUS_MS, abandoned::add) {
                        delay(1)
                        calling.countDown()
                        answer.await()
                        LeasedCall(7, lease)
                    }
                } catch (e: Throwable) {
                    failure.set(e)
                    throw e
                }
            }
            assertTrue(calling.await(WAIT_S, TimeUnit.SECONDS))
            job.cancel()
            answer.countDown()
            job.join()
        }

        assertTrue(failure.get() is CancellationException)
        assertFalse(failure.get() is TimeoutCancellationException)
        assertEquals(listOf(7), abandoned)
        assertTrue(lease.isClosed)
        assertEquals(1, unbinds.get())
    }

    @Test
    fun theLeaseIsClosedAndTheCancellationKeptWhenAbandoningFails() {
        val unbinds = AtomicInteger()
        val lease = registeredLease { unbinds.incrementAndGet() }

        val failure = runCatching {
            runBlocking(Dispatchers.Default) {
                AidlPluginHost.withTimeoutAbandoningLate<Int>(TIMEOUT_MS, { throw IllegalStateException("plugin gone") }) {
                    delay(1)
                    blockUninterruptibly(LATE_MS)
                    LeasedCall(7, lease)
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertTrue(lease.isClosed)
        assertEquals(1, unbinds.get())
    }

    @Test
    fun aResultReturnedInTimeFromAnotherContextIsNotTouched() {
        val abandoned = CopyOnWriteArrayList<Int>()

        val value = runBlocking {
            AidlPluginHost.withContextAbandoningLate(Dispatchers.IO, abandoned::add) { 7 }
        }

        assertEquals(7, value)
        assertTrue(abandoned.isEmpty())
    }

    @Test
    fun aResultAnsweringInAnotherContextAfterTheCallerWasCancelledIsAbandoned() {
        val abandoned = CopyOnWriteArrayList<Int>()
        val calling = CountDownLatch(1)
        val answer = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        runBlocking {
            val job = launch(Dispatchers.Default) {
                try {
                    AidlPluginHost.withContextAbandoningLate(Dispatchers.IO, abandoned::add) {
                        calling.countDown()
                        answer.await()
                        7
                    }
                } catch (e: Throwable) {
                    failure.set(e)
                    throw e
                }
            }
            assertTrue(calling.await(WAIT_S, TimeUnit.SECONDS))
            job.cancel()
            answer.countDown()
            job.join()
        }

        assertTrue(failure.get() is CancellationException)
        assertEquals(listOf(7), abandoned)
    }

    /**
     * Gallery and Yolo wait in `runBlocking(scriptRuntime.coroutineContext)`, which runs on `Dispatchers.Default`. Stopping the
     * script interrupts that wait: `runBlocking` throws [InterruptedException] at once and cancels its coroutine, which goes on
     * running on `Dispatchers.Default`, so the late result still reaches [AidlPluginHost.withContextAbandoningLate].
     * zh-CN: Gallery 与 Yolo 在 `runBlocking(scriptRuntime.coroutineContext)` 中等待, 该协程运行在 `Dispatchers.Default` 上. 停止脚本会中断这一等待:
     * `runBlocking` 立即抛出 [InterruptedException] 并取消其协程, 协程继续在 `Dispatchers.Default` 上运行, 因此迟到的结果仍会到达
     * [AidlPluginHost.withContextAbandoningLate].
     */
    @Test
    fun aResultAnsweringAfterTheScriptThreadWasInterruptedInRunBlockingOnTheDefaultDispatcherIsAbandoned() {
        assertInterruptedWaitAbandonsTheLateResult(Dispatchers.Default + SupervisorJob())
    }

    /**
     * Without a dispatcher of its own, the coroutine of an interrupted `runBlocking` resumes on the default executor once its
     * event loop has stopped, so the late result is abandoned there.
     * zh-CN: 没有自身调度器时, 被中断的 `runBlocking` 的协程在其事件循环停止之后于默认执行器上恢复, 迟到的结果在那里被放弃.
     */
    @Test
    fun aResultAnsweringAfterTheScriptThreadWasInterruptedInAPlainRunBlockingIsAbandoned() {
        assertInterruptedWaitAbandonsTheLateResult(null)
    }

    /**
     * The shape of the AutoJs4 runtime admission: `runBlocking { withTimeout { ... } }` with the opening call on the script
     * thread itself. The timeout of a plain `runBlocking` still fires while that thread is blocked in the call.
     * zh-CN: AutoJs4 运行时受理调用的形态: `runBlocking { withTimeout { ... } }`, 打开调用在脚本线程上执行. 普通 `runBlocking` 的超时在该线程阻塞于调用期间
     * 仍会触发.
     */
    @Test
    fun aLeasedCallBlockingTheThreadOfAPlainRunBlockingPastTheTimeoutIsAbandoned() {
        val abandoned = CopyOnWriteArrayList<Int>()
        val lease = registeredLease()

        val failure = runCatching {
            runBlocking {
                AidlPluginHost.withTimeoutAbandoningLate(TIMEOUT_MS, abandoned::add) {
                    delay(1)
                    blockUninterruptibly(LATE_MS)
                    LeasedCall(7, lease)
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertEquals(listOf(7), abandoned)
        assertTrue(lease.isClosed)
    }

    /**
     * An interrupt that lands while the opening call blocks the thread of a plain `runBlocking` does not drop the result: the
     * coroutine completes before `runBlocking` looks at the interrupt, so the call belongs to the caller and the interrupt stays
     * pending for it.
     * zh-CN: 打开调用阻塞普通 `runBlocking` 所在线程期间到达的中断不会丢弃结果: 协程在 `runBlocking` 检查中断之前已完成, 因此调用归调用方,
     * 中断留给调用方处理.
     */
    @Test
    fun anInterruptDuringACallOnTheThreadOfAPlainRunBlockingLeavesTheCallWithTheCaller() {
        val abandoned = CopyOnWriteArrayList<Int>()
        val lease = registeredLease()
        val calling = CountDownLatch(1)
        val outcome = AtomicReference<Any?>()
        val interruptKept = AtomicReference<Boolean?>()

        val script = Thread {
            outcome.set(
                runCatching {
                    runBlocking {
                        AidlPluginHost.withTimeoutAbandoningLate(GENEROUS_MS, abandoned::add) {
                            delay(1)
                            calling.countDown()
                            blockUninterruptibly(LATE_MS)
                            LeasedCall(7, lease)
                        }
                    }
                }.fold({ it }, { it }),
            )
            interruptKept.set(Thread.interrupted())
        }
        script.start()
        assertTrue(calling.await(WAIT_S, TimeUnit.SECONDS))
        script.interrupt()
        script.join(TimeUnit.SECONDS.toMillis(WAIT_S))

        val call = outcome.get()
        assertTrue(call is LeasedCall<*>)
        assertEquals(7, (call as LeasedCall<*>).value)
        assertEquals(true, interruptKept.get())
        assertTrue(abandoned.isEmpty())
        assertFalse(lease.isClosed)
    }

    private fun assertInterruptedWaitAbandonsTheLateResult(context: kotlin.coroutines.CoroutineContext?) {
        val abandoned = CopyOnWriteArrayList<Int>()
        val calling = CountDownLatch(1)
        val answer = CountDownLatch(1)
        val released = CountDownLatch(1)
        val waitFailure = AtomicReference<Throwable?>()
        val block: suspend kotlinx.coroutines.CoroutineScope.() -> Int = {
            AidlPluginHost.withContextAbandoningLate(Dispatchers.IO, { value: Int -> abandoned.add(value); released.countDown() }) {
                calling.countDown()
                answer.await()
                7
            }
        }

        val script = Thread {
            waitFailure.set(
                runCatching { if (context == null) runBlocking(block = block) else runBlocking(context, block) }.exceptionOrNull(),
            )
        }
        script.start()
        assertTrue(calling.await(WAIT_S, TimeUnit.SECONDS))
        script.interrupt()
        script.join(TimeUnit.SECONDS.toMillis(WAIT_S))
        assertFalse(script.isAlive)
        assertTrue(waitFailure.get() is InterruptedException)
        assertNull(abandoned.firstOrNull())

        answer.countDown()

        assertTrue(released.await(WAIT_S, TimeUnit.SECONDS))
        assertEquals(listOf(7), abandoned)
    }

    // Sleeps through interrupts like a Binder call and keeps the interrupt for the caller.
    // zh-CN: 像 Binder 调用一样不响应中断地休眠, 并为调用方保留中断状态.
    private fun blockUninterruptibly(millis: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        var interrupted = false
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) break
            try {
                TimeUnit.NANOSECONDS.sleep(remaining)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private companion object {
        const val TIMEOUT_MS = 100L
        const val LATE_MS = 400L
        const val GENEROUS_MS = 10_000L
        const val WAIT_S = 10L
    }
}
