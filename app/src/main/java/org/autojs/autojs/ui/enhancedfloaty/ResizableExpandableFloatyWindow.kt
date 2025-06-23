package org.autojs.autojs.ui.enhancedfloaty

import android.graphics.PixelFormat
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
 */
open class ResizableExpandableFloatyWindow(private var floaty: ResizableExpandableFloaty) : FloatyWindow() {

    private val mViewStack = ViewStack { v -> mViewSwitcher.setSecondView(v) }

    private lateinit var mCollapsedView: View
    private lateinit var mExpandedView: View
    private lateinit var mViewSwitcher: ViewSwitcher
    private lateinit var mDragGesture: DragGesture

    private var mResizer: View? = null
    private var mMoveCursor: View? = null
    private var mViewAttachedTasks: CopyOnWriteArrayList<() -> Unit> = CopyOnWriteArrayList()

    private var mCollapsedViewX = 0
    private var mCollapsedViewY = 0
    private var mExpandedViewX = 0
    private var mExpandedViewY = 0

    override fun onCreateView(service: FloatyService): View {
        inflateWindowViews(service)
        val windowView = View.inflate(service, R.layout.ef_expandable_floaty_container, null).apply {
            isFocusableInTouchMode = true
        }
        mViewSwitcher = windowView.findViewById<ViewSwitcher?>(R.id.container).also { switcher ->
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

    fun addOnViewAttachedTask(task: () -> Unit) {
        mViewAttachedTasks.add(task)
    }

    override fun onAttachToWindow(view: View, manager: WindowManager) {
        super.onAttachToWindow(view, manager)
        initGesture()
        setKeyListener()
        setInitialState()
        mViewAttachedTasks.forEach { it.invoke() }
        clearOnViewAttachedTask()
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
            mCollapsedViewX = floaty.initialY
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
        mViewSwitcher.showSecond()
        // enableWindowLimit();
        if (floaty.shouldRequestFocusWhenExpand()) {
            requestWindowFocus()
        }
        mDragGesture.isKeepToSide = false
        windowBridge?.updatePosition(mExpandedViewX, mExpandedViewY)
    }

    fun collapse() {
        mViewSwitcher.showFirst()
        disableWindowFocus()
        setWindowLayoutNoLimit()
        mDragGesture.isKeepToSide = true
        windowBridge?.updatePosition(mCollapsedViewX, mCollapsedViewY)
    }

    private fun onBackPressed() {
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

    private fun disableWindowFocus() {
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags or FLAG_NOT_FOCUSABLE })
    }

    fun setWindowLayoutInScreen() {
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags or FLAG_LAYOUT_IN_SCREEN })
    }

    fun requestWindowFocus() {
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags and FLAG_NOT_FOCUSABLE.inv() })
        windowView?.requestFocus()
    }

    private fun setWindowLayoutNoLimit() {
        updateWindowLayoutParams(windowLayoutParams.apply { flags = flags or FLAG_LAYOUT_NO_LIMITS })
    }

    fun setTouchable(touchable: Boolean) {
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
