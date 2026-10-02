package org.autojs.autojs.ui.floating.layoutinspector.search

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.FilterProfile
import org.autojs.autojs.core.accessibility.inspector.FilterProfileCatalog
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.accessibility.inspector.InspectorQuery
import org.autojs.autojs.core.accessibility.inspector.InspectorQueryError
import org.autojs.autojs.core.accessibility.inspector.KeywordFilter
import org.autojs.autojs.core.accessibility.inspector.SelectorExport
import org.autojs.autojs.core.accessibility.inspector.QueryHistory
import org.autojs.autojs.core.accessibility.inspector.TreeCandidates
import org.autojs.autojs.core.accessibility.inspector.TreeMatch
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.Filter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The search state of one layout inspection (roadmap D5): the query, the filter profile being
 * edited (roadmap D6, on top of the persisted [catalog]), the session-only region condition, their
 * combined result over the current root, the navigation cursor and the per-window search bar
 * state. Created by the first inspector window and handed to the next one on every bounds /
 * hierarchy switch, so the user's query survives the switch; dropped with the last window. The
 * selected profile is remembered across sessions and in effect as soon as the session starts.
 *
 * Text changes are evaluated after a 150 ms debounce (roadmap D17), everything else at once; all
 * calls happen on the main thread. Listeners are told after every evaluation and cursor move.
 *
 * zh-CN: 一次布局分析的搜索状态 (Roadmap D5): 查询, 正在编辑的筛选配置 (Roadmap D6, 基于持久化的 [catalog]), 仅限本次会话的区域
 * 条件, 它们在当前根节点上的合并结果, 导航游标, 以及各窗口搜索栏的展开状态. 由首个分析窗口创建, 范围 / 层次窗口每次互切时传给
 * 下一个窗口, 因此用户的查询不会因切换丢失; 随最后一个窗口一起丢弃. 已选配置跨会话记忆, 会话一开始即生效.
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

    var query: InspectorQuery = InspectorQuery(mode = QueryModeStore.load())
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

    /** All filters in effect after the last evaluation (query, profile, region), for the explainer (roadmap P2.1). zh-CN: 最近一次求值生效的全部过滤器 (查询, 配置, 区域), 供解释器使用 (Roadmap P2.1). */
    var filters: List<Filter> = emptyList()
        private set

    /** The filters the query text alone compiled to (the head of [filters]). zh-CN: 仅由查询文本编译出的过滤器 ([filters] 的开头部分). */
    private var queryFilters: List<Filter> = emptyList()

    /** The persisted query history (roadmap P2.2), loaded once per inspection. zh-CN: 持久化的查询历史 (Roadmap P2.2), 每次布局分析加载一次. */
    val history: QueryHistory by lazy { QueryHistoryStore.load() }

    /** The persisted filter profiles with the remembered selection (roadmap D6). zh-CN: 持久化的筛选配置及记忆的选中项 (Roadmap D6). */
    val catalog: FilterProfileCatalog = FilterProfileStore.load()

    /**
     * The profile in effect: the selected catalog entry plus the user's unsaved edits; compare with
     * [isProfileDirty]. Edits apply at once (roadmap D6).
     * zh-CN: 生效中的配置: 选中的目录条目加上用户未保存的修改; 见 [isProfileDirty]. 修改即时生效 (Roadmap D6).
     */
    var profile: FilterProfile = catalog.selected
        private set

    val isProfileDirty: Boolean get() = profile != catalog.selected

    /** The screen-bound region condition (roadmap D5, P3.4), session-only. zh-CN: 与屏幕绑定的区域条件 (Roadmap D5, P3.4), 仅限本次会话. */
    var region: Rect? = null
        private set

    /** Picker candidates of the current root (roadmap D16), computed on demand. zh-CN: 当前根节点的选择器候选 (Roadmap D16), 按需计算. */
    val candidates: TreeCandidates get() = candidatesCache ?: TreeCandidates.collect(InspectorNode(root)).also { candidatesCache = it }

    private var candidatesCache: TreeCandidates? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val listeners = CopyOnWriteArrayList<Listener>()

    private var pending = false

    private val evaluateTask = Runnable {
        pending = false
        evaluate()
    }

    init {
        if (!profile.isEmpty) evaluate()
    }

    /** Whether a query, profile or region is in effect (matching or failing). zh-CN: 是否有查询, 配置或区域在生效 (匹配中或失败). */
    val isActive: Boolean get() = !match.isUnfiltered || error != null

    /** Conditions set outside the query text, shown as the filter badge. zh-CN: 查询文本之外设置的条件数, 用作筛选角标. */
    val conditionCount: Int get() = profile.conditionCount + (if (region != null) 1 else 0)

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
        if (query.mode != this.query.mode) QueryModeStore.save(query.mode)
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

    /** Switches the query mode (roadmap D2 / P4.1); the mode is remembered across sessions by [setQuery]. zh-CN: 切换查询模式 (Roadmap D2 / P4.1); 模式由 [setQuery] 跨会话记忆. */
    fun setMode(mode: InspectorQuery.Mode) = setQuery(query.copy(mode = mode), immediate = true)

    /**
     * "Copy as selector" (roadmap P4.2, D8): the conditions in effect as a script selector, or null
     * while the query does not compile. The keyword property is the one the current match hit
     * (falling back to the first match, then to text).
     * zh-CN: "复制为选择器" (Roadmap P4.2, D8): 生效中的条件渲染为脚本选择器, 查询无法编译时为 null. 关键词属性取当前匹配命中的属性
     * (依次回退到首个匹配, 再到 text).
     */
    fun exportSelector(): SelectorExport.Rendered? {
        flush()
        if (error != null) return null
        val keyword = keywordFilter
        val node = currentMatch?.let(::InspectorNode)?.takeIf { match.isMatched(it) } ?: match.matches.firstOrNull()
        val hit = if (keyword != null && node != null) keyword.hit(node) else null
        return SelectorExport.render(query, queryFilters, hit, profile.toFilters() + listOfNotNull(regionFilter()))
    }

    /** Re-evaluates over a new root (window switch) and forgets the cursor. zh-CN: 在新的根节点上重新求值 (切换窗口) 并清除游标. */
    fun setRoot(root: NodeInfo) {
        this.root = root
        currentMatch = null
        candidatesCache = null
        mainHandler.removeCallbacks(evaluateTask)
        pending = false
        evaluate()
    }

    /* Filter profile (roadmap D6). zh-CN: 筛选配置 (Roadmap D6). */

    /** Applies an edited profile at once; it keeps the id of the selected entry. zh-CN: 立即应用编辑后的配置; 它保留选中条目的 id. */
    fun setProfile(profile: FilterProfile) {
        if (this.profile == profile) return
        this.profile = profile.copy(id = catalog.selectedId)
        evaluateNow()
    }

    /** Switches to a catalog entry, dropping unsaved edits; the selection is remembered. zh-CN: 切换到目录中的条目并丢弃未保存的修改; 选中项被记忆. */
    fun selectProfile(id: String): FilterProfile {
        profile = catalog.select(id)
        FilterProfileStore.saveSelection(catalog)
        evaluateNow()
        return profile
    }

    /** Writes the edits back into the selected user profile; false for the default profile (use [saveProfileAs]). zh-CN: 把修改写回选中的用户配置; 默认配置返回 false (改用 [saveProfileAs]). */
    fun saveProfile(): Boolean {
        if (!catalog.update(profile)) return false
        FilterProfileStore.save(catalog)
        notifyListeners()
        return true
    }

    /** Stores the current conditions as a new profile named [name] and selects it. zh-CN: 把当前条件存为名为 [name] 的新配置并选中它. */
    fun saveProfileAs(name: String): FilterProfile {
        profile = catalog.saveAs(profile, name)
        FilterProfileStore.save(catalog)
        notifyListeners()
        return profile
    }

    fun discardProfileEdits() {
        if (!isProfileDirty) return
        profile = catalog.selected
        evaluateNow()
    }

    fun renameProfile(id: String, name: String): Boolean {
        if (!catalog.rename(id, name)) return false
        if (id == catalog.selectedId) profile = profile.copy(name = catalog.selected.name)
        FilterProfileStore.save(catalog)
        notifyListeners()
        return true
    }

    /** Deletes a user profile; when it was selected the default takes over at once. zh-CN: 删除用户配置; 若它正被选中则默认配置立即接替. */
    fun deleteProfile(id: String): Boolean {
        val wasSelected = id == catalog.selectedId
        if (!catalog.delete(id)) return false
        FilterProfileStore.save(catalog)
        if (wasSelected) {
            profile = catalog.selected
            evaluateNow()
        } else {
            notifyListeners()
        }
        return true
    }

    /** Sets or clears the region condition (screen coordinates). zh-CN: 设置或清除区域条件 (屏幕坐标). */
    fun setRegion(region: Rect?) {
        if (this.region == region) return
        this.region = region?.let(::Rect)
        evaluateNow()
    }

    private fun evaluateNow() {
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

    /**
     * Writes the current query into the history; called when the user submits it (enter, previous /
     * next, results list), not on every keystroke (roadmap P2.2). Failing queries are not recorded.
     * zh-CN: 把当前查询写入历史; 在用户提交时调用 (回车, 上一个 / 下一个, 结果列表), 而非每次击键 (Roadmap P2.2). 编译失败的查询不记录.
     */
    fun commitQuery() {
        flush()
        if (error == null && history.record(query)) QueryHistoryStore.save(history)
    }

    fun removeFromHistory(query: InspectorQuery) {
        if (history.remove(query)) QueryHistoryStore.save(history)
    }

    fun clearHistory() {
        if (history.clear()) QueryHistoryStore.save(history)
    }

    /**
     * Query filters first (so the keyword is the first explainer step), then the profile, then the
     * region. A query that does not compile leaves the tree unfiltered even when a profile is set,
     * so the user sees the error rather than a silently narrower result.
     * zh-CN: 查询过滤器在前 (关键词成为解释器的第一步), 其后为配置, 最后为区域. 查询无法编译时即使设置了配置也不筛选, 让用户看到错误
     * 而非悄然变窄的结果.
     */
    private fun evaluate() {
        val started = SystemClock.elapsedRealtime()
        when (val compiled = query.compile()) {
            is InspectorQuery.Compiled.Filters -> {
                error = null
                keywordFilter = compiled.filters.firstOrNull() as? KeywordFilter
                queryFilters = compiled.filters
                filters = compiled.filters + profile.toFilters() + listOfNotNull(regionFilter())
                match = TreeMatch.evaluate(InspectorNode(root), filters)
            }
            is InspectorQuery.Compiled.Error -> {
                error = compiled.error
                queryFilters = emptyList()
                filters = emptyList()
                match = TreeMatch.unfiltered()
                keywordFilter = null
            }
        }
        currentMatch?.let { if (!match.isMatched(InspectorNode(it))) currentMatch = null }
        Log.d(TAG, "evaluate: ${match.count} matches, ${SystemClock.elapsedRealtime() - started} ms, query = $query, profile = ${profile.conditionCount} condition(s), region = $region")
        notifyListeners()
    }

    private fun regionFilter(): Filter? = region?.let {
        BoundsFilter(it.left.toDouble(), it.top.toDouble(), it.right.toDouble(), it.bottom.toDouble(), BoundsFilter.TYPE_INSIDE)
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
