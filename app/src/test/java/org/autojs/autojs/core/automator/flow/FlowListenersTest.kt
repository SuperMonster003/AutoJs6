package org.autojs.autojs.core.automator.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FlowListeners]: `of` collapses nulls and a single listener, the fan-out notifies every
 * listener in order for both callbacks.
 * zh-CN: [FlowListeners]: `of` 折叠 null 与单个监听器, 分发器对两个回调都按顺序通知每个监听器.
 */
class FlowListenersTest {

    private val rig = FlowRig()

    private class Recorder(private val tag: String, private val seen: MutableList<String>) : FlowListener {

        override fun onStepStart(node: FlowNode) {
            seen += "$tag>${node.step.name}"
        }

        override fun onStepEnd(node: FlowNode, state: FlowNode.State, elapsed: Long) {
            seen += "$tag<${node.step.name} $elapsed"
        }

    }

    @Test
    fun ofCollapsesNullsAndSingles() {
        val single = object : FlowListener {}
        assertNull(FlowListeners.of())
        assertNull(FlowListeners.of(null, null))
        assertSame(single, FlowListeners.of(null, single, null))
        assertTrue(FlowListeners.of(single, single) is FlowListeners)
    }

    @Test
    fun theFanOutKeepsTheOrder() {
        val seen = ArrayList<String>()
        val fan = FlowListeners.of(Recorder("a", seen), null, Recorder("b", seen))!!
        val node = rig.scheduler.of(1)
        fan.onStepStart(node)
        fan.onStepEnd(node, FlowNode.State.Fulfilled(1), 5)
        assertEquals(listOf("a>of", "b>of", "a<of 5", "b<of 5"), seen)
    }

    @Test
    fun aSchedulerNotifiesEveryListenerOfTheFanOut() {
        val seen = ArrayList<String>()
        rig.scheduler.listener = FlowListeners.of(Recorder("a", seen), Recorder("b", seen))
        rig.scheduler.of(1).next(FlowStep.Sleep(1, 1))
        rig.drain()
        assertTrue(seen.toString(), seen.contains("a>sleep") && seen.contains("b>sleep"))
        assertTrue(seen.toString(), seen.indexOf("a>sleep") < seen.indexOf("b>sleep"))
        assertTrue(seen.toString(), seen.any { it.startsWith("a<sleep ") } && seen.any { it.startsWith("b<sleep ") })
    }

}
