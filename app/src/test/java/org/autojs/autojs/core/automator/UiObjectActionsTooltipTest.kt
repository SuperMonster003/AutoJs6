package org.autojs.autojs.core.automator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tooltip actions of [UiObjectActions] are performed only on a node whose action list has them: for a View with no
 * tooltip the framework throws on the main thread of the node's process (`View.showLongClickTooltip`), which crashes
 * that app (docs/selector/ROADMAP.md appendix C Q8).
 * zh-CN: [UiObjectActions] 的 tooltip 动作只在动作列表含有它们的节点上执行: 对没有 tooltip 的 View, 框架会在节点所属进程的主线程抛出
 * (View.showLongClickTooltip), 使该应用崩溃 (docs/selector/ROADMAP.md 附录 C Q8).
 */
class UiObjectActionsTooltipTest {

    /** A node with the actions of [UiObjectActions], as `UiObject` is one. zh-CN: 带 [UiObjectActions] 动作的节点, 如同 UiObject. */
    private class ActingNode(val node: FakeUiNode) : UiNode<FakeUiNode> by node, UiObjectActions {
        override fun performAction(action: Int, vararg arguments: ActionArgument) = node.performAction(action, *arguments)
    }

    private fun acting(vararg actions: String) = ActingNode(FakeUiNode.root { this.actions = actions.toList() })

    @Test
    fun aNodeWithoutTheActionIsFalseAndNothingIsPerformed() {
        val acting = acting("ACTION_CLICK", "ACTION_LONG_CLICK")
        assertFalse(acting.showTooltip())
        assertFalse(acting.hideTooltip())
        assertEquals(emptyList<Int>(), acting.node.performedActions)
    }

    @Test
    fun theOtherTooltipActionDoesNotCount() {
        val showing = acting("ACTION_SHOW_TOOLTIP")
        assertFalse(showing.hideTooltip())
        assertEquals(emptyList<Int>(), showing.node.performedActions)
        val hiding = acting("ACTION_HIDE_TOOLTIP")
        assertFalse(hiding.showTooltip())
        assertEquals(emptyList<Int>(), hiding.node.performedActions)
    }

    @Test
    fun aNodeWithTheActionPerformsItAsBefore() {
        val acting = acting("ACTION_CLICK", "ACTION_SHOW_TOOLTIP", "ACTION_HIDE_TOOLTIP")
        assertTrue(acting.showTooltip())
        assertTrue(acting.hideTooltip())
        assertEquals(listOf(android.R.id.accessibilityActionShowTooltip, android.R.id.accessibilityActionHideTooltip), acting.node.performedActions)
    }

    @Test
    fun theResultIsWhatThePerformedActionReports() {
        val acting = acting("ACTION_SHOW_TOOLTIP").apply { node.actionResult = false }
        assertFalse(acting.showTooltip())
        assertEquals(listOf(android.R.id.accessibilityActionShowTooltip), acting.node.performedActions)
    }

}
