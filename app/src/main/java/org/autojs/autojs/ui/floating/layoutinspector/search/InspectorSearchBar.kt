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
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.InspectorQueryError
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
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

        fun onExpandedChanged(expanded: Boolean)

    }

    var host: Host? = null

    /** Whether the back key collapses an expanded, empty bar (roadmap P1.5). zh-CN: 返回键是否收起展开且为空的搜索栏 (Roadmap P1.5). */
    var collapsesOnBack = false

    var isExpanded = true
        private set

    private val bar = LayoutInspectorSearchBarBinding.inflate(LayoutInflater.from(context), this, true)

    private val pill = LayoutInspectorSearchPillBinding.inflate(LayoutInflater.from(context), this, true)

    private var session: InspectorSearchSession? = null

    private var suppressWatcher = false

    private val maxBarWidth = (MAX_BAR_WIDTH_DP * resources.displayMetrics.density).roundToInt()

    private val normalColor = context.getColor(R.color.day_night)

    private val errorColor = context.getColor(R.color.layout_hierarchy_window_node_bounds)

    private val inputMethodManager get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    init {
        bar.query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (!suppressWatcher) session?.setText(s.toString())
            }
        })
        bar.query.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (submit) navigate(forward = true)
            submit
        }
        bar.collapse.setOnClickListener { setExpanded(false) }
        bar.regex.setOnClickListener { session?.let { it.setRegex(!it.query.regex) } }
        bar.previous.setOnClickListener { navigate(forward = false) }
        bar.next.setOnClickListener { navigate(forward = true) }
        bar.count.setOnClickListener { session?.error?.let { ViewUtils.showToast(context, describe(it), true) } }
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
            hideKeyboard()
        }
        host?.onExpandedChanged(expanded)
    }

    /** Handles the back key as described on the class; false when the window should. zh-CN: 按类注释处理返回键; 应由窗口处理时返回 false. */
    fun onBackPressed(): Boolean {
        val session = session ?: return false
        if (!isExpanded) return false
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
        session?.removeListener(this)
        super.onDetachedFromWindow()
    }

    /** Caps the expanded bar at [MAX_BAR_WIDTH_DP] and centers it (roadmap Q7). zh-CN: 展开态宽度上限 [MAX_BAR_WIDTH_DP] 并居中 (Roadmap Q7). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lp = bar.root.layoutParams as LayoutParams
        val available = MeasureSpec.getSize(widthMeasureSpec) - lp.leftMargin - lp.rightMargin
        val target = min(available, maxBarWidth)
        if (target > 0 && lp.width != target) {
            lp.width = target
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun navigate(forward: Boolean) {
        val session = session ?: return
        val node = if (forward) session.next() else session.previous()
        if (node == null) {
            if (session.isActive && session.error == null) ViewUtils.showToast(context, R.string.text_inspector_no_matching_nodes)
            return
        }
        host?.onCurrentMatchChanged(node)
    }

    private fun render() {
        val session = session ?: return
        val error = session.error
        val counter: String
        val counterColor: Int
        when {
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
        bar.count.contentDescription = error?.let(::describe)
        pill.pillCount.text = counter
        pill.pillCount.setTextColor(counterColor)
        pill.pillCount.visibility = if (counter.isEmpty()) GONE else VISIBLE

        val navigable = !session.isUnfiltered && session.matchCount > 0
        for (button in listOf(bar.previous, bar.next)) {
            button.isEnabled = navigable
            button.alpha = if (navigable) 1f else DISABLED_ALPHA
        }
        val regexColor = if (session.query.regex) ThemeColorManager.colorPrimary else ColorUtils.applyAlpha(normalColor, INACTIVE_ICON_ALPHA)
        bar.regex.imageTintList = ColorStateList.valueOf(regexColor)
    }

    private fun describe(error: InspectorQueryError): String = when (error.kind) {
        InspectorQueryError.Kind.INVALID_REGEX -> context.getString(R.string.text_inspector_invalid_regex, error.detail.orEmpty())
        InspectorQueryError.Kind.UNSUPPORTED_MODE -> context.getString(R.string.text_inspector_query_mode_unavailable)
        InspectorQueryError.Kind.SYNTAX -> error.detail.orEmpty()
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

        private const val INACTIVE_ICON_ALPHA = 0.6

    }

}
