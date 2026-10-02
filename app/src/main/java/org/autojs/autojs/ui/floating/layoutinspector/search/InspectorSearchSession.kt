package org.autojs.autojs.ui.floating.layoutinspector.search

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.accessibility.inspector.InspectorQuery
import org.autojs.autojs.core.accessibility.inspector.InspectorQueryError
import org.autojs.autojs.core.accessibility.inspector.KeywordFilter
import org.autojs.autojs.core.accessibility.inspector.TreeMatch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The search state of one layout inspection (roadmap D5): the query, its compiled result over the
 * current root, the navigation cursor and the per-window search bar state. Created by the first
 * inspector window and handed to the next one on every bounds / hierarchy switch, so the user's
 * query survives the switch; dropped with the last window.
 *
 * Text changes are evaluated after a 150 ms debounce (roadmap D17), everything else at once; all
 * calls happen on the main thread. Listeners are told after every evaluation and cursor move.
 *
 * zh-CN: 一次布局分析的搜索状态 (Roadmap D5): 查询, 其在当前根节点上的编译结果, 导航游标, 以及各窗口搜索栏的展开状态.
 * 由首个分析窗口创建, 范围 / 层次窗口每次互切时传给下一个窗口, 因此用户的查询不会因切换丢失; 随最后一个窗口一起丢弃.
 * 文本变化在 150 ms 去抖后求值 (Roadmap D17), 其余立即求值; 所有调用都在主线程. 每次求值与游标移动后通知监听者.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class InspectorSearchSession(root: NodeInfo) : InspectorMatchView {

    fun interface Listener {
        fun onSearchStateChanged(session: InspectorSearchSession)
    }

    var root: NodeInfo = root
        private set

    var query: InspectorQuery = InspectorQuery()
        private set

    /** Why the query does not compile, or null. zh-CN: 查询未能编译的原因, 无则为 null. */
    var error: InspectorQueryError? = null
        private set

    var match: TreeMatch<InspectorNode> = TreeMatch.unfiltered()
        private set

    /** The match the user navigated to or tapped last; null before the first navigation. zh-CN: 用户最近导航到或点选的匹配项; 首次导航前为 null. */
    var currentMatch: NodeInfo? = null
        private set

    /** Search bar state of each window kind, kept across switches (roadmap D9). zh-CN: 各类窗口的搜索栏展开状态, 切换时保留 (Roadmap D9). */
    var isBarExpandedInHierarchy = true

    var isBarExpandedInBounds = false

    private var keywordFilter: KeywordFilter? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val listeners = CopyOnWriteArrayList<Listener>()

    private var pending = false

    private val evaluateTask = Runnable {
        pending = false
        evaluate()
    }

    /** Whether a query is in effect (matching or failing). zh-CN: 是否有查询在生效 (匹配中或失败). */
    val isActive: Boolean get() = !match.isUnfiltered || error != null

    val matchCount: Int get() = match.count

    /** Position of [currentMatch] among the matches, -1 when it is none of them. zh-CN: [currentMatch] 在匹配项中的序号, 不是匹配项时为 -1. */
    val currentIndex: Int get() = match.indexOf(currentMatch?.let(::InspectorNode))

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Replaces the query; text-only changes are debounced unless [immediate]. zh-CN: 替换查询; 仅文本变化时去抖, 除非 [immediate]. */
    fun setQuery(query: InspectorQuery, immediate: Boolean = false) {
        if (this.query == query) return
        this.query = query
        mainHandler.removeCallbacks(evaluateTask)
        if (immediate) {
            pending = false
            evaluate()
        } else {
            pending = true
            mainHandler.postDelayed(evaluateTask, DEBOUNCE_MILLIS)
        }
    }

    fun setText(text: String, immediate: Boolean = false) = setQuery(query.copy(text = text), immediate)

    fun setRegex(enabled: Boolean) = setQuery(query.copy(regex = enabled), immediate = true)

    /** Re-evaluates over a new root (window switch) and forgets the cursor. zh-CN: 在新的根节点上重新求值 (切换窗口) 并清除游标. */
    fun setRoot(root: NodeInfo) {
        this.root = root
        currentMatch = null
        mainHandler.removeCallbacks(evaluateTask)
        pending = false
        evaluate()
    }

    /** Runs a debounced evaluation now, so that navigation sees the latest text. zh-CN: 立即执行尚在去抖中的求值, 使导航看到最新文本. */
    fun flush() {
        if (!pending) return
        mainHandler.removeCallbacks(evaluateTask)
        pending = false
        evaluate()
    }

    fun next(): NodeInfo? = move(forward = true)

    fun previous(): NodeInfo? = move(forward = false)

    private fun move(forward: Boolean): NodeInfo? {
        flush()
        val current = currentMatch?.let(::InspectorNode)
        val target = (if (forward) match.next(current) else match.previous(current))?.info
        currentMatch = target
        notifyListeners()
        return target
    }

    /** Records the node the user selected so the counter shows its position. zh-CN: 记录用户选中的节点, 使计数显示其序号. */
    fun setCurrentMatch(node: NodeInfo?) {
        if (currentMatch === node) return
        currentMatch = node
        notifyListeners()
    }

    private fun evaluate() {
        val started = SystemClock.elapsedRealtime()
        when (val compiled = query.compile()) {
            is InspectorQuery.Compiled.Filters -> {
                error = null
                match = TreeMatch.evaluate(InspectorNode(root), compiled.filters)
                keywordFilter = compiled.filters.firstOrNull() as? KeywordFilter
            }
            is InspectorQuery.Compiled.Error -> {
                error = compiled.error
                match = TreeMatch.unfiltered()
                keywordFilter = null
            }
        }
        currentMatch?.let { if (!match.isMatched(InspectorNode(it))) currentMatch = null }
        Log.d(TAG, "evaluate: ${match.count} matches, ${SystemClock.elapsedRealtime() - started} ms, query = $query")
        notifyListeners()
    }

    private fun notifyListeners() {
        for (listener in listeners) listener.onSearchStateChanged(this)
    }

    override val isUnfiltered: Boolean get() = match.isUnfiltered

    override fun isKept(node: NodeInfo): Boolean = match.isKept(InspectorNode(node))

    override fun isMatched(node: NodeInfo): Boolean = match.isMatched(InspectorNode(node))

    override fun highlightRange(text: CharSequence): IntRange? = keywordFilter?.firstMatchRange(text)

    companion object {

        private const val TAG = "InspectorSearchSession"

        const val DEBOUNCE_MILLIS = 150L

    }

}
