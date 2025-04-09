package org.autojs.autojs.core.automator

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoAllocator
import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoHelper
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.isJsUndefined
import org.autojs.autojs.extension.ArrayExtensions.toHashCode
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.DisplayUtils.toRoundIntX
import org.autojs.autojs.util.DisplayUtils.toRoundIntY
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.roundToInt

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
@Suppress("unused", "DEPRECATION", "MemberVisibilityCanBePrivate")
open class UiObject(
    info: Any?,
    private val allocator: AccessibilityNodeInfoAllocator?,
    private val depth: Int,
    private val indexInParent: Int,
) : AccessibilityNodeInfoCompat(info), UiObjectActions {

    private val accessibilityBridge by lazy { AutoJs.instance.createAccessibilityBridge() }

    constructor(
        info: Any?,
        allocator: AccessibilityNodeInfoAllocator,
        indexInParent: Int,
    ) : this(info, allocator, 0, indexInParent)

    @JvmOverloads
    constructor(
        info: Any?,
        depth: Int = 0,
        indexInParent: Int = -1,
    ) : this(info, null, depth, indexInParent)

    open fun parent(): UiObject? = try {
        super.getParent()?.let { node ->
            UiObject(node.unwrap(), depth - 1, node.parent?.run {
                // @Dubious by SuperMonster003 on May 26, 2022.
                //  ! May lead to poor performance?
                //  ! zh-CN: 可能导致性能不佳?
                (0 until childCount).firstOrNull { getChild(it) == node } ?: -1
            } ?: 0)
        }
    } catch (e: IllegalStateException) {
        // FIXME by Stardust on May 5, 2017.
        null.also { e.printStackTrace() }
    }

    fun parent(i: Int): UiObject? = if (i < 0) throw Exception("i < 0") else compass("p$i")

    open fun child(i: Int): UiObject? {
        if (i < 0) {
            return (i + childCount).takeIf { it >= 0 }?.let { child(it) }
        }
        return try {
            super.getChild(i)?.run { UiObject(unwrap(), depth + 1, i) }
        } catch (e: IllegalStateException) {
            // FIXME by Stardust on May 5, 2017.
            null.also { e.printStackTrace() }
        }
    }

    // @Deprecated by SuperMonster003 on Jul 20, 2023.
    //  ! Author: 抠脚本人
    //  ! Reason: Replaced with offset(i) [zh-CN: 替代为 offset(i)].
    @Deprecated("Deprecated in Java", ReplaceWith("offset(i)"))
    open fun brother(i: Int): UiObject? = offset(i)

    open fun offset(i: Int): UiObject? = try {
        if (i == 0) this
        else parent()?.child(indexInParent + i)
    } catch (e: ArrayIndexOutOfBoundsException) {
        null.also { e.printStackTrace() }
    }

    open fun sibling(i: Int): UiObject? = try {
        if (i == indexInParent) this
        else parent()?.child(i)
    } catch (e: ArrayIndexOutOfBoundsException) {
        null.also { e.printStackTrace() }
    }

    fun siblingCount() = parent()?.childCount ?: 1

    fun isSingleton() = siblingCount() == 1

    fun firstSibling() = sibling(0)

    fun lastSibling() = sibling(-1)

    open fun nextSibling() = offset(1)

    open fun previousSibling() = offset(-1)

    open fun childCount() = childCount

    fun hasChildren() = childCount > 0

    fun firstChild() = child(0)

    fun lastChild() = child(childCount - 1)

    fun children() = List(childCount) { child(it) }.let { UiObjectCollection.of(it) }

    fun siblings() = List(siblingCount()) { sibling(it) }.let { UiObjectCollection.of(it) }

    fun indexInParent() = indexInParent

    @JvmOverloads
    fun find(selector: UiSelector? = UiSelector()): UiObjectCollection = (selector ?: UiSelector()).findOf(this)

    fun findOne(selector: UiSelector): UiObject? = selector.findOneOf(this)

    open fun bounds() = AccessibilityNodeInfoHelper.getBoundsInScreen(this)

    fun boundsInScreen() = bounds()

    fun boundsLeft() = bounds().left

    fun left() = boundsLeft()

    fun boundsTop() = bounds().top

    fun top() = boundsTop()

    fun boundsRight() = bounds().right

    fun right() = boundsRight()

    fun boundsBottom() = bounds().bottom

    fun bottom() = boundsBottom()

    fun boundsWidth() = bounds().width()

    fun width() = boundsWidth()

    fun boundsHeight() = bounds().height()

    fun height() = boundsHeight()

    fun boundsCenterX() = bounds().centerX()

    fun centerX() = boundsCenterX()

    fun boundsExactCenterX() = bounds().exactCenterX()

    fun exactCenterX() = boundsExactCenterX()

    fun boundsCenterY() = bounds().centerY()

    fun centerY() = boundsCenterY()

    fun boundsExactCenterY() = bounds().exactCenterY()

    fun exactCenterY() = boundsExactCenterY()

    fun point() = Point(exactCenterX().toDouble(), exactCenterY().toDouble())

    fun center() = point()

    fun size() = Size(width().toDouble(), height().toDouble())

    @JvmOverloads
    fun clickBounds(offsetX: Double = 0.0, offsetY: Double = 0.0): Boolean {
        // @Hint by SuperMonster003 on Jul 10, 2024.
        //  ! Without ScreenMetrics info related to ScriptRuntime.
        //  ! 未包含 "脚本运行时" 相关联的 ScreenMetrics 信息.
        val automatorTmp = GlobalActionAutomator(ScriptRuntime.applicationContext, null) {
            accessibilityBridge.ensureServiceStarted()
            accessibilityBridge.service!!
        }
        return automatorTmp.click(
            centerX() + toRoundIntX(offsetX, false),
            centerY() + toRoundIntY(offsetY, false),
        )
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("boundsInScreen() or bounds()"))
    open fun boundsInParent() = AccessibilityNodeInfoHelper.getBoundsInParent(this)

    open fun drawingOrder() = drawingOrder

    fun fullId(): String? = viewIdResourceName

    open fun id(): String? = fullId()

    fun idEntry() = fullId()?.let {
        when (it.contains(UiSelector.ID_IDENTIFIER)) {
            true -> it.split(UiSelector.ID_IDENTIFIER).last()
            else -> it
        }
    }

    fun simpleId(): String? = idEntry()

    @SuppressLint("DiscouragedApi")
    fun idHex(): String? {
        val pkg = packageName() ?: return null
        val fullId = viewIdResourceName ?: return null
        val resources = try {
            GlobalAppContext.get().packageManager.getResourcesForApplication(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return null
        }
        return "0x${Integer.toHexString(resources.getIdentifier(fullId, null, null))}"
    }

    open fun text() = text?.toString() ?: ""

    override fun getText(): CharSequence? = takeUnless { isPassword }?.let { super.getText() } ?: ""

    open fun desc(): String? = contentDescription?.toString()

    fun content(): String = desc() ?: text()

    open fun className(): String? = className?.toString()

    open fun packageName(): String? = packageName?.toString()

    open fun depth() = depth

    open fun checkable() = isCheckable

    open fun checked() = isChecked

    open fun focusable() = isFocusable

    open fun focused() = isFocused

    open fun visibleToUser() = isVisibleToUser

    open fun accessibilityFocused() = isAccessibilityFocused

    open fun selected() = isSelected

    open fun clickable() = isClickable

    open fun longClickable() = isLongClickable

    open fun enabled() = isEnabled

    fun password() = isPassword

    open fun scrollable() = isScrollable

    fun editable() = isEditable

    open fun row() = collectionItemInfo?.rowIndex ?: -1

    open fun column() = collectionItemInfo?.columnIndex ?: -1

    open fun rowSpan() = collectionItemInfo?.rowSpan ?: -1

    open fun columnSpan() = collectionItemInfo?.columnSpan ?: -1

    open fun rowCount() = collectionInfo?.rowCount ?: 0

    open fun columnCount() = collectionInfo?.columnCount ?: 0

    @ScriptInterface
    fun actionNames() = actionList.map { action ->
        getActionSymbolicName(action.id).let { actionName ->
            if (actionName == "ACTION_UNKNOWN" && action.label != null) {
                action.label.toString()
            } else {
                actionName
            }
        }
    }

    @ScriptInterface
    fun hasAction(vararg actions: Any): Boolean {
        return actionNames().let { actionNames ->
            actions.all {
                when (it) {
                    is Int -> hasAction(getActionSymbolicName(it))
                    is Double -> hasAction(getActionSymbolicName(it.toInt()))
                    is String -> actionNames.contains(if (it.startsWith("ACTION_")) it else "ACTION_$it")
                    else -> false
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    fun compass(compassArg: CharSequence?): UiObject? {
        var w: UiObject = this
        var compass = compassArg?.takeUnless { it == COMPASS_PASS_ON }?.also { ensureCompass(it) } ?: return w

        while (compass.isNotEmpty()) {
            // p2 : [ .parent().parent() ]
            // p : [ p1 ]
            // ppp : [ p3 ]
            // p4ppp12p : [ p4, pp, p12, p -> 4, 2, 12, 1 -> p19 ]
            val mP = "^p[p\\d]*".toRegex().find(compass)
            if (mP != null) {
                var upMax: Int = "^p\\d+|^p+(?!\\d)".toRegex().findAll(compass).fold(0) { acc: Int, result ->
                    result.value.let { acc + if (it.contains("\\d".toRegex())) it.substring(1).toInt() else it.length }
                }
                while (upMax-- > 0) {
                    w = w.parent() ?: return null
                }
                compass.substring(mP.value.length).also { compass = it }
                continue
            }

            // c0c2c0c1 : [ .child(0).child(2).child(0).child(1) ]
            // c0>2>0>1 : [ .child(0).child(2).child(0).child(1) ]
            // c-3 : [ .child(childCount()-3) ]
            // c-3c2c-1 : [ .child(childCount()-3).child(2).child(childCount()-1) ]
            // c1>2>3>0>-1>1 : [ c1 c2 c3 c0 c-1 c1 ]
            val mC = "^c-?\\d+([>c]?-?\\d+)*".toRegex().find(compass)
            if (mC != null) {
                val numbers = mC.value.split("[>c]".toRegex()).filterNot { it.isEmpty() }
                for (s in numbers) {
                    val cc = w.childCount().takeIf { it > 0 } ?: return null
                    val i = s.toInt().let { if (it < 0) it + cc else it }.takeIf { it in 0.until(cc) } ?: return null
                    w = w.child(i) ?: return null
                }
                compass.substring(mC.value.length).also { compass = it }
                continue
            }

            // s2 : [ .parent().child(2) ]
            // s-2 : [ .parent().child(childCount() - 2) ]
            // s>2 : [ .parent().child(idxInParent() + 2) ]
            // s<2 : [ .parent().child(idxInParent() - 2) ]
            val mS = "^s[<>]?-?\\d+".toRegex().find(compass)
            if (mS != null) {
                val parent = w.parent() ?: return null
                var idx = w.indexInParent().takeIf { it >= 0 } ?: return null
                val str = mS.value
                val offset: Int = "-?\\d+".toRegex().find(str)?.value?.toDouble()?.roundToInt() ?: return null
                val cc by lazy { parent.childCount() }
                when {
                    str.contains('>') -> idx += offset
                    str.contains('<') -> idx -= offset
                    else -> offset.let { if (it < 0) it + cc else it }.also { idx = it }
                }
                idx.takeIf { it in 0.until(cc) }?.let { parent.child(it) }?.also { w = it } ?: return null
                compass.substring(mS.value.length).also { compass = it }
                continue
            }

            // k/k1 : [ .clickable() || .parent().clickable() aka pk/p1k ]
            // k2 : [ .clickable() || .parent().clickable() || .p2.clickable() aka p2k ]
            // kn : [ .clickable() || .pn.clickable() aka pnk ]
            val mK = "^k[k\\d]*".toRegex().find(compass)
            if (mK != null) {
                var upMax = "^k(\\d*)".toRegex().findAll(compass)
                    .map { "\\d+".toRegex().find(it.value)?.value ?: "1" }
                    .fold(0) { acc: Int, n -> acc + n.toInt() }
                var wTmp: UiObject = w
                while (upMax-- >= 0) when {
                    wTmp.clickable().also { if (it) w = wTmp } -> break
                    else -> wTmp = wTmp.parent() ?: return null
                }
                compass.substring(mK.value.length).also { compass = it }
                continue
            }

            throw IllegalArgumentException(str(R.string.error_invalid_rest_compass, compass))
        }

        return w
    }

    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        return performAction(action, Bundle().apply { arguments.forEach { it.putIn(this) } })
    }

    override fun performAction(action: Int, bundle: Bundle): Boolean = try {
        when (bundle.isEmpty) {
            true -> super.performAction(action)
            else -> super.performAction(action, bundle)
        }
    } catch (e: IllegalStateException) {
        // FIXME by Stardust on May 5, 2017.
        false.also { e.printStackTrace() }
    }

    override fun performAction(action: Int): Boolean = try {
        super.performAction(action)
    } catch (e: IllegalStateException) {
        // FIXME by Stardust on May 5, 2017.
        false.also { e.printStackTrace() }
    }

    override fun getChild(index: Int): AccessibilityNodeInfoCompat =
        allocator?.getChild(this, index) ?: super.getChild(index)

    override fun getParent(): AccessibilityNodeInfoCompat? = allocator?.getParent(this) ?: super.getParent()

    override fun findAccessibilityNodeInfosByText(text: String): List<AccessibilityNodeInfoCompat> {
        return allocator?.findAccessibilityNodeInfosByText(this, text)
            ?: super.findAccessibilityNodeInfosByText(text)
    }

    override fun findAccessibilityNodeInfosByViewId(viewId: String): List<AccessibilityNodeInfoCompat> {
        return allocator?.findAccessibilityNodeInfosByViewId(this, viewId)
            ?: super.findAccessibilityNodeInfosByViewId(viewId)
    }

    @Deprecated("Deprecated in Java")
    override fun recycle() {
        try {
            super.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun summary(): String {
        val separatorLv0 = "\n"
        val separatorLv1 = "$separatorLv0  "
        val separatorLv2 = "$separatorLv1  "
        
        val dataList = listOf<Pair<String, () -> Any?>>(

            /* Common */

            "packageName" to { packageName() },
            "parent" to { parent?.className },
            "id" to { id() },
            "fullId" to { fullId() },
            "idHex" to { idHex() },
            "desc" to { desc() },
            "text" to { text() },
            "bounds" to { bounds() },
            "center" to { center() },
            "className" to { className() },
            "clickable" to { clickable() },
            "longClickable" to { longClickable() },
            "scrollable" to { scrollable() },
            "indexInParent" to { indexInParent() },
            "childCount" to { childCount() },
            "depth" to { depth() },

            /* Regular */

            "checked" to { checked() },
            "enabled" to { enabled() },
            "editable" to { editable() },
            "focusable" to { focusable() },
            "checkable" to { checkable() },
            "selected" to { selected() },
            "dismissable" to { isDismissable },
            "visibleToUser" to { visibleToUser() },

            /* Rare */

            "contextClickable" to { isContextClickable },
            "focused" to { focused() },
            "accessibilityFocused" to { isAccessibilityFocused },
            "rowCount" to { rowCount() },
            "columnCount" to { columnCount() },
            "row" to { row() },
            "column" to { column() },
            "rowSpan" to { rowSpan() },
            "columnSpan" to { columnSpan() },
            "drawingOrder" to { drawingOrder },

            /* List */

            "actions" to { actionNames() },
        )
        return dataList.joinToString(prefix = "{$separatorLv1", separator = separatorLv1, postfix = "$separatorLv0}") { (name, action) ->
            val value = when (val actionResult = action()) {
                is CharSequence -> "\"$actionResult\""
                is Iterable<*> -> actionResult.joinToString(prefix = "[$separatorLv2", separator = separatorLv2, postfix = "$separatorLv1]")
                is Array<*> -> actionResult.joinToString(prefix = "[$separatorLv2", separator = separatorLv2, postfix = "$separatorLv1]")
                else -> actionResult
            }
            "$name=$value"
        }
    }

    override fun toString(): String {
        val simpledClassName = "$className".substringAfterLast(".")
        return "[${UiObject::class.java.simpleName}] $simpledClassName ${summary()}"
    }

    override fun hashCode() = listOf(
        "${packageName()}",
        "$parent",
        "${id()}",
        "${fullId()}",
        "${idHex()}",
        "${desc()}",
        text(),
        "${bounds()}",
        "${className()}",
        "${clickable()}",
        "${longClickable()}",
        "${scrollable()}",
        "${indexInParent()}",
        "${childCount()}",
        "${depth()}",
        "${checked()}",
        "${enabled()}",
        "${editable()}",
        "${focusable()}",
        "${checkable()}",
        "${selected()}",
        "$isDismissable",
        "${visibleToUser()}",
        "$isContextClickable",
        "${focused()}",
        "$isAccessibilityFocused",
        "${rowCount()}",
        "${columnCount()}",
        "${row()}",
        "${column()}",
        "${rowSpan()}",
        "${columnSpan()}",
        "$drawingOrder",
        "${actionNames()}",
        "${isSingleton()}",
        "${firstSibling()}",
        "${lastSibling()}",
        "${firstChild()}",
        "${lastChild()}",
    ).toHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiObject) return false

        // @Reference to Hunter1023 (https://github.com/Hunter1023) by SuperMonster003 on Mar 28, 2025.
        //  ! http://issues.autojs6.com/288#issuecomment-2692248348
        val info = unwrap() ?: return false

        return info == other.unwrap() &&
                packageName() == other.packageName() &&
                parent == other.parent &&
                id() == other.id() &&
                fullId() == other.fullId() &&
                idHex() == other.idHex() &&
                desc() == other.desc() &&
                text() == other.text() &&
                bounds() == other.bounds() &&
                className() == other.className() &&
                clickable() == other.clickable() &&
                longClickable() == other.longClickable() &&
                scrollable() == other.scrollable() &&
                indexInParent() == other.indexInParent() &&
                childCount() == other.childCount() &&
                depth() == other.depth() &&
                checked() == other.checked() &&
                enabled() == other.enabled() &&
                editable() == other.editable() &&
                focusable() == other.focusable() &&
                checkable() == other.checkable() &&
                selected() == other.selected() &&
                isDismissable == other.isDismissable &&
                visibleToUser() == other.visibleToUser() &&
                isContextClickable == other.isContextClickable &&
                focused() == other.focused() &&
                isAccessibilityFocused == other.isAccessibilityFocused &&
                rowCount() == other.rowCount() &&
                columnCount() == other.columnCount() &&
                row() == other.row() &&
                column() == other.column() &&
                rowSpan() == other.rowSpan() &&
                columnSpan() == other.columnSpan() &&
                drawingOrder == other.drawingOrder &&
                actionNames() == other.actionNames() &&
                isSingleton() == other.isSingleton() &&
                "${firstSibling()}" == "${other.firstSibling()}" &&
                "${lastSibling()}" == "${other.lastSibling()}" &&
                "${firstChild()}" == "${other.firstChild()}" &&
                "${lastChild()}" == "${other.lastChild()}"
    }

    fun isSimilar(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        when (other) {
            is Scriptable -> {
                val thisHashCode = hashCode()
                val otherHashCode = RhinoUtils.hashCodeOfScriptable(other) ?: return false
                return thisHashCode == otherHashCode
            }
            else -> {
                return when {
                    other.javaClass != UiObject::class.java -> false
                    else -> hashCode() == other.hashCode()
                }
            }
        }
    }

    companion object {

        internal const val ACTION_APPEND_TEXT = 0x00200001
        internal const val COMPASS_PASS_ON = "%"
        internal const val RESULT_TYPE_WIDGET = "widget"

        private val RESULT_GROUP_WIDGET by lazy { arrayOf("#", "w", RESULT_TYPE_WIDGET) }
        private val RESULT_GROUP_WIDGET_COLLECTION by lazy { arrayOf("{}", "wc", "collection", "list").plus(listAliases("#", "w")) }
        private val RESULT_GROUP_WIDGETS by lazy { arrayOf("[]", "ws", "widgets").plus(arrayAliases("#", "w")) }
        private val RESULT_GROUP_CONTENT by lazy { arrayOf("$", "txt", "content") }
        private val RESULT_GROUP_CONTENTS by lazy { arrayOf("contents").plus(arrayAliases("$", "txt", "content")) }
        private val RESULT_GROUP_POINT by lazy { arrayOf(".", "pt", "point") }
        private val RESULT_GROUP_POINTS by lazy { arrayOf("points", "pts").plus(arrayAliases(".", "point", "pt")) }
        private val RESULT_GROUP_SELECTOR by lazy { arrayOf("@", "selector", "sel") }
        private val RESULT_GROUP_EXISTENCE by lazy { arrayOf("?", "exists") }

        @JvmStatic
        fun isCompass(s: Any?): Boolean {
            return s is CharSequence && (s == COMPASS_PASS_ON || s.isEmpty() || s.contains("^(([pkc>]|s[<>]?)-?\\d*)+$".toRegex()))
        }

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun ensureCompass(s: CharSequence?) {
            s ?: return
            require(isCompass(s)) { str(R.string.error_invalid_compass, s) }
        }

        @JvmStatic
        fun detect(scriptRuntime: ScriptRuntime, w: UiObject?, compass: CharSequence?, resultType: Any?, callback: BaseFunction? = null): Any? {
            return Detector(compass, object : Detector.Result(resultType) {

                override fun byOne() = w

                override fun byAll() = throw IllegalResultTypeException(resultType.toString())

            }, callback).detect(scriptRuntime)
        }

        // @Hint by SuperMonster003 on May 12, 2022.
        //  ! Argument root should be nullable because an exception
        //  ! will happen on devices with Android API Level >= 31 (12) [S].
        //  # java.lang.NullPointerException: Parameter specified as non-null is null:
        //  # method kotlin.jvm.internal.Intrinsics.checkNotNullParameter, parameter root.
        //  ! zh-CN:
        //  ! 参数 root 应该是可空的, 因为在安卓 API 级别 >= 31 (12) [S] 的设备上会发生异常.
        //  # java.lang.NullPointerException: 指定的非空参数为空:
        //  # 方法 kotlin.jvm.internal.Intrinsics.checkNotNullParameter, 参数 root.
        @JvmStatic
        fun createRoot(root: AccessibilityNodeInfo?) = UiObject(root, null, 0, -1)

        internal fun createRoot(root: AccessibilityNodeInfo?, allocator: AccessibilityNodeInfoAllocator?) =
            UiObject(root, allocator, 0, -1)

        private fun arrayAliases(symbol: String, vararg others: String) = arrayOf(
            "$symbol$symbol", "$symbol[]", "[$symbol]",
        ) + others.map { listOf("$it[]", "[$it]") }.flatten()

        @Suppress("SameParameterValue")
        private fun listAliases(symbol: String, vararg others: String) = arrayOf(
            "$symbol{}", "{$symbol}",
        ) + others.map { listOf("$it{}", "{$it}") }.flatten()

        // @Hint Copied by SuperMonster003 on Nov 5, 2022.
        //  ! androidx.core.view.accessibility.AccessibilityNodeInfoCompat.getActionSymbolicName
        private fun getActionSymbolicName(action: Int) = when (action) {
            ACTION_FOCUS -> "ACTION_FOCUS"
            ACTION_CLEAR_FOCUS -> "ACTION_CLEAR_FOCUS"
            ACTION_SELECT -> "ACTION_SELECT"
            ACTION_CLEAR_SELECTION -> "ACTION_CLEAR_SELECTION"
            ACTION_CLICK -> "ACTION_CLICK"
            ACTION_LONG_CLICK -> "ACTION_LONG_CLICK"
            ACTION_ACCESSIBILITY_FOCUS -> "ACTION_ACCESSIBILITY_FOCUS"
            ACTION_CLEAR_ACCESSIBILITY_FOCUS -> "ACTION_CLEAR_ACCESSIBILITY_FOCUS"
            ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "ACTION_NEXT_AT_MOVEMENT_GRANULARITY"
            ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY"
            ACTION_NEXT_HTML_ELEMENT -> "ACTION_NEXT_HTML_ELEMENT"
            ACTION_PREVIOUS_HTML_ELEMENT -> "ACTION_PREVIOUS_HTML_ELEMENT"
            ACTION_SCROLL_FORWARD -> "ACTION_SCROLL_FORWARD"
            ACTION_SCROLL_BACKWARD -> "ACTION_SCROLL_BACKWARD"
            ACTION_CUT -> "ACTION_CUT"
            ACTION_COPY -> "ACTION_COPY"
            ACTION_PASTE -> "ACTION_PASTE"
            ACTION_SET_SELECTION -> "ACTION_SET_SELECTION"
            ACTION_EXPAND -> "ACTION_EXPAND"
            ACTION_COLLAPSE -> "ACTION_COLLAPSE"
            ACTION_SET_TEXT -> "ACTION_SET_TEXT"
            ACTION_DISMISS -> "ACTION_DISMISS"
            android.R.id.accessibilityActionDragStart -> "ACTION_DRAG_START"
            android.R.id.accessibilityActionDragDrop -> "ACTION_DRAG_DROP"
            android.R.id.accessibilityActionDragCancel -> "ACTION_DRAG_CANCEL"
            android.R.id.accessibilityActionScrollUp -> "ACTION_SCROLL_UP"
            android.R.id.accessibilityActionScrollLeft -> "ACTION_SCROLL_LEFT"
            android.R.id.accessibilityActionScrollDown -> "ACTION_SCROLL_DOWN"
            android.R.id.accessibilityActionScrollRight -> "ACTION_SCROLL_RIGHT"
            android.R.id.accessibilityActionPageDown -> "ACTION_PAGE_DOWN"
            android.R.id.accessibilityActionPageUp -> "ACTION_PAGE_UP"
            android.R.id.accessibilityActionPageLeft -> "ACTION_PAGE_LEFT"
            android.R.id.accessibilityActionPageRight -> "ACTION_PAGE_RIGHT"
            android.R.id.accessibilityActionShowOnScreen -> "ACTION_SHOW_ON_SCREEN"
            android.R.id.accessibilityActionScrollToPosition -> "ACTION_SCROLL_TO_POSITION"
            android.R.id.accessibilityActionContextClick -> "ACTION_CONTEXT_CLICK"
            android.R.id.accessibilityActionSetProgress -> "ACTION_SET_PROGRESS"
            android.R.id.accessibilityActionMoveWindow -> "ACTION_MOVE_WINDOW"
            android.R.id.accessibilityActionShowTextSuggestions -> "ACTION_SHOW_TEXT_SUGGESTIONS"
            android.R.id.accessibilityActionShowTooltip -> "ACTION_SHOW_TOOLTIP"
            android.R.id.accessibilityActionHideTooltip -> "ACTION_HIDE_TOOLTIP"
            android.R.id.accessibilityActionPressAndHold -> "ACTION_PRESS_AND_HOLD"
            android.R.id.accessibilityActionImeEnter -> "ACTION_IME_ENTER"
            else -> "ACTION_UNKNOWN"
        }

        internal class Detector(
            private val compass: CharSequence? = COMPASS_PASS_ON,
            private val result: Result,
            val callback: BaseFunction? = null,
            private val selector: UiSelector? = UiSelector(),
        ) {

            fun detect(scriptRuntime: ScriptRuntime): Any? {
                val resultType = result.type.takeUnless { it == "" } ?: return null
                return when (resultType) {
                    in RESULT_GROUP_WIDGET -> getUiObject()
                    in RESULT_GROUP_WIDGET_COLLECTION -> getUiObjectCollection { it?.compass(compass) }
                    in RESULT_GROUP_WIDGETS -> getUiObjectArray { it?.compass(compass) }

                    in RESULT_GROUP_CONTENT -> getUiObject()?.content() ?: ""
                    in RESULT_GROUP_CONTENTS -> getUiObjectArray { it?.compass(compass)?.content() }

                    in RESULT_GROUP_POINT -> getUiObject()?.point()
                    in RESULT_GROUP_POINTS -> getUiObjectArray { it?.compass(compass)?.point() }

                    in RESULT_GROUP_SELECTOR -> when (compass) {
                        null, COMPASS_PASS_ON -> selector
                        else -> throw IllegalArgumentException(str(R.string.error_selector_result_type_with_compass))
                    }

                    in RESULT_GROUP_EXISTENCE -> when (compass) {
                        null, COMPASS_PASS_ON -> selector?.exists() == true
                        else -> getUiObject() != null
                    }

                    is List<*> -> when {
                        resultType.isEmpty() /* taken as widgets */ -> getUiObjectArray { it?.compass(compass) }
                        else -> invoke(resultType)
                    }

                    else -> invoke(resultType)
                }.let { result ->
                    if (callback.isJsNullish()) result else {
                        detectWithCallback(scriptRuntime, callback, result).let {
                            if (it.isJsUndefined()) result else it
                        }
                    }
                }.let { Context.javaToJS(it, scriptRuntime.topLevelScope) }
            }

            private fun invoke(resultType: Any?, args: Array<Any?> = emptyArray()) = (resultType as? String)?.let {
                Invoker(getUiObject(), it, args).invoke()
            }

            private fun invoke(dataList: List<*> /* [ funcName | resultType, ...args[] ] */) = try {
                invoke(dataList[0], dataList.takeLast(dataList.size - 1).toTypedArray())
            } catch (e: Exception) {
                when (dataList.size) {
                    1 -> invokeAsUiObjectArray(dataList[0], e)
                    else -> throw e
                }
            }

            private fun invokeAsUiObjectArray(resultType: Any?, e: Exception) = when (resultType) {
                in RESULT_GROUP_WIDGET -> getUiObjectArray { it?.compass(compass) }
                in RESULT_GROUP_CONTENT -> getUiObjectArray { it?.compass(compass)?.content() }
                in RESULT_GROUP_POINT -> getUiObjectArray { it?.compass(compass)?.point() }
                else -> throw e
            }

            internal abstract class Result(val type: Any? = RESULT_TYPE_WIDGET) {

                abstract fun byOne(): UiObject?

                abstract fun byAll(): UiObjectCollection

            }

            private fun getUiObject() = result.byOne()?.compass(compass)

            private fun getUiObjectCollection(transformer: (UiObject?) -> UiObject?) = UiObjectCollection
                .transform(result.byAll(), transformer)
                .let { RhinoUtils.wrap(it) }

            private fun getUiObjectArray(transformer: (UiObject?) -> Any?) = UiObjectCollection
                .mapNotNull(result.byAll(), transformer)
                .toNativeArray()

            private fun detectWithCallback(scriptRuntime: ScriptRuntime, callback: BaseFunction?, args: Array<Any?>) = callback?.let {
                RhinoUtils.callFunction(scriptRuntime, it, args)
            }

            private fun detectWithCallback(scriptRuntime: ScriptRuntime, callback: BaseFunction?, arg: Any?) = callback?.let {
                detectWithCallback(scriptRuntime, it, arrayOf(arg))
            }

        }

        private class Invoker(val w: UiObject?, val name: String, val params: Array<Any?>?) {

            fun invoke(): Any? = when {
                w == null -> null
                name.isEmpty() -> null
                else -> Interceptor(name, params).let { interceptor ->
                    try {
                        invokeMethod(w, interceptor.name, interceptor)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (isIgnoredException(e)) return null
                        try {
                            val getterName = "get${interceptor.name.replaceFirstChar { s -> s.uppercase() }}"
                            invokeMethod(w, getterName, interceptor)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (isIgnoredException(e)) return null
                            throw IllegalResultTypeException(name, params)
                        }
                    }
                }
            }

            private fun invokeMethod(w: UiObject, getterName: String, interceptor: Interceptor): Any? = w.javaClass.getMethod(getterName, *interceptor.classes).invoke(w, *interceptor.args)

            private fun isIgnoredException(e: Exception): Boolean {
                return e.cause is NullPointerException ||
                        e.cause is InterruptedException ||
                        e.cause?.cause is InterruptedException
            }

        }

        private class Interceptor(val name: String, params: Array<Any?>?) {

            var classes: Array<out Class<*>> = emptyArray()
            var args: Array<Any?> = emptyArray()

            init {
                if (params != null) {
                    if (has(name)) {
                        classes = interceptedMap[name]!!.args
                        args = params.mapIndexed { i, param ->
                            param.also {
                                if (it is Double) {
                                    when (classes[i]) {
                                        cIntPrim -> return@mapIndexed it.toInt()
                                        cFloatPrim -> return@mapIndexed it.toFloat()
                                    }
                                }
                            }
                        }.toTypedArray()
                    } else {
                        classes = params.map { params.javaClass }.toTypedArray()
                        args = params
                    }
                }
            }

            companion object {

                private val cFloatPrim = Float::class.javaPrimitiveType!!
                private val cIntPrim: Class<Int> = Int::class.javaPrimitiveType!!
                private val cString = String::class.java
                private val cBoolean = Boolean::class.java

                val interceptedMap: Map<String, Intercepted> by lazy {
                    val numberClassMap: Map<Array<out Class<*>>, Array<String>> = mapOf(
                        arrayOf(cIntPrim) to arrayOf(
                            "child", "getChild", "performAction", "setDrawingOrder", "setInputType",
                            "setLiveRegion", "setMaxTextLength", "setMovementGranularities",
                        ),
                        arrayOf(cString) to arrayOf(
                            "compass",
                            "findAccessibilityNodeInfosByText",
                            "findAccessibilityNodeInfosByViewId",
                            "setClassName",
                            "setContentDescription",
                            "setError",
                            "setHintText",
                            "setPackageName",
                            "setPaneTitle",
                            "setRoleDescription",
                            "setStateDescription",
                            "setText",
                            "setTooltipText",
                            "setViewIdResourceName",
                        ),
                        arrayOf(cBoolean) to arrayOf(
                            "setAccessibilityFocused",
                            "setCanOpenPopup",
                            "setCheckable",
                            "setChecked",
                            "setClickable",
                            "setEditable",
                            "setEnabled",
                            "setFocusable",
                            "setFocused",
                            "setHeading",
                            "setImportantForAccessibility",
                            "setLongClickable",
                            "setMultiLine",
                            "setPassword",
                            "setScreenReaderFocusable",
                            "setScrollable",
                            "setSelected",
                            "setShowingHintText",
                            "setTextEntryKey",
                            "setVisibleToUser",
                            "setContentInvalid",
                            "setContextClickable",
                            "setDismissable",
                        ),
                        arrayOf(cFloatPrim) to arrayOf("setProgress"),
                        arrayOf(cIntPrim, cIntPrim) to arrayOf("setTextSelection", "setSelection", "scrollTo"),
                    )
                    numberClassMap.entries.map { entry: Map.Entry<Array<out Class<*>>, Array<String>> ->
                        entry.value.map { Intercepted(it, *entry.key) }
                    }.flatten().associateBy { it.name }
                }

                fun has(name: String) = interceptedMap.containsKey(name)

            }

        }

        private class Intercepted(val name: String, vararg val args: Class<*>)

        private class IllegalResultTypeException : IllegalArgumentException {

            constructor(s: String) : super(str(R.string.error_illegal_ui_object_result_type, s))

            constructor(resultType: String, resultParams: Array<Any?>?) : super(
                when (resultParams?.isNotEmpty()) {
                    true -> str(
                        R.string.error_unknown_picker_result_type_with_params,
                        resultType,
                        "[${resultParams.joinToString { it.toString() }}]"
                    )
                    else -> str(R.string.error_unknown_picker_result_type, resultType)
                }
            )

        }

    }

}
