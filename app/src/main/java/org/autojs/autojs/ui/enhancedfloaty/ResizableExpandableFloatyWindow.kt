package org.autojs.autojs.ui.enhancedfloaty

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge.DefaultImpl
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.ui.enhancedfloaty.gesture.ResizeGesture
import org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat
import org.autojs.autojs.ui.widget.ViewSwitcher
import org.autojs.autojs6.R
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by Stardust on Apr 18, 2017.
 * Modified by SuperMonster003 as of Apr 29, 2023.
 * Transformed by SuperMonster003 on Dec 2, 2023.
 * Modified by SuperMonster003 as of Sep 8, 2026.
 */
open class ResizableExpandableFloatyWindow(private var floaty: ResizableExpandableFloaty) : FloatyWindow() {

    private val mViewStack = ViewStack { v -> mViewSwitcher.setSecondView(v) }

    private lateinit var mCollapsedView: View
    private lateinit var mExpandedView: View
    private lateinit var mViewSwitcher: ViewSwitcher
    private var mDragGesture: DragGesture? = null

    private var mResizer: View? = null
    private var mMoveCursor: View? = null
    private var mViewAttachedTasks: CopyOnWriteArrayList<() -> Unit> = CopyOnWriteArrayList()

    private var mCollapsedViewX = 0
    private var mCollapsedViewY = 0
    private var mExpandedViewX = 0
    private var mExpandedViewY = 0

    /**
     * Whether the window view is currently attached to the window manager. Set on the UI thread in
     * [onAttachToWindow] and cleared in [close], so callers can tell a pending window (added to a
     * starting [FloatyService]) from a live one.
     *
     * zh-CN: 窗口视图当前是否已附加到窗口管理器. 在 UI 线程于 [onAttachToWindow] 置位并在 [close] 清除, 便于调用方区分尚未创建的窗口 (已加入正在启动的 [FloatyService]) 与已存在的窗口.
     */
    @Volatile
    var isWindowAttached = false
        private set

    val isExpanded: Boolean
        get() = ::mViewSwitcher.isInitialized && mViewSwitcher.currentView === mExpandedView

    override fun onCreateView(service: FloatyService): View {
        inflateWindowViews(service)
        val windowView = View.inflate(service, R.layout.ef_expandable_floaty_container, null).apply {
            isFocusableInTouchMode = true
        }
        mViewSwitcher = windowView.findViewById<ViewSwitcher>(R.id.container).also { switcher ->
            switcher.measureAllChildren = false
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).let { params ->
                switcher.addView(mCollapsedView, params)
                switcher.addView(mExpandedView, params)
            }
        }
        mViewStack.setRootView(mExpandedView)
        return windowView
    }

    fun clearOnViewAttachedTask() {
        mViewAttachedTasks.clear()
    }

    /**
     * Runs [task] once the window view is attached; immediately when it already is.
     * zh-CN: 窗口视图附加后执行 [task]; 若已附加则立即执行.
     */
    fun addOnViewAttachedTask(task: () -> Unit) {
        if (isWindowAttached) {
            task.invoke()
        } else {
            mViewAttachedTasks.add(task)
        }
    }

    override fun onAttachToWindow(view: View, manager: WindowManager) {
        super.onAttachToWindow(view, manager)
        initGesture()
        setKeyListener()
        setInitialState()
        isWindowAttached = true
        mViewAttachedTasks.forEach { it.invoke() }
        clearOnViewAttachedTask()
        floaty.onAttachedToWindow(this)
    }

    override fun close() {
        val wasAttached = isWindowAttached
        isWindowAttached = false
        if (wasAttached) {
            floaty.onDetachedFromWindow(this)
        }
        super.close()
    }

    private fun initGesture() {
        enableResize()
        enableMove()
    }

    private fun enableResize() {
        if (mResizer != null) {
            ResizeGesture.enableResize(mResizer, mExpandedView, windowBridge)
        }
    }

    private fun enableMove() {
        if (mMoveCursor != null) {
            DragGesture(windowBridge, mMoveCursor).apply { pressedAlpha = 1.0f }
        }
        this.mDragGesture = DragGesture(windowBridge, mCollapsedView).apply {
            unpressedAlpha = floaty.collapsedViewUnpressedAlpha
            pressedAlpha = floaty.collapsedViewPressedAlpha
            isKeepToSide = true
            keepToSideHiddenWidthRadio = floaty.collapsedHiddenWidthRadio
            setOnDraggedViewClickListener { expand() }
        }
    }

    private fun setKeyListener() {
        windowView?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onBackPressed()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                onHomePressed()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
    }

    private fun setInitialState() {
        if (floaty.isInitialExpanded) {
            mExpandedViewX = floaty.initialX
            mExpandedViewY = floaty.initialY
            expand()
        } else {
            mCollapsedViewX = floaty.initialX
            mCollapsedViewY = floaty.initialY
            windowBridge?.updatePosition(mCollapsedViewX, mCollapsedViewY)
        }
    }

    override fun onCreateWindowBridge(params: WindowManager.LayoutParams): WindowBridge {
        return object : DefaultImpl(params, windowManager, windowView) {
            override fun updatePosition(x: Int, y: Int) {
                super.updatePosition(x, y)
                if (mViewSwitcher.currentView === mExpandedView) {
                    mExpandedViewX = x
                    mExpandedViewY = y
                } else {
                    mCollapsedViewX = x
                    mCollapsedViewY = y
                }
            }
        }
    }

    private fun inflateWindowViews(service: FloatyService?) {
        mExpandedView = floaty.inflateExpandedView(service, this)
        mCollapsedView = floaty.inflateCollapsedView(service, this)
        mResizer = floaty.getResizerView(mExpandedView)
        mMoveCursor = floaty.getMoveCursorView(mExpandedView)
    }

    override fun onCreateWindowLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowTypeCompat.getPhoneWindowType(),
            INITIAL_WINDOW_PARAM_FLAG,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    fun expand() {
        if (!::mViewSwitcher.isInitialized) return
        mViewSwitcher.showSecond()
        // enableWindowLimit();
        if (floaty.shouldRequestFocusWhenExpand()) {
            requestWindowFocus()
        }
        mDragGesture?.isKeepToSide = false
        windowBridge?.updatePosition(mExpandedViewX, mExpandedViewY)
    }

    /** Collapses the window and places the collapsed view at ([x], [y]). zh-CN: 收起窗口并将收起后的视图置于 ([x], [y]). */
    fun collapse(x: Int, y: Int) {
        mCollapsedViewX = x
        mCollapsedViewY = y
        collapse()
    }

    fun collapse() {
        if (!::mViewSwitcher.isInitialized) return
        mViewSwitcher.showFirst()
        releaseWindowFocus()
        setWindowLayoutNoLimit()
        mDragGesture?.isKeepToSide = true
        windowBridge?.updatePosition(mCollapsedViewX, mCollapsedViewY)
    }

    private fun onBackPressed() {
        if (floaty.onBackPressed(this)) {
            return
        }
        if (mViewStack.canGoBack()) {
            mViewStack.goBack()
        } else {
            collapse()
        }
    }

    private fun onHomePressed() {
        mViewStack.goBackToFirst()
        collapse()
    }

    /**
     * Gives key and IME focus back to the app behind the window.
     * zh-CN: 将按键与输入法焦点交还给窗口下方的应用.
     */
    fun releaseWindowFocus() {
        if (!isWindowAttached) return
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags or FLAG_NOT_FOCUSABLE })
    }

    fun setWindowLayoutInScreen() {
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags or FLAG_LAYOUT_IN_SCREEN })
    }

    /**
     * Lays the window out over the whole display: the status bar and a display cutout no longer
     * offset its coordinates, only the navigation bar stays excluded from its parent frame.
     *
     * zh-CN: 让窗口在整个屏幕范围内布局: 状态栏与刘海区域不再使其坐标偏移, 仅导航栏仍被排除在父框架之外.
     */
    fun setWindowLayoutEdgeToEdge() {
        if (!isWindowAttached) return
        updateWindowLayoutParams(windowLayoutParams.apply {
            flags = flags or FLAG_LAYOUT_IN_SCREEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        })
    }

    fun requestWindowFocus() {
        if (!isWindowAttached) return
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags and FLAG_NOT_FOCUSABLE.inv() })
        windowView?.requestFocus()
    }

    val isWindowFocusable: Boolean
        get() = isWindowAttached && windowLayoutParams.flags and FLAG_NOT_FOCUSABLE == 0

    private fun setWindowLayoutNoLimit() {
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags or FLAG_LAYOUT_NO_LIMITS })
    }

    fun setTouchable(touchable: Boolean) {
        if (!isWindowAttached) return
        updateWindowLayoutParams(windowLayoutParams.apply {
            flags = when (touchable) {
                true -> flags and FLAG_NOT_TOUCHABLE.inv()
                else -> flags or FLAG_NOT_TOUCHABLE
            }
        })
    }

    companion object {

        private const val INITIAL_WINDOW_PARAM_FLAG = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_NO_LIMITS

    }
}
