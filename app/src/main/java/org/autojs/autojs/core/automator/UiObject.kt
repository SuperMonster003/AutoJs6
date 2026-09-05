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
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassParser
import org.autojs.autojs.core.automator.pick.CompassSyntaxException
import org.autojs.autojs.rhino.JsArgumentCoercion
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsUndefined
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.DisplayUtils.toRoundIntX
import org.autojs.autojs.util.DisplayUtils.toRoundIntY
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.abs

/**
 * The platform node as scripts see it: an [AccessibilityNodeInfoCompat] with the navigation,
 * property and action methods of [UiNode] (filters, search algorithms and the compass walk face
 * that interface, so they can be unit-tested over an in-memory tree).
 * zh-CN: 脚本所见的平台节点: 带有 [UiNode] 的导航, 属性与动作方法的 [AccessibilityNodeInfoCompat] (过滤器, 搜索算法与
 * 罗盘导航面向该接口, 因而可以在内存节点树上做单元测试).
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
@Suppress("unused", "DEPRECATION", "MemberVisibilityCanBePrivate")
open class UiObject(
    info: Any?,
    private val allocator: AccessibilityNodeInfoAllocator?,
    private val depth: Int,
    private val indexInParent: Int,
) : AccessibilityNodeInfoCompat(info), UiObjectActions, UiNode<UiObject> {

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

    override fun parent(): UiObject? = runCatching {
        super.getParent()?.let { node ->
            UiObject(node.unwrap(), allocator, depth - 1, node.parent?.run {
                // @Dubious by SuperMonster003 on May 26, 2022.
                //  ! May lead to poor performance?
                //  ! zh-CN: 可能导致性能不佳?
                (0 until childCount).firstOrNull { getChild(it) == node } ?: -1
            } ?: 0)
        }
    }.onFailure {
        // FIXME by Stardust on May 5, 2017.
        it.printStackTrace()
    }.getOrNull()

    fun parent(i: Int): UiObject? {
        require(i >= 0) { "Index for UiObject#parent cannot be negative ($i)" }
        return compass("p$i")
    }

    override fun child(i: Int): UiObject? = when {
        i >= childCount -> {
            null
        }
        i < 0 -> when (val targetIdx = i + childCount) {
            in 0 until childCount -> child(targetIdx)
            else -> null
        }
        else -> runCatching {
            // @Caution by SuperMonster003 on Jun 19, 2025.
            //  ! `super.getChild` may cause ArrayIndexOutOfBoundsException when `i` is out of range of [0..childCound).
            //  ! zh-CN: 当 `i` 超出 [0..childCount) 范围时可能导致 ArrayIndexOutOfBoundsException 异常.
            // @Dubious by SuperMonster003 on Jun 19, 2025.
            //  ! It seems that the situation above only happens on API Level 29 (Android 10) [Q] ?
            //  ! zh-CN: 似乎上述情况只发生在 API Level 29 (Android 10) [Q] ?
            super.getChild(i)?.let { UiObject(it.unwrap(), allocator, depth + 1, i) }
        }.onFailure {
            // FIXME by Stardust on May 5, 2017.
            it.printStackTrace()
        }.getOrNull()
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

    override fun childCount() = childCount

    override fun hasChildren() = childCount > 0

    fun firstChild() = child(0)

    fun lastChild() = child(childCount - 1)

    fun children() = List(childCount) { child(it) }.let { UiObjectCollection.of(it) }

    fun siblings() = List(siblingCount()) { sibling(it) }.let { UiObjectCollection.of(it) }

    override fun indexInParent() = indexInParent

    @JvmOverloads
    fun find(selector: UiSelector? = UiSelector()): UiObjectCollection = (selector ?: UiSelector()).findOf(this)

    fun findOne(selector: UiSelector): UiObject? = selector.findOneOf(this)

    open fun bounds() = AccessibilityNodeInfoHelper.getBoundsInScreen(this)

    fun boundsInScreen() = bounds()

    fun boundsLeft() = bounds().left

    override fun left() = boundsLeft()

    fun boundsTop() = bounds().top

    override fun top() = boundsTop()

    fun boundsRight() = bounds().right

    override fun right() = boundsRight()

    fun boundsBottom() = bounds().bottom

    override fun bottom() = boundsBottom()

    fun boundsWidth() = bounds().width()

    override fun width() = boundsWidth()

    fun boundsHeight() = bounds().height()

    override fun height() = boundsHeight()

    fun boundsCenterX() = bounds().centerX()

    override fun centerX() = boundsCenterX()

    fun boundsExactCenterX() = bounds().exactCenterX()

    fun exactCenterX() = boundsExactCenterX()

    fun boundsCenterY() = bounds().centerY()

    override fun centerY() = boundsCenterY()

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
            accessibilityBridge.service
        }
        return automatorTmp.click(
            centerX() + toRoundIntX(offsetX, false),
            centerY() + toRoundIntY(offsetY, false),
        )
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("boundsInScreen() or bounds()"))
    open fun boundsInParent() = AccessibilityNodeInfoHelper.getBoundsInParent(this)

    override fun drawingOrder() = drawingOrder

    fun fullId(): String? = viewIdResourceName

    override fun id(): String? = fullId()

    fun idEntry() = fullId()?.let {
        when (it.contains(UiSelector.ID_IDENTIFIER)) {
            true -> it.split(UiSelector.ID_IDENTIFIER).last()
            else -> it
        }
    }

    fun simpleId(): String? = idEntry()

    @SuppressLint("DiscouragedApi")
    override fun idHex(): String? {
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

    override fun text() = text?.toString() ?: ""

    override fun getText(): CharSequence? = takeUnless { isPassword }?.let { super.getText() } ?: ""

    override fun desc(): String? = contentDescription?.toString()

    override fun content(): String = desc() ?: text()

    override fun className(): String? = className?.toString()

    override fun packageName(): String? = packageName?.toString()

    override fun depth() = depth

    override fun checkable() = isCheckable

    override fun checked() = isChecked

    override fun focusable() = isFocusable

    override fun focused() = isFocused

    override fun visibleToUser() = isVisibleToUser

    override fun accessibilityFocused() = isAccessibilityFocused

    override fun selected() = isSelected

    override fun clickable() = isClickable

    override fun longClickable() = isLongClickable

    override fun enabled() = isEnabled

    override fun password() = isPassword

    override fun scrollable() = isScrollable

    override fun editable() = isEditable

    override fun contentInvalid() = isContentInvalid

    override fun contextClickable() = isContextClickable

    override fun multiLine() = isMultiLine

    override fun dismissable() = isDismissable

    override fun row() = collectionItemInfo?.rowIndex ?: -1

    override fun column() = collectionItemInfo?.columnIndex ?: -1

    override fun rowSpan() = collectionItemInfo?.rowSpan ?: -1

    override fun columnSpan() = collectionItemInfo?.columnSpan ?: -1

    override fun rowCount() = collectionInfo?.rowCount ?: 0

    override fun columnCount() = collectionInfo?.columnCount ?: 0

    @ScriptInterface
    override fun actionNames() = actionList.map { action ->
        UiNodeActions.symbolicName(action.id).let { actionName ->
            if (actionName == UiNodeActions.UNKNOWN && action.label != null) {
                action.label.toString()
            } else {
                actionName
            }
        }
    }

    @ScriptInterface
    override fun hasAction(vararg actions: Any): Boolean = UiNodeActions.hasAll(actionNames(), actions)

    // The walk itself is Compass (Android-free); this wrapper adds the localized error messages.
    // zh-CN: 行走本身在 Compass 中 (不依赖 Android); 此包装只补充本地化的错误信息.
    @Throws(IllegalArgumentException::class)
    fun compass(compassArg: CharSequence?): UiObject? {
        val compass = compassArg?.takeUnless { it == COMPASS_PASS_ON }?.also { ensureCompass(it) } ?: return this
        val steps = try {
            CompassParser.parse(compass)
        } catch (e: CompassSyntaxException) {
            throw IllegalArgumentException(str(R.string.error_invalid_rest_compass, e.rest))
        }
        return Compass.navigate(this, steps)
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

    // The compat accessors register what they hand out with the runtime's allocator (which works on
    // the raw node, so this does not recurse); the navigation helpers above do not, see NodeLifecycle.
    // zh-CN: compat 访问器把交出的节点登记到运行时的分配器 (它在原始节点上操作, 因此不会递归); 上方的导航方法不登记, 见 NodeLifecycle.
    override fun getChild(index: Int): AccessibilityNodeInfoCompat? {
        return when (val allocator = allocator) {
            null -> super.getChild(index)
            else -> allocator.getChild(this, index)
        }
    }

    override fun getParent(): AccessibilityNodeInfoCompat? {
        return when (val allocator = allocator) {
            null -> super.getParent()
            else -> allocator.getParent(this)
        }
    }

    override fun findAccessibilityNodeInfosByText(text: String): List<AccessibilityNodeInfoCompat> {
        return when (val allocator = allocator) {
            null -> super.findAccessibilityNodeInfosByText(text)
            else -> allocator.findAccessibilityNodeInfosByText(this, text)
        }
    }

    override fun findAccessibilityNodeInfosByViewId(viewId: String): List<AccessibilityNodeInfoCompat> {
        return when (val allocator = allocator) {
            null -> super.findAccessibilityNodeInfosByViewId(viewId)
            else -> allocator.findAccessibilityNodeInfosByViewId(this, viewId)
        }
    }

    // With an allocator the node is released through the runtime's lifecycle and forgotten by the
    // allocator, so that the script's exit pass does not try again.
    // zh-CN: 有分配器时节点经运行时的生命周期对象释放并从分配器中移除, 脚本退出时的释放过程不会再次尝试.
    @Deprecated("Deprecated in Java")
    override fun recycle() {
        try {
            when (val allocator = allocator) {
                null -> super.recycle()
                else -> allocator.recycle(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun summary(): String = listOf(

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
    ).let { StringUtils.toFormattedSummary(it) }

    override fun toString(): String {
        val simpledClassName = "$className".substringAfterLast(".")
        return "[${UiObject::class.java.simpleName}] $simpledClassName ${summary()}"
    }

    fun isSimilar(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiObject) return false

        // @Reference to Hunter1023 (https://github.com/Hunter1023) by SuperMonster003 on Mar 28, 2025.
        //  ! http://issues.autojs6.com/288#issuecomment-2692248348
        val info = unwrap() ?: return false

        return info == other.unwrap() &&
                bounds() == other.bounds() &&
                packageName() == other.packageName() &&
                parent == other.parent &&
                id() == other.id() &&
                fullId() == other.fullId() &&
                idHex() == other.idHex() &&
                desc() == other.desc() &&
                text() == other.text() &&
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
                "${firstSibling() ?: ""}" == "${other.firstSibling()}" &&
                "${lastSibling() ?: ""}" == "${other.lastSibling()}" &&
                "${firstChild() ?: ""}" == "${other.firstChild()}" &&
                "${lastChild() ?: ""}" == "${other.lastChild()}"
    }

    // Captures current state; refreshing it won't affect the original.
    // zh-CN: 捕获当前状态; 刷新它不会影响原始对象.
    fun snapshot(): UiObject {
        val raw = unwrap()
        val rawCopy = AccessibilityNodeInfo.obtain(raw)
        return UiObject(rawCopy, allocator, depth, indexInParent)
    }

    @JvmOverloads
    fun isShifted(tolerance: Double = 0.0): Boolean {
        val probe = snapshot()
        try {
            val a = probe.bounds() /* Before. */
            if (!probe.refresh()) return true
            val b = probe.bounds() /* After. */
            return when {
                tolerance < 0 -> throw WrappedIllegalArgumentException(
                    "Argument \"tolerance\" ${tolerance.jsBrief()} for UiObject#isShifted must be greater than or equal to 0",
                )
                tolerance < 1 -> (width() to height()).let { (w, h) ->
                    return@let abs(a.left - b.left) > tolerance * w
                            || abs(a.top - b.top) > tolerance * h
                            || abs(a.right - b.right) > tolerance * w
                            || abs(a.bottom - b.bottom) > tolerance * h
                }
                else -> let {
                    return@let abs(a.left - b.left) > tolerance
                            || abs(a.top - b.top) > tolerance
                            || abs(a.right - b.right) > tolerance
                            || abs(a.bottom - b.bottom) > tolerance
                }
            }
        } finally {
            NodeLifecycle.default.release(probe)
        }
    }

    companion object {

        internal const val ACTION_APPEND_TEXT = 0x00200001
        internal const val COMPASS_PASS_ON = Compass.PASS_ON
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
        fun isCompass(s: Any?): Boolean = Compass.isCompass(s)

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun ensureCompass(s: CharSequence?) {
            s ?: return
            require(isCompass(s)) { str(R.string.error_invalid_compass, s) }
        }

        @JvmStatic
        fun detect(scriptRuntime: ScriptRuntime, w: UiObject?, compass: CharSequence?, resultType: Any?, callback: Callable? = null): Any? {
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

        internal class Detector(
            private val compass: CharSequence? = COMPASS_PASS_ON,
            private val result: Result,
            val callback: Callable? = null,
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

            private fun detectWithCallback(scriptRuntime: ScriptRuntime, callback: Callable?, args: Array<Any?>) = callback?.let {
                RhinoUtils.callFunction(
                    scriptRuntime,
                    it,
                    null as VarScope?,
                    Undefined.SCRIPTABLE_UNDEFINED,
                    args,
                )
            }

            private fun detectWithCallback(scriptRuntime: ScriptRuntime, callback: Callable?, arg: Any?) = callback?.let {
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
                            classes.getOrNull(i)?.let { JsArgumentCoercion.coerce(it, param) } ?: param
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
