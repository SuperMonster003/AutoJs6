package org.autojs.autojs.core.console

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloaty.AbstractResizableExpandableFloaty
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.util.DrawableUtils
import org.autojs.autojs.util.ViewUtils.setViewMeasure
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatingConsoleExpandBinding
import org.autojs.autojs6.databinding.FloatingWindowCollapseBinding
import kotlin.math.roundToInt

/**
 * Created by Stardust on Apr 20, 2017.
 */
class ConsoleFloaty(private val console: ConsoleImpl) : AbstractResizableExpandableFloaty() {

    private lateinit var expandedBinding: FloatingConsoleExpandBinding
    private lateinit var collapsedBinding: FloatingWindowCollapseBinding

    private lateinit var mContextWrapper: ContextWrapper

    private var mResizer: View? = null
    private var mMoveCursor: View? = null
    private var mTitleView: TextView? = null
    private var mTitleText: CharSequence? = null
    private var mtTitleIconsTint: Int? = null

    private var mMinimizeButton: ImageView? = null
    private var mControllingButton: ImageView? = null
    private var mCloseButton: ImageView? = null

    val defaultViewWidth
        // @History deviceScreenWidth * 2 / 3
        get() = ScreenMetrics.deviceScreenWidth * 0.71

    val defaultViewHeight
        // @History deviceScreenHeight / 3
        get() = ScreenMetrics.deviceScreenHeight * 0.42

    var expandedView: View? = null
        private set

    var titleBarView: LinearLayout? = null
        private set

    init {
        isInitialExpanded = true
        isShouldRequestFocusWhenExpand = false
        collapsedViewUnpressedAlpha = 1.0f
    }

    override fun inflateCollapsedView(service: FloatyService, window: ResizableExpandableFloatyWindow): View {
        ensureContextWrapper(service)
        return FloatingWindowCollapseBinding.inflate(LayoutInflater.from(mContextWrapper)).also { collapsedBinding = it }.root
    }

    override fun inflateExpandedView(service: FloatyService, window: ResizableExpandableFloatyWindow): View {
        ensureContextWrapper(service)
        return FloatingConsoleExpandBinding.inflate(LayoutInflater.from(mContextWrapper)).also { expandedBinding = it }.root.also { view ->
            expandedView = view
            setUpConsole(window)
            setInitialMeasure(service)
        }
    }

    private fun ensureContextWrapper(context: Context) {
        if (!::mContextWrapper.isInitialized) {
            mContextWrapper = ContextThemeWrapper(context, R.style.ConsoleTheme)
        }
    }

    private fun setInitialMeasure(service: FloatyService) {

        // @Caution by SuperMonster003 on Mar 28, 2023.
        //  ! This will cause initialized console floaty view stuck
        //  ! without responding to button listeners and log listener.
        //  ! zh-CN:
        //  ! 这将导致初始化的控制台浮动视图卡住, 无法响应按钮监听器和日志监听器.
        // view.post(() -> ViewUtils.setViewMeasure(view, mInitialWidth, mInitialHeight));

        console.uiHandler.post {
            val width = service.initialSize?.width ?: 0.0
            val height = service.initialSize?.height ?: 0.0
            setViewMeasure(
                expandedView!!,
                // if (width != 0) width else view.context.resources.getDimensionPixelSize(R.dimen.expanded_view_initial_width),
                (if (width > 0) width else defaultViewWidth).roundToInt(),
                // if (height != 0) height else view.context.resources.getDimensionPixelSize(R.dimen.expanded_view_initial_height),
                (if (height > 0) height else defaultViewHeight).roundToInt(),
            )
        }
    }

    private fun initConsoleTitleBar(window: ResizableExpandableFloatyWindow) {
        mTitleView = expandedBinding.title.apply {
            text = mTitleText
        }
        titleBarView = expandedBinding.titleBar.apply {
            setTitleViewBackgroundColor(this, console.configurator.titleBackgroundColor)
        }
        expandedBinding.close.let {
            it.setOnClickListener { console.hide() }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mCloseButton = it
        }
        expandedBinding.moveOrResize.let {
            it.setOnClickListener {
                mMoveCursor?.run {
                    if (isVisible) {
                        visibility = View.GONE
                        mResizer?.visibility = View.GONE
                    } else {
                        visibility = View.VISIBLE
                        mResizer?.visibility = View.VISIBLE
                    }
                }
            }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mControllingButton = it
        }
        expandedBinding.minimize.let {
            it.setOnClickListener { window.collapse() }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mMinimizeButton = it
        }
    }

    private fun setUpConsole(window: ResizableExpandableFloatyWindow) {
        expandedBinding.console.apply {
            setConsole(console)
        }
        initConsoleTitleBar(window)
    }

    override fun getResizerView(expandedView: View): View {
        return expandedBinding.resizer.also { mResizer = it }
    }

    override fun getMoveCursorView(expandedView: View): View {
        return expandedBinding.moveCursor.also { mMoveCursor = it }
    }

    fun setTitle(title: CharSequence?) {
        mTitleText = title
        mTitleView?.apply { post { text = title } }
    }

    fun getTitle(): String = mTitleText?.toString() ?: ""

    fun setTitleBackgroundColor(@ColorInt color: Int) {
        console.configurator.setTitleBackgroundColor(color)
        titleBarView?.apply { post { setTitleViewBackgroundColor(this, console.configurator.titleBackgroundColor) } }
    }

    fun setTitleBackgroundTint(tint: Int) {
        console.configurator.setTitleBackgroundTint(tint)
        titleBarView?.apply { post { setTitleViewBackgroundColor(this, console.configurator.titleBackgroundColor) } }
    }

    fun setTitleBackgroundAlpha(alpha: Int) {
        console.configurator.setTitleBackgroundAlpha(alpha)
        titleBarView?.apply { post { setTitleViewBackgroundColor(this, console.configurator.titleBackgroundColor) } }
    }

    fun setTitleIconsTint(color: Int) {
        mtTitleIconsTint = color
        arrayOf(mMinimizeButton, mControllingButton, mCloseButton).filterNotNull().forEach {
            it.imageTintList = ColorStateList.valueOf(color)
        }
    }

    fun setTitleTextSize(size: Float) {
        mTitleView?.textSize = size
    }

    fun getTitleTextSize(): Float {
        return mTitleView?.textSize ?: 0f
    }

    fun setTitleTextColor(color: Int) {
        mTitleView?.setTextColor(color)
    }

    fun getTitleTextColor() = mTitleView?.textColors?.defaultColor ?: Color.BLACK

    private fun setTitleViewBackgroundColor(view: LinearLayout, color: Int) {
        view.background = DrawableUtils.setDrawableColorFilterSrc(view.background, color)
    }

    internal fun setCloseButton(resId: Int) {
        mCloseButton?.let {
            val res = console.uiHandler.applicationContext.resources
            val padding = (7 * res.displayMetrics.density).toInt()
            it.post {
                it.setImageDrawable(ResourcesCompat.getDrawable(res, resId, null))
                it.setPadding(padding, padding, padding, padding)
            }
        }
    }

    fun clearStates() {
        mtTitleIconsTint = null
    }

}