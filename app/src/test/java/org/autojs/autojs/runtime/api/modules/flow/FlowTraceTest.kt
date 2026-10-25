package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.CombinatorKind
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowRig
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.core.automator.query.PollPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The console lines of `flow.trace(true)`: step label with target and node id, elapsed time and
 * the outcome brief for fulfilled / rejected / cancelled / script-thrown values.
 * zh-CN: `flow.trace(true)` 的控制台行: 带目标与节点 id 的步骤标签, 耗时, 以及兑现 / 拒绝 / 取消 / 脚本抛出值的结果摘要.
 */
class FlowTraceTest {

    private val rig = FlowRig()

    private val idPattern = Regex(" #[0-9a-f]{1,4}$")

    private fun withoutId(label: String) = label.replace(idPattern, "")

    @Test
    fun labelsCarryTheStepItsTargetAndAnId() {
        val wait = rig.scheduler.start(FlowStep.Wait(FlowCondition.Native("content(\"x\")") { null }, PollPolicy(timeout = 100, interval = 10)))
        assertTrue(idPattern.containsMatchIn(FlowTraceFormat.label(wait)))
        assertEquals("wait content(\"x\")", withoutId(FlowTraceFormat.label(wait)))
        assertEquals("[flow] > wait content(\"x\")", withoutId(FlowTraceFormat.start(wait)))
        assertEquals("sleep 100 ms", withoutId(FlowTraceFormat.label(rig.scheduler.start(FlowStep.Sleep(100, 100)))))
        assertEquals("sleep 100..200 ms", withoutId(FlowTraceFormat.label(rig.scheduler.start(FlowStep.Sleep(100, 200)))))
        assertEquals("setText (\"abc\")", withoutId(FlowTraceFormat.label(rig.scheduler.of(1).next(FlowStep.Action("setText", listOf("abc"))))))
        assertEquals("click", withoutId(FlowTraceFormat.label(rig.scheduler.of(1).next(FlowStep.Action("click", emptyList())))))
        assertEquals("timeout 500 ms", withoutId(FlowTraceFormat.label(rig.scheduler.of(1).next(FlowStep.Timeout(500)))))
        assertEquals("retry x3 / 50 ms", withoutId(FlowTraceFormat.label(rig.scheduler.of(1).next(FlowStep.Retry(3, 50)))))
        assertEquals("race 2 source(s)", withoutId(FlowTraceFormat.label(rig.scheduler.start(FlowStep.Combinator(CombinatorKind.RACE, listOf("a", "b"), PollPolicy(timeout = 100, interval = 10))))))
        assertEquals("of", withoutId(FlowTraceFormat.label(rig.scheduler.of(1))))
        assertEquals("smartClick text(\"x\")", withoutId(FlowTraceFormat.label(rig.scheduler.of(1).next(FlowStep.Tool("smartClick", "text(\"x\")") { v, _ -> v }))))
        assertEquals("typeInto", withoutId(FlowTraceFormat.label(rig.scheduler.of(1).next(FlowStep.Tool("typeInto") { v, _ -> v }))))
    }

    @Test
    fun outcomesAreBrief() {
        val node = rig.scheduler.of(1)
        assertEquals("fulfilled: 1", FlowTraceFormat.outcome(FlowNode.State.Fulfilled(1)))
        assertEquals("fulfilled: \"text\"", FlowTraceFormat.outcome(FlowNode.State.Fulfilled("text")))
        assertEquals("fulfilled: 2", FlowTraceFormat.outcome(FlowNode.State.Fulfilled(2.0)))
        assertEquals("fulfilled: 2.5", FlowTraceFormat.outcome(FlowNode.State.Fulfilled(2.5)))
        assertEquals("fulfilled: NaN", FlowTraceFormat.outcome(FlowNode.State.Fulfilled(Double.NaN)))
        assertEquals("fulfilled: undefined", FlowTraceFormat.outcome(FlowNode.State.Fulfilled(org.mozilla.javascript.Undefined.instance)))
        assertEquals("fulfilled: Flow(of)", FlowTraceFormat.outcome(FlowNode.State.Fulfilled(node)))
        assertEquals("rejected: TIMEOUT (${FlowError.Timeout("wait", "content(\"x\")", 300, 3).message})", FlowTraceFormat.outcome(FlowNode.State.Rejected(FlowError.Timeout("wait", "content(\"x\")", 300, 3))))
        assertEquals("cancelled", FlowTraceFormat.outcome(FlowNode.State.Rejected(FlowError.Cancelled("sleep"))))
        assertEquals("rejected: java.lang.IllegalStateException: boom", FlowTraceFormat.outcome(FlowNode.State.Rejected(IllegalStateException("boom"))))
        assertEquals("pending", FlowTraceFormat.outcome(FlowNode.State.Pending))
        val long = "x".repeat(200)
        assertEquals(80, FlowTraceFormat.brief(long).length - 2)
        assertTrue(FlowTraceFormat.brief(long).endsWith("...\""))
        assertEquals("[flow] < of 12 ms -> fulfilled: 1", withoutId(FlowTraceFormat.end(node, FlowNode.State.Fulfilled(1), 12)).replace(Regex(" #[0-9a-f]{1,4} "), " "))
    }

}
