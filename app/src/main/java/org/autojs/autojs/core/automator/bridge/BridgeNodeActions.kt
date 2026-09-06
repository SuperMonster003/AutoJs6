package org.autojs.autojs.core.automator.bridge

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiNode

/**
 * The node actions of the Node Bridge's frozen `accessibility` protocol (roadmap P5.4), over any
 * [UiNode]: [click] climbs from the node to the nearest enabled clickable node and falls back to
 * the node's own `ACTION_CLICK`, [perform] (`longClick` / `scrollForward` / `scrollBackward`)
 * climbs to the first enabled node accepting the action, [setText] requires an enabled node. The
 * ancestors visited while climbing are released through [lifecycle]; the node itself belongs to
 * the caller.
 * zh-CN: Node Bridge 冻结的 `accessibility` 协议的节点动作 (Roadmap P5.4), 面向任意 [UiNode]: [click] 从节点上溯到最近的启用且可点击的
 * 节点, 都不行时退回节点自身的 `ACTION_CLICK`; [perform] (`longClick` / `scrollForward` / `scrollBackward`) 上溯到首个启用且接受该动作的
 * 节点; [setText] 要求节点启用. 上溯途中访问的祖先经 [lifecycle] 释放; 节点本身属于调用方.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class BridgeNodeActions(private val lifecycle: NodeLifecycle) {

    fun <N : UiNode<N>> click(node: N): Boolean {
        if (climb(node) { it.enabled() && it.clickable() && it.performAction(ACTION_CLICK) }) return true
        return node.performAction(ACTION_CLICK)
    }

    fun <N : UiNode<N>> perform(node: N, action: Int): Boolean = climb(node) { it.enabled() && it.performAction(action) }

    fun <N : UiNode<N>> setText(node: N, text: String): Boolean {
        if (!node.enabled()) return false
        return node.performAction(ACTION_SET_TEXT, ActionArgument.CharSequenceActionArgument(ACTION_ARGUMENT_SET_TEXT, text))
    }

    // The node itself, then its ancestors, until one satisfies [accept]; ancestors are released once
    // passed. zh-CN: 先是节点自身, 再是其祖先, 直到某个满足 [accept]; 经过的祖先随即释放.
    private fun <N : UiNode<N>> climb(node: N, accept: (N) -> Boolean): Boolean {
        var current: N? = node
        while (current != null) {
            val visited: N = current
            val accepted = accept(visited)
            val next = if (accepted) null else visited.parent()
            if (visited !== node) lifecycle.release { visited.recycle() }
            if (accepted) return true
            current = next
        }
        return false
    }

    companion object {

        const val ACTION_CLICK = AccessibilityNodeInfoCompat.ACTION_CLICK

        const val ACTION_LONG_CLICK = AccessibilityNodeInfoCompat.ACTION_LONG_CLICK

        const val ACTION_SCROLL_FORWARD = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD

        const val ACTION_SCROLL_BACKWARD = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD

        const val ACTION_SET_TEXT = AccessibilityNodeInfoCompat.ACTION_SET_TEXT

        const val ACTION_ARGUMENT_SET_TEXT = AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE

    }

}
