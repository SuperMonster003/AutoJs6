package org.autojs.autojs.core.automator

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoAllocator
import org.autojs.autojs.core.accessibility.AccessibilityNodeInfoHelper
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.UiSelectorQuery
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.diagnostics.NodeDump
import org.autojs.autojs.core.automator.flow.MissingPolicy
import org.autojs.autojs.core.automator.flow.Stability
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassParser
import org.autojs.autojs.core.automator.pick.CompassSyntaxException
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.core.automator.pick.PickResultAdapter
import org.autojs.autojs.core.automator.pick.PickResultType
import org.autojs.autojs.core.automator.query.NodeWaits
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.query.Poller
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.automator.DumpArguments
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.DisplayUtils.toRoundIntX
import org.autojs.autojs.util.DisplayUtils.toRoundIntY
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.abs

/**
 * The platform node as scripts see it: an [AccessibilityNodeInfoCompat] with the navigation,
 * property and action methods of [UiNode] (filters, search algorithms and the compass walk face
 * that interface, so they can be unit-tested over an in-memory tree). The pick machinery
 * (`detect`, result types) lives in `core/automator/pick`, the summary / fingerprint in
 * [UiObjectSummary]; this class keeps the platform binding.
 * zh-CN: 脚本所见的平台节点: 带有 [UiNode] 的导航, 属性与动作方法的 [AccessibilityNodeInfoCompat] (过滤器, 搜索算法与
 * 罗盘导航面向该接口, 因而可以在内存节点树上做单元测试). 拾取机制 (`detect`, 结果类型) 在 `core/automator/pick`,
 * 摘要 / 指纹在 [UiObjectSummary]; 本类保留平台绑定.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
@Suppress("unused", "DEPRECATION", "MemberVisibilityCanBePrivate")
open class UiObject private constructor(
    info: Any?,
    private val allocator: AccessibilityNodeInfoAllocator?,
    private val depth: Int,
    // The bridge of the query the node came from (null for nodes created elsewhere); it lends
    // clickBounds() the script's gesture dispatcher.
    // zh-CN: 节点所来自查询的 bridge (其它途径创建的节点为 null); 为 clickBounds() 提供脚本的手势派发器.
    private val bridge: AccessibilityBridge?,
    indexInParentSupplier: (UiObject) -> Int,
) : AccessibilityNodeInfoCompat(info), UiObjectActions, UiNode<UiObject> {

    // Computed on first use: a node reached through parent() would otherwise scan its
    // grandparent's children on every step up.
    // zh-CN: 首次使用时计算: 否则经 parent() 到达的节点每上溯一级都要扫描祖父节点的全部子节点.
    private val indexInParent: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { indexInParentSupplier(this) }

    private val standaloneBridge by lazy { AutoJs.instance.createAccessibilityBridge() }

    // The bounds on screen, read once per node (P2.1): the eight metrics and the bounds filters read the four fields
    // instead of copying a new Rect each time; refresh() drops them, as it updates the platform node they come from.
    // zh-CN: 屏幕边界, 每个节点只读取一次 (P2.1): 八个度量与 bounds 过滤器读这四个字段, 不再每次复制一个新 Rect; refresh() 更新其来源的平台节点, 同时丢弃它们.
    @Volatile
    private var boundsRead = false
    private var cachedLeft = 0
    private var cachedTop = 0
    private var cachedRight = 0
    private var cachedBottom = 0

    private fun readBounds() {
        if (boundsRead) return
        val rect = Rect()
        getBoundsInScreen(rect)
        cachedLeft = rect.left
        cachedTop = rect.top
        cachedRight = rect.right
        cachedBottom = rect.bottom
        boundsRead = true
    }

    constructor(
        info: Any?,
        allocator: AccessibilityNodeInfoAllocator?,
        depth: Int,
        indexInParent: Int,
    ) : this(info, allocator, depth, null, { indexInParent })

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
            UiObject(node.unwrap(), allocator, depth - 1, bridge, ::indexAmongSiblings)
        }
    }.onFailure {
        // FIXME by Stardust on May 5, 2017.
        it.printStackTrace()
    }.getOrNull()

    // The index of [node] among its parent's children: -1 when not found, 0 without a parent, -1
    // when the platform refuses (a recycled node).
    // zh-CN: [node] 在其父节点的子节点中的序号: 找不到为 -1, 无父节点为 0, 平台拒绝 (已回收的节点) 为 -1.
    private fun indexAmongSiblings(node: UiObject): Int = runCatching {
        // Compared as platform nodes: AccessibilityNodeInfoCompat.equals also requires the same class.
        // zh-CN: 以平台节点比较: AccessibilityNodeInfoCompat.equals 还要求类相同.
        val info = node.unwrap()
        node.getParent()?.run {
            (0 until childCount).firstOrNull { getChild(it)?.unwrap() == info } ?: -1
        } ?: 0
    }.getOrDefault(-1)

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
            super.getChild(i)?.let { UiObject(it.unwrap(), allocator, depth + 1, bridge) { i } }
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

    // A new Rect each call: scripts may change the one they get.
    // zh-CN: 每次调用返回新的 Rect: 脚本可能修改拿到的对象.
    open fun bounds(): Rect {
        readBounds()
        return Rect(cachedLeft, cachedTop, cachedRight, cachedBottom)
    }

    fun boundsInScreen() = bounds()

    fun boundsLeft(): Int = readBounds().let { cachedLeft }

    override fun left() = boundsLeft()

    fun boundsTop(): Int = readBounds().let { cachedTop }

    override fun top() = boundsTop()

    fun boundsRight(): Int = readBounds().let { cachedRight }

    override fun right() = boundsRight()

    fun boundsBottom(): Int = readBounds().let { cachedBottom }

    override fun bottom() = boundsBottom()

    fun boundsWidth(): Int = readBounds().let { cachedRight - cachedLeft }

    override fun width() = boundsWidth()

    fun boundsHeight(): Int = readBounds().let { cachedBottom - cachedTop }

    override fun height() = boundsHeight()

    // The rounding of Rect.centerX() / exactCenterX() and of their Y twins.
    // zh-CN: 与 Rect.centerX() / exactCenterX() 及其 Y 方向版本相同的取整.
    fun boundsCenterX(): Int = readBounds().let { (cachedLeft + cachedRight) shr 1 }

    override fun centerX() = boundsCenterX()

    fun boundsExactCenterX(): Float = readBounds().let { (cachedLeft + cachedRight) * 0.5f }

    fun exactCenterX() = boundsExactCenterX()

    fun boundsCenterY(): Int = readBounds().let { (cachedTop + cachedBottom) shr 1 }

    override fun centerY() = boundsCenterY()

    fun boundsExactCenterY(): Float = readBounds().let { (cachedTop + cachedBottom) * 0.5f }

    fun exactCenterY() = boundsExactCenterY()

    fun point() = Point(exactCenterX().toDouble(), exactCenterY().toDouble())

    fun center() = point()

    fun size() = Size(width().toDouble(), height().toDouble())

    /**
     * A tap gesture at the centre plus the offsets (each a fraction of the screen side when in
     * (-1, 1), pixels otherwise). Dispatched through the script's gesture dispatcher when the node
     * came from a query of a script (see [AccessibilityBridge.tap]); the node's own bounds are
     * device pixels, so the script's `setScreenMetrics` scaling is deliberately not applied.
     * zh-CN: 在中心加偏移处 (各偏移在 (-1, 1) 内为屏幕边长的比例, 否则为像素) 做点按手势. 节点来自脚本的查询时经脚本的手势
     * 派发器派发 (见 [AccessibilityBridge.tap]); 节点边界本身是设备像素, 因此有意不应用脚本 `setScreenMetrics` 的缩放.
     */
    @JvmOverloads
    fun clickBounds(offsetX: Double = 0.0, offsetY: Double = 0.0): Boolean {
        val x = centerX() + toRoundIntX(offsetX, false)
        val y = centerY() + toRoundIntY(offsetY, false)
        bridge?.tap?.let { return it.tap(x, y) }
        // Without a script's dispatcher: a standalone one; the calling thread waits while the result
        // arrives on the automator's shared callback thread, so its own Looper is left alone.
        // zh-CN: 没有脚本的派发器时: 独立派发器; 调用线程等待, 结果经 automator 共享的回调线程送达, 不触碰调用线程自己的 Looper.
        val automatorTmp = GlobalActionAutomator(ScriptRuntime.applicationContext, null) {
            standaloneBridge.ensureServiceStarted()
            standaloneBridge.service
        }
        return automatorTmp.click(x, y)
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

    // Drops the bounds read before (P2.1): the platform node now holds the current ones.
    // zh-CN: 丢弃此前读取的边界 (P2.1): 平台节点此时持有当前的边界.
    override fun refresh(): Boolean = super.refresh().also { boundsRead = false }

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

    /** The property listing of [UiObjectSummary.summary]. zh-CN: [UiObjectSummary.summary] 的属性列表. */
    fun summary(): String = UiObjectSummary.summary(this)

    override fun toString(): String = UiObjectSummary.describe(this, UiObject::class.java.simpleName)

    /** The exhaustive comparison of [UiObjectSummary.isSimilar]. zh-CN: [UiObjectSummary.isSimilar] 的详尽比较. */
    fun isSimilar(other: Any?): Boolean = UiObjectSummary.isSimilar(this, other)

    /**
     * A cheap hash of the node's own state (bounds, text, description, class name, child count,
     * key boolean properties); see [UiObjectSummary.fingerprint].
     * zh-CN: 节点自身状态 (边界, 文本, 描述, 类名, 子节点数, 关键布尔属性) 的廉价哈希; 见 [UiObjectSummary.fingerprint].
     */
    fun fingerprint(): Int = UiObjectSummary.fingerprint(this)

    /**
     * The node's own properties as a JavaScript object (`JSON.stringify(node)` calls this; the
     * children are not included, see [dumpSubtree]): the [NodeDump.DEFAULT_JSON] set.
     * zh-CN: 节点自身属性构成的 JavaScript 对象 (`JSON.stringify(node)` 会调用它; 不含子节点, 见 [dumpSubtree]): 即 [NodeDump.DEFAULT_JSON] 集合.
     */
    @JvmOverloads
    fun toJSON(key: Any? = null): Any? = RhinoUtils.withRhinoContext { cx ->
        with(RhinoUtils) { toJsValue(cx.getCurrentTopCallScopeOrDefault(), NodeDump.properties(this@UiObject, NodeDump.DEFAULT_JSON)) }
    }

    /**
     * The subtree under this node as text / JSON / XML, with the options of `auto.dump`
     * (`format`, `maxDepth`, `properties`, `visibleOnly`, `maxNodes`, `indent`; see [NodeDump]).
     * zh-CN: 以本节点为根的子树的文本 / JSON / XML, 选项同 `auto.dump` (`format`, `maxDepth`, `properties`, `visibleOnly`, `maxNodes`, `indent`; 见 [NodeDump]).
     */
    @JvmOverloads
    fun dumpSubtree(options: Any? = null): String = NodeDump.dump(this, DumpArguments.parse(options, allowRoot = false).options).text

    // Captures current state; refreshing it won't affect the original.
    // zh-CN: 捕获当前状态; 刷新它不会影响原始对象.
    fun snapshot(): UiObject {
        val raw = unwrap()
        val rawCopy = AccessibilityNodeInfo.obtain(raw)
        return UiObject(rawCopy, allocator, depth, bridge) { indexInParent }
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

    /**
     * Whether the node has left the window (roadmap P5.3): refreshing a copy of it fails, so its
     * own properties are what they were and actions on it no longer reach a view. The node itself
     * is not refreshed. zh-CN: 节点是否已离开窗口 (Roadmap P5.3): 刷新它的副本失败, 其自身属性停留在过去, 对它的动作不再到达视图.
     * 节点本身不被刷新.
     */
    fun isStale(): Boolean {
        val probe = snapshot()
        try {
            return !probe.refresh()
        } finally {
            NodeLifecycle.default.release(probe)
        }
    }

    /**
     * Waits until the node is gone (roadmap P5.3): no longer refreshable, or refreshed but not
     * visible to the user (a dismissed dialog, a finished progress bar). `true` when it went within
     * [timeout] milliseconds (0 or less: no limit), polled every [interval] milliseconds and woken
     * early by accessibility events under `auto.setFlags(["eventAssistedPolling"])`. Not for the
     * UI thread. zh-CN: 等待节点消失 (Roadmap P5.3): 不能再刷新, 或刷新后对用户不可见 (已关闭的对话框, 已完成的进度条). 在 [timeout]
     * 毫秒内 (0 或负数: 不限) 消失时为 `true`, 每 [interval] 毫秒轮询一次, `auto.setFlags(["eventAssistedPolling"])` 下由无障碍事件
     * 提前唤醒. 不可在 UI 线程调用.
     */
    @JvmOverloads
    fun waitUntilGone(timeout: Long = DEFAULT_WAIT_TIMEOUT, interval: Long = DEFAULT_WAIT_INTERVAL): Boolean {
        ensureNotUiThread("waitUntilGone")
        return NodeWaits.untilGone(waitPoller(), waitPolicy("waitUntilGone", timeout, interval)) { isGone() }
    }

    /**
     * Waits until the node's own state (bounds, text, description, class name, child count, key
     * flags: [fingerprint]) has stayed the same for [stableFor] milliseconds (roadmap P5.3),
     * refreshing this node as it samples, so that it reflects the settled state on return. `true`
     * once stable within [timeout] milliseconds (0 or less: no limit); `false` on timeout, or as
     * soon as the node is gone. Polled every [interval] milliseconds, event-assisted like
     * [waitUntilGone]. Not for the UI thread.
     * zh-CN: 等待节点自身状态 (边界, 文本, 描述, 类名, 子节点数, 关键标志: [fingerprint]) 保持不变达 [stableFor] 毫秒 (Roadmap P5.3),
     * 采样时刷新本节点, 返回时它反映稳定后的状态. 在 [timeout] 毫秒 (0 或负数: 不限) 内稳定为 `true`; 超时或节点一旦消失为 `false`.
     * 每 [interval] 毫秒轮询一次, 事件辅助同 [waitUntilGone]. 不可在 UI 线程调用.
     */
    @JvmOverloads
    fun waitForStable(stableFor: Long = Stability.DEFAULT_STABLE_FOR, timeout: Long = DEFAULT_WAIT_TIMEOUT, interval: Long = DEFAULT_WAIT_INTERVAL): Boolean {
        ensureNotUiThread("waitForStable")
        if (stableFor < 0) {
            throw WrappedIllegalArgumentException("Argument \"stableFor\" ${stableFor.jsBrief()} for UiObject#waitForStable must be greater than or equal to 0")
        }
        val stability = Stability(stableFor = stableFor, missing = MissingPolicy.FAIL)
        return NodeWaits.untilStable(waitPoller(), waitPolicy("waitForStable", timeout, interval), { SystemClock.uptimeMillis() }, stability) {
            if (refresh()) this else null
        }
    }

    private fun isGone(): Boolean {
        val probe = snapshot()
        try {
            return !probe.refresh() || !probe.isVisibleToUser
        } finally {
            NodeLifecycle.default.release(probe)
        }
    }

    // The script's poller: the bridge's event pulse under FLAG_EVENT_ASSISTED_POLLING, else plain sleeps.
    // zh-CN: 脚本的轮询器: FLAG_EVENT_ASSISTED_POLLING 下为 bridge 的事件脉冲, 否则为普通休眠.
    private fun waitPoller(): Poller = bridge?.takeIf { it.isEventAssistedPolling }?.pulse?.poller ?: UiSelectorQuery.defaultPoller

    private fun waitPolicy(function: String, timeout: Long, interval: Long): PollPolicy {
        if (interval < 0) {
            throw WrappedIllegalArgumentException("Argument \"interval\" ${interval.jsBrief()} for UiObject#$function must be greater than or equal to 0")
        }
        val policy = PollPolicy.within(timeout, interval)
        return if (bridge?.isEventAssistedPolling == true) policy.assisted() else policy
    }

    private fun ensureNotUiThread(function: String) {
        if (RhinoUtils.isUiThread()) {
            throw IllegalThreadStateException(str(R.string.error_function_called_in_ui_thread, "UiObject#$function"))
        }
    }

    companion object {

        internal const val ACTION_APPEND_TEXT = 0x00200001
        internal const val COMPASS_PASS_ON = Compass.PASS_ON

        /** The timeout of `waitUntilGone()` / `waitForStable()` without an argument. zh-CN: 不带参数的 `waitUntilGone()` / `waitForStable()` 的超时. */
        const val DEFAULT_WAIT_TIMEOUT = 10_000L

        /** Their poll interval, the one of `findOne(timeout)`. zh-CN: 它们的轮询间隔, 同 `findOne(timeout)`. */
        const val DEFAULT_WAIT_INTERVAL = PollPolicy.DEFAULT_INTERVAL
        internal const val RESULT_TYPE_WIDGET = PickResultType.WIDGET

        @JvmStatic
        fun isCompass(s: Any?): Boolean = Compass.isCompass(s)

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun ensureCompass(s: CharSequence?) {
            s ?: return
            require(isCompass(s)) { str(R.string.error_invalid_compass, s) }
        }

        /**
         * `detect(w, compass, resultType, callback)` with the positions told by the caller; see [PickArguments.legacyDetect].
         * zh-CN: 位置已由调用方区分的 `detect(w, compass, resultType, callback)`; 见 [PickArguments.legacyDetect].
         */
        @JvmStatic
        fun detect(scriptRuntime: ScriptRuntime, w: UiObject?, compass: CharSequence?, resultType: Any?, callback: Callable? = null): Any? {
            return PickResultAdapter.legacyDetect(scriptRuntime, w, compass, resultType, callback)
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

        /**
         * A root node of a script's query: [allocator] registers what the node hands out, [bridge]
         * lends `clickBounds()` the script's gesture dispatcher; both are inherited by the nodes
         * reached from it.
         * zh-CN: 脚本查询的根节点: [allocator] 登记节点交出的内容, [bridge] 为 `clickBounds()` 提供脚本的手势派发器;
         * 二者都被由此到达的节点继承.
         */
        internal fun createRoot(root: AccessibilityNodeInfo?, allocator: AccessibilityNodeInfoAllocator?, bridge: AccessibilityBridge? = null) =
            UiObject(root, allocator, 0, bridge) { -1 }

    }

}
