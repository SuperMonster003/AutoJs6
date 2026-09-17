package org.autojs.autojs.core.console

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.console.ConsoleImpl.LogEntry
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ConsoleViewBinding
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Displays the entries of one [ConsoleImpl]: an optional title bar (title text plus a "more"
 * button), the log list and an input bar. The input bar shows up on demand: when a script is
 * waiting on `console.rawInput()`, when the host turns it on ([isInputBarVisible]) or when the
 * host runs its own command evaluator ([isCommandModeEnabled], the TypeScript console of the log
 * page). Time prefixes and level coloring are rendered per view ([isTimeVisible], [timeFormat],
 * [isColorful]), so the same console can be shown differently by different views.
 *
 * zh-CN: 显示一个 [ConsoleImpl] 的条目: 可选的标题栏 (标题文本与 "更多" 按钮), 日志列表与输入栏. 输入栏按需出现: 脚本在 `console.rawInput()` 上等待时,
 * 宿主开启时 ([isInputBarVisible]), 或宿主运行自身的命令求值器时 ([isCommandModeEnabled], 即日志页的 TypeScript 控制台).
 * 时间前缀与级别着色按视图渲染 ([isTimeVisible], [timeFormat], [isColorful]), 因此同一控制台可在不同视图中以不同方式显示.
 *
 * Created by Stardust on May 2, 2017.
 * Transformed by SuperMonster003 on Sep 8, 2026.
 */
open class ConsoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), ConsoleImpl.LogListener {

    fun interface OnStackFrameClickListener {
        fun onStackFrameClick(fileName: String, lineNumber: Int, columnNumber: Int)
    }

    fun interface OnCommandSubmitListener {
        fun onCommandSubmit(command: String)
    }

    fun interface OnCommandStopListener {
        fun onCommandStop()
    }

    fun interface OnMoreButtonClickListener {
        fun onMoreButtonClick(anchor: View)
    }

    private val binding: ConsoleViewBinding = ConsoleViewBinding.inflate(LayoutInflater.from(context), this, true)

    private val mColors: MutableMap<Int, Int> = HashMap()
    private val mLogEntries = ArrayList<LogEntry>()
    private val mAdapter = Adapter()

    private var mConsole: ConsoleImpl? = null
    private var mLogActivity: WeakReference<LogActivity>? = null
    private var mShouldStopRefresh = false

    private var mEnableStackFrameLinks = false
    private var mStackFrameClickListener: OnStackFrameClickListener? = null
    private var mCommandSubmitListener: OnCommandSubmitListener? = null
    private var mCommandStopListener: OnCommandStopListener? = null
    private var mMoreButtonClickListener: OnMoreButtonClickListener? = null
    private var mCommandInputBusy = false
    private var mPendingInputCount = 0
    private var mInputTextColor: Int? = null

    private var mLastScaleFactor = 1f
    private var mLastTextSize = 0f
    private var mIsPinchToZoomEnabled = false

    /** Invoked on a touch-down inside the input field, before it can take focus. zh-CN: 输入框内触摸按下时 (取得焦点之前) 调用. */
    var onInputTouchDown: (() -> Unit)? = null

    /** Invoked when the input field gains or loses focus. zh-CN: 输入框获得或失去焦点时调用. */
    var onInputFocusChanged: ((Boolean) -> Unit)? = null

    /** Invoked when a script starts waiting for a line on the bound console. zh-CN: 脚本开始在绑定的控制台上等待输入时调用. */
    var onInputRequested: (() -> Unit)? = null

    /** Invoked whenever the input bar is shown or hidden. zh-CN: 输入栏显示或隐藏时调用. */
    var onInputBarVisibilityChanged: ((Boolean) -> Unit)? = null

    val console: ConsoleImpl?
        get() = mConsole

    val titleBarView: View
        get() = binding.titleBar

    val moreButtonView: View
        get() = binding.more

    val inputView: TextView
        get() = binding.input

    val logListView: RecyclerView
        get() = binding.logList

    val textColors: Map<Int, Int>
        get() = mColors

    // ========== Display options / 显示选项 ==========

    private var timeSettings: ConsoleTimeSettings? = null

    internal fun restoreTimePreferences(settings: ConsoleTimeSettings) {
        timeSettings = settings
        isTimeVisible = settings.isTimeVisible
        timeFormat = settings.timeFormat
    }

    var isTimeVisible: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                mAdapter.notifyDataSetChanged()
            }
        }

    var timeFormat: String = ConsoleLogFormat.DEFAULT_TIME_PATTERN
        set(value) {
            val normalized = ConsoleLogFormat.normalizeTimePattern(value)
            if (field != normalized) {
                field = normalized
                if (isTimeVisible) mAdapter.notifyDataSetChanged()
            }
        }

    var isColorful: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                mAdapter.notifyDataSetChanged()
            }
        }

    var textSize: Float
        get() = mAdapter.textSize.takeIf { it > 0 } ?: DEFAULT_TEXT_SIZE
        set(value) {
            mLastTextSize = value
            mAdapter.updateTextSize(value)
        }

    // ========== Title bar / 标题栏 ==========

    var isTitleBarVisible: Boolean
        get() = binding.titleBar.isVisible
        set(value) {
            binding.titleBar.isVisible = value
        }

    var titleTextSize: Float
        get() = DisplayUtils.pxToSp(binding.title.textSize)
        set(value) = binding.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)

    var titleTextColor: Int
        get() = binding.title.textColors.defaultColor
        set(value) = binding.title.setTextColor(value)

    // ========== Input bar / 输入栏 ==========

    /** Whether the host wants the input bar shown even without a waiting script. zh-CN: 宿主是否希望即使没有等待中的脚本也显示输入栏. */
    var isInputBarVisible: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateInputBar()
            }
        }

    /** Whether the host evaluates submitted lines itself (the TypeScript console). zh-CN: 宿主是否自行求值提交的行 (TypeScript 控制台). */
    var isCommandModeEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateInputBar()
            }
        }

    val isInputBarShown: Boolean
        get() = binding.inputContainer.isVisible

    val hasPendingInput: Boolean
        get() = mPendingInputCount > 0

    init {
        applyDefaultColors()
        if (attrs != null) {
            readAttributes(attrs, defStyleAttr)
        }
        binding.logList.layoutManager = LinearLayoutManager(context)
        binding.logList.adapter = mAdapter
        setupPinchToZoom()
        setupTitleBar()
        setupInputBar()
    }

    private fun applyDefaultColors() {
        getLogLevelMap().forEach { (level, colorRes) -> mColors[level] = context.getColor(colorRes) }
    }

    private fun readAttributes(attrs: AttributeSet, defStyleAttr: Int) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ConsoleView, defStyleAttr, 0)
        try {
            mapOf(
                Log.VERBOSE to R.styleable.ConsoleView_color_verbose,
                Log.DEBUG to R.styleable.ConsoleView_color_debug,
                Log.INFO to R.styleable.ConsoleView_color_info,
                Log.WARN to R.styleable.ConsoleView_color_warn,
                Log.ERROR to R.styleable.ConsoleView_color_error,
                Log.ASSERT to R.styleable.ConsoleView_color_assert,
            ).forEach { (level, styleable) ->
                val color = ta.getColor(styleable, Color.TRANSPARENT)
                if (color != Color.TRANSPARENT) mColors[level] = color
            }
            if (ta.getBoolean(R.styleable.ConsoleView_excludeFromNavigationBar, false)) {
                binding.consoleContent.excludePaddingClippableViewFromBottomNavigationBar()
            }
        } finally {
            ta.recycle()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPinchToZoom() {
        val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentFactor = (Math.floor((detector.scaleFactor * 10).toDouble()) / 10).toFloat()
                if (mLastTextSize <= 0) {
                    mLastTextSize = textSize
                }
                if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                    val currentTextSize = mLastTextSize + (if (currentFactor > mLastScaleFactor) 1 else -1)
                    mLastTextSize = currentTextSize.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
                    textSize = mLastTextSize
                    mLastScaleFactor = currentFactor
                }
                return super.onScale(detector)
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                mLastScaleFactor = 1.0f
                super.onScaleEnd(detector)
            }
        })
        binding.logList.setOnTouchListener { _, event ->
            if (!mIsPinchToZoomEnabled) {
                return@setOnTouchListener super.onTouchEvent(event)
            }
            detector.onTouchEvent(event)
            !detector.isInProgress && super.onTouchEvent(event)
        }
    }

    private fun setupTitleBar() {
        binding.titleBar.background = ColorDrawable(context.getColor(R.color.floating_console_title_bar_bg))
        binding.more.setOnClickListener { anchor ->
            val listener = mMoreButtonClickListener
            if (listener != null) listener.onMoreButtonClick(anchor) else showDefaultMoreMenu(anchor)
        }
    }

    /**
     * Menu of the title bar "more" button when no host takes it over (UI-mode consoles): input bar
     * toggle, clear, copy and the display settings dialog.
     * zh-CN: 宿主未接管时 (UI 模式控制台) 标题栏 "更多" 按钮的菜单: 输入栏开关, 清空, 复制与显示设置对话框.
     */
    private fun showDefaultMoreMenu(anchor: View) {
        PopupMenu(context, anchor, Gravity.END).apply {
            menuInflater.inflate(R.menu.menu_embedded_console_more, menu)
            menu.findItem(R.id.action_input_bar)?.isChecked = isInputBarVisible
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_input_bar -> isInputBarVisible = !isInputBarVisible
                    R.id.action_clear -> mConsole?.clear()
                    R.id.action_copy -> mConsole?.copyAll()
                    R.id.action_settings -> ConsoleSettingsDialog(context, ViewSettingsModel()).show()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    /** Settings model editing this view directly (no window options). zh-CN: 直接编辑本视图的设置模型 (无窗口选项). */
    private inner class ViewSettingsModel : ConsoleSettingsDialog.Model {

        override var isTimeVisible: Boolean
            get() = this@ConsoleView.isTimeVisible
            set(value) {
                timeSettings?.isTimeVisible = value
                this@ConsoleView.isTimeVisible = value
            }

        override var timeFormat: String
            get() = this@ConsoleView.timeFormat
            set(value) {
                timeSettings?.timeFormat = value
                this@ConsoleView.timeFormat = value
            }

        override var isColorful: Boolean
            get() = this@ConsoleView.isColorful
            set(value) {
                this@ConsoleView.isColorful = value
            }

        override var textSize: Float
            get() = this@ConsoleView.textSize
            set(value) {
                this@ConsoleView.textSize = value
            }

        override val hasWindowOptions = false

        override var isAvoidStatusBar = true

        override var titleBackgroundAlpha = 1.0

        override var contentBackgroundAlpha = 1.0

    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInputBar() {
        binding.submit.setOnClickListener { performInputAction() }
        binding.input.setOnEditorActionListener { _, actionId, event ->
            val isKeyboardAction = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE
            val isEnterKey = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (!isKeyboardAction && !isEnterKey) {
                return@setOnEditorActionListener false
            }
            submitInput()
            true
        }
        binding.input.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onInputTouchDown?.invoke()
            false
        }
        binding.input.setOnFocusChangeListener { _, hasFocus -> onInputFocusChanged?.invoke(hasFocus) }
        updateInputBar()
    }

    protected open fun getLogLevelMap(): Map<Int, Int> = mapOf(
        Log.VERBOSE to R.color.console_view_verbose,
        Log.DEBUG to R.color.console_view_debug,
        Log.INFO to R.color.console_view_info,
        Log.WARN to R.color.console_view_warn,
        Log.ERROR to R.color.console_view_error,
        Log.ASSERT to R.color.console_view_assert,
    )

    // ========== Binding / 绑定 ==========

    fun setConsole(console: ConsoleImpl) {
        mConsole?.removeLogListener(this)
        mConsole = console
        mLogEntries.clear()
        mAdapter.notifyDataSetChanged()
        console.setConsoleView(this)
        mPendingInputCount = console.pendingInputCount
        updateInputBar()
    }

    fun setLogActivity(activity: LogActivity?) {
        mLogActivity = activity?.let { WeakReference(it) }
    }

    fun export(fileName: String) {
        mLogActivity?.get()?.export(fileName)
    }

    // ========== Colors and sizes / 颜色与尺寸 ==========

    fun setTextColors(colors: Array<out Int?>) {
        val levels = intArrayOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.ASSERT)
        var isReplaced = false
        colors.forEachIndexed { index, color ->
            if (color != null && index < levels.size) {
                mColors[levels[index]] = color
                isReplaced = true
            }
        }
        if (isReplaced) mAdapter.notifyDataSetChanged()
    }

    fun setLevelTextColor(level: Int, @ColorInt color: Int) {
        mColors[level] = color
        mAdapter.notifyDataSetChanged()
    }

    fun setVerboseTextColor(@ColorInt color: Int) = setLevelTextColor(Log.VERBOSE, color)

    fun setDebugTextColor(@ColorInt color: Int) = setLevelTextColor(Log.DEBUG, color)

    fun setInfoTextColor(@ColorInt color: Int) = setLevelTextColor(Log.INFO, color)

    fun setWarnTextColor(@ColorInt color: Int) = setLevelTextColor(Log.WARN, color)

    fun setErrorTextColor(@ColorInt color: Int) = setLevelTextColor(Log.ERROR, color)

    fun setAssertTextColor(@ColorInt color: Int) = setLevelTextColor(Log.ASSERT, color)

    fun setPinchToZoomEnabled(enabled: Boolean) {
        mIsPinchToZoomEnabled = enabled
    }

    fun setTitle(title: CharSequence?) {
        binding.title.text = title ?: ""
    }

    fun getTitle(): CharSequence = binding.title.text

    fun setTitleBackgroundColor(@ColorInt color: Int) = ViewUtils.setBackgroundColor(binding.titleBar, color)

    fun setTitleBackgroundTint(@ColorInt tint: Int?) {
        binding.titleBar.background?.mutate()?.clearColorFilter()
        ViewCompat.setBackgroundTintList(binding.titleBar, tint?.let { ColorStateList.valueOf(it) })
    }

    fun setTitleBackgroundAlpha(alpha: Double) {
        binding.titleBar.background?.mutate()?.alpha = ColorUtils.toUint8(alpha, true)
    }

    /**
     * Applies color, tint and alpha to the title bar background at once to prevent a transition
     * flash on the first frame.
     * zh-CN: 一次性把颜色, 着色与透明度应用到标题栏背景, 防止首帧过渡闪现.
     */
    fun setTitleBackground(@ColorInt color: Int, @ColorInt tint: Int?, alpha: Double) {
        applyBackground(binding.titleBar, color, tint, alpha)
    }

    fun setTitleIconsTint(@ColorInt color: Int?) {
        binding.more.imageTintList = ColorStateList.valueOf(color ?: Color.WHITE)
    }

    fun setMoreButtonIcon(@DrawableRes resId: Int, paddingDp: Int = 8) {
        val padding = (paddingDp * resources.displayMetrics.density).roundToInt()
        binding.more.setImageDrawable(ResourcesCompat.getDrawable(resources, resId, context.theme))
        binding.more.setPadding(padding, padding, padding, padding)
    }

    fun setOnMoreButtonClickListener(listener: OnMoreButtonClickListener?) {
        mMoreButtonClickListener = listener
    }

    fun setContentBackgroundColor(@ColorInt color: Int) {
        ensureContentBackground()
        ViewUtils.setBackgroundColor(binding.contentContainer, color)
    }

    fun setContentBackgroundTint(@ColorInt tint: Int?) {
        ensureContentBackground()
        binding.contentContainer.background?.mutate()?.clearColorFilter()
        ViewCompat.setBackgroundTintList(binding.contentContainer, tint?.let { ColorStateList.valueOf(it) })
    }

    fun setContentBackgroundAlpha(alpha: Double) {
        ensureContentBackground()
        binding.contentContainer.background?.mutate()?.alpha = ColorUtils.toUint8(alpha, true)
    }

    fun setContentBackground(@ColorInt color: Int, @ColorInt tint: Int?, alpha: Double) {
        ensureContentBackground()
        applyBackground(binding.contentContainer, color, tint, alpha)
    }

    fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        binding.content.setPadding(left, top, right, bottom)
    }

    /** Sets the background drawables the floating window uses for its rounded corners. zh-CN: 设置浮动窗口用于圆角的背景 drawable. */
    fun setBackgroundDrawables(titleBar: android.graphics.drawable.Drawable?, content: android.graphics.drawable.Drawable?) {
        binding.titleBar.background = titleBar
        binding.contentContainer.background = content
    }

    private fun ensureContentBackground() {
        if (binding.contentContainer.background == null) {
            binding.contentContainer.background = ColorDrawable(Color.TRANSPARENT)
        }
    }

    private fun applyBackground(view: View, @ColorInt color: Int, @ColorInt tint: Int?, alpha: Double) {
        val bg = view.background?.mutate()
        ViewUtils.setBackgroundColor(view, color, false)
        ViewCompat.setBackgroundTintList(view, tint?.let { ColorStateList.valueOf(it) })
        bg?.alpha = ColorUtils.toUint8(alpha, true)
        view.background = bg
    }

    // ========== Stack frame links / 栈帧链接 ==========

    fun setEnableStackFrameLinks(enabled: Boolean) {
        mEnableStackFrameLinks = enabled
    }

    fun setOnStackFrameClickListener(listener: OnStackFrameClickListener?) {
        mStackFrameClickListener = listener
    }

    /**
     * Parses [content] and wraps recognised stack frames into clickable spans.
     * zh-CN: 解析 [content] 并把识别出的栈帧包装为可点击的 span.
     */
    @VisibleForTesting
    fun createClickableContent(content: CharSequence): CharSequence {
        if (!mEnableStackFrameLinks) {
            return content
        }
        val text = content.toString()
        val spannable = SpannableString(text)
        val links = StackFrameParser.parse(text)
        if (links.isEmpty()) return content
        links.forEach { link ->
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    mStackFrameClickListener?.onStackFrameClick(link.sourceName, link.lineIndex, link.columnNumber)
                }
            }
            spannable.setSpan(clickableSpan, link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(LINK_COLOR), link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    // ========== Input bar behaviour / 输入栏行为 ==========

    fun setOnCommandSubmitListener(listener: OnCommandSubmitListener?) {
        mCommandSubmitListener = listener
        updateInputBar()
    }

    fun setOnCommandStopListener(listener: OnCommandStopListener?) {
        mCommandStopListener = listener
        updateInputBar()
    }

    fun setCommandInputBusy(busy: Boolean) {
        mCommandInputBusy = busy
        binding.input.isEnabled = !busy
        updateInputBar()
        if (!busy) {
            binding.input.requestFocus()
        }
    }

    fun setCommandInputTextColor(@ColorInt color: Int) {
        mInputTextColor = color
        binding.input.setTextColor(color)
        binding.input.setHintTextColor(ColorUtils.applyAlpha(color, 0.6))
        binding.input.background?.mutate()?.setTint(color)
    }

    /** Focuses the input field and shows the soft keyboard. zh-CN: 聚焦输入框并弹出软键盘. */
    fun focusInput() {
        if (!isInputBarShown) return
        binding.input.post {
            binding.input.requestFocus()
            ViewUtils.showSoftInput(binding.input)
        }
    }

    fun clearInputFocus() {
        ViewUtils.hideSoftInput(binding.input)
        binding.input.clearFocus()
    }

    val hasInputFocus: Boolean
        get() = binding.input.hasFocus()

    private fun performInputAction() {
        if (mCommandInputBusy && !hasPendingInput) {
            mCommandStopListener?.onCommandStop()
            return
        }
        submitInput()
    }

    private fun submitInput() {
        val rawText = binding.input.text.toString()
        val console = mConsole
        if (console != null && console.hasPendingInput) {
            if (console.submitInput(rawText)) {
                binding.input.setText("")
            }
            return
        }
        val text = rawText.trim()
        if (text.isEmpty()) return
        if (isCommandModeEnabled) {
            val listener = mCommandSubmitListener ?: return
            if (mCommandInputBusy) return
            binding.input.setText("")
            listener.onCommandSubmit(text)
            return
        }
        ViewUtils.showToast(context, R.string.text_no_script_waiting_for_console_input)
    }

    private fun updateInputBar() {
        val shown = isCommandModeEnabled || isInputBarVisible || hasPendingInput
        val wasShown = binding.inputContainer.isVisible
        binding.inputContainer.isVisible = shown
        val hintRes = when {
            hasPendingInput -> R.string.text_console_input_hint
            isCommandModeEnabled -> R.string.text_typescript_console_hint
            else -> R.string.text_no_script_waiting_for_console_input
        }
        binding.input.setHint(hintRes)
        val stopping = mCommandInputBusy && !hasPendingInput
        val icon = when {
            stopping -> R.drawable.ic_stop_button_fill
            isCommandModeEnabled && !hasPendingInput -> R.drawable.ic_play_arrow_white_48dp
            else -> R.drawable.ic_send_black_48dp
        }
        val tint = if (stopping) R.color.dialog_button_caution else R.color.dialog_button_finish
        val description = when {
            stopping -> R.string.text_stop
            isCommandModeEnabled && !hasPendingInput -> R.string.text_run
            else -> R.string.text_send
        }
        binding.submit.setImageResource(icon)
        binding.submit.imageTintList = ColorStateList.valueOf(context.getColor(tint))
        binding.submit.contentDescription = context.getString(description)
        binding.submit.isEnabled = when {
            hasPendingInput -> true
            stopping -> mCommandStopListener != null
            isCommandModeEnabled -> mCommandSubmitListener != null
            else -> true
        }
        if (!shown && wasShown) {
            clearInputFocus()
        }
        if (shown != wasShown) {
            onInputBarVisibilityChanged?.invoke(shown)
        }
    }

    // ========== Log listener / 日志监听 ==========

    override fun onNewLog(logEntry: LogEntry) {
        /* Entries are polled periodically. zh-CN: 条目由定时轮询刷新. */
    }

    override fun onLogClear() {
        post {
            mLogEntries.clear()
            mAdapter.notifyDataSetChanged()
        }
    }

    override fun onInputRequestsChanged(pendingCount: Int) {
        post {
            val hadPending = hasPendingInput
            mPendingInputCount = pendingCount
            updateInputBar()
            if (!hadPending && pendingCount > 0) {
                onInputRequested?.invoke()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mShouldStopRefresh = false
        // Listen again after a detach; the console only counts listening views as its display surfaces.
        // zh-CN: 脱离窗口后重新监听; 控制台仅把正在监听的视图视为其显示面.
        mConsole?.let {
            it.addLogListener(this)
            mPendingInputCount = it.pendingInputCount
        }
        updateInputBar()
        postDelayed(object : Runnable {
            override fun run() {
                refreshLog()
                if (!mShouldStopRefresh) {
                    postDelayed(this, REFRESH_INTERVAL.toLong())
                }
            }
        }, REFRESH_INTERVAL.toLong())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mShouldStopRefresh = true
        mConsole?.removeLogListener(this)
    }

    private fun refreshLog() {
        val console = mConsole ?: return
        // Entries are appended by id (ids only grow), so the console's soft cap, which drops the
        // oldest entries, cannot desynchronize this view (MCP roadmap P1.3).
        // zh-CN: 按 id 追加条目 (id 只增不减), 因此控制台丢弃最旧条目的软上限不会使本视图失步 (MCP 路线图 P1.3).
        val fresh: List<LogEntry>
        synchronized(console.logEntries) {
            val logEntries = console.logEntries
            val size = logEntries.size
            if (size == 0) {
                return
            }
            val lastSeenId = if (mLogEntries.isEmpty()) -1 else mLogEntries[mLogEntries.size - 1].id
            var start = size
            while (start > 0 && logEntries[start - 1].id > lastSeenId) {
                start--
            }
            if (start >= size) {
                return
            }
            fresh = ArrayList(logEntries.subList(start, size))
        }
        val oldSize = mLogEntries.size
        mLogEntries.addAll(fresh)
        mAdapter.notifyItemRangeInserted(oldSize, fresh.size)
        val overflow = mLogEntries.size - ConsoleImpl.MAX_LOG_ENTRIES
        if (overflow > 0) {
            mLogEntries.subList(0, overflow).clear()
            mAdapter.notifyItemRangeRemoved(0, overflow)
        }
        binding.logList.scrollToPosition(mLogEntries.size - 1)
    }

    private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView: TextView = itemView as TextView

        init {
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        /**
         * Text size in sp; 0 means "not set yet" and is filled from the first bound item.
         * zh-CN: 文本大小 (sp), 0 表示尚未设置, 将由首个绑定的条目填充.
         */
        var textSize: Float = 0f
            private set

        fun updateTextSize(value: Float) {
            if (textSize == value) return
            textSize = value
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(context).inflate(R.layout.console_view_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val textView = holder.textView
            val logEntry = mLogEntries[position]
            val text = when (isTimeVisible) {
                true -> ConsoleLogFormat.prefix(logEntry.timestamp, logEntry.level, timeFormat) + logEntry.content
                else -> logEntry.content
            }
            val color = mColors[if (isColorful) logEntry.level else Log.DEBUG]
            textView.text = createClickableContent(text)
            if (color != null) {
                textView.setTextColor(color)
            }
            ThemeColorHelper.setThemeColorPrimary(textView, true)
            if (textSize > 0) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            } else {
                // Remember the item layout's default size without notifying: a notify call here would
                // run inside the layout pass and RecyclerView throws IllegalStateException for that.
                // zh-CN: 仅记录条目布局的默认字号而不通知刷新, 布局过程中调用 notify 会使 RecyclerView 抛出 IllegalStateException.
                textSize = DisplayUtils.pxToSp(textView.textSize)
            }
        }

        override fun getItemCount() = mLogEntries.size

    }

    companion object {

        private const val REFRESH_INTERVAL = 100
        private const val DEFAULT_TEXT_SIZE = 14f
        private const val MIN_TEXT_SIZE = 8.0f
        private const val MAX_TEXT_SIZE = 56.0f

        /** Blue color for clickable links. zh-CN: 可点击链接的蓝色. */
        private const val LINK_COLOR = 0xFF2196F3.toInt()

    }

}
