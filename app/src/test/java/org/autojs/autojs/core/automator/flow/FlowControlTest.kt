package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.query.PollPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FlowControlTest {

    private fun fn(body: (List<Any?>) -> Any?): Fn = body
    private fun optional(cond: Any?, handler: Fn, timeout: Long = 100) =
        FlowStep.WhenPresent(FlowStep.Wait(cond, PollPolicy(timeout, 20)), handler)

    @Test
    fun absentOptionalTargetPreservesInputButServiceErrorsPropagate() {
        val rig = FlowRig()
        val skipped = rig.scheduler.of("input").next(optional(rig.never, fn { fail("handler must not run") }))
        rig.drain()
        assertEquals("input", skipped.value)
        val error = FlowError.A11yUnavailable("probe")
        val failed = rig.scheduler.start(optional({ throw error }, fn { fail("handler must not run") }))
        rig.drain()
        assertSame(error, failed.error)
    }

    @Test
    fun optionalHandlerReceivesMatchAndItsTimeoutIsNotSwallowed() {
        val rig = FlowRig()
        rig.bindings.screen["dialog"] = "matched"
        val success = rig.scheduler.of("input").next(optional("dialog", fn {
            assertEquals(listOf("matched"), it)
            rig.scheduler.of("ignored")
        }))
        rig.drain()
        assertEquals("input", success.value)
        val error = FlowError.Timeout("handler", null, 50, 1)
        val failed = rig.scheduler.start(optional("dialog", fn { throw error }))
        rig.drain()
        assertSame(error, failed.error)
    }

    @Test
    fun optionalDetectionCannotSwallowTheChainDeadline() {
        val rig = FlowRig()
        val flow = rig.scheduler.of(null).next(FlowStep.Timeout(30)).next(optional(rig.never, fn { null }, 500))
        rig.drain()
        assertEquals("chain", (flow.error as FlowError).reason)
    }

    @Test
    fun repeatChecksFirstThenAwaitsEachAttemptAndReturnsTheConditionValue() {
        val rig = FlowRig()
        val attempts = mutableListOf<Long>()
        val action = fn {
            attempts += it[0] as Long
            if (attempts.size == 2) rig.bindings.screen["done"] = "result"
            rig.scheduler.of("action result")
        }
        val flow = rig.scheduler.start(FlowStep.RepeatUntil(action, "done", 1000, interval = 25))
        rig.drain()
        assertEquals(listOf(1L, 2L), attempts)
        assertEquals("result", flow.value)
        assertEquals(25L, rig.clock.now)
        val already = rig.scheduler.start(FlowStep.RepeatUntil(fn { fail("already done") }, "done", 1000))
        rig.drain()
        assertEquals("result", already.value)
    }

    @Test
    fun attemptLimitAndZeroTimeoutNeverRunAnExtraAction() {
        val rig = FlowRig()
        var calls = 0
        val flow = rig.scheduler.start(FlowStep.RepeatUntil(fn { calls++; null }, rig.never, 1000, 2, 0))
        rig.drain()
        assertEquals(2, calls)
        assertEquals("attempts", (flow.error as FlowError).reason)
        val once = rig.scheduler.start(FlowStep.RepeatUntil(fn { calls++; null }, rig.never, 0))
        rig.drain()
        assertEquals(2, calls)
        assertEquals("timeout", (once.error as FlowError).reason)
    }

    @Test
    fun defaultAndZeroAttemptLimitsAllowMoreThanTenActions() {
        for (explicitZero in listOf(false, true)) {
            val rig = FlowRig()
            var calls = 0
            val action = fn { calls++; null }
            val cond = { if (calls == 12) "ready" else null }
            val step = if (explicitZero) FlowStep.RepeatUntil(action, cond, 1000, maxAttempts = 0, interval = 1)
            else FlowStep.RepeatUntil(action, cond, 1000, interval = 1)
            val flow = rig.scheduler.start(step)
            rig.drain()
            assertEquals(12, calls)
            assertEquals("ready", flow.value)
        }
    }

    @Test
    fun unlimitedAttemptsStillStopAtTheTotalTimeout() {
        val rig = FlowRig()
        var calls = 0
        val flow = rig.scheduler.start(FlowStep.RepeatUntil(fn { calls++; null }, rig.never, 25, maxAttempts = 0, interval = 1))
        rig.drain()
        assertTrue(calls > 10)
        assertEquals(25L, rig.clock.now)
        assertEquals("timeout", (flow.error as FlowError).reason)
        assertEquals(0, rig.scheduler.pendingCount)
    }

    @Test
    fun errorsOnlyRetryWhenExplicitlyAcceptedAndCancellationNeverRetries() {
        val rig = FlowRig()
        val error = IllegalStateException("transient")
        var calls = 0
        val action = fn {
            if (++calls == 1) throw error
            rig.bindings.screen["done"] = true
            null
        }
        val failed = rig.scheduler.start(FlowStep.RepeatUntil(action, "done", 1000))
        rig.drain()
        assertSame(error, failed.error)
        calls = 0
        val retry = fn { it[0] === error }
        val success = rig.scheduler.start(FlowStep.RepeatUntil(action, "done", 1000, retryOn = retry))
        rig.drain()
        assertEquals(true, success.value)
        assertEquals(2, calls)
        val cancelled = rig.scheduler.start(FlowStep.RepeatUntil(fn { throw FlowError.Cancelled("action") }, rig.never, 1000, retryOn = fn { fail("must not retry") }))
        rig.drain()
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun deadlineCancelsAnUnsettledReturnedFlowAndIsInheritedAtCreation() {
        val rig = FlowRig()
        lateinit var child: FlowNode
        val flow = rig.scheduler.of(null).next(FlowStep.Scope(FlowScope(root = "root")))
            .next(FlowStep.RepeatUntil(fn {
                child = rig.scheduler.defer()
                assertEquals("root", child.initialContext.scope.root)
                assertEquals(120L, child.initialContext.deadline)
                child
            }, rig.never, 120))
        rig.drain()
        assertEquals("timeout", (flow.error as FlowError).reason)
        assertEquals(120L, rig.clock.now)
        assertTrue(child.isCancelled)
        assertEquals(0, rig.scheduler.pendingCount)
    }

    @Test
    fun cancellationDuringTheFactoryAlsoCancelsWorkCreatedAfterCancellation() {
        val rig = FlowRig()
        lateinit var flow: FlowNode
        lateinit var child: FlowNode
        flow = rig.scheduler.start(FlowStep.RepeatUntil(fn {
            flow.cancel()
            child = rig.scheduler.defer()
            child
        }, rig.never, 500))
        rig.drain()
        assertTrue(flow.isCancelled)
        assertTrue(child.isCancelled)
        assertEquals(0, rig.scheduler.pendingCount)
    }

    @Test(timeout = 5000)
    fun returnedSubflowCompletesOnItsOwnWorkerWithoutBlockingItsScriptCallback() {
        val worker = ThreadLane("control-test-worker")
        val script = ThreadLane("control-test-script")
        val bindings = FakeBindings()
        val finished = CountDownLatch(1)
        val scheduler = FlowScheduler(worker, script, bindings, { System.nanoTime() / 1_000_000 }, { Thread.sleep(it) }, workerFactory = { ThreadLane("control-test-child") })
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val flow = scheduler.start(FlowStep.RepeatUntil(fn {
            scheduler.start(FlowStep.Sleep(25)).next(FlowStep.Callback(CallbackKind.THEN, fn {
                ready.set(true)
                null
            }))
        }, { if (ready.get()) "done" else null }, 2000))
        flow.onSettled { finished.countDown() }
        assertTrue(finished.await(3, TimeUnit.SECONDS))
        assertEquals("done", flow.value)
        assertEquals(0, scheduler.pendingCount)
    }
}
