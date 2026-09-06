package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowNode.State
import org.autojs.autojs.core.automator.flow.FlowStep.Action
import org.autojs.autojs.core.automator.flow.FlowStep.Callback
import org.autojs.autojs.core.automator.flow.FlowStep.Combinator
import org.autojs.autojs.core.automator.flow.FlowStep.GlobalAction
import org.autojs.autojs.core.automator.flow.FlowStep.Retry
import org.autojs.autojs.core.automator.flow.FlowStep.Scope
import org.autojs.autojs.core.automator.flow.FlowStep.Sleep
import org.autojs.autojs.core.automator.flow.FlowStep.Timeout
import org.autojs.autojs.core.automator.flow.FlowStep.Wait
import org.autojs.autojs.core.automator.flow.FlowStep.WaitStable
import org.autojs.autojs.core.automator.query.PollPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The value rules of roadmap Appendix A.4, the timing of the waits, cancellation, whole-chain
 * timeout, retry and the combinators, on faked lanes and a manual clock; the last test uses real
 * threads for `await` and the interrupt of a cancellation.
 * zh-CN: Roadmap 附录 A.4 的取值规则, 等待的计时, 取消, 全链超时, 重试与组合器, 基于替身 lane 与手动时钟; 最后一个测试用真实线程
 * 验证 `await` 与取消的中断.
 */
class FlowSchedulerTest {

    private val rig = FlowRig()
    private val s = rig.scheduler
    private val b = rig.bindings

    private fun fn(block: (List<Any?>) -> Any?): Fn = block

    private fun policy(timeout: Long, interval: Long = 100) = PollPolicy(timeout = timeout, interval = interval)

    private fun error(node: FlowNode): FlowError = node.error as FlowError

    // Values and lanes. zh-CN: 取值与 lane.

    @Test
    fun valuesFlowThroughCallbacksOnTheScriptLane() {
        val end = s.of(1)
            .next(Callback(CallbackKind.MAP, fn { (it[0] as Int) + 1 }))
            .next(Callback(CallbackKind.THEN, fn { (it[0] as Int) * 10 }))
        assertTrue(end.state is State.Pending)
        assertEquals(0, rig.worker.size)
        rig.drain()
        assertEquals(20, end.value)
        assertEquals(2, rig.script.executed)
        assertEquals(0, rig.worker.executed)
    }

    @Test
    fun waitResolvesWhatItFindsAndCountsAttempts() {
        val target = FakeUiNode.root("t")
        val node = s.start(Wait(rig.appearing(3, target), policy(1000)))
        assertTrue(node.state is State.Pending)
        rig.drain()
        assertSame(target, node.value)
        assertEquals(3, b.probes)
        assertEquals(listOf(100L, 100L), rig.sleeps)
        assertEquals(200, rig.clock.now)
        assertEquals(1, rig.worker.executed)
    }

    @Test
    fun waitTimesOutWithSelectorElapsedAndAttempts() {
        val node = s.start(Wait("login", policy(300)))
        rig.drain()
        val e = error(node) as FlowError.Timeout
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals("wait", e.step)
        assertEquals("text(\"login\")", e.selector)
        // Attempts at 0, 100, 200, 300 and 400 ms: the timeout is measured after an attempt and must be exceeded.
        assertEquals(400, e.elapsed)
        assertEquals(5, e.attempts)
        assertNull(e.reason)
    }

    @Test
    fun waitWhileResolvesTrueOnceTheProbeFindsNothing() {
        var calls = 0
        val node = s.start(Wait({ if (++calls < 3) "x" else null }, policy(1000), negate = true))
        rig.drain()
        assertEquals(true, node.value)
        assertEquals(3, calls)
    }

    @Test
    fun withinMakesTheFoundNodeTheRootOfLaterWaits() {
        val container = FakeUiNode.root("container")
        val item = FakeUiNode.root("item")
        val w1 = s.start(Wait({ container }, policy(1000), within = true))
        val w2 = w1.next(Wait({ item }, policy(1000)))
        rig.drain()
        assertSame(item, w2.value)
        assertNull(b.probeLog.first().second.root)
        assertSame(container, b.probeLog.last().second.root)
        assertSame(container, w1.context.scope.root)
    }

    @Test
    fun scopeOverridesOnlyTheGivenParts() {
        val node = s.of(1)
            .next(Scope(FlowScope(root = "r", compass = "p")))
            .next(Scope(FlowScope(compass = "c2")))
        rig.drain()
        assertEquals(1, node.value)
        assertEquals(FlowScope(root = "r", compass = "c2"), node.context.scope)
    }

    @Test
    fun actionsResolveTheTargetOrFailByTheStrictRule() {
        val ok = FakeUiNode.root("ok")
        val failing = FakeUiNode.root("failing") { actionResult = false }
        val clicked = s.of(ok).next(Action("click"))
        val strict = s.of(failing).next(Action("click"))
        val lenient = s.of(failing).next(Action("click", strict = false))
        val invalid = s.of("not a node").next(Action("click"))
        rig.drain()
        assertSame(ok, clicked.value)
        assertEquals(listOf(0), ok.performedActions)
        assertEquals(FlowError.Code.ACTION_FAILED, error(strict).code)
        assertEquals("click", error(strict).step)
        assertEquals(false, lenient.value)
        assertEquals(FlowError.Code.INVALID_TARGET, error(invalid).code)
        assertEquals(4, rig.worker.executed)
    }

    @Test
    fun globalActionsPassTheValueThroughAndFailOnlyWhenStrict() {
        val back = s.of("v").next(GlobalAction("back"))
        rig.drain()
        assertEquals("v", back.value)
        b.globalResult = false
        val strict = s.of("v").next(GlobalAction("home"))
        val lenient = s.of("v").next(GlobalAction("home", strict = false))
        rig.drain()
        assertEquals(FlowError.Code.ACTION_FAILED, error(strict).code)
        assertEquals("v", lenient.value)
        assertEquals(listOf("back", "home", "home"), b.globalActions)
    }

    @Test
    fun sleepPicksFromTheRangeAndPassesThrough() {
        val node = s.of("v").next(Sleep(100, 300))
        rig.drain()
        assertEquals("v", node.value)
        assertEquals(listOf(100L), rig.sleeps)
        val halfway = FlowRig(random = 0.5)
        halfway.scheduler.of("v").next(Sleep(100, 300))
        halfway.drain()
        assertEquals(listOf(200L), halfway.sleeps)
    }

    // Rejections and callbacks. zh-CN: 拒绝与回调.

    @Test
    fun aRejectionSkipsToTheCatchAndTheChainResumesAfterIt() {
        val end = s.start(Wait(rig.never, policy(100)))
            .next(Action("click"))
            .next(Callback(CallbackKind.THEN, fn { "not reached" }))
            .next(Callback(CallbackKind.CATCH, onErr = fn { "recovered from " + (it[0] as FlowError).code }))
            .next(Callback(CallbackKind.THEN, fn { it[0].toString() + "!" }))
        rig.drain()
        assertEquals("recovered from TIMEOUT!", end.value)
        assertEquals(2, b.invoked.size)
        assertEquals(1, rig.worker.executed)
    }

    @Test
    fun finallyRunsOnEitherOutcomeAndRestoresIt() {
        val calls = ArrayList<String>()
        val fulfilled = s.of(7).next(Callback(CallbackKind.FINALLY, fn { calls += "ok"; "ignored" }))
        val rejected = s.start(Wait(rig.never, PollPolicy.ONCE)).next(Callback(CallbackKind.FINALLY, fn { calls += "err"; "ignored" }))
        val throwing = s.of(7).next(Callback(CallbackKind.FINALLY, fn { throw IllegalStateException("boom") }))
        rig.drain()
        assertEquals(7, fulfilled.value)
        assertEquals(FlowError.Code.TIMEOUT, error(rejected).code)
        assertEquals(listOf("ok", "err"), calls)
        assertTrue(throwing.error is IllegalStateException)
    }

    @Test
    fun filterRejectsFalsyResultsAndPeekOnlyObserves() {
        val kept = s.of(5).next(Callback(CallbackKind.FILTER, fn { (it[0] as Int) > 3 }))
        val dropped = s.of(1).next(Callback(CallbackKind.FILTER, fn { (it[0] as Int) > 3 }))
        val seen = ArrayList<Any?>()
        val peeked = s.of(9).next(Callback(CallbackKind.PEEK, fn { seen += it[0]; "ignored" }))
        rig.drain()
        assertEquals(5, kept.value)
        assertEquals(FlowError.Code.INVALID_TARGET, error(dropped).code)
        assertEquals("filtered out", error(dropped).reason)
        assertEquals(9, peeked.value)
        assertEquals(listOf<Any?>(9), seen)
    }

    @Test
    fun runExecutesOnTheWorkerLaneWithoutArgumentsAtTheRoot() {
        val root = s.start(Callback(CallbackKind.RUN, fn { "args=" + it.size }))
        val chained = root.next(Callback(CallbackKind.RUN, fn { it[0].toString() + "+" }))
        rig.script.runAll()
        assertTrue(root.state is State.Pending)
        rig.worker.runAll()
        assertEquals("args=0", root.value)
        assertEquals("args=0+", chained.value)
        assertEquals(2, rig.worker.executed)
    }

    @Test
    fun aCallbackReturningAFlowIsAdopted() {
        val pendingWait = s.start(Wait(rig.appearing(2, "found"), policy(1000)))
        val adopted = s.of(1).next(Callback(CallbackKind.THEN, fn { pendingWait }))
        val immediate = s.of(1).next(Callback(CallbackKind.THEN, fn { s.of(42) }))
        rig.drain()
        assertEquals("found", adopted.value)
        assertEquals(42, immediate.value)
    }

    @Test
    fun aThrowingCallbackRejectsWithItsException() {
        val node = s.of(1)
            .next(Callback(CallbackKind.THEN, fn { throw IllegalArgumentException("bad") }))
            .next(Callback(CallbackKind.CATCH, onErr = fn { (it[0] as Throwable).message }))
        rig.drain()
        assertEquals("bad", node.value)
    }

    // Whole-chain timeout and retry. zh-CN: 全链超时与重试.

    @Test
    fun timeoutClipsLaterWaitsAndFailsStepsPastTheDeadline() {
        val wait = s.of(1).next(Timeout(250)).next(Wait(rig.never, policy(1000)))
        rig.drain()
        val e = error(wait) as FlowError.Timeout
        assertEquals("chain", e.reason)
        assertEquals(300, e.elapsed)
        assertEquals(4, e.attempts)

        val late = FlowRig()
        val click = late.scheduler.of(FakeUiNode.root("t")).next(Timeout(100)).next(Sleep(150)).next(Action("click"))
        late.drain()
        assertEquals(listOf(100L), late.sleeps)
        val e2 = error(click) as FlowError.Timeout
        assertEquals("click", e2.step)
        assertEquals("chain", e2.reason)
    }

    @Test
    fun retryRerunsTheFailedStepUntilItSucceeds() {
        val target = FakeUiNode.root("t")
        val wait = s.start(Wait(rig.appearing(6, target), PollPolicy(times = 2, interval = 10)))
        val retry = wait.next(Retry(times = 3, delay = 50))
        rig.drain()
        assertEquals(FlowError.Code.TIMEOUT, error(wait).code)
        assertSame(target, retry.value)
        assertEquals(6, b.probes)
        assertEquals(listOf(10L, 50L, 10L, 50L, 10L), rig.sleeps)
    }

    @Test
    fun retryKeepsTheLastErrorWhenExhausted() {
        val retry = s.start(Wait(rig.never, PollPolicy.ONCE)).next(Retry(2))
        rig.drain()
        assertEquals(3, b.probes)
        assertEquals(FlowError.Code.TIMEOUT, error(retry).code)
    }

    @Test
    fun retryPassesAFulfilledValueThroughAndDoesNotRerunACancellation() {
        val ok = s.of(3).next(Retry(2))
        rig.drain()
        assertEquals(3, ok.value)
        val wait = s.start(Wait(rig.never, policy(1000)))
        val retry = wait.next(Retry(5))
        wait.cancel()
        rig.drain()
        assertTrue(retry.isCancelled)
        assertEquals(0, b.probes)
    }

    @Test
    fun retryRerunsACallbackOnItsOwnLane() {
        var calls = 0
        val then = s.of(1).next(Callback(CallbackKind.THEN, fn { if (++calls < 3) throw IllegalStateException("flaky") else "ok" }))
        val retry = then.next(Retry(5))
        rig.drain()
        assertEquals("ok", retry.value)
        assertEquals(3, calls)
        assertEquals(3, rig.script.executed)
        assertEquals(0, rig.worker.executed)
    }

    // Combinators. zh-CN: 组合器.

    @Test
    fun allCollectsInOrderAndRejectsOnTheFirstRejection() {
        val target = FakeUiNode.root("t")
        val all = s.start(Combinator(CombinatorKind.ALL, listOf(s.of(1), rig.appearing(2, target), s.of(3)), policy(1000)))
        rig.drain()
        assertEquals(listOf<Any?>(1, target, 3), all.value)
        val failing = s.start(Combinator(CombinatorKind.ALL, listOf(rig.never, s.of(1)), PollPolicy.ONCE))
        rig.drain()
        assertEquals(FlowError.Code.TIMEOUT, error(failing).code)
    }

    @Test
    fun raceTakesTheFirstSettledAndCancelsTheWaitsItCreated() {
        val race = s.start(Combinator(CombinatorKind.RACE, listOf(rig.appearing(1, "fast"), rig.appearing(5, "slow")), policy(1000)))
        rig.drain()
        assertEquals("fast", race.value)
        assertEquals(1, b.probes)
        assertEquals(0, s.pendingCount)
        val rejected = s.start(Combinator(CombinatorKind.RACE, listOf(rig.never, rig.appearing(9, "late")), PollPolicy.ONCE))
        rig.drain()
        assertEquals(FlowError.Code.TIMEOUT, error(rejected).code)
    }

    @Test
    fun anyTakesTheFirstFulfilledAndRejectsOnlyWhenAllReject() {
        val any = s.start(Combinator(CombinatorKind.ANY, listOf(s.start(Wait(rig.never, PollPolicy.ONCE)), s.of("second")), policy(1000)))
        assertEquals("second", any.value)
        val allFail = s.start(Combinator(CombinatorKind.ANY, listOf(rig.never, rig.never), PollPolicy.ONCE))
        rig.drain()
        assertEquals(FlowError.Code.TIMEOUT, error(allFail).code)
        assertEquals(emptyList<Any?>(), s.start(Combinator(CombinatorKind.ALL, emptyList(), PollPolicy.ONCE)).value)
        assertEquals(FlowError.Code.INVALID_TARGET, error(s.start(Combinator(CombinatorKind.RACE, emptyList(), PollPolicy.ONCE))).code)
    }

    // Cancellation. zh-CN: 取消.

    @Test
    fun cancelRejectsPendingStepsAndStopsTheWaitTheyDependOn() {
        val wait = s.start(Wait(rig.never, policy(1000)))
        val click = wait.next(Action("click"))
        val calls = ArrayList<String>()
        val fin = click.next(Callback(CallbackKind.FINALLY, fn { calls += "finally"; null }))
        assertTrue(click.cancel())
        assertTrue(click.isCancelled)
        assertTrue(wait.isCancelled)
        assertFalse(click.cancel())
        rig.drain()
        assertTrue(fin.isCancelled)
        assertEquals(listOf("finally"), calls)
        assertEquals(0, b.probes)
        assertEquals(0, rig.worker.interrupts)
        assertEquals("click", (click.error as FlowError.Cancelled).step)
    }

    @Test
    fun cancellingARunningWaitInterruptsItAndStopsTheAttempts() {
        lateinit var wait: FlowNode
        var calls = 0
        wait = s.start(Wait({ if (++calls == 2) wait.cancel(); null }, policy(10_000)))
        rig.drain()
        assertTrue(wait.isCancelled)
        assertEquals(2, calls)
        assertEquals(1, rig.worker.interrupts)
        val e = error(wait) as FlowError.Cancelled
        assertEquals("wait", e.step)
        assertEquals("fn", e.selector)
    }

    @Test
    fun cancellingOneBranchLeavesTheSiblingAlive() {
        val target = FakeUiNode.root("t")
        val wait = s.start(Wait(rig.appearing(2, target), policy(1000)))
        val a = wait.next(Callback(CallbackKind.THEN, fn { "a" }))
        val other = wait.next(Callback(CallbackKind.THEN, fn { "b" }))
        a.cancel()
        assertTrue(a.isCancelled)
        assertFalse(wait.isSettled)
        rig.drain()
        assertSame(target, wait.value)
        assertEquals("b", other.value)
    }

    @Test
    fun cancelAllRejectsEveryPendingNode() {
        val w1 = s.start(Wait(rig.never, policy(1000)))
        val w2 = s.start(Wait(rig.never, policy(1000))).next(Callback(CallbackKind.THEN, fn { 1 }))
        assertEquals(3, s.pendingCount)
        assertEquals(3, s.cancelAll())
        assertEquals(0, s.pendingCount)
        assertTrue(w1.isCancelled)
        assertTrue(w2.isCancelled)
        rig.drain()
        assertEquals(0, b.probes)
    }

    @Test
    fun listenerSeesEveryStepStartAndEnd() {
        val events = ArrayList<String>()
        s.listener = object : FlowListener {
            override fun onStepStart(node: FlowNode) {
                events += "start ${node.step.name}"
            }

            override fun onStepEnd(node: FlowNode, state: State, elapsed: Long) {
                events += "end ${node.step.name} ${state::class.simpleName} $elapsed"
            }
        }
        s.of(1).next(Wait(rig.appearing(2, "x"), policy(1000))).next(Callback(CallbackKind.THEN, fn { it[0] }))
        rig.drain()
        assertEquals(
            listOf("start of", "end of Fulfilled 0", "start wait", "end wait Fulfilled 100", "start then", "end then Fulfilled 0"),
            events,
        )
    }

    // Stability waits. zh-CN: 稳定等待.

    @Test
    fun waitForStableResolvesOnceTheTargetStopsChanging() {
        val node = FakeUiNode.root("t") { bounds(0, 0, 10, 10) }
        var calls = 0
        val cond = {
            calls += 1
            if (calls <= 3) node.text = "v$calls"
            node
        }
        val stable = s.start(WaitStable(cond, policy(5000), Stability(stableFor = 200)))
        rig.drain()
        assertSame(node, stable.value)
        assertEquals(5, calls)
        assertEquals(400, rig.clock.now)
    }

    @Test
    fun waitForStableTimesOutWithTheReason() {
        var n = 0
        val changing = { FakeUiNode.root("t") { text = "v${n++}"; bounds(0, 0, 10, 10) } }
        val unstable = s.start(WaitStable(changing, policy(300), Stability(200)))
        val missing = s.start(WaitStable(rig.never, policy(300), Stability(200)))
        rig.drain()
        assertEquals(FlowError.Code.TIMEOUT, error(unstable).code)
        assertEquals("unstable", error(unstable).reason)
        assertEquals(FlowError.Code.TIMEOUT, error(missing).code)
        assertEquals("missing", error(missing).reason)
        assertEquals("waitForStable", error(missing).step)
    }

    @Test
    fun missingFailPolicyRejectsOnceASeenTargetDisappears() {
        var calls = 0
        val flicker = { calls += 1; if (calls == 1) FakeUiNode.root("t") else null }
        val node = s.start(WaitStable(flicker, policy(5000), Stability(200, missing = MissingPolicy.FAIL)))
        rig.drain()
        val e = error(node)
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals("missing", e.reason)
        assertEquals(2, e.attempts)
    }

    @Test
    fun waitForVisibleNeedsVisibilityAndWaitForHiddenResolvesTrueOnceGone() {
        val node = FakeUiNode.root("t") { bounds(0, 0, 10, 10); visibleToUser = false }
        var calls = 0
        val cond = {
            calls += 1
            if (calls >= 3) node.visibleToUser = true
            node
        }
        val visible = s.start(WaitStable(cond, policy(5000), Stability(0), StableMode.VISIBLE))
        rig.drain()
        assertSame(node, visible.value)
        assertEquals(3, calls)

        val gone = FlowRig()
        var shown = 0
        val vanishing = { shown += 1; if (shown <= 2) FakeUiNode.root("x") { bounds(0, 0, 10, 10) } else null }
        val hidden = gone.scheduler.start(WaitStable(vanishing, policy(5000), Stability(100), StableMode.HIDDEN))
        gone.drain()
        assertEquals(true, hidden.value)
        assertEquals(4, shown)
        assertEquals(300, gone.clock.now)
    }

    @Test
    fun aCustomComparatorGoesThroughTheBindings() {
        val sameLength: Fn = { args -> (args[0] as String).length == (args[1] as String).length }
        var calls = 0
        val cond = { calls += 1; listOf("a", "bb", "cc", "dd", "ee")[minOf(calls - 1, 4)] }
        val node = s.start(WaitStable(cond, policy(5000), Stability(200, StabilityCompare.Custom(sameLength))))
        rig.drain()
        assertEquals("dd", node.value)
        assertEquals(4, calls)
        assertEquals(3, b.invoked.count { it.first === sameLength })
    }

    // Real threads. zh-CN: 真实线程.

    @Test(timeout = 10_000)
    fun awaitBlocksUntilSettledAndCancelInterruptsARealSleep() {
        val scheduler = FlowScheduler(ThreadLane("flow-test-worker"), ThreadLane("flow-test-script"), FakeBindings(), { System.currentTimeMillis() }, { Thread.sleep(it) })
        val start = System.currentTimeMillis()
        val found = scheduler.start(Wait({ if (System.currentTimeMillis() - start >= 100) "ok" else null }, PollPolicy(timeout = 5000, interval = 20)))
        assertEquals("ok", found.await(3000))

        val stuck = scheduler.start(Wait({ null }, PollPolicy(timeout = 60_000, interval = 30_000)))
        Thread.sleep(150)
        assertTrue(stuck.cancel())
        assertThrows(FlowError.Cancelled::class.java) { stuck.await(1000) }
        // The worker came out of its 30 s sleep: the next task runs promptly.
        val after = scheduler.start(Callback(CallbackKind.RUN, fn { 7 }))
        assertEquals(7, after.await(2000))

        val slow = scheduler.start(Wait({ null }, PollPolicy(timeout = 60_000, interval = 50)))
        val sync = assertThrows(FlowError.Timeout::class.java) { slow.await(100) }
        assertEquals("sync", sync.reason)
        assertTrue(slow.cancel())
        assertEquals(0, scheduler.pendingCount)
    }

}
