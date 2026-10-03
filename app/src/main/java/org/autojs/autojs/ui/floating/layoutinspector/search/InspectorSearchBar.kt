package org.autojs.autojs.ui.floating.layoutinspector.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isVisible
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.InspectorQuery
import org.autojs.autojs.core.accessibility.inspector.InspectorQueryError
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.LayoutInspectorHistoryItemBinding
import org.autojs.autojs6.databinding.LayoutInspectorSearchBarBinding
import org.autojs.autojs6.databinding.LayoutInspectorSearchPillBinding
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The search bar of the layout inspector windows (roadmap P1.2): an input with a regex switch, the
 * match counter (`3/17`, the count alone before the first navigation, `!` for a query that does
 * not compile) and previous / next buttons, which collapses into a draggable pill carrying the
 * counter (roadmap D9). Bound to the [InspectorSearchSession] shared by the windows; the hosting
 * window learns about navigation and the expanded state through [Host].
 *
 * The back key is offered to [onBackPressed] first: an expanded bar with text clears the text, an
 * expanded empty bar collapses when [collapsesOnBack] is set (windows whose bar starts collapsed),
 * otherwise the window handles the key.
 *
 * zh-CN: 布局分析窗口的搜索栏 (Roadmap P1.2): 输入框, 正则开关, 匹配计数 (`3/17`, 首次导航前只显示总数, 查询无法编译时为 `!`)
 * 与上一个 / 下一个按钮, 可收起为携带计数的可拖动浮标 (Roadmap D9). 绑定到各窗口共享的 [InspectorSearchSession]; 宿主窗口
 * 经 [Host] 得知导航与展开状态. 返回键先交给 [onBackPressed]: 展开且有文本时清空文本, 展开且为空时若 [collapsesOnBack]
 * (默认收起的窗口) 则收起, 否则交回窗口处理.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class InspectorSearchBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs), InspectorSearchSession.Listener {

    interface Host {

        /** The user navigated to [node]; the window selects and reveals it. zh-CN: 用户导航到了 [node]; 窗口应选中并展示它. */
        fun onCurrentMatchChanged(node: NodeInfo)

        /** The user asked for the node info dialog of [node] (results list long-press). zh-CN: 用户要求查看 [node] 的节点信息 (结果列表长按). */
        fun onShowNodeInfo(node: NodeInfo)

        /**
         * The user wants to drag a region on the screen (roadmap P3.4); true when this window starts
         * the drag, false when it cannot (the hierarchy window) and the bar should say so.
         * zh-CN: 用户想在屏幕上拖出区域 (Roadmap P3.4); 本窗口开始拖拽时返回 true, 无法支持 (层次窗口) 时返回 false 并由搜索栏提示.
         */
        fun onSelectRegionRequested(): Boolean

        fun onExpandedChanged(expanded: Boolean)

    }

    var host: Host? = null

    /** Whether the back key collapses an expanded, empty bar (roadmap P1.5). zh-CN: 返回键是否收起展开且为空的搜索栏 (Roadmap P1.5). */
    var collapsesOnBack = false

    var isExpanded = true
        private set

    /** Height the expanded bar occupies at the top, margins included; windows that place content under it use it as top inset. zh-CN: 展开态搜索栏在顶部占据的高度 (含边距); 把内容放在其下方的窗口以此作为顶部内边距. */
    val barHeightPx: Int
        get() = (bar.root.layoutParams as LayoutParams).let { it.height + it.topMargin + it.bottomMargin }

    private val bar = LayoutInspectorSearchBarBinding.inflate(LayoutInflater.from(context), this, true)

    private val pill = LayoutInspectorSearchPillBinding.inflate(LayoutInflater.from(context), this, true)

    private var session: InspectorSearchSession? = null

    private var suppressWatcher = false

    private val maxBarWidth = (MAX_BAR_WIDTH_DP * resources.displayMetrics.density).roundToInt()

    private val colors = InspectorTheme(context, context.getColor(R.color.layout_inspector_search_surface))

    private val normalColor = colors.text

    private val errorColor = colors.readable(context.getColor(R.color.layout_hierarchy_window_node_bounds))

    private val inputMethodManager get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /**
     * The query history drop-down (roadmap P2.2): an in-window list under the bar rather than a
     * popup window, because a popup surface over the overlay window aborts HWUI on some devices
     * (Redmi 22120RN86C, API 33: `Failed to set damage region on surface ... EGL_BAD_ACCESS`).
     * zh-CN: 查询历史下拉 (Roadmap P2.2): 窗口内嵌在搜索栏下方的列表, 而非弹出窗口, 因为悬浮窗之上的弹出窗口 surface 会在部分设备上
     * 使 HWUI 中止 (Redmi 22120RN86C, API 33: `Failed to set damage region on surface ... EGL_BAD_ACCESS`).
     */
    private val historyList = DropDownListView(context).apply {
        visibility = GONE
        // Must not take focus from the input: that would hide the IME and, through the focus
        // listener, close the drop-down on the first touch.
        // zh-CN: 不能从输入框夺走焦点: 否则会收起 IME, 并经焦点监听在第一次触摸时就关闭下拉.
        isFocusable = false
        isFocusableInTouchMode = false
        divider = null
        dividerHeight = 0
        isVerticalScrollBarEnabled = false
        background = context.getDrawable(R.drawable.layout_inspector_history_surface)
        elevation = HISTORY_ELEVATION_DP * resources.displayMetrics.density
        clipToOutline = true
        maxHeight = (HISTORY_MAX_HEIGHT_DP * resources.displayMetrics.density).roundToInt()
        setOnItemClickListener { _, _, position, _ -> onHistoryItemClicked(position) }
        setOnItemLongClickListener { _, _, position, _ -> onHistoryItemLongClicked(position) }
    }

    /**
     * The strip under the bar (roadmap P4.1): the field mode syntax cheat sheet while the input has
     * focus, or the error (position, term, candidates) while the query does not compile. An
     * in-window view for the same reason as [historyList]; hidden while the history is dropped down.
     * zh-CN: 搜索栏下方的提示条 (Roadmap P4.1): 输入框获焦时显示字段模式语法速查, 查询无法编译时显示错误 (位置, 条件, 候选).
     * 与 [historyList] 出于同一原因使用窗口内视图; 历史下拉展开时隐藏.
     */
    private val hintView = TextView(context).apply {
        visibility = GONE
        textSize = 12f
        background = context.getDrawable(R.drawable.layout_inspector_history_surface)
        elevation = HISTORY_ELEVATION_DP * resources.displayMetrics.density
        val horizontal = (HINT_PADDING_H_DP * resources.displayMetrics.density).roundToInt()
        val vertical = (HINT_PADDING_V_DP * resources.displayMetrics.density).roundToInt()
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    private val hintColor = colors.secondaryText

    init {
        colors.applyTo(bar.query)
        bar.filterBadge.backgroundTintList = ColorStateList.valueOf(colors.accent)
        bar.filterBadge.setTextColor(colors.onAccent)
        val barLp = bar.root.layoutParams as LayoutParams
        for (dropDown in listOf(historyList, hintView)) {
            addView(dropDown, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = barLp.topMargin + barLp.height + (HISTORY_OFFSET_DP * resources.displayMetrics.density).roundToInt()
                leftMargin = barLp.leftMargin
                rightMargin = barLp.rightMargin
            })
        }
        bar.query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (!suppressWatcher) session?.setText(s.toString())
                if (s.isNotEmpty()) dismissHistory() else if (bar.query.hasFocus()) showHistoryIfIdle()
            }
        })
        bar.query.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (submit) navigate(forward = true)
            submit
        }
        bar.query.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showHistoryIfIdle() else dismissHistory()
            renderHint()
        }
        bar.query.setOnClickListener { showHistoryIfIdle() }
        bar.collapse.setOnClickListener { setExpanded(false) }
        bar.filter.setOnClickListener { showFilterProfiles() }
        bar.regex.setOnClickListener { session?.let { it.setRegex(!it.query.regex) } }
        bar.mode.setOnClickListener { cycleMode() }
        bar.count.setOnLongClickListener { copySelector(); true }
        bar.previous.setOnClickListener { navigate(forward = false) }
        bar.next.setOnClickListener { navigate(forward = true) }
        bar.count.setOnClickListener { showResults() }
        pill.root.setOnClickListener { setExpanded(true, focus = true) }
        installPillDrag()
    }

    /** Attaches to [session] and shows its current query; [expanded] is the initial state. zh-CN: 绑定到 [session] 并显示其当前查询; [expanded] 为初始状态. */
    fun bind(session: InspectorSearchSession, expanded: Boolean) {
        this.session?.removeListener(this)
        this.session = session
        session.addListener(this)
        setQueryText(session.query.text)
        setExpanded(expanded)
        render()
    }

    fun setExpanded(expanded: Boolean, focus: Boolean = false) {
        isExpanded = expanded
        bar.root.visibility = if (expanded) VISIBLE else GONE
        pill.root.visibility = if (expanded) GONE else VISIBLE
        if (expanded) {
            if (focus) showKeyboard()
        } else {
            dismissHistory()
            hideKeyboard()
        }
        renderHint()
        host?.onExpandedChanged(expanded)
    }

    /** Handles the back key as described on the class; false when the window should. zh-CN: 按类注释处理返回键; 应由窗口处理时返回 false. */
    fun onBackPressed(): Boolean {
        val session = session ?: return false
        if (!isExpanded) return false
        if (historyList.visibility == VISIBLE) {
            dismissHistory()
            return true
        }
        if (bar.query.text.isNotEmpty()) {
            setQueryText("")
            session.setText("", immediate = true)
            return true
        }
        if (collapsesOnBack) {
            setExpanded(false)
            return true
        }
        return false
    }

    override fun onSearchStateChanged(session: InspectorSearchSession) = render()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session?.addListener(this)
    }

    override fun onDetachedFromWindow() {
        dismissHistory()
        session?.removeListener(this)
        super.onDetachedFromWindow()
    }

    /** Caps the expanded bar (and the drop-down under it) at [MAX_BAR_WIDTH_DP] and centers them (roadmap Q7). zh-CN: 展开态搜索栏 (及其下拉) 宽度上限 [MAX_BAR_WIDTH_DP] 并居中 (Roadmap Q7). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lp = bar.root.layoutParams as LayoutParams
        val available = MeasureSpec.getSize(widthMeasureSpec) - lp.leftMargin - lp.rightMargin
        val target = min(available, maxBarWidth)
        if (target > 0 && lp.width != target) {
            lp.width = target
        }
        for (dropDown in listOf(historyList, hintView)) {
            val dropLp = dropDown.layoutParams as LayoutParams
            if (target > 0 && dropLp.width != target) {
                dropLp.width = target
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** A touch outside the bar and the drop-down closes the drop-down; the touch itself goes on to whatever is beneath. zh-CN: 搜索栏与下拉之外的触摸关闭下拉; 触摸本身继续传给下方的视图. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && historyList.visibility == VISIBLE && !hits(bar.root, ev) && !hits(historyList, ev)) {
            dismissHistory()
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun hits(view: View, ev: MotionEvent): Boolean {
        return ev.x >= view.left && ev.x < view.right && ev.y >= view.top && ev.y < view.bottom
    }

    private fun navigate(forward: Boolean) {
        val session = session ?: return
        session.commitQuery()
        val node = if (forward) session.next() else session.previous()
        if (node == null) {
            if (session.isActive && session.error == null) ViewUtils.showToast(context, R.string.text_inspector_no_matching_nodes)
            return
        }
        host?.onCurrentMatchChanged(node)
    }

    /**
     * The counter opens the results list (roadmap P2.1, D1); for a query that does not compile it
     * shows the error instead, and it does nothing while no query is in effect.
     * zh-CN: 计数位打开结果列表 (Roadmap P2.1, D1); 查询无法编译时改为显示错误, 没有查询生效时不响应.
     */
    private fun showResults() {
        val session = session ?: return
        val host = host ?: return
        session.error?.let {
            ViewUtils.showToast(context, describe(it), true)
            return
        }
        session.flush()
        if (session.isUnfiltered) return
        session.commitQuery()
        dismissHistory()
        hideKeyboard()
        SearchResultsDialog(context, session, host).show()
    }

    /** The filter button opens the profile dialog (roadmap P3.2). zh-CN: 筛选按钮打开配置对话框 (Roadmap P3.2). */
    private fun showFilterProfiles() {
        val session = session ?: return
        val host = host ?: return
        dismissHistory()
        hideKeyboard()
        FilterProfileDialog(context, session, host).show()
    }

    /** Drops the history down under the bar when the focused input is empty and there is history. zh-CN: 输入框获焦且为空并且有历史时, 在搜索栏下方展开历史下拉. */
    private fun showHistoryIfIdle() {
        val session = session ?: return
        if (!isExpanded || bar.query.text.isNotEmpty() || session.history.isEmpty) {
            dismissHistory()
            return
        }
        historyList.adapter = HistoryAdapter(session.history.items)
        historyList.visibility = VISIBLE
        renderHint()
    }

    private fun dismissHistory() {
        historyList.visibility = GONE
        renderHint()
    }

    /** The mode toggle cycles keyword -> field -> selector (D2); the first switch into the field / selector mode shows its syntax once. zh-CN: 模式开关按关键词 -> 字段 -> 选择器循环 (D2); 首次切到字段 / 选择器模式时展示一次其语法. */
    private fun cycleMode() {
        val session = session ?: return
        val next = when (session.query.mode) {
            InspectorQuery.Mode.KEYWORD -> InspectorQuery.Mode.FIELD
            InspectorQuery.Mode.FIELD -> InspectorQuery.Mode.SELECTOR
            InspectorQuery.Mode.SELECTOR -> InspectorQuery.Mode.KEYWORD
        }
        session.setMode(next)
        when (next) {
            InspectorQuery.Mode.FIELD -> if (!QueryModeStore.isFieldModeIntroduced) {
                QueryModeStore.markFieldModeIntroduced()
                ViewUtils.showToast(context, R.string.text_inspector_field_mode_intro, true)
            }
            InspectorQuery.Mode.SELECTOR -> if (!QueryModeStore.isSelectorModeIntroduced) {
                QueryModeStore.markSelectorModeIntroduced()
                ViewUtils.showToast(context, R.string.text_inspector_selector_mode_intro, true)
            }
            InspectorQuery.Mode.KEYWORD -> Unit
        }
    }

    /** Long-press on the counter copies the conditions as a selector (roadmap P4.2, D8). zh-CN: 长按计数位把条件复制为选择器 (Roadmap P4.2, D8). */
    private fun copySelector() {
        val session = session ?: return
        SelectorClipboard.copy(context, session) { it.error?.let(::describe).orEmpty() }
    }

    private fun renderHint() {
        val session = session
        if (session == null || !isExpanded || historyList.isVisible) {
            hintView.visibility = GONE
            return
        }
        val error = session.error
        val syntaxHint = when (session.query.mode) {
            InspectorQuery.Mode.KEYWORD -> null
            InspectorQuery.Mode.FIELD -> R.string.text_inspector_field_syntax_hint
            InspectorQuery.Mode.SELECTOR -> R.string.text_inspector_selector_syntax_hint
        }
        when {
            // An unfinished expression is not a mistake: hint color, and the cheat sheet takes over while typing.
            // zh-CN: 未结束的表达式不是错误: 用提示色, 输入中仍以速查为主.
            error != null && error.kind == InspectorQueryError.Kind.INCOMPLETE -> {
                hintView.text = if (syntaxHint != null && bar.query.hasFocus()) context.getString(syntaxHint) else describe(error)
                hintView.setTextColor(hintColor)
                hintView.visibility = VISIBLE
            }
            error != null -> {
                hintView.text = describe(error)
                hintView.setTextColor(errorColor)
                hintView.visibility = VISIBLE
            }
            syntaxHint != null && bar.query.hasFocus() -> {
                hintView.text = context.getString(syntaxHint)
                hintView.setTextColor(hintColor)
                hintView.visibility = VISIBLE
            }
            else -> hintView.visibility = GONE
        }
    }

    private fun onHistoryItemClicked(position: Int) {
        val session = session ?: return
        val items = session.history.items
        dismissHistory()
        if (position >= items.size) {
            session.clearHistory()
            return
        }
        val query = items[position]
        setQueryText(query.text)
        session.setQuery(query, immediate = true)
        render()
    }

    private fun onHistoryItemLongClicked(position: Int): Boolean {
        val session = session ?: return false
        val items = session.history.items
        if (position >= items.size) return false
        session.removeFromHistory(items[position])
        ViewUtils.showToast(context, R.string.text_inspector_history_entry_removed)
        if (session.history.isEmpty) dismissHistory() else showHistoryIfIdle()
        return true
    }

    /** History rows (query text with a regex tag) followed by a "clear history" row. zh-CN: 历史行 (查询文本与正则标记), 末尾为 "清空历史" 行. */
    private inner class HistoryAdapter(private val items: List<InspectorQuery>) : BaseAdapter() {

        override fun getCount() = items.size + 1

        override fun getItem(position: Int): Any? = items.getOrNull(position)

        override fun getItemId(position: Int) = position.toLong()

        override fun getViewTypeCount() = 2

        override fun getItemViewType(position: Int) = if (position < items.size) 0 else 1

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val binding = convertView?.let { LayoutInspectorHistoryItemBinding.bind(it) }
                ?: LayoutInspectorHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val query = items.getOrNull(position)
            if (query != null) {
                binding.text.text = query.text
                binding.text.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                binding.text.setTextColor(normalColor)
                val tag = when {
                    query.mode == InspectorQuery.Mode.FIELD -> FIELD_TAG
                    query.mode == InspectorQuery.Mode.SELECTOR -> SELECTOR_TAG
                    query.regex -> REGEX_TAG
                    else -> ""
                }
                binding.tag.text = tag
                binding.tag.setTextColor(colors.secondaryText)
                binding.tag.visibility = if (tag.isEmpty()) GONE else VISIBLE
            } else {
                binding.text.setText(R.string.text_inspector_clear_query_history)
                binding.text.gravity = Gravity.CENTER
                binding.text.setTextColor(colors.accent)
                binding.tag.visibility = GONE
            }
            return binding.root
        }

    }

    /** A list whose height stops growing at [maxHeight]. zh-CN: 高度不超过 [maxHeight] 的列表. */
    private class DropDownListView(context: Context) : ListView(context) {

        var maxHeight = Int.MAX_VALUE

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.UNSPECIFIED -> maxHeight
                else -> min(MeasureSpec.getSize(heightMeasureSpec), maxHeight)
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
        }

    }

    private fun render() {
        val session = session ?: return
        val error = session.error
        val counter: String
        val counterColor: Int
        when {
            error != null && error.kind == InspectorQueryError.Kind.INCOMPLETE -> {
                counter = INCOMPLETE_COUNTER
                counterColor = normalColor
            }
            error != null -> {
                counter = "!"
                counterColor = errorColor
            }
            session.isUnfiltered -> {
                counter = ""
                counterColor = normalColor
            }
            else -> {
                val index = session.currentIndex
                counter = if (index >= 0) "${index + 1}/${session.matchCount}" else session.matchCount.toString()
                counterColor = if (session.matchCount == 0) errorColor else normalColor
            }
        }
        bar.count.text = counter
        bar.count.setTextColor(counterColor)
        bar.count.contentDescription = error?.let(::describe) ?: context.getString(R.string.text_inspector_view_matches)
        pill.pillCount.text = counter
        pill.pillCount.setTextColor(counterColor)
        pill.pillCount.visibility = if (counter.isEmpty()) GONE else VISIBLE

        val navigable = !session.isUnfiltered && session.matchCount > 0
        for (button in listOf(bar.previous, bar.next)) {
            button.isEnabled = navigable
            button.alpha = if (navigable) 1f else DISABLED_ALPHA
        }
        val regexColor = if (session.query.regex) colors.control else colors.secondaryText
        bar.regex.imageTintList = ColorStateList.valueOf(regexColor)
        // The regex switch belongs to the keyword mode only (D2); the other modes spell regexes themselves.
        // zh-CN: 正则开关只属于关键词模式 (D2); 其他模式自行表达正则.
        val mode = session.query.mode
        bar.regex.visibility = if (mode == InspectorQuery.Mode.KEYWORD) VISIBLE else GONE
        bar.mode.text = modeLabel(mode)
        bar.mode.contentDescription = context.getString(R.string.text_inspector_query_mode, context.getString(modeName(mode)))
        bar.mode.setTextColor(if (mode == InspectorQuery.Mode.KEYWORD) normalColor else colors.accent)
        renderHint()

        val conditions = session.conditionCount
        bar.filterBadge.text = if (conditions > MAX_BADGE) "$MAX_BADGE+" else conditions.toString()
        bar.filterBadge.visibility = if (conditions > 0) VISIBLE else GONE
        bar.filter.imageTintList = ColorStateList.valueOf(if (conditions > 0) colors.control else normalColor)
    }

    private fun describe(error: InspectorQueryError): String = when (error.kind) {
        InspectorQueryError.Kind.INVALID_REGEX -> context.getString(R.string.text_inspector_invalid_regex, error.detail.orEmpty())
        InspectorQueryError.Kind.INCOMPLETE -> context.getString(R.string.text_inspector_expression_unfinished)
        InspectorQueryError.Kind.SYNTAX -> buildString {
            val detail = error.detail.orEmpty()
            append(if (error.position >= 0) context.getString(R.string.text_inspector_syntax_error_at, error.position, detail) else detail)
            if (error.candidates.isNotEmpty()) {
                append('\n').append(context.getString(R.string.text_inspector_did_you_mean, error.candidates.joinToString(", ")))
            }
        }
    }

    private fun modeLabel(mode: InspectorQuery.Mode): String = when (mode) {
        InspectorQuery.Mode.KEYWORD -> KEYWORD_LABEL
        InspectorQuery.Mode.FIELD -> FIELD_LABEL
        InspectorQuery.Mode.SELECTOR -> SELECTOR_LABEL
    }

    private fun modeName(mode: InspectorQuery.Mode): Int = when (mode) {
        InspectorQuery.Mode.KEYWORD -> R.string.text_inspector_mode_keyword
        InspectorQuery.Mode.FIELD -> R.string.text_inspector_mode_field
        InspectorQuery.Mode.SELECTOR -> R.string.text_inspector_mode_selector
    }

    private fun setQueryText(text: String) {
        suppressWatcher = true
        bar.query.setText(text)
        bar.query.setSelection(text.length)
        suppressWatcher = false
    }

    private fun showKeyboard() {
        bar.query.requestFocus()
        bar.query.post { inputMethodManager.showSoftInput(bar.query, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard() {
        windowToken?.let { inputMethodManager.hideSoftInputFromWindow(it, 0) }
        bar.query.clearFocus()
    }

    /**
     * The pill follows the finger within this view (which is why the hosting window gives this
     * view the whole screen); a touch that never leaves the slop is a tap and expands the bar.
     * zh-CN: 浮标在本视图范围内跟随手指 (因此宿主窗口把整个屏幕交给本视图); 未超出 slop 的触摸视为点击并展开搜索栏.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun installPillDrag() {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        pill.root.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = v.translationX
                    startY = v.translationY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        val lp = v.layoutParams as LayoutParams
                        val minX = -(width - v.width - lp.leftMargin - lp.rightMargin).toFloat()
                        val maxY = (height - v.height - lp.topMargin - lp.bottomMargin).toFloat()
                        v.translationX = (startX + dx).coerceIn(min(minX, 0f), 0f)
                        v.translationY = (startY + dy).coerceIn(0f, max(maxY, 0f))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    companion object {

        private const val MAX_BAR_WIDTH_DP = 480

        private const val DISABLED_ALPHA = 0.38f

        private const val HISTORY_OFFSET_DP = 4

        private const val HISTORY_MAX_HEIGHT_DP = 280

        private const val HISTORY_ELEVATION_DP = 6f

        private const val MAX_BADGE = 9

        private const val HINT_PADDING_H_DP = 12

        private const val HINT_PADDING_V_DP = 8

        /** Marks history rows recorded with the regex switch on. zh-CN: 标记正则开关开启时记录的历史行. */
        private const val REGEX_TAG = "RegEx"

        /** Marks field mode history rows; also the mode toggle's label for that mode. zh-CN: 标记字段模式的历史行; 也是该模式下模式开关的标签. */
        private const val FIELD_TAG = "k=v"

        /** Marks selector mode history rows; also the mode toggle's label for that mode. zh-CN: 标记选择器模式的历史行; 也是该模式下模式开关的标签. */
        private const val SELECTOR_TAG = "$()"

        private const val KEYWORD_LABEL = "Aa"

        private const val FIELD_LABEL = FIELD_TAG

        private const val SELECTOR_LABEL = SELECTOR_TAG

        /** The counter while the selector expression is unfinished. zh-CN: 选择器表达式未结束时的计数位. */
        private const val INCOMPLETE_COUNTER = "\u2026"

    }

}
