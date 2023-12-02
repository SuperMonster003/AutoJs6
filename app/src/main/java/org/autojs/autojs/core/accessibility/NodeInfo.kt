package org.autojs.autojs.core.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import androidx.annotation.Keep
import android.view.accessibility.AccessibilityNodeInfo

import org.autojs.autojs.core.automator.UiObject

import java.util.ArrayList
import java.util.HashMap

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Jun 17, 2022.
 */

@Suppress("unused", "MemberVisibilityCanBePrivate")
@Keep
class NodeInfo(private val resources: Resources?, private val node: UiObject, var parent: NodeInfo?) {

    @Suppress("DEPRECATION")
    val boundsInParent = Rect().also { node.getBoundsInParent(it) }
    val boundsInScreen = Rect().also { node.getBoundsInScreen(it) }

    val packageName = node.packageName()
    val id = node.simpleId()
    val fullId = node.fullId()
    @SuppressLint("DiscouragedApi")
    val idHex = takeIf { resources != null && packageName != null && fullId != null }?.let {
        "0x${Integer.toHexString(resources!!.getIdentifier(fullId, null, null))}"
    }
    val desc = node.desc()
    val text = node.text()
    val bounds = boundsInScreen
    val center = node.center()
    val className = node.className()
    val clickable = node.isClickable
    val longClickable = node.isLongClickable
    val scrollable = node.isScrollable
    val indexInParent = node.indexInParent()
    val childCount = node.childCount()
    val depth = node.depth()
    val checked = node.isChecked
    val enabled = node.isEnabled
    val editable = node.isEditable
    val focusable = node.isFocusable
    val checkable = node.isCheckable
    val selected = node.isSelected
    val dismissable = node.isDismissable
    val visibleToUser = node.visibleToUser()
    val contextClickable = node.isContextClickable
    val focused = node.focused()
    val accessibilityFocused = node.isAccessibilityFocused
    val rowCount = node.rowCount()
    val columnCount = node.columnCount()
    val row = node.row()
    val column = node.column()
    val rowSpan = node.rowSpan()
    val columnSpan = node.columnSpan()
    val drawingOrder = node.drawingOrder
    val actionNames = node.actionNames()

    val children = ArrayList<NodeInfo>()

    override fun toString() = "$className${node.summary()}"

    companion object {

        fun boundsToString(rect: Rect) = rect.toString().replace(" - ", " , ").replace(" ", "").substring(4)

        internal fun capture(resourcesCache: HashMap<String, Resources>, context: Context, uiObject: UiObject, parent: NodeInfo?): NodeInfo {
            val resources: Resources? = uiObject.packageName()?.let { pkg ->
                return@let resourcesCache[pkg] ?: run {
                    try {
                        return@run context.packageManager.getResourcesForApplication(pkg).also { resourcesCache[pkg] = it }
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace()
                        return@run null
                    }
                }
            }
            return NodeInfo(resources, uiObject, parent).apply {
                0.until(uiObject.childCount)
                    .asSequence()
                    .mapNotNull { uiObject.child(it) }
                    .forEach { children += capture(resourcesCache, context, it, this) }
            }
        }

        fun capture(context: Context, root: AccessibilityNodeInfo): NodeInfo {
            val r = UiObject.createRoot(root)
            val resourcesCache = HashMap<String, Resources>()
            return capture(resourcesCache, context, r, null)
        }

    }

}
