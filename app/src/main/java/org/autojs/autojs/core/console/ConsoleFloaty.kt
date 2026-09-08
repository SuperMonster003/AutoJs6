package org.autojs.autojs.core.console

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloaty.AbstractResizableExpandableFloaty
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.util.ContextUtils.withHostConfiguration
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setViewMeasure
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatingConsoleExpandBinding
import org.autojs.autojs6.databinding.FloatingWindowCollapseBinding
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating window of a [ConsoleImpl]. Owns the window views and turns the console's
 * [ConsoleImpl.Configurator] into view state: the whole configuration is applied when the window
 * attaches and individual parts are re-applied through [postApply] afterwards.
 *
 * The title bar is the drag handle; the "more" button opens the window menu (minimize, resize
 * mode, input bar, clear, copy, settings, close). Resize mode dims the content and lets the user
 * drag its right edge, bottom edge or corner grip; a tap leaves the mode.
 *
 * zh-CN: [ConsoleImpl] 的浮动窗口. 持有窗口视图并把控制台的 [ConsoleImpl.Configurator] 转换为视图状态: 窗口附加时应用整套配置, 之后各部分通过 [postApply] 重新应用.
 * 标题栏即拖动手柄; "更多" 按钮打开窗口菜单 (最小化, 尺寸调整模式, 输入栏, 清空, 复制, 设置, 关闭). 尺寸调整模式会遮罩内容区, 用户可拖动其右边缘, 下边缘或角部手柄; 轻触即退出该模式.
 *
 * Created by Stardust on Apr 20, 2017.
 * Rewritten by SuperMonster003 on Sep 8, 2026.
 */
class ConsoleFloaty(private val console: ConsoleImpl) : AbstractResizableExpandableFloaty() {

    private var mContext: Context? = null
    private var mWindow: ResizableExpandableFloatyWindow? = null
    private var mBinding: FloatingConsoleExpandBinding? = null
    private var mCollapsedBinding: FloatingWindowCollapseBinding? = null
    private var mStatusBarHeight = 0
    private var mMoreIconRes: Int? = null
    private var mPositionBeforeKeyboard: ConsoleWindowGeometry.Point? = null
    private var mPopupMenu: PopupMenu? = null
    private val mResizeController = ResizeController()

    private val configurator: ConsoleImpl.Configurator
        get() = console.configurator

    val defaultViewWidth: Double
        // @History deviceScreenWidth * 2 / 3
        get() = ScreenMetrics.deviceScreenWidth * 0.71

    val defaultViewHeight: Double
        // @History deviceScreenHeight / 3
        get() = ScreenMetrics.deviceScreenHeight * 0.42

    val isAttached: Boolean
        get() = mBinding != null && mWindow?.isWindowAttached == true

    var isResizeMode = false
        private set

    val expandedView: View?
        get() = mBinding?.root

    val consoleView: FloatingConsoleView?
        get() = mBinding?.console

    val titleBarView: View?
        get() = consoleView?.titleBarView

    /** The laid-out window size, or null before the first layout. zh-CN: 已布局的窗口尺寸, 首次布局前为 null. */
    val currentSize: ConsoleWindowGeometry.Size?
        get() = expandedView?.takeIf { isAttached && it.width > 0 && it.height > 0 }?.let { ConsoleWindowGeometry.Size(it.width, it.height) }

    /** The current window position in content coordinates, or null when not attached. zh-CN: 当前窗口位置 (内容坐标), 未附加时为 null. */
    val currentContentPosition: ConsoleWindowGeometry.Point?
        get() {
            if (!isAttached) return null
            val bridge = mWindow?.windowBridge ?: return null
            return ConsoleWindowGeometry.Point(bridge.x, ConsoleWindowGeometry.toContentY(bridge.y, screen(bridge)))
        }

    init {
        isInitialExpanded = true
        isShouldRequestFocusWhenExpand = false
        collapsedViewUnpressedAlpha = 1.0f
    }

    // ========== Inflation and attachment / 视图创建与附加 ==========

    override fun inflateCollapsedView(service: FloatyService, window: ResizableExpandableFloatyWindow): View {
        val context = ensureContext(service)
        return FloatingWindowCollapseBinding.inflate(LayoutInflater.from(context)).also { mCollapsedBinding = it }.root
    }

    override fun inflateExpandedView(service: FloatyService, window: ResizableExpandableFloatyWindow): View {
        val context = ensureContext(service)
        mWindow = window
        val binding = FloatingConsoleExpandBinding.inflate(LayoutInflater.from(context)).also { mBinding = it }
        binding.console.apply {
            setConsole(this@ConsoleFloaty.console)
            isTitleBarVisible = true
            setOnMoreButtonClickListener { anchor -> showMoreMenu(anchor) }
            onInputTouchDown = { mWindow?.requestWindowFocus() }
            onInputFocusChanged = { focused -> onInputFocusChanged(focused) }
            onInputRequested = { onInputRequested() }
            onInputBarVisibilityChanged = { shown -> if (!shown) releaseInputFocus() }
        }
        mResizeController.bind(binding)
        applyTitle()
        applyTitleStyle()
        applyContentStyle()
        applyDisplayOptions()
        applyInputVisibility()
        return binding.root
    }

    private fun ensureContext(service: FloatyService): Context {
        return mContext ?: ContextThemeWrapper(service.withHostConfiguration(), R.style.ConsoleTheme).also { mContext = it }
    }

    override fun onAttachedToWindow(window: ResizableExpandableFloatyWindow) {
        mWindow = window
        val context = mContext ?: window.windowView?.context ?: return
        mStatusBarHeight = ViewUtils.getStatusBarHeight(context)
        // Window coordinates start at the top-left of the screen; the status bar is handled by the
        // placement math (see screen()), so it is neither double-counted nor unavoidable.
        // zh-CN: 窗口坐标自屏幕左上角起算; 状态栏由定位计算处理 (见 screen()), 既不会被重复计入也不会无法覆盖.
        window.setWindowLayoutEdgeToEdge()
        setupTitleBarDrag(window)
        setupKeyboardAvoidance()
        applySize()
        applyTouchable()
        // Place the window before its first frame; gravity needs the measured size, so hide the
        // view until the first layout pass has positioned it.
        // zh-CN: 在首帧之前定位窗口; gravity 需要测量后的尺寸, 因此在首次布局完成定位前隐藏视图.
        val view = expandedView ?: return
        val needsMeasuredSize = configurator.gravity != Gravity.NO_GRAVITY
        if (needsMeasuredSize) view.alpha = 0f
        applyPlacement()
        view.doOnLayout {
            applyPlacement()
            if (needsMeasuredSize) view.post { view.alpha = 1f }
        }
        // A new size may invalidate the position (gravity depends on it and a clamped window may
        // now hang off the screen); user resizing pins the window itself.
        // zh-CN: 尺寸变化可能使位置失效 (gravity 依赖尺寸, 被限制的窗口也可能因此超出屏幕); 用户手动调整尺寸时窗口位置由其自身固定.
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (sizeChanged && !isResizeMode) {
                view.post { applyPlacement() }
            }
        }
        console.onFloatyAttached()
    }

    override fun onDetachedFromWindow(window: ResizableExpandableFloatyWindow) {
        mPopupMenu?.dismiss()
        mPopupMenu = null
        isResizeMode = false
        mPositionBeforeKeyboard = null
        mBinding = null
        mCollapsedBinding = null
        mWindow = null
        console.onFloatyDetached()
    }

    override fun onBackPressed(window: ResizableExpandableFloatyWindow): Boolean {
        if (isResizeMode) {
            exitResizeMode()
            return true
        }
        if (consoleView?.hasInputFocus == true) {
            releaseInputFocus()
            return true
        }
        return false
    }

    fun clearStates() {
        mMoreIconRes = null
    }

    /** Runs [block] on the UI thread when the window is attached; a no-op otherwise (attachment applies everything). zh-CN: 窗口已附加时在 UI 线程执行 [block]; 否则不做任何事 (附加时会应用全部配置). */
    fun postApply(block: ConsoleFloaty.() -> Unit) {
        console.uiHandler.post {
            if (isAttached) runCatching { block() }.onFailure { it.printStackTrace() }
        }
    }

    // ========== Applying the configuration / 应用配置 ==========

    fun applyTitle() {
        consoleView?.setTitle(configurator.title ?: ConsoleImpl.DEFAULT_TITLE)
    }

    fun applyTitleStyle() {
        val view = consoleView ?: return
        configurator.titleTextSize?.let { view.titleTextSize = it }
        configurator.titleTextColor?.let { view.titleTextColor = it }
        view.setTitleBackground(configurator.titleBackgroundColor, configurator.titleBackgroundTint, configurator.titleBackgroundAlpha)
        view.setTitleIconsTint(configurator.titleIconsTint)
    }

    fun applyContentStyle() {
        val view = consoleView ?: return
        configurator.contentTextSize?.let { view.textSize = it }
        configurator.contentTextColors?.let { view.setTextColors(it) }
        view.setContentBackground(configurator.contentBackgroundColor, configurator.contentBackgroundTint, configurator.contentBackgroundAlpha)
    }

    fun applyDisplayOptions() {
        val view = consoleView ?: return
        view.isTimeVisible = configurator.isTimeVisible
        view.timeFormat = configurator.timeFormat
        view.isColorful = configurator.isColorful
    }

    fun applyInputVisibility() {
        consoleView?.isInputBarVisible = configurator.isInputVisible
    }

    fun applyTouchable() {
        mWindow?.setTouchable(configurator.isTouchable)
    }

    fun applySize() {
        val view = expandedView ?: return
        val bridge = mWindow?.windowBridge ?: return
        val screen = screen(bridge)
        val requested = configurator.size
            ?.let { ConsoleWindowGeometry.Size(it.width.roundToInt(), it.height.roundToInt()) }
            ?: ConsoleWindowGeometry.Size(defaultViewWidth.roundToInt(), defaultViewHeight.roundToInt())
        val size = ConsoleWindowGeometry.clampSize(requested.width, requested.height, 0, screen.topInset, minWidth(), minHeight(), screen)
        if (view.layoutParams == null) return
        if (view.layoutParams.width != size.width || view.layoutParams.height != size.height) {
            setViewMeasure(view, size.width, size.height)
        }
        mResizeController.updateLabel(size.width, size.height)
    }

    fun applyPlacement() {
        val bridge = mWindow?.windowBridge ?: return
        val view = expandedView ?: return
        if (mWindow?.isExpanded == false) return
        // Prefer the requested size: a pending layout still reports the previous measured size.
        // zh-CN: 优先使用请求的尺寸: 布局尚未完成时测量尺寸仍是旧值.
        val params = view.layoutParams
        val size = ConsoleWindowGeometry.Size(
            params?.width?.takeIf { it > 0 } ?: currentSize?.width ?: defaultViewWidth.roundToInt(),
            params?.height?.takeIf { it > 0 } ?: currentSize?.height ?: defaultViewHeight.roundToInt(),
        )
        val screen = screen(bridge)
        val position = configurator.position
        val point = ConsoleWindowGeometry.resolve(
            configurator.gravity,
            view.layoutDirection == View.LAYOUT_DIRECTION_LTR,
            position?.x?.roundToInt(),
            position?.y?.roundToInt(),
            size.width,
            size.height,
            screen,
        )
        mPositionBeforeKeyboard = null
        if (bridge.x != point.x || bridge.y != point.y) {
            bridge.updatePosition(point.x, point.y)
        }
    }

    /**
     * The area the window is laid out in, in window coordinates. After
     * [ResizableExpandableFloatyWindow.setWindowLayoutEdgeToEdge] that is the display minus the
     * navigation bar, so `y = 0` is the top of the screen and the status bar only matters through
     * [ConsoleWindowGeometry.Screen.topInset]. Display metrics before R already exclude the
     * navigation bar; R and above derive the area from the current window metrics.
     *
     * zh-CN: 窗口的布局区域 (窗口坐标系). 经 [ResizableExpandableFloatyWindow.setWindowLayoutEdgeToEdge] 设置后为屏幕减去导航栏的区域,
     * 因此 `y = 0` 即屏幕顶端, 状态栏仅通过 [ConsoleWindowGeometry.Screen.topInset] 起作用. R 之前的显示度量已排除导航栏; R 及以上由当前窗口度量推导.
     */
    private fun screen(bridge: WindowBridge): ConsoleWindowGeometry.Screen {
        val area = layoutArea(bridge)
        return ConsoleWindowGeometry.Screen(area.width, area.height, if (configurator.isAvoidStatusBar) area.statusBarHeight else 0)
    }

    private class LayoutArea(val width: Int, val height: Int, val statusBarHeight: Int)

    private fun layoutArea(bridge: WindowBridge): LayoutArea {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val context = mWindow?.windowView?.context ?: mContext
            context?.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.let { metrics ->
                val insets = metrics.windowInsets
                val navigation = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                return LayoutArea(
                    metrics.bounds.width() - navigation.left - navigation.right,
                    metrics.bounds.height() - navigation.top - navigation.bottom,
                    maxOf(
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top,
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()).top,
                    ),
                )
            }
        }
        return LayoutArea(bridge.screenWidth, bridge.screenHeight, mStatusBarHeight)
    }

    /**
     * Minimizes into the collapsed icon at the window's own corner, kept clear of the status bar
     * (which sits above overlay windows and would swallow taps on the icon).
     * zh-CN: 最小化为位于窗口自身角落的收起图标, 并避开状态栏 (状态栏位于悬浮窗之上, 会吞掉对图标的点击).
     */
    private fun collapseNearCurrentPosition() {
        val window = mWindow ?: return
        val bridge = window.windowBridge ?: run { window.collapse(); return }
        val area = layoutArea(bridge)
        val icon = mCollapsedBinding?.root?.width?.takeIf { it > 0 } ?: dp(COLLAPSED_ICON_SIZE_DP)
        val point = ConsoleWindowGeometry.clampPosition(bridge.x, bridge.y, icon, icon, ConsoleWindowGeometry.Screen(area.width, area.height, area.statusBarHeight))
        window.collapse(point.x, point.y)
    }

    private fun minWidth() = dp(MIN_WIDTH_DP)

    private fun minHeight() = dp(MIN_HEIGHT_DP)

    private fun dp(value: Int): Int {
        val density = (mContext ?: expandedView?.context)?.resources?.displayMetrics?.density ?: 3f
        return (value * density).roundToInt()
    }

    // ========== Title bar drag / 标题栏拖动 ==========

    private fun setupTitleBarDrag(window: ResizableExpandableFloatyWindow) {
        val bridge = window.windowBridge ?: return
        val titleBar = titleBarView ?: return
        DragGesture(bridge, titleBar).apply {
            pressedAlpha = 1.0f
            setPositionFilter { x, y ->
                val size = currentSize ?: ConsoleWindowGeometry.Size(bridge.width, bridge.height)
                val point = ConsoleWindowGeometry.clampPosition(x, y, size.width, size.height, screen(bridge))
                intArrayOf(point.x, point.y)
            }
            setOnDragEndListener {
                mPositionBeforeKeyboard = null
                currentContentPosition?.let { console.onUserMoved(it.x, it.y) }
            }
        }
    }

    // ========== Input focus and keyboard / 输入焦点与键盘 ==========

    internal fun onInputRequested() {
        val window = mWindow ?: return
        if (!window.isExpanded) window.expand()
        window.requestWindowFocus()
        consoleView?.focusInput()
    }

    private fun onInputFocusChanged(focused: Boolean) {
        if (focused) {
            avoidKeyboardHeuristically()
        } else {
            restorePositionAfterKeyboard()
            if (consoleView?.isInputBarShown != true) mWindow?.releaseWindowFocus()
        }
    }

    private fun releaseInputFocus() {
        consoleView?.clearInputFocus()
        restorePositionAfterKeyboard()
        mWindow?.releaseWindowFocus()
    }

    private fun setupKeyboardAvoidance() {
        val windowView = mWindow?.windowView ?: return
        ViewCompat.setOnApplyWindowInsetsListener(windowView) { _, insets ->
            val overlap = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (overlap > 0 && consoleView?.hasInputFocus == true) {
                avoidKeyboard(overlap)
            } else if (overlap == 0 && consoleView?.hasInputFocus != true) {
                restorePositionAfterKeyboard()
            }
            insets
        }
    }

    /** Moves the window up by the reported keyboard overlap. zh-CN: 按报告的键盘遮挡高度上移窗口. */
    private fun avoidKeyboard(overlap: Int) {
        val bridge = mWindow?.windowBridge ?: return
        val view = expandedView ?: return
        val origin = mPositionBeforeKeyboard ?: ConsoleWindowGeometry.Point(bridge.x, bridge.y).also { mPositionBeforeKeyboard = it }
        val keyboardTop = bridge.y + view.height - overlap
        val targetY = maxOf(screen(bridge).topInset, keyboardTop - view.height)
        if (targetY < bridge.y) bridge.updatePosition(origin.x, targetY)
    }

    /**
     * Without IME insets (pre-R overlay windows) assume the keyboard covers the lower part of the
     * screen and keep the window above it.
     * zh-CN: 没有输入法 insets 时 (R 之前的悬浮窗) 假定键盘覆盖屏幕下部, 让窗口保持在其上方.
     */
    private fun avoidKeyboardHeuristically() {
        val bridge = mWindow?.windowBridge ?: return
        val view = expandedView ?: return
        val screen = screen(bridge)
        val visibleBottom = (screen.height * KEYBOARD_FREE_SCREEN_RATIO).roundToInt()
        if (bridge.y + view.height <= visibleBottom) return
        val origin = mPositionBeforeKeyboard ?: ConsoleWindowGeometry.Point(bridge.x, bridge.y).also { mPositionBeforeKeyboard = it }
        bridge.updatePosition(origin.x, maxOf(screen.topInset, visibleBottom - view.height))
    }

    private fun restorePositionAfterKeyboard() {
        val origin = mPositionBeforeKeyboard ?: return
        mPositionBeforeKeyboard = null
        mWindow?.windowBridge?.updatePosition(origin.x, origin.y)
    }

    // ========== Menu / 菜单 ==========

    private fun showMoreMenu(anchor: View) {
        val context = mContext ?: anchor.context
        val window = mWindow
        // A focusable window lets the popup take key focus (back key dismisses it).
        // zh-CN: 可聚焦的窗口让弹出菜单获得按键焦点 (返回键可关闭它).
        val hadInputFocus = consoleView?.hasInputFocus == true
        window?.requestWindowFocus()
        PopupMenu(context, anchor, Gravity.END).apply {
            mPopupMenu = this
            menuInflater.inflate(R.menu.menu_floating_console_more, menu)
            menu.findItem(R.id.action_resize)?.isChecked = isResizeMode
            menu.findItem(R.id.action_input_bar)?.isChecked = configurator.isInputVisible
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_minimize -> {
                        exitResizeMode()
                        releaseInputFocus()
                        collapseNearCurrentPosition()
                    }
                    R.id.action_resize -> toggleResizeMode()
                    R.id.action_input_bar -> console.setInputVisible(!configurator.isInputVisible)
                    R.id.action_clear -> console.clear()
                    R.id.action_copy -> console.copyAll()
                    R.id.action_settings -> showSettingsDialog()
                    R.id.action_close -> console.hide()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            setOnDismissListener {
                mPopupMenu = null
                if (!hadInputFocus && consoleView?.hasInputFocus != true) window?.releaseWindowFocus()
            }
            show()
        }
    }

    private fun showSettingsDialog() {
        val context = mContext ?: expandedView?.context ?: return
        ConsoleSettingsDialog(context, FloatySettingsModel()).build().showAdaptive()
    }

    private inner class FloatySettingsModel : ConsoleSettingsDialog.Model {

        override var isTimeVisible: Boolean
            get() = configurator.isTimeVisible
            set(value) = console.setTimeVisible(value)

        override var timeFormat: String
            get() = configurator.timeFormat
            set(value) = console.setTimeFormat(value)

        override var isColorful: Boolean
            get() = configurator.isColorful
            set(value) = console.setColorful(value)

        override var textSize: Float
            get() = configurator.contentTextSize ?: consoleView?.textSize ?: 14f
            set(value) = console.setContentTextSize(value)

        override val hasWindowOptions = true

        override var isAvoidStatusBar: Boolean
            get() = configurator.isAvoidStatusBar
            set(value) = console.setAvoidStatusBar(value)

        override var titleBackgroundAlpha: Double
            get() = configurator.titleBackgroundAlpha
            set(value) = console.setTitleBackgroundAlpha(value)

        override var contentBackgroundAlpha: Double
            get() = configurator.contentBackgroundAlpha
            set(value) = console.setContentBackgroundAlpha(value)

    }

    // ========== Countdown icon / 倒计时图标 ==========

    fun setMoreButtonIcon(resId: Int) {
        mMoreIconRes = resId
        consoleView?.setMoreButtonIcon(resId, COUNTDOWN_ICON_PADDING_DP)
    }

    fun resetMoreButtonIcon() {
        mMoreIconRes = null
        consoleView?.setMoreButtonIcon(R.drawable.ic_more_vert_24dp)
    }

    // ========== Resize mode / 尺寸调整模式 ==========

    fun toggleResizeMode() {
        if (isResizeMode) exitResizeMode() else enterResizeMode()
    }

    fun enterResizeMode() {
        val binding = mBinding ?: return
        if (!isAttached) return
        releaseInputFocus()
        isResizeMode = true
        currentSize?.let { mResizeController.updateLabel(it.width, it.height) }
        binding.resizeOverlay.isVisible = true
    }

    fun exitResizeMode() {
        isResizeMode = false
        mBinding?.resizeOverlay?.isVisible = false
    }

    private inner class ResizeController {

        private var mStartRawX = 0f
        private var mStartRawY = 0f
        private var mStartWidth = 0
        private var mStartHeight = 0
        private var mAffectsWidth = true
        private var mAffectsHeight = true
        private var mMoved = false

        @SuppressLint("ClickableViewAccessibility")
        fun bind(binding: FloatingConsoleExpandBinding) {
            binding.resizeOverlay.setOnTouchListener { view, event -> onTouch(view, event) }
        }

        fun updateLabel(width: Int, height: Int) {
            mBinding?.resizeSizeLabel?.text = "$width x $height"
        }

        private fun onTouch(overlay: View, event: MotionEvent): Boolean {
            val view = expandedView ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mStartRawX = event.rawX
                    mStartRawY = event.rawY
                    mStartWidth = view.width
                    mStartHeight = view.height
                    mMoved = false
                    val edge = dp(EDGE_ZONE_DP)
                    val nearEnd = overlay.width - event.x <= edge
                    val nearBottom = overlay.height - event.y <= edge
                    mAffectsWidth = nearEnd || !nearBottom
                    mAffectsHeight = nearBottom || !nearEnd
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - mStartRawX
                    val dy = event.rawY - mStartRawY
                    val slop = ViewConfiguration.get(overlay.context).scaledTouchSlop
                    if (!mMoved && hypot(dx, dy) < slop) return true
                    mMoved = true
                    resizeTo(
                        if (mAffectsWidth) mStartWidth + dx.roundToInt() else mStartWidth,
                        if (mAffectsHeight) mStartHeight + dy.roundToInt() else mStartHeight,
                    )
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!mMoved) {
                        if (event.actionMasked == MotionEvent.ACTION_UP) exitResizeMode()
                        return true
                    }
                    currentSize?.let { console.onUserResized(it.width, it.height) }
                    return true
                }
            }
            return false
        }

        private fun resizeTo(width: Int, height: Int) {
            val view = expandedView ?: return
            val bridge = mWindow?.windowBridge ?: return
            val size = ConsoleWindowGeometry.clampSize(width, height, bridge.x, bridge.y, minWidth(), minHeight(), screen(bridge))
            if (abs(view.layoutParams.width - size.width) == 0 && abs(view.layoutParams.height - size.height) == 0) return
            setViewMeasure(view, size.width, size.height)
            updateLabel(size.width, size.height)
        }

    }

    companion object {

        private const val MIN_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 120
        private const val EDGE_ZONE_DP = 32
        private const val COUNTDOWN_ICON_PADDING_DP = 7
        private const val COLLAPSED_ICON_SIZE_DP = 46
        private const val KEYBOARD_FREE_SCREEN_RATIO = 0.55

    }

}
