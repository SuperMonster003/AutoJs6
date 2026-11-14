package org.autojs.autojs.core.accessibility.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep a virtual-screen task from reading the physical screen's windows.
 * zh-CN: 保证虚拟屏任务不会读到物理主屏窗口的规则.
 */
class DisplayWindowScopeTest {

    private data class Win(val displayId: Int, val layer: Int = 0, val throws: Boolean = false)

    private fun scope() = DisplayWindowScope<Win> { window ->
        if (window.throws) throw IllegalStateException("stale window")
        window.displayId
    }

    private val main = Win(0)
    private val virtual = Win(136)
    private val other = Win(135)

    @Test
    fun noScopeNoScriptFilterLeavesFrameworkBehaviour() {
        val scope = scope()
        assertNull(scope.effectiveFilter())
        assertFalse(scope.isScoped)
        assertEquals(DisplayWindowScope.NO_SCOPE, scope.scopedDisplayId)
    }

    @Test
    fun scopeAloneAcceptsOnlyTheBoundDisplay() {
        val scope = scope()
        scope.setDisplayScope(136)
        val filter = scope.effectiveFilter()
        assertNotNull(filter)
        assertTrue(scope.isScoped)
        assertTrue(filter!!(virtual))
        assertFalse(filter(main))
        assertFalse(filter(other))
    }

    @Test
    fun scriptFilterAloneBehavesAsBefore() {
        val scope = scope()
        scope.setScriptFilter { it.layer > 0 }
        val filter = scope.effectiveFilter()!!
        assertTrue(filter(Win(0, layer = 1)))
        assertTrue(filter(Win(136, layer = 1)))
        assertFalse(filter(main))
    }

    @Test
    fun scopeAndScriptFilterIntersect() {
        val scope = scope()
        scope.setDisplayScope(136)
        scope.setScriptFilter { it.layer > 0 }
        val filter = scope.effectiveFilter()!!
        assertTrue(filter(Win(136, layer = 1)))
        assertFalse(filter(virtual)) // wrong layer
        assertFalse(filter(Win(0, layer = 1))) // wrong display
    }

    @Test
    fun clearingScriptFilterDoesNotEscapeTheDisplayScope() {
        val scope = scope()
        scope.setDisplayScope(136)
        scope.setScriptFilter { false }
        assertFalse(scope.effectiveFilter()!!(virtual))
        // This is what auto.setWindowFilter(null) does: the script filter goes away, the task scope stays.
        scope.setScriptFilter(null)
        val filter = scope.effectiveFilter()!!
        assertTrue(filter(virtual))
        assertFalse(filter(main))
    }

    @Test
    fun clearingDisplayScopeRestoresTheScriptFilter() {
        val scope = scope()
        scope.setDisplayScope(136)
        scope.setScriptFilter { it.layer > 0 }
        scope.setDisplayScope(DisplayWindowScope.NO_SCOPE)
        val filter = scope.effectiveFilter()!!
        assertFalse(scope.isScoped)
        assertTrue(filter(Win(0, layer = 1)))
        assertFalse(filter(main))
    }

    @Test
    fun throwingDisplayAccessorRejectsOnlyThatWindow() {
        val scope = scope()
        scope.setDisplayScope(136)
        val filter = scope.effectiveFilter()!!
        assertFalse(filter(Win(136, throws = true)))
        assertTrue(filter(virtual))
    }

    @Test
    fun throwingScriptFilterRejectsOnlyThatWindow() {
        val scope = scope()
        scope.setScriptFilter { throw IllegalStateException("bad JS filter") }
        val filter = scope.effectiveFilter()!!
        assertFalse(filter(main))
    }

}
