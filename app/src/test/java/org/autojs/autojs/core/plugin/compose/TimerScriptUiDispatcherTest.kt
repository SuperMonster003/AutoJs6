package org.autojs.autojs.core.plugin.compose

import org.autojs.autojs.core.looper.TimerCallbackRejectedException
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.ScriptDispatchQueueOverflowException
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference

class TimerScriptUiDispatcherTest {
    @Test fun reservedControlsRemainDeliverableWhenTheExplicitQueueIsFull() {
        val posts = ArrayDeque<() -> Unit>()
        val native = ArrayDeque<() -> Unit>()
        val delivered = ArrayList<String>()
        val user = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = { posts.add(it) }, releaseWait = {}, maxQueuedActions = 2)
        val control = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = { native.add(it) }, releaseWait = {}, maxQueuedActions = 1)
        val dispatcher = TimerScriptUiDispatcher(Thread.currentThread(),
            enqueue = { action -> user.dispatchValues(emptyArray()) { action() } },
            cancel = { user.cancelSilently(); control.cancelSilently() },
            enqueueControl = { action -> control.dispatchValues(emptyArray()) { action() } })
        val first = dispatcher.createControlChannel { delivered += "receipt" }
        val second = dispatcher.createControlChannel { delivered += "close" }
        dispatcher.post { delivered += "user1" }; dispatcher.post { delivered += "user2" }
        assertThrows(ScriptDispatchQueueOverflowException::class.java) { dispatcher.post {} }
        repeat(100) { first.request(); second.request() }
        assertEquals(1, native.size)
        native.removeFirst().invoke()
        assertEquals(listOf("receipt", "close"), delivered)
        assertEquals(1, posts.size)
        posts.removeFirst().invoke()
        assertEquals(listOf("receipt", "close", "user1", "user2"), delivered)
        dispatcher.close()
    }

    @Test fun controlReservationsAreBoundedAndClosedSlotsAreImmediatelyReusable() {
        val native = ArrayDeque<() -> Unit>()
        val dispatcher = TimerScriptUiDispatcher(Thread.currentThread(), enqueue = {}, cancel = {},
            enqueueControl = { native.add(it) }, maxControlChannels = 2)
        val permanent = dispatcher.createControlChannel {}
        var temporary = dispatcher.createControlChannel { fail("Closed controls must not run") }
        val error = assertThrows(ComposeUiContractException::class.java) { dispatcher.createControlChannel {} }
        assertEquals(ComposeUiErrorCodes.LIMIT_EXCEEDED, error.code)
        repeat(100) {
            temporary.request(); temporary.close()
            temporary = dispatcher.createControlChannel { fail("Closed controls must not run") }
        }
        temporary.request(); temporary.close()
        assertEquals(1, native.size)
        native.removeFirst().invoke()
        permanent.close()
        val replacement = dispatcher.createControlChannel {}
        replacement.close(); dispatcher.close()
    }

    @Test fun quittingLooperRejectionCancelsOnceEvenWhileItsThreadIsAlive() {
        val origin = Thread.currentThread()
        var schedules = 0
        var cancellations = 0
        var releases = 0
        var calls = 0
        val async = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = {
            schedules++
            throw TimerCallbackRejectedException()
        }, releaseWait = { releases++ })
        val dispatcher = TimerScriptUiDispatcher(origin,
            enqueue = { action -> async.dispatchValues(emptyArray()) { action() } },
            cancel = { cancellations++; async.cancelSilently() })
        assertTrue(origin.isAlive)
        dispatcher.post { calls++ }
        dispatcher.post { calls++ }
        dispatcher.close()
        assertEquals(1, schedules)
        assertEquals(1, cancellations)
        assertEquals(1, releases)
        assertEquals(0, calls)
        assertTrue(async.isTerminal)
    }

    @Test fun queueOverflowStillEscapesAndDoesNotCloseAUsableDispatcher() {
        val queue = ArrayDeque<() -> Unit>()
        var cancellations = 0
        val delivered = ArrayList<Int>()
        val async = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = { queue.add(it) },
            releaseWait = {}, maxQueuedActions = 1)
        val dispatcher = TimerScriptUiDispatcher(Thread.currentThread(),
            enqueue = { action -> async.dispatchValues(emptyArray()) { action() } },
            cancel = { cancellations++; async.cancelSilently() })
        dispatcher.post { delivered += 1 }
        assertThrows(ScriptDispatchQueueOverflowException::class.java) { dispatcher.post { delivered += 2 } }
        assertEquals(0, cancellations)
        assertFalse(async.isTerminal)
        queue.removeFirst().invoke()
        dispatcher.post { delivered += 3 }
        queue.removeFirst().invoke()
        assertEquals(listOf(1, 3), delivered)
        dispatcher.close()
        assertEquals(1, cancellations)
    }

    @Test fun unrelatedIllegalStateAndLinkageFailuresAreNeverMistakenForLooperShutdown() {
        val queue = ArrayDeque<() -> Unit>()
        var cancellations = 0
        var failure: Throwable? = IllegalStateException(TimerCallbackRejectedException().message)
        val dispatcher = TimerScriptUiDispatcher(Thread.currentThread(), enqueue = {
            failure?.let { problem -> throw problem }
            queue.add(it)
        }, cancel = { cancellations++ })
        assertSame(failure, assertThrows(IllegalStateException::class.java) { dispatcher.post {} })
        failure = NoClassDefFoundError("Missing scheduler class")
        assertSame(failure, assertThrows(NoClassDefFoundError::class.java) { dispatcher.post {} })
        assertEquals(0, cancellations)
        failure = null
        var delivered = false
        dispatcher.post { delivered = true }
        queue.removeFirst().invoke()
        assertTrue(delivered)
        dispatcher.close()
        assertEquals(1, cancellations)
    }

    @Test fun closeSuppressesAlreadyQueuedActionsButOwnerCanStillFinishCleanupInline() {
        val origin = Thread.currentThread()
        val queue = ArrayDeque<() -> Unit>()
        val callers = ArrayList<Thread>()
        var cancellations = 0
        val dispatcher = TimerScriptUiDispatcher(origin, enqueue = { queue.add(it) }, cancel = { cancellations++ })
        dispatcher.post { fail("Cancelled callback must not execute") }
        dispatcher.close()
        queue.removeFirst().invoke()
        val failure = AtomicReference<Throwable>()
        val foreign = Thread {
            try { dispatcher.dispatch { callers += Thread.currentThread() } }
            catch (error: Throwable) { failure.set(error) }
        }
        foreign.start(); foreign.join(5000)
        assertFalse(foreign.isAlive)
        failure.get()?.let { throw AssertionError("Foreign dispatch failed", it) }
        assertTrue(callers.isEmpty())
        dispatcher.dispatch { callers += Thread.currentThread() }
        assertEquals(listOf(origin), callers)
        assertEquals(1, cancellations)
    }
}
