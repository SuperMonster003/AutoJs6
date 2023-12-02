package org.autojs.autojs.core.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Created by Stardust on Mar 6, 2017.
 */
object AccessibilityNodeInfoHelper {

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("getBoundsInScreen(Rect)"))
    fun getBoundsInParent(nodeInfo: AccessibilityNodeInfo) = Rect().also { nodeInfo.getBoundsInParent(it) }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("getBoundsInScreen(Rect)"))
    fun getBoundsInParent(nodeInfo: AccessibilityNodeInfoCompat) = Rect().also { nodeInfo.getBoundsInParent(it) }

    @JvmStatic
    fun getBoundsInScreen(nodeInfo: AccessibilityNodeInfo) = Rect().also { nodeInfo.getBoundsInScreen(it) }

    @JvmStatic
    fun getBoundsInScreen(nodeInfo: AccessibilityNodeInfoCompat) = Rect().also { nodeInfo.getBoundsInScreen(it) }

}
