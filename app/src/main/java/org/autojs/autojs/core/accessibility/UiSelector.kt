@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.autojs.autojs.core.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectActions
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.UiObjectCollection.Companion.EMPTY
import org.autojs.autojs.core.automator.UiObjectCollection.Companion.of
import org.autojs.autojs.core.automator.UiObjectIterator
import org.autojs.autojs.core.automator.diagnostics.A11yStats
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.core.automator.pick.PickExecutor
import org.autojs.autojs.core.automator.pick.PickResultAdapter
import org.autojs.autojs.core.automator.query.HitVerifier
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.selector.SelectorStringParser
import org.autojs.autojs.core.automator.search.BFS
import org.autojs.autojs.core.automator.search.DFS
import org.autojs.autojs.core.automator.search.FastPath
import org.autojs.autojs.core.automator.search.LazySearch
import org.autojs.autojs.core.automator.search.SearchAlgorithm
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The script-facing selector: the builder methods are generated from [SelectorRegistry] into
 * [UiSelectorBuilders] and routed back here through [applyEntry]; this class keeps the queries
 * (`find*`, `exists`, `untilFind*`; the polling ones run as a [UiSelectorQuery]) and the search
 * settings; `pickup` is the entry of the current augment layer to the [PickExecutor] of `core/automator/pick`.
 * zh-CN: 面向脚本的选择器: builder 方法由 [SelectorRegistry] 生成到 [UiSelectorBuilders] 并经 [applyEntry] 转回此处;
 * 本类保留查询 (`find*`, `exists`, `untilFind*`; 轮询的查询以 [UiSelectorQuery] 执行) 与搜索设置; `pickup` 是
 * 现有 augment 层通往 `core/automator/pick` 中 [PickExecutor] 的入口.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
open class UiSelector : UiSelectorBuilders, UiObjectActions, StringReadable {

    internal var selector = Selector()

    internal var searchAlgorithm: SearchAlgorithm = DFS

    private val mAccessibilityBridge: AccessibilityBridge?

    /** The bridge of the script the selector belongs to, whose `stats` count its searches. zh-CN: 选择器所属脚本的 bridge, 其 `stats` 统计它的搜索. */
    internal val bridge: AccessibilityBridge? get() = mAccessibilityBridge

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

    /**
     * The first node of a single search, refreshed before it is returned: a node that has left the window since the
     * search is searched for again (docs/selector/ROADMAP.md D6 (c), P3.2; off under `noRefreshOnHit`). Under
     * `prefer(...)` the best of all the nodes found (P3.4).
     * zh-CN: 单次搜索的首个节点, 返回前刷新: 搜索之后已离开窗口的节点会重新搜索 (D6 (c), P3.2; `noRefreshOnHit` 下关闭). `prefer(...)` 下为找到的全部节点中的最优者 (P3.4).
     */
    @ScriptInterface
    fun findOnce(): UiObject? = verified(single()) { single() }

    private fun single(): UiObject? = when (val preference = selector.preference) {
        null -> findOnce(0)
        else -> preference.best(find().nodes)
    }

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

    /**
     * Appends the terms of a string selector (roadmap D13 / P5.3), `select("text=Login clickable")`:
     * whitespace-separated `key=value`, `key~=regex` (or `key=/regex/flags`), `key` and `!key`, with
     * quotes around a value that has spaces; the keys are the registry properties (`auto.explain`
     * lists them). Returns this selector; a malformed string throws [org.autojs.autojs.core.automator.selector.SelectorStringException]
     * with the character position. `select()` without an argument stays the node action.
     * zh-CN: 追加字符串选择器 (Roadmap D13 / P5.3) 的各条件, `select("text=Login clickable")`: 以空白分隔的 `key=value`,
     * `key~=regex` (或 `key=/regex/flags`), `key` 与 `!key`, 含空格的值加引号; 键为注册表属性 (`auto.explain` 会列出). 返回本选择器;
     * 格式错误的字符串抛出带字符位置的 [org.autojs.autojs.core.automator.selector.SelectorStringException]. 无参数的 `select()` 仍是节点动作.
     */
    @ScriptInterface
    fun select(syntax: String): UiSelector = SelectorStringParser().parse(syntax, this)

    /** Whether a single search finds a node; the node is not refreshed (P3.2). zh-CN: 单次搜索是否找到节点; 不刷新该节点 (P3.2). */
    @ScriptInterface
    fun exists() = findOnce(0) != null

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
     * A lazy `find()` (roadmap P5.3): the matches under the current window roots, searched in
     * `find()` order (the selector's algorithm, DFS by default) as the iterator advances, so a
     * consumer that stops early (`break` out of `for (let node of sel.findIterator())`, or
     * `close()`) never walks the rest of the tree; nodes the filters reject are released as the walk
     * passes them. One search, no polling, on the calling thread; not counted in `auto.stats`.
     * zh-CN: 惰性的 `find()` (Roadmap P5.3): 当前窗口根节点之下的匹配, 随迭代器前进按 `find()` 的顺序 (选择器的算法, 默认 DFS) 搜索,
     * 提前停止的消费方 (`for (let node of sel.findIterator())` 中 `break`, 或 `close()`) 不会遍历树的其余部分; 被过滤器拒绝的节点在
     * 经过时释放. 单次搜索, 不轮询, 在调用线程上; 不计入 `auto.stats`.
     */
    @ScriptInterface
    fun findIterator(): UiObjectIterator {
        val bridge = mAccessibilityBridge ?: return UiObjectIterator(LazySearch(emptyList(), selector))
        bridge.ensureServiceStarted()
        return UiObjectIterator(LazySearch(searchRoots(bridge), selector, breadthFirst = searchAlgorithm === BFS))
    }

    /**
     * One attempt of a query: a single search, where a search posted to the UI thread under
     * [AccessibilityBridge.FLAG_FIND_ON_UI_THREAD] is waited for at most [budget] milliseconds
     * ([PollPolicy.UNBOUNDED]: interruptibly, without limit). [attempt] tells the framework fast
     * path what it may decide (docs/selector/ROADMAP.md P2.3; see [FastPath]); [trace], when given, receives whether the
     * attempt looked up and walked (appendix C Q15 / Q17).
     * zh-CN: 查询的一次尝试: 单次搜索; [AccessibilityBridge.FLAG_FIND_ON_UI_THREAD] 下投递到 UI 线程的搜索至多等待
     * [budget] 毫秒 ([PollPolicy.UNBOUNDED]: 可中断地无限等待). [attempt] 告诉框架快路径它可以决定什么 (P2.3; 见 [FastPath]);
     * 给出 [trace] 时记入这次尝试是否查找, 是否遍历 (附录 C Q15 / Q17).
     */
    internal fun findWithin(max: Int, budget: Long, attempt: FastPath.Attempt = FastPath.Attempt.SINGLE, trace: FastPath.Trace? = null): UiObjectCollection {
        val start = SystemClock.uptimeMillis()
        try {
            return findWithinUntimed(max, budget, attempt, trace)
        } finally {
            mAccessibilityBridge?.stats?.recordSearch(toString(), SystemClock.uptimeMillis() - start)
        }
    }

    private fun findWithinUntimed(max: Int, budget: Long, attempt: FastPath.Attempt, trace: FastPath.Trace?): UiObjectCollection {
        // The bridge routes to the shared controller; without a bridge nothing can be searched anyway.
        // zh-CN: bridge 会转到共享的控制器; 没有 bridge 时也无从查找.
        mAccessibilityBridge?.ensureServiceStarted()
        return when {
            mAccessibilityBridge == null -> findImpl(max, attempt, trace)
            isMainThread() -> findImpl(max, attempt, trace)
            mAccessibilityBridge.flags and AccessibilityBridge.FLAG_FIND_ON_UI_THREAD == 0 -> findImpl(max, attempt, trace)
            else -> findOnUiThread(mAccessibilityBridge, max, budget, attempt, trace)
        }
    }

    // A FutureTask instead of the former VolatileBox.blockedGet(): the wait is bounded when the query
    // is, interruptible otherwise, and a search that never ran cannot be waited for forever.
    // zh-CN: 以 FutureTask 取代原先的 VolatileBox.blockedGet(): 查询有超时则等待有界, 否则可中断; 从未执行的搜索不会被无限等待.
    private fun findOnUiThread(bridge: AccessibilityBridge, max: Int, budget: Long, attempt: FastPath.Attempt, trace: FastPath.Trace?): UiObjectCollection {
        val task = FutureTask { findImpl(max, attempt, trace) }
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
    internal fun query(policy: PollPolicy = PollPolicy.ONCE, max: Int = Int.MAX_VALUE): UiSelectorQuery {
        val bridge = mAccessibilityBridge
        return when {
            // Event-assisted polling (roadmap P5.2, on by default since 6.8.0: D6 (a), D9): a repeated query sleeps until the
            // screen changes, at least 50 ms after an attempt and at most 200 ms; without a bound service (no events yet) the
            // fixed interval stays.
            // zh-CN: 事件辅助轮询 (Roadmap P5.2, 自 6.8.0 起默认开启: D6 (a), D9): 重复的查询休眠到屏幕变化为止, 距上一次尝试至少 50 ms,
            // 至多 200 ms; 服务尚未绑定 (还没有事件) 时保持固定间隔.
            bridge != null && bridge.isEventAssistedPolling && bridge.service != null && policy.times != 1L && !policy.isNoop -> UiSelectorQuery(this, policy.assisted(), max, bridge.pulse.poller)
            else -> UiSelectorQuery(this, policy, max)
        }
    }

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

    internal fun findOneOf(root: UiObject): UiObject? = verified(firstOf(root)) { firstOf(root) }

    private fun firstOf(root: UiObject): UiObject? = when (val preference = selector.preference) {
        null -> findOf(root, 1).takeIf { it.size() > 0 }?.get(0)
        else -> preference.best(findOf(root, Int.MAX_VALUE).nodes)
    }

    /**
     * [found], checked before a single-result entry returns it (docs/selector/ROADMAP.md D6 (c), P3.2, [HitVerifier]):
     * refreshed, and searched for [again] when it has left the window, at most [HitVerifier.RETRIES] times ([unbounded]:
     * until a node refreshes); unchecked under `noRefreshOnHit` or without a bridge. The searches again count in
     * `auto.stats` (`queries.refinds`).
     * zh-CN: 单结果入口返回前校验的 [found] (D6 (c), P3.2, [HitVerifier]): 刷新它, 已离开窗口时 [again] 重新搜索, 至多 [HitVerifier.RETRIES] 次
     * ([unbounded]: 直到节点刷新成功); `noRefreshOnHit` 下或没有 bridge 时不校验. 重新搜索计入 `auto.stats` (`queries.refinds`).
     */
    internal fun verified(found: UiObject?, unbounded: Boolean = false, again: () -> UiObject?): UiObject? {
        val bridge = mAccessibilityBridge
        if (found == null || bridge == null || !bridge.isRefreshOnHit) return found
        return HitVerifier.verify(found, if (unbounded) Int.MAX_VALUE else HitVerifier.RETRIES, refresh = { it.refresh() }) {
            bridge.stats.recordRefind()
            again()
        }
    }

    internal fun findAndReturnList(root: UiObject, max: Int = Int.MAX_VALUE): List<UiObject> = searchAlgorithm.search(root, selector.snapshot(), max)

    /**
     * The search over any [UiNode] tree. Captured-tree callers can supply a no-op [lifecycle] when
     * they retain the nodes across queries; other callers keep the platform's default policy.
     * zh-CN: 对任意 [UiNode] 树的搜索. 捕获树调用方跨查询保留节点时可提供不回收的 [lifecycle],
     * 其它调用方继续使用平台默认策略.
     */
    internal fun <N : UiNode<N>> searchIn(root: N, max: Int = Int.MAX_VALUE, lifecycle: NodeLifecycle = NodeLifecycle.default): List<N> =
        searchAlgorithm.search(root, selector.snapshot(), max, lifecycle)

    protected fun findImpl(max: Int): UiObjectCollection = findImpl(max, FastPath.Attempt.SINGLE, null)

    private fun findImpl(max: Int, attempt: FastPath.Attempt, trace: FastPath.Trace?): UiObjectCollection {
        val bridge = mAccessibilityBridge ?: return of(emptyList())
        return search(bridge, searchRoots(bridge), max, attempt, trace)
    }

    protected fun findImpl(node: AccessibilityNodeInfo, max: Int): UiObjectCollection = findImpl(listOf(node), max)

    protected fun findImpl(roots: List<AccessibilityNodeInfo>, max: Int): UiObjectCollection {
        val bridge = mAccessibilityBridge ?: return of(emptyList())
        return search(bridge, roots.filter { !bridge.config.isInBlacklist("${it.packageName}") }.map { UiObject.createRoot(it, mAllocator, bridge) }, max, FastPath.Attempt.SINGLE, null)
    }

    // The window roots a search walks. Roots of blacklisted packages (guard mode, AccessibilityConfig) are skipped here,
    // not by the bridge. The framework's prefetching strategy is kept (P2.1, appendix C Q12).
    // zh-CN: 搜索遍历的窗口根节点. 黑名单包名 (保护模式, AccessibilityConfig) 的根节点在此跳过, 而非由 bridge 过滤. 沿用框架的预取策略 (P2.1, 附录 C Q12).
    private fun searchRoots(bridge: AccessibilityBridge): List<UiObject> = bridge.windowRoots()
        .filter { !bridge.config.isInBlacklist("${it.packageName}") }
        .map { UiObject.createRoot(it, mAllocator, bridge) }

    /**
     * The search over [roots]: the framework fast path first where [attempt] allows it (P2.3, [FastPath]; off under
     * `noFastPath` and for the packages of [AccessibilityBridge.fastPathLedger]), else the walk of every root in turn
     * with the filters of the moment, cheapest first (P2.2). [trace] receives whether the search looked up and walked
     * (Q15 / Q17).
     * zh-CN: 在 [roots] 上的搜索: [attempt] 允许时先走框架快路径 (P2.3, [FastPath]; `noFastPath` 下与 [AccessibilityBridge.fastPathLedger] 中的包除外),
     * 否则依次遍历每个根节点, 使用当时的过滤器, 按开销从低到高 (P2.2). [trace] 记入是否查找, 是否遍历 (Q15 / Q17).
     */
    private fun search(bridge: AccessibilityBridge, roots: List<UiObject>, max: Int, attempt: FastPath.Attempt, trace: FastPath.Trace?): UiObjectCollection {
        val filter = selector.snapshot()
        val algorithm = searchAlgorithm
        var key: FastPath.Key? = null
        var emptyLookup = false
        if (attempt == FastPath.Attempt.WALK) {
            bridge.stats.recordFastPath(A11yStats.FastPathEvent.WALK)
        }
        if (attempt.looksUp && max > 0 && roots.isNotEmpty() && bridge.isFastPathEnabled) {
            key = FastPath.keyOf(selector.effectiveFilters)?.takeIf { k -> roots.none { bridge.fastPathLedger.isUnreliable(it.packageName(), k.kind) } }
            if (key != null) {
                trace?.lookedUp = true
                val outcome = FastPath.decide(roots, key, filter, max == 1 && algorithm === DFS, attempt, UiObjectLookup)
                when (outcome) {
                    is FastPath.Outcome.Hit -> {
                        bridge.stats.recordFastPath(A11yStats.FastPathEvent.HIT)
                        return of(listOf(outcome.node))
                    }
                    FastPath.Outcome.Miss -> {
                        bridge.stats.recordFastPath(A11yStats.FastPathEvent.MISS)
                        return of(emptyList())
                    }
                    is FastPath.Outcome.Search -> {
                        bridge.stats.recordFastPath(A11yStats.FastPathEvent.FALLBACK)
                        emptyLookup = outcome.emptyLookup
                    }
                }
            }
        }
        trace?.walked = true
        val result = mutableListOf<UiObject?>()
        for (root in roots) {
            val found = algorithm.search(root, filter, max - result.size)
            if (emptyLookup && found.isNotEmpty() && key != null) {
                // The lookup missed what the walk finds (appendix C Q1): no longer trusted for that package.
                // zh-CN: 查找漏掉了遍历找到的节点 (附录 C Q1): 此后不再信任该包的查找.
                bridge.fastPathLedger.markUnreliable(root.packageName(), key.kind)
            }
            result.addAll(found)
            if (result.size >= max) break
        }
        if (emptyLookup && result.isNotEmpty()) {
            bridge.stats.recordFastPath(A11yStats.FastPathEvent.MISMATCH)
        }
        return of(result)
    }

    private fun findOf(root: UiObject, max: Int): UiObjectCollection {
        val start = SystemClock.uptimeMillis()
        try {
            return of(findAndReturnList(root, max))
        } finally {
            mAccessibilityBridge?.stats?.recordSearch(toString(), SystemClock.uptimeMillis() - start)
        }
    }

    internal fun addFilter(filter: Filter) = also { selector.add(filter) }

    internal fun useAlgorithm(name: String) {
        searchAlgorithm = algorithmOf(name) ?: throw IllegalArgumentException(str(R.string.error_unknown_algorithm_selector_param, name))
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

        /**
         * The search algorithm `algorithm(name)` selects (`BFS` / `DFS`, any case), or null for an unknown name; told
         * before applying it (docs/selector/ROADMAP.md P1.4). zh-CN: `algorithm(name)` 选择的搜索算法 (`BFS` / `DFS`, 不区分大小写), 未知名称为 null; 施加前先查 (P1.4).
         */
        internal fun algorithmOf(name: String): SearchAlgorithm? = when {
            name.equals("BFS", true) -> BFS
            name.equals("DFS", true) -> DFS
            else -> null
        }

        /**
         * `pickup(root?, selector, compass?, resultType?, callback?)` with the positions told by the caller (the current
         * augment layer, the toolkit, flows): searches with the bridge of [scriptRuntime]; see [PickArguments.legacyPickup]
         * and [PickExecutor]. zh-CN: 位置已由调用方区分的 `pickup` (现有 augment 层, 工具集, Flow): 以 [scriptRuntime] 的 bridge 搜索;
         * 见 [PickArguments.legacyPickup] 与 [PickExecutor].
         */
        @JvmStatic
        fun pickup(
            scriptRuntime: ScriptRuntime,
            root: UiObject?,
            selector: Any?,
            compass: CharSequence?,
            resultType: Any?,
            callback: Callable? = null,
        ): Any? = PickResultAdapter.legacyPickup(scriptRuntime, root, selector, compass, resultType, callback)

    }

}
