package org.autojs.autojs.core.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import androidx.annotation.Keep
import android.view.accessibility.AccessibilityNodeInfo

import org.autojs.autojs.core.automator.UiObject
import org.opencv.core.Point

import java.util.ArrayList
import java.util.HashMap

/**
 * A detached copy of one accessibility node with its children, as shown by the layout inspector.
 * Built either from a live [UiObject] or from a [NodeInfoSnapshot] loaded from a layout file, so the
 * inspector windows work the same on a live capture and on a saved one.
 * <p>
 * The property names and JVM field types are read reflectively by the node info view; keep them stable.
 * <p>
 * zh-CN: 无障碍节点及其子节点的独立副本, 供布局分析使用. 可由实时的 [UiObject] 或从布局文件读出的 [NodeInfoSnapshot] 构建,
 * 因此分析窗口对实时捕获与已保存的布局表现一致. 属性名与 JVM 字段类型会被节点信息视图反射读取, 需保持稳定.
 *
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@Keep
class NodeInfo private constructor(
    val boundsInParent: Rect,
    val boundsInScreen: Rect,
    val packageName: String?,
    val id: String?,
    val fullId: String?,
    val idHex: String?,
    val desc: String?,
    val text: String,
    val center: Point,
    val className: String?,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val indexInParent: Int,
    val childCount: Int,
    val depth: Int,
    val checked: Boolean,
    val enabled: Boolean,
    val editable: Boolean,
    val focusable: Boolean,
    val checkable: Boolean,
    val selected: Boolean,
    val dismissable: Boolean,
    val visibleToUser: Boolean,
    val contextClickable: Boolean,
    val focused: Boolean,
    val accessibilityFocused: Boolean,
    val rowCount: Int,
    val columnCount: Int,
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
    val drawingOrder: Int,
    val actionNames: List<String>,
    var parent: NodeInfo?,
    private val summary: String,
) {

    val bounds = boundsInScreen
    var hidden = false

    val children = ArrayList<NodeInfo>()

    @Suppress("DEPRECATION")
    constructor(resources: Resources?, node: UiObject, parent: NodeInfo?) : this(
        boundsInParent = Rect().also { node.getBoundsInParent(it) },
        boundsInScreen = Rect().also { node.getBoundsInScreen(it) },
        packageName = node.packageName(),
        id = node.simpleId(),
        fullId = node.fullId(),
        idHex = idHexOf(resources, node.packageName(), node.fullId()),
        desc = node.desc(),
        text = node.text(),
        center = node.center(),
        className = node.className(),
        clickable = node.isClickable,
        longClickable = node.isLongClickable,
        scrollable = node.isScrollable,
        indexInParent = node.indexInParent(),
        childCount = node.childCount(),
        depth = node.depth(),
        checked = node.isChecked,
        enabled = node.isEnabled,
        editable = node.isEditable,
        focusable = node.isFocusable,
        checkable = node.isCheckable,
        selected = node.isSelected,
        dismissable = node.isDismissable,
        visibleToUser = node.visibleToUser(),
        contextClickable = node.isContextClickable,
        focused = node.focused(),
        accessibilityFocused = node.isAccessibilityFocused,
        rowCount = node.rowCount(),
        columnCount = node.columnCount(),
        row = node.row(),
        column = node.column(),
        rowSpan = node.rowSpan(),
        columnSpan = node.columnSpan(),
        drawingOrder = node.drawingOrder,
        actionNames = node.actionNames(),
        parent = parent,
        summary = node.summary(),
    )

    /**
     * Restores a node from a saved layout; children are attached by [NodeInfoSnapshot.toNodeInfo].
     * zh-CN: 从已保存的布局恢复节点; 子节点由 [NodeInfoSnapshot.toNodeInfo] 挂接.
     */
    constructor(snapshot: NodeInfoSnapshot, parent: NodeInfo?) : this(
        boundsInParent = snapshot.boundsInParent.toRect(),
        boundsInScreen = snapshot.bounds.toRect(),
        packageName = snapshot.packageName,
        id = snapshot.id,
        fullId = snapshot.fullId,
        idHex = snapshot.idHex,
        desc = snapshot.desc,
        text = snapshot.text,
        center = Point(snapshot.center.getOrElse(0) { 0.0 }, snapshot.center.getOrElse(1) { 0.0 }),
        className = snapshot.className,
        clickable = snapshot.clickable,
        longClickable = snapshot.longClickable,
        scrollable = snapshot.scrollable,
        indexInParent = snapshot.indexInParent,
        childCount = snapshot.childCount,
        depth = snapshot.depth,
        checked = snapshot.checked,
        enabled = snapshot.enabled,
        editable = snapshot.editable,
        focusable = snapshot.focusable,
        checkable = snapshot.checkable,
        selected = snapshot.selected,
        dismissable = snapshot.dismissable,
        visibleToUser = snapshot.visibleToUser,
        contextClickable = snapshot.contextClickable,
        focused = snapshot.focused,
        accessibilityFocused = snapshot.accessibilityFocused,
        rowCount = snapshot.rowCount,
        columnCount = snapshot.columnCount,
        row = snapshot.row,
        column = snapshot.column,
        rowSpan = snapshot.rowSpan,
        columnSpan = snapshot.columnSpan,
        drawingOrder = snapshot.drawingOrder,
        actionNames = snapshot.actionNames,
        parent = parent,
        summary = snapshot.summary,
    )

    /** The one-line summary shown after the class name, e.g. by [toString]. zh-CN: 类名之后的单行摘要, 如 [toString] 所用. */
    fun summary() = summary

    override fun toString() = "$className$summary"

    companion object {

        @JvmStatic
        fun boundsToString(rect: Rect) = rect.toString().replace(" - ", " , ").replace(" ", "").substring(4)

        @SuppressLint("DiscouragedApi")
        private fun idHexOf(resources: Resources?, packageName: String?, fullId: String?): String? {
            if (resources == null || packageName == null || fullId == null) return null
            return "0x${Integer.toHexString(resources.getIdentifier(fullId, null, null))}"
        }

        private fun IntArray.toRect() = when (size) {
            4 -> Rect(this[0], this[1], this[2], this[3])
            else -> Rect()
        }

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
