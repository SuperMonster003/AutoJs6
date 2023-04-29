package org.autojs.autojs.core.accessibility

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
 * Created by Stardust on 2017/3/10.
 * Modified by SuperMonster003 as of Jun 17, 2022.
 */

@Suppress("unused")
@Keep
class NodeInfo(private val resources: Resources?, private val node: UiObject, var parent: NodeInfo?) {

    @Suppress("DEPRECATION")
    val boundsInParent = Rect().also { node.getBoundsInParent(it) }
    val boundsInScreen = Rect().also { node.getBoundsInScreen(it) }
    val bounds = boundsToString(boundsInScreen)

    val children = ArrayList<NodeInfo>()
    var fullId: String? = node.viewIdResourceName
    val simpleId = node.simpleId()
    val desc = node.desc()
    val text = node.text()
    val className = node.className()
    val packageName = node.packageName()
    val depth = node.depth()
    val drawingOrder = node.drawingOrder
    val accessibilityFocused = node.isAccessibilityFocused
    val checked = node.isChecked
    val checkable = node.isCheckable
    val clickable = node.isClickable
    val contextClickable = node.isContextClickable
    val dismissable = node.isDismissable
    val enabled = node.isEnabled
    val editable = node.isEditable
    val focusable = node.isFocusable
    val focused = node.focused()
    val longClickable = node.isLongClickable
    val selected = node.isSelected
    val scrollable = node.isScrollable
    val visibleToUser = node.visibleToUser()
    val row = node.row()
    val column = node.column()
    val rowCount = node.rowCount()
    val columnCount = node.columnCount()
    val rowSpan = node.rowSpan()
    val columnSpan = node.columnSpan()
    val indexInParent = node.indexInParent()
    val childCount = node.childCount()
    val actionNames = node.actionNames()

    val idHex = takeIf { resources != null && packageName != null && fullId != null }?.let {
        "0x${Integer.toHexString(resources!!.getIdentifier(fullId, null, null))}"
    }

    override fun toString() =
        "$className" + "{" +
        "childCount=${children.size}, " +
        "boundsInScreen=$boundsInScreen, " +
        "boundsInParent=$boundsInParent, " +
        "id='$simpleId', " +
        "desc='$desc', " +
        "packageName='$packageName', " +
        "text='$text', " +
        "depth=$depth, " +
        "drawingOrder=$drawingOrder, " +
        "accessibilityFocused=$accessibilityFocused, " +
        "checked=$checked, " +
        "clickable=$clickable, " +
        "contextClickable=$contextClickable, " +
        "dismissable=$dismissable, " +
        "editable=$editable, " +
        "enabled=$enabled, " +
        "focusable=$focusable, " +
        "longClickable=$longClickable, " +
        "row=$row, " +
        "column=$column, " +
        "rowCount=$rowCount, " +
        "columnCount=$columnCount, " +
        "rowSpan=$rowSpan, " +
        "columnSpan=$columnSpan, " +
        "selected=$selected, " +
        "scrollable=$scrollable, " +
        "bounds='$bounds', " +
        "checkable=$checkable, " +
        "focused=$focused, " +
        "visibleToUser=$visibleToUser, " +
        "parent=${parent?.className}" +
        "actions=${actionNames}" +
        "}"

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
