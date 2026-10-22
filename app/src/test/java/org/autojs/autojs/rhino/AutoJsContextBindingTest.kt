package org.autojs.autojs.rhino

import org.autojs.autojs.rhino.AutoJsContext.Binding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mozilla.javascript.ContextFactory

/**
 * JVM tests of the engine bindings of one Rhino Context (docs/memory/ROADMAP.md H4 / P4.4). Every UI script enters the Context of
 * the main thread; when the newest one ends, the Context must go back to the newest engine still running instead of keeping the
 * ended one (with its WrapFactory and standard objects), and an engine ending out of order must not change the current binding.
 * zh-CN: 一个 Rhino Context 的引擎绑定的 JVM 测试 (docs/memory/ROADMAP.md H4 / P4.4). 每个 UI 脚本都进入主线程的 Context; 最新的脚本结束时,
 * Context 必须回到仍在运行的最新引擎, 而不是留着已结束的引擎 (及其 WrapFactory 与标准对象); 不按顺序结束的引擎不得改变当前绑定.
 */
class AutoJsContextBindingTest {

    private val bindings = ArrayList<Binding<String>>()
    private val applied = ArrayList<String>()

    private fun bind(owner: String) = Binding.bind(bindings, owner) { applied += owner }

    private fun current() = bindings.lastOrNull()?.owner

    @Test
    fun theNewestEngineEndingGivesTheContextBackToTheOneStillRunning() {
        bind("outer")
        bind("inner")

        val next = Binding.release(bindings, "inner")

        assertNotNull(next)
        assertEquals("outer", next!!.owner)
        next.apply(AutoJsContext(ContextFactory()))
        assertEquals(listOf("outer"), applied)
        assertEquals("outer", current())
    }

    @Test
    fun anOlderEngineEndingFirstLeavesTheCurrentBinding() {
        bind("outer")
        bind("inner")

        assertNull(Binding.release(bindings, "outer"))

        assertEquals("inner", current())
        assertEquals(listOf("inner"), bindings.map { it.owner })
    }

    @Test
    fun theLastEngineEndingLeavesNoOwner() {
        bind("only")

        val next = Binding.release(bindings, "only")

        assertNotNull(next)
        assertNull(next!!.owner)
        assertEquals(0, bindings.size)
    }

    @Test
    fun bindingAgainMovesTheEngineToTheTop() {
        bind("a")
        bind("b")
        bind("a")

        assertEquals(listOf("b", "a"), bindings.map { it.owner })
        assertEquals("b", Binding.release(bindings, "a")!!.owner)
    }

    @Test
    fun releasingAnEngineThatIsNotBoundChangesNothing() {
        bind("a")

        assertNull(Binding.release(bindings, "never-bound"))

        assertEquals(listOf("a"), bindings.map { it.owner })
    }

    @Test
    fun threeNestedEnginesUnwindInAnyOrder() {
        bind("a")
        bind("b")
        bind("c")

        assertNull(Binding.release(bindings, "b"))
        assertSame("a", Binding.release(bindings, "c")!!.owner)
        assertNull(Binding.release(bindings, "a")!!.owner)
    }
}
