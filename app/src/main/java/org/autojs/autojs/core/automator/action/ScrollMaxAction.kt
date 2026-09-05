package org.autojs.autojs.core.automator.action

import android.graphics.Rect
import android.util.Log

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.UiObject

import java.util.HashSet

/**
 * Created by Stardust on Jan 27, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
class ScrollMaxAction(private val mScrollAction: Int) : SimpleAction() {
    private var mMaxScrollableNode: UiObject? = null
    private var mRootNode: UiObject? = null
    private val mRecycledMaxUiObjects = HashSet<UiObject>()
    private val mLifecycle = NodeLifecycle.default

    override fun perform(root: UiObject): Boolean {
        reset()
        mRootNode = root
        findMaxScrollableNodeInfo(root)
        val result = mMaxScrollableNode != null && mMaxScrollableNode!!.performAction(mScrollAction)
        reset()
        return result
    }

    private fun reset() {
        mMaxScrollableNode?.let {
            if (it !== mRootNode) {
                mLifecycle.release(it)
            }
        }
        mRootNode = null
        mMaxScrollableNode = mRootNode
        mRecycledMaxUiObjects.clear()
    }

    private fun findMaxScrollableNodeInfo(nodeInfo: UiObject?) {
        if (nodeInfo == null)
            return

        if (nodeInfo.isScrollable) {
            val maxScrollableNode = mMaxScrollableNode
            if (maxScrollableNode == null) {
                mMaxScrollableNode = nodeInfo
            } else if (getAreaInScreen(maxScrollableNode) < getAreaInScreen(nodeInfo)) {
                if (maxScrollableNode !== mRootNode) {
                    mRecycledMaxUiObjects.add(maxScrollableNode)
                    mLifecycle.release(maxScrollableNode)
                }
                mMaxScrollableNode = nodeInfo
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            val child = nodeInfo.child(i)
            if (child != null) {
                findMaxScrollableNodeInfo(child)
                if (mMaxScrollableNode !== child && !mRecycledMaxUiObjects.contains(child)) {
                    mLifecycle.release(child)
                }
            }
        }
    }

    private fun getAreaInScreen(nodeInfo: UiObject): Long {
        val rect = Rect()
        nodeInfo.getBoundsInScreen(rect)
        val area = rect.width().toLong() * rect.height()
        Log.v(TAG, "area=$area")
        return area
    }

    companion object {

        private val TAG = ScrollMaxAction::class.java.simpleName
    }

}
