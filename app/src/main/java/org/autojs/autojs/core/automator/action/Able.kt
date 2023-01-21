package org.autojs.autojs.core.automator.action

import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.tool.SparseArrayEntries

/**
 * Created by Stardust on 2017/1/27.
 */
interface Able {

    fun isAble(node: UiObject): Boolean

    companion object {

        val ABLE_MAP = SparseArrayEntries<Able>()
                .entry(AccessibilityNodeInfo.ACTION_CLICK, object : Able {
                    override fun isAble(node: UiObject) = node.isClickable
                })
                .entry(AccessibilityNodeInfo.ACTION_LONG_CLICK, object : Able {
                    override fun isAble(node: UiObject) = node.isLongClickable
                })
                .entry(AccessibilityNodeInfo.ACTION_FOCUS, object : Able {
                    override fun isAble(node: UiObject) = node.isFocusable
                })
                .entry(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, object : Able {
                    override fun isAble(node: UiObject) = node.isScrollable
                })
                .entry(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, object : Able {
                    override fun isAble(node: UiObject) = node.isScrollable
                })
                .sparseArray()
    }

}
