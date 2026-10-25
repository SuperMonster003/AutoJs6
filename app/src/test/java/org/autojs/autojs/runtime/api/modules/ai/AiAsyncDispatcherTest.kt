package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiAsyncDispatcherTest {

    @Test fun blockingJoinCanDrainOnlyItsOriginatingLane() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val released = AtomicInteger()
        val dispatcher = AiAsyncDispatcher({ it }, scheduled::addLast, { released.incrementAndGet() })
        dispatcher.dispatchValues(arrayOf("input")) { delivered += it[0] as String }
        val wrong = AtomicReference<Throwable>()
        Thread { try { dispatcher.drainOnOriginatingThread() } catch (e: Throwable) { wrong.set(e) } }.apply { start(); join() }
        assertTrue(wrong.get() is IllegalStateException); assertTrue(delivered.isEmpty())
        dispatcher.drainOnOriginatingThread()
        dispatcher.terminalValues(arrayOf("done")) { delivered += it[0] as String }
        dispatcher.drainOnOriginatingThread()
        while (scheduled.isNotEmpty()) scheduled.removeFirst().invoke()
        assertEquals(listOf("input", "done"), delivered); assertEquals(1, released.get())
    }

    @Test
    fun valuesAreConvertedAndDeliveredOnlyByCapturedScheduler() {
        val scheduled = ArrayDeque<() -> Unit>()
        val mapperThread = AtomicReference<String>()
        val actionThread = AtomicReference<String>()
        val releaseThread = AtomicReference<String>()
        val delivered = AtomicReference<Array<Any?>>()
        val releaseCount = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { value ->
                mapperThread.set(Thread.currentThread().name)
                "mapped:$value"
            },
            scheduler = scheduled::addLast,
            releaseWait = {
                releaseCount.incrementAndGet()
                releaseThread.set(Thread.currentThread().name)
            },
        )

        assertTrue(dispatcher.dispatchValues(arrayOf("delta")) { values ->
            actionThread.set(Thread.currentThread().name)
            delivered.set(values)
        })
        assertTrue(dispatcher.terminalValues(arrayOf("done")) { values ->
            actionThread.set(Thread.currentThread().name)
            delivered.set(values)
        })

        assertEquals(1, scheduled.size)
        assertEquals(null, mapperThread.get())
        assertEquals(0, releaseCount.get())

        Thread(scheduled.removeFirst(), "captured-js-thread").apply {
            start()
            join()
        }

        assertEquals("captured-js-thread", mapperThread.get())
        assertEquals("captured-js-thread", actionThread.get())
        assertEquals("captured-js-thread", releaseThread.get())
        assertEquals("mapped:done", delivered.get()[0])
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun onlyOneTerminalActionIsAcceptedAndLaterValuesAreRejected() {
        val scheduled = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val releaseCount = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )

        assertTrue(dispatcher.dispatchValues(arrayOf("delta")) { events += it[0] as String })
        assertTrue(dispatcher.terminalValues(arrayOf("done")) { events += it[0] as String })
        assertFalse(dispatcher.terminalValues(arrayOf("error")) { events += it[0] as String })
        assertFalse(dispatcher.dispatchValues(arrayOf("late")) { events += it[0] as String })
        assertTrue(dispatcher.isTerminal)

        scheduled.removeFirst().invoke()

        assertEquals(listOf("delta", "done"), events)
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun queuedTerminalStillReleasesWaitAfterEarlierListenerFailure() {
        val scheduled = ArrayDeque<() -> Unit>()
        val releaseCount = AtomicInteger()
        val terminalDelivered = AtomicInteger()
        val expected = IllegalStateException("listener failed")
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )

        dispatcher.dispatchValues(emptyArray()) { throw expected }
        dispatcher.terminalValues { terminalDelivered.incrementAndGet() }

        try {
            scheduled.removeFirst().invoke()
            fail("Expected the first queued failure to be rethrown")
        } catch (actual: Throwable) {
            assertSame(expected, actual)
        }

        assertEquals(1, terminalDelivered.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun promiseResolutionIsMappedAndTerminal() {
        val scheduled = ArrayDeque<() -> Unit>()
        val releaseCount = AtomicInteger()
        val resolved = AtomicReference<Any?>()
        val promise = ScriptPromiseAdapter().onResolve(object : ScriptPromiseAdapter.Callback {
            override fun call(arg: Any?) {
                resolved.set(arg)
            }
        })
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { value -> "js:$value" },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )

        assertTrue(dispatcher.resolve(promise, "answer"))
        assertFalse(dispatcher.reject(promise, "late error"))
        assertEquals(null, resolved.get())

        scheduled.removeFirst().invoke()

        assertEquals("js:answer", resolved.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun terminalSchedulingFailureStillReleasesWaitExactlyOnce() {
        val releaseCount = AtomicInteger()
        val expected = IllegalStateException("scheduler unavailable")
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = { throw expected },
            releaseWait = { releaseCount.incrementAndGet() },
        )

        try {
            dispatcher.terminalValues()
            fail("Expected scheduler failure")
        } catch (actual: Throwable) {
            assertSame(expected, actual)
        }

        assertTrue(dispatcher.isTerminal)
        assertFalse(dispatcher.cancelSilently())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun promiseSchedulingFailureStillInvokesLifecycleCleanup() {
        val cleanupCount = AtomicInteger()
        val expected = IllegalStateException("scheduler unavailable")
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = { throw expected },
            releaseWait = {},
        )

        try {
            dispatcher.resolve(
                ScriptPromiseAdapter(),
                "answer",
                { cleanupCount.incrementAndGet() },
            )
            fail("Expected scheduler failure")
        } catch (actual: Throwable) {
            assertSame(expected, actual)
        }

        assertEquals(1, cleanupCount.get())
    }

    @Test
    fun terminalQueuedDuringNonTerminalSchedulingFailureStillReleasesWait() {
        val releaseCount = AtomicInteger()
        val expected = IllegalStateException("scheduler unavailable")
        lateinit var dispatcher: AiAsyncDispatcher
        dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = {
                assertTrue(dispatcher.terminalValues())
                throw expected
            },
            releaseWait = { releaseCount.incrementAndGet() },
        )

        try {
            dispatcher.dispatchValues(emptyArray()) {}
            fail("Expected scheduler failure")
        } catch (actual: Throwable) {
            assertSame(expected, actual)
        }

        assertTrue(dispatcher.isTerminal)
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun terminalDrainsPromiseMicrotasksBeforeReleasingLooperWait() {
        val order = mutableListOf<String>()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = { it() },
            releaseWait = { order += "release" },
            drainMicrotasks = { order += "microtasks" },
        )

        dispatcher.terminalValues {
            order += "resolve"
        }

        assertEquals(listOf("resolve", "microtasks", "release"), order)
    }

    @Test
    fun silentCancellationPurgesQueuedEventsAndTerminalDelivery() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val releaseCount = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )
        dispatcher.dispatchValues(arrayOf("delta")) { delivered += it[0] as String }
        dispatcher.terminalValues(arrayOf("done")) { delivered += it[0] as String }

        assertTrue(dispatcher.cancelSilently())
        scheduled.removeFirst().invoke()

        assertTrue(delivered.isEmpty())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun silentCancellationCannotSuppressTerminalCleanupAlreadyClaimedByDrain() {
        val mappingStarted = CountDownLatch(1)
        val finishMapping = CountDownLatch(1)
        val released = CountDownLatch(1)
        val terminalDelivered = AtomicInteger()
        val releaseCount = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = {
                mappingStarted.countDown()
                assertTrue(finishMapping.await(5L, TimeUnit.SECONDS))
                it
            },
            scheduler = { drain ->
                Thread(drain, "captured-js-thread").start()
            },
            releaseWait = {
                releaseCount.incrementAndGet()
                released.countDown()
            },
        )

        assertTrue(
            dispatcher.terminalValues(arrayOf("done")) {
                terminalDelivered.incrementAndGet()
            },
        )
        assertTrue(mappingStarted.await(5L, TimeUnit.SECONDS))
        assertFalse(dispatcher.cancelSilently())
        finishMapping.countDown()

        assertTrue(released.await(5L, TimeUnit.SECONDS))
        assertEquals(1, terminalDelivered.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun visibleTerminalReplacementPurgesQueuedNonTerminalActions() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val releaseCount = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )
        dispatcher.dispatchValues(arrayOf("first")) { delivered += it[0] as String }
        dispatcher.dispatchValues(arrayOf("second")) { delivered += it[0] as String }

        assertTrue(
            dispatcher.replaceQueuedWithTerminalValues(arrayOf("cancelled")) {
                delivered += it[0] as String
            },
        )
        assertFalse(dispatcher.dispatchValues(arrayOf("late")) { delivered += it[0] as String })
        scheduled.removeFirst().invoke()

        assertEquals(listOf("cancelled"), delivered)
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun visibleTerminalReplacementCannotRevokeRunningActionButPurgesRemainingQueue() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val releaseCount = AtomicInteger()
        val actionStarted = CountDownLatch(1)
        val finishAction = CountDownLatch(1)
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )
        dispatcher.dispatchValues(arrayOf("running")) {
            delivered += it[0] as String
            actionStarted.countDown()
            assertTrue(finishAction.await(5L, TimeUnit.SECONDS))
        }
        dispatcher.dispatchValues(arrayOf("queued")) { delivered += it[0] as String }

        val drainThread = Thread(scheduled.removeFirst(), "captured-js-thread")
        drainThread.start()
        assertTrue(actionStarted.await(5L, TimeUnit.SECONDS))
        assertTrue(
            dispatcher.replaceQueuedWithTerminalValues(arrayOf("cancelled")) {
                delivered += it[0] as String
            },
        )
        finishAction.countDown()
        drainThread.join(5_000L)

        assertFalse(drainThread.isAlive)
        assertEquals(listOf("running", "cancelled"), delivered)
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun visibleTerminalReplacementSchedulingFailureStillReleasesWaitExactlyOnce() {
        val releaseCount = AtomicInteger()
        val expected = IllegalStateException("scheduler unavailable")
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = { throw expected },
            releaseWait = { releaseCount.incrementAndGet() },
        )

        try {
            dispatcher.replaceQueuedWithTerminalValues()
            fail("Expected scheduler failure")
        } catch (actual: Throwable) {
            assertSame(expected, actual)
        }

        assertTrue(dispatcher.isTerminal)
        assertFalse(dispatcher.cancelSilently())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun callbackQueueRejectsUnboundedProducerButStillAcceptsTerminalError() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val releaseCount = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
            maxQueuedActions = 2,
            maxQueuedBytes = 256,
        )
        dispatcher.dispatchValues(arrayOf("first")) { delivered += it[0] as String }
        dispatcher.dispatchValues(arrayOf("second")) { delivered += it[0] as String }

        assertThrows(AiDispatchQueueOverflowException::class.java) {
            dispatcher.dispatchValues(arrayOf("overflow")) {}
        }
        assertTrue(dispatcher.terminalValues(arrayOf("error")) {
            delivered += it[0] as String
        })

        scheduled.removeFirst().invoke()

        assertEquals(listOf("first", "second", "error"), delivered)
        assertEquals(1, releaseCount.get())
    }

}
