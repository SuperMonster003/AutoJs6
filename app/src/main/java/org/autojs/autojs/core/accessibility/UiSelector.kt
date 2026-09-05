@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.autojs.autojs.core.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObject.Companion.COMPASS_PASS_ON
import org.autojs.autojs.core.automator.UiObject.Companion.Detector
import org.autojs.autojs.core.automator.UiObject.Companion.Detector.Result
import org.autojs.autojs.core.automator.UiObject.Companion.RESULT_TYPE_WIDGET
import org.autojs.autojs.core.automator.UiObjectActions
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.UiObjectCollection.Companion.EMPTY
import org.autojs.autojs.core.automator.UiObjectCollection.Companion.of
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.selector.SelectorMapException
import org.autojs.autojs.core.automator.selector.SelectorMapParser
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.search.BFS
import org.autojs.autojs.core.automator.search.DFS
import org.autojs.autojs.core.automator.search.SearchAlgorithm
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.regexp.NativeRegExp
import java.math.BigInteger
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The script-facing selector: the builder methods are generated from [SelectorRegistry] into
 * [UiSelectorBuilders] and routed back here through [applyEntry]; this class keeps the queries
 * (`find*`, `exists`, `untilFind*`; the polling ones run as a [UiSelectorQuery]), the search
 * settings and the `pickup` machinery.
 * zh-CN: 面向脚本的选择器: builder 方法由 [SelectorRegistry] 生成到 [UiSelectorBuilders] 并经 [applyEntry] 转回此处;
 * 本类保留查询 (`find*`, `exists`, `untilFind*`; 轮询的查询以 [UiSelectorQuery] 执行), 搜索设置与 `pickup` 机制.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
open class UiSelector : UiSelectorBuilders, UiObjectActions, StringReadable {

    internal var selector = Selector()

    internal var searchAlgorithm: SearchAlgorithm = DFS

    private val mAccessibilityBridge: AccessibilityBridge?

    private val mAllocator: AccessibilityNodeInfoAllocator?

    constructor() : this(AccessibilityService.bridge)

    constructor(accessibilityBridge: AccessibilityBridge?) : this(accessibilityBridge, null)

    constructor(accessibilityBridge: AccessibilityBridge?, allocator: AccessibilityNodeInfoAllocator?) : super() {
        mAccessibilityBridge = accessibilityBridge
        mAllocator = allocator
    }

    override fun applyEntry(name: String, vararg args: Any?): UiSelector = also {
        when (SelectorRegistry.apply(this, name, args.asList())) {
            SelectorRegistry.Outcome.APPLIED -> Unit
            SelectorRegistry.Outcome.NO_SUCH_PROPERTY -> throw IllegalStateException("Selector property $name is not registered")
            SelectorRegistry.Outcome.NO_MATCHING_OVERLOAD -> throw IllegalArgumentException("Arguments ${args.contentToString()} do not fit selector property $name")
        }
    }

    @ScriptInterface
    fun findOnce(): UiObject? = findOnce(0)

    @ScriptInterface
    fun findOnce(index: Int): UiObject? = when {
        index >= 0 -> find(index + 1).let { found ->
            when (index) {
                in 0 until found.size() -> found[index]
                else -> null
            }
        }
        else -> find().let { found ->
            when (val reverseIndex = index + found.size()) {
                in 0 until found.size() -> found[reverseIndex]
                else -> null
            }
        }
    }

    @ScriptInterface
    fun exists() = findOnce() != null

    @ScriptInterface
    fun find(): UiObjectCollection = find(Int.MAX_VALUE)

    /**
     * A single search over the current window roots; no polling. A negative [max] keeps the last
     * `-max` nodes. zh-CN: 对当前窗口根节点的单次搜索, 不轮询. 负数 [max] 保留最后 `-max` 个节点.
     */
    @ScriptInterface
    fun find(max: Int): UiObjectCollection = when {
        max >= 0 -> findWithin(max, PollPolicy.UNBOUNDED)
        else -> findWithin(Int.MAX_VALUE, PollPolicy.UNBOUNDED).let { found ->
            val foundSize = found.size()
            when (val startIndex = foundSize + max) {
                in 0 until foundSize -> {
                    of(found.nodes.slice(startIndex until foundSize))
                }
                else -> EMPTY
            }
        }
    }

    /**
     * One attempt of a query: a single search, where a search posted to the UI thread under
     * [AccessibilityBridge.FLAG_FIND_ON_UI_THREAD] is waited for at most [budget] milliseconds
     * ([PollPolicy.UNBOUNDED]: interruptibly, without limit).
     * zh-CN: 查询的一次尝试: 单次搜索; [AccessibilityBridge.FLAG_FIND_ON_UI_THREAD] 下投递到 UI 线程的搜索至多等待
     * [budget] 毫秒 ([PollPolicy.UNBOUNDED]: 可中断地无限等待).
     */
    internal fun findWithin(max: Int, budget: Long): UiObjectCollection {
        // The bridge routes to the shared controller; without a bridge nothing can be searched anyway.
        // zh-CN: bridge 会转到共享的控制器; 没有 bridge 时也无从查找.
        mAccessibilityBridge?.ensureServiceStarted()
        return when {
            mAccessibilityBridge == null -> findImpl(max)
            isMainThread() -> findImpl(max)
            mAccessibilityBridge.flags and AccessibilityBridge.FLAG_FIND_ON_UI_THREAD == 0 -> findImpl(max)
            else -> findOnUiThread(mAccessibilityBridge, max, budget)
        }
    }

    // A FutureTask instead of the former VolatileBox.blockedGet(): the wait is bounded when the query
    // is, interruptible otherwise, and a search that never ran cannot be waited for forever.
    // zh-CN: 以 FutureTask 取代原先的 VolatileBox.blockedGet(): 查询有超时则等待有界, 否则可中断; 从未执行的搜索不会被无限等待.
    private fun findOnUiThread(bridge: AccessibilityBridge, max: Int, budget: Long): UiObjectCollection {
        val task = FutureTask { findImpl(max) }
        bridge.post(task)
        try {
            return when (budget) {
                PollPolicy.UNBOUNDED -> task.get()
                else -> task.get(budget, TimeUnit.MILLISECONDS)
            }
        } catch (e: TimeoutException) {
            task.cancel(false)
            throw IllegalStateException(str(R.string.error_find_on_ui_thread_timeout, budget))
        } catch (e: InterruptedException) {
            task.cancel(false)
            throw ScriptInterruptedException()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** A query over this selector; see [UiSelectorQuery]. zh-CN: 对本选择器的查询; 见 [UiSelectorQuery]. */
    internal fun query(policy: PollPolicy = PollPolicy.ONCE, max: Int = Int.MAX_VALUE) = UiSelectorQuery(this, policy, max)

    /**
     * Polls every 50 ms until a node is found or more than [timeout] milliseconds have passed.
     * A [timeout] of 0 or less polls until found and so may block the script thread forever.
     * zh-CN: 每 50 毫秒轮询一次, 直到找到节点或经过的时间超过 [timeout] 毫秒. [timeout] 为 0 或负数时轮询直到找到,
     * 因此可能永久阻塞脚本线程.
     */
    @ScriptInterface
    fun findOne(timeout: Long): UiObject? {
        ensureNotMainThread("findOne")
        return query(PollPolicy.within(timeout), 1).one()
    }

    /** May block the script thread forever; see [untilFindOne]. zh-CN: 可能永久阻塞脚本线程; 见 [untilFindOne]. */
    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("untilFindOne()"))
    fun findOne(): UiObject {
        ensureNotMainThread("findOne")
        return query(PollPolicy.FOREVER, 1).one()!!
    }

    /**
     * Polls every 50 ms until a node is found; may block the script thread forever (stopping the
     * script interrupts the wait). zh-CN: 每 50 毫秒轮询一次直到找到节点; 可能永久阻塞脚本线程 (停止脚本会中断等待).
     */
    @ScriptInterface
    fun untilFindOne(): UiObject {
        ensureNotMainThread("untilFindOne")
        return query(PollPolicy.FOREVER, 1).one()!!
    }

    /**
     * Polls every 50 ms until at least one node is found; may block the script thread forever
     * (stopping the script interrupts the wait).
     * zh-CN: 每 50 毫秒轮询一次直到至少找到一个节点; 可能永久阻塞脚本线程 (停止脚本会中断等待).
     */
    @ScriptInterface
    fun untilFind(): UiObjectCollection {
        ensureNotMainThread("untilFind")
        return query(PollPolicy.FOREVER).collection()
    }

    /** May block the script thread forever; see [untilFind]. zh-CN: 可能永久阻塞脚本线程; 见 [untilFind]. */
    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("untilFind()"))
    fun waitFor(): UiObjectCollection {
        ensureNotMainThread("waitFor")
        return untilFind()
    }

    /** Waits for the nodes as [untilFind] does, so may block forever. zh-CN: 如 [untilFind] 一样等待节点, 因此可能永久阻塞. */
    @ScriptInterface
    fun performAction(action: Int) = performAction(action, *emptyArray())

    /** Waits for the nodes as [untilFind] does, so may block forever. zh-CN: 如 [untilFind] 一样等待节点, 因此可能永久阻塞. */
    @ScriptInterface
    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        return untilFind().performAction(action, *arguments)
    }

    private fun ensureNotMainThread(function: String) {
        if (isMainThread()) {
            throw IllegalThreadStateException(str(R.string.error_function_called_in_ui_thread, function))
        }
    }

    override fun toString() = selector.toString()

    override fun toStringReadable() = "[${UiSelector::class.java.simpleName}: ${toString()}]"

    internal fun findOf(root: UiObject): UiObjectCollection = findOf(root, Int.MAX_VALUE)

    internal fun findOneOf(root: UiObject): UiObject? = findOf(root, 1).takeIf { it.size() > 0 }?.get(0)

    internal fun findAndReturnList(root: UiObject, max: Int = Int.MAX_VALUE): List<UiObject> = searchAlgorithm.search(root, selector, max)

    protected fun findImpl(max: Int): UiObjectCollection = findImpl(mAccessibilityBridge?.windowRoots() ?: emptyList(), max)

    protected fun findImpl(node: AccessibilityNodeInfo, max: Int): UiObjectCollection = findImpl(listOf(node), max)

    // Roots of blacklisted packages (guard mode, AccessibilityConfig) are skipped here, not by the bridge.
    // zh-CN: 黑名单包名 (保护模式, AccessibilityConfig) 的根节点在此跳过, 而非由 bridge 过滤.
    protected fun findImpl(roots: List<AccessibilityNodeInfo>, max: Int): UiObjectCollection {
        mAccessibilityBridge ?: return of(emptyList())
        fun isInWhitelist(node: AccessibilityNodeInfo) = !mAccessibilityBridge.config.isInBlacklist("${node.packageName}")
        val result = mutableListOf<UiObject?>()
        for (root in roots) {
            if (isInWhitelist(root)) {
                result.addAll(findAndReturnList(UiObject.createRoot(root, mAllocator), max - result.size))
                if (result.size >= max) break
            }
        }
        return of(result)
    }

    private fun findOf(root: UiObject, max: Int): UiObjectCollection = of(findAndReturnList(root, max))

    internal fun addFilter(filter: Filter) = also { selector.add(filter) }

    internal fun useAlgorithm(name: String) {
        searchAlgorithm = when {
            name.equals("BFS", true) -> BFS
            name.equals("DFS", true) -> DFS
            else -> throw IllegalArgumentException(str(R.string.error_unknown_algorithm_selector_param, name))
        }
    }

    fun append(selector: UiSelector?) = also {
        selector?.let {
            this.selector.append(it)
            this.searchAlgorithm = it.searchAlgorithm
        }
    }

    fun plus(selector: UiSelector?) = selector?.let { paramSel ->
        UiSelector(mAccessibilityBridge, mAllocator).also { newSel ->
            newSel.selector.filters.addAll(this.selector.filters + paramSel.selector.filters)
            newSel.searchAlgorithm = paramSel.searchAlgorithm
        }
    } ?: this

    companion object {

        internal const val ID_IDENTIFIER = ":id/"

        @JvmStatic
        fun pickup(
            scriptRuntime: ScriptRuntime,
            root: UiObject?,
            selector: Any?,
            compass: CharSequence?,
            resultType: Any?,
            callback: Callable? = null,
        ): Any? = Picker.Builder(scriptRuntime.accessibilityBridge)
            .setRoot(root)
            .setCompass(compass)
            .setResultType(resultType)
            .setSelector(scriptRuntime, selector)
            .setCallback(callback)
            .build()
            .pick(scriptRuntime)

        internal class Picker private constructor(
            val root: UiObject?,
            val selector: UiSelector?,
            val compass: CharSequence?,
            val resultType: Any?,
            val callback: Callable? = null,
        ) {

            internal fun pick(scriptRuntime: ScriptRuntime) = Detector(compass, object : Result(resultType) {

                override fun byOne(): UiObject? {
                    return selector?.let { sel ->
                        root?.let { return sel.findOneOf(it) } ?: sel.findOnce()
                    }
                }

                override fun byAll(): UiObjectCollection {
                    return selector?.let { sel ->
                        root?.let { return sel.findOf(it) } ?: sel.find()
                    } ?: EMPTY
                }

            }, callback, selector).detect(scriptRuntime)

            // The picker searches with the bridge of the calling script runtime, so that its window
            // filter and blacklist apply and the search keeps working after the service restarts.
            // zh-CN: 拾取器使用调用方脚本运行时的 bridge 进行搜索, 使其窗口过滤器与黑名单生效, 且服务重启后仍可搜索.
            internal class Builder(accessibilityBridge: AccessibilityBridge? = null) {

                private var mRoot: UiObject? = null
                private var mSelector = UiSelector(accessibilityBridge)
                private var mCompass: CharSequence? = COMPASS_PASS_ON
                private var mResultType: Any = RESULT_TYPE_WIDGET
                private var mCallback: Callable? = null

                fun setRoot(root: UiObject?) = also { mRoot = root }

                fun setSelector(scriptRuntime: ScriptRuntime, selector: Any?) = also {
                    when (selector) {
                        null -> return@also
                        is String -> setSelector(selector)
                        is NativeRegExp -> setSelector(selector)
                        is UiSelector -> setSelector(selector)
                        is Map<*, *> -> setSelector(scriptRuntime, selector)
                        is List<*> -> setSelector(scriptRuntime, selector)
                        is BigInteger -> setSelector(selector.toString())
                        is Double -> setSelector(selector.toInt().toString())
                        is NativeJavaMethod -> throw SelectorMethodWithoutCallingException(selector)
                        else -> throw IllegalArgumentException(str(R.string.error_unknown_picker_selector_type, selector.toString(), selector::class.java))
                    }
                }

                private fun setSelector(selector: UiSelector) = also {
                    mSelector.append(selector)
                }

                private fun setSelector(selector: String) = also {
                    mSelector.append(UiSelector().content(selector))
                }

                private fun setSelector(selector: NativeRegExp) = also {
                    mSelector.append(UiSelector().contentMatch(selector))
                }

                private fun setSelector(scriptRuntime: ScriptRuntime, selector: List<*>): Builder = also {
                    selector.filterNotNull().forEach { setSelector(scriptRuntime, it) }
                }

                private fun setSelector(scriptRuntime: ScriptRuntime, selector: Map<*, *>) = also {
                    val parser = SelectorMapParser(SelectorRegistry) { value ->
                        (value as? Callable)?.let { func ->
                            BooleanFilter.BooleanSupplier { node ->
                                RhinoUtils.coerceBoolean(RhinoUtils.callFunction(scriptRuntime, func, null as VarScope?, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(node)))
                            }
                        }
                    }
                    try {
                        parser.parse(selector, UiSelector())
                    } catch (e: SelectorMapException.NoSuchProperty) {
                        throw IllegalArgumentException(str(R.string.error_no_such_selector_method, e.key, e.value.toString(), e.value?.let { it::class.java } ?: "null"))
                    } catch (e: SelectorMapException.IllegalValue) {
                        throw IllegalMapSelectorException(e.key, e.value ?: "null")
                    }.let { mSelector.append(it) }
                }

                fun setCompass(compass: CharSequence?) = also { compass?.let { mCompass = it } }

                fun setResultType(resultType: Any?) = also { resultType?.let { mResultType = it } }

                fun setCallback(callback: Callable?) = also { callback?.let { mCallback = it } }

                fun build() = Picker(mRoot, mSelector, mCompass, mResultType, mCallback)

            }

        }

        internal class IllegalMapSelectorException(key: String, value: Any) : IllegalArgumentException(
            str(R.string.error_invalid_selector_map_element, key, value.run {
                when (this) {
                    is NativeArray -> "[${joinToString { it.toString() }}]"
                    else -> toString()
                }
            }, value::class.java)
        )

        internal class SelectorMethodWithoutCallingException(selector: NativeJavaMethod) : IllegalArgumentException(
            str(R.string.error_selector_method_without_calling, selector.toString())
        )

    }

}
