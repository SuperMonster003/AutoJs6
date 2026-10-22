package org.autojs.autojs.core.plugin

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * The hand-over of a connected dedicated binding to the coroutine waiting in `awaitDedicatedServiceBinding`
 * (docs/memory/ROADMAP.md appendix C Q57): a binding that does not reach its waiter, because the waiter is cancelled
 * after the resume but before it runs again or was cancelled before the binding arrived, releases its lease; a binding
 * that reaches its waiter stays with the caller, even if the caller is cancelled later (R2).
 * zh-CN: 已连接的专用绑定交给 `awaitDedicatedServiceBinding` 中等待它的协程 (docs/memory/ROADMAP.md 附录 C Q57): 绑定没有送达等待方时
 * (等待方在恢复之后, 再次运行之前被取消, 或在绑定到达之前已被取消) 释放其租约; 送达等待方的绑定归调用方, 即使调用方之后被取消 (R2).
 */
class AidlPluginHostDedicatedBindingTest {

    @Test
    fun aBindingResumedButCancelledBeforeItsWaiterRunsIsReleased() {
        val dispatcher = QueueDispatcher()
        var unbinds = 0
        val lease = registeredLease { unbinds++ }
        val waiter = Waiter(dispatcher)
        dispatcher.runAll()

        AidlPluginHost.deliverDedicatedBinding(waiter.continuation, BINDING, lease)
        // The caller's timeout or the end of the script, before the dispatcher runs the waiter again.
        // zh-CN: 调用方超时或脚本结束, 发生在调度器再次运行等待方之前.
        waiter.job.cancel()
        dispatcher.runAll()

        assertNull(waiter.received)
        assertTrue(waiter.failure is CancellationException)
        assertTrue(lease.isClosed)
        assertEquals(1, unbinds)
    }

    @Test
    fun aDeliveredBindingStaysWithTheCallerEvenIfItIsCancelledLater() {
        val dispatcher = QueueDispatcher()
        var unbinds = 0
        val lease = registeredLease { unbinds++ }
        val waiter = Waiter(dispatcher, keepRunning = true)
        dispatcher.runAll()

        AidlPluginHost.deliverDedicatedBinding(waiter.continuation, BINDING, lease)
        dispatcher.runAll()

        assertEquals(BINDING, waiter.received)
        assertFalse(lease.isClosed)
        assertEquals(0, unbinds)

        waiter.job.cancel()
        dispatcher.runAll()

        assertTrue(waiter.failure is CancellationException)
        assertFalse(lease.isClosed)
        assertEquals(0, unbinds)

        lease.close()

        assertEquals(1, unbinds)
    }

    @Test
    fun aBindingArrivingAfterItsWaiterWasCancelledIsReleasedAtOnce() {
        val dispatcher = QueueDispatcher()
        var unbinds = 0
        val lease = registeredLease { unbinds++ }
        val waiter = Waiter(dispatcher)
        dispatcher.runAll()
        waiter.job.cancel()
        dispatcher.runAll()

        AidlPluginHost.deliverDedicatedBinding(waiter.continuation, BINDING, lease)
        dispatcher.runAll()

        assertNull(waiter.received)
        assertTrue(lease.isClosed)
        assertEquals(1, unbinds)
    }

    @Test
    fun theWaitersOwnCancellationHandlerAndTheHandOverReleaseTheLeaseOnce() {
        val dispatcher = QueueDispatcher()
        var unbinds = 0
        val lease = registeredLease { unbinds++ }
        // awaitDedicatedServiceBinding also closes the lease from invokeOnCancellation.
        // zh-CN: awaitDedicatedServiceBinding 也在 invokeOnCancellation 中关闭租约.
        val waiter = Waiter(dispatcher, onCancellation = { lease.close() })
        dispatcher.runAll()

        AidlPluginHost.deliverDedicatedBinding(waiter.continuation, BINDING, lease)
        waiter.job.cancel()
        dispatcher.runAll()

        assertNull(waiter.received)
        assertTrue(lease.isClosed)
        assertEquals(1, unbinds)
    }

    private fun registeredLease(onUnbind: () -> Unit): ServiceBindingLease =
        ServiceBindingLease(onUnbind).apply { markRegistered() }

    private class Waiter(
        dispatcher: CoroutineDispatcher,
        keepRunning: Boolean = false,
        onCancellation: (() -> Unit)? = null,
    ) {
        lateinit var continuation: CancellableContinuation<String>
        var received: String? = null
        var failure: Throwable? = null

        val job = CoroutineScope(dispatcher).launch {
            try {
                received = suspendCancellableCoroutine { cont ->
                    continuation = cont
                    onCancellation?.let { handler -> cont.invokeOnCancellation { handler() } }
                }
                if (keepRunning) awaitCancellation()
            } catch (e: CancellationException) {
                failure = e
                throw e
            }
        }
    }

    // Runs dispatched work only when the test asks, so a resume can be followed by a cancellation before the waiter runs.
    // zh-CN: 只在测试要求时运行已分发的任务, 使恢复之后, 等待方运行之前可以插入取消.
    private class QueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private companion object {
        const val BINDING = "binding"
    }
}
