package org.autojs.autojs.core.console

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.deviceScreenHeight
import org.autojs.autojs.runtime.api.ScreenMetrics.Companion.deviceScreenWidth
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloaty.AbstractResizableExpandableFloaty
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.util.ViewUtils.setViewMeasure
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/4/20.
 */
class ConsoleFloaty(private val mConsole: ConsoleImpl) : AbstractResizableExpandableFloaty() {

    private var mContextWrapper: ContextWrapper? = null
    private var mResizer: View? = null
    private var mMoveCursor: View? = null
    private var mTitleView: TextView? = null
    private var mTitleText: CharSequence? = null
    private var mTitleBackgroundColor: Int? = null
    private var mTitleBackgroundAlpha: Int? = null
    private var mtTitleIconsTint: Int? = null

    private var mMinimizeButton: ImageView? = null
    private var mControllingButton: ImageView? = null
    private var mCloseButton: ImageView? = null

    private val defaultViewWidth
        // @History deviceScreenWidth * 2 / 3
        get() = (deviceScreenWidth * 0.71).toInt()

    private val defaultViewHeight
        // @History deviceScreenHeight / 3
        get() = (deviceScreenHeight * 0.42).toInt()

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
        return View.inflate(mContextWrapper, R.layout.floating_window_collapse, null)
    }

    private fun ensureContextWrapper(context: Context) {
        mContextWrapper ?: ContextThemeWrapper(context, R.style.ConsoleTheme).also { mContextWrapper = it }
    }

    override fun inflateExpandedView(service: FloatyService, window: ResizableExpandableFloatyWindow): View {
        ensureContextWrapper(service)
        return View.inflate(mContextWrapper, R.layout.floating_console_expand, null).also { view ->
            expandedView = view
            setUpConsole(view, window)
            setInitialMeasure(service)
        }
    }

    private fun setInitialMeasure(service: FloatyService) {

        // @Caution by SuperMonster003 on Mar 28, 2023.
        //  ! This will cause initialized console floaty view stuck
        //  ! without responding to button listeners and log listener.
        // view.post(() -> ViewUtils.setViewMeasure(view, mInitialWidth, mInitialHeight));

        mConsole.uiHandler.post {
            val width = service.initialSize?.width?.toInt() ?: 0
            val height = service.initialSize?.height?.toInt() ?: 0
            setViewMeasure(
                expandedView!!,
                // if (width != 0) width else view.context.resources.getDimensionPixelSize(R.dimen.expanded_view_initial_width),
                if (width > 0) width else defaultViewWidth,
                // if (height != 0) height else view.context.resources.getDimensionPixelSize(R.dimen.expanded_view_initial_height),
                if (height > 0) height else defaultViewHeight,
            )
        }
    }

    private fun initConsoleTitleBar(view: View, window: ResizableExpandableFloatyWindow) {
        mTitleView = view.findViewById<TextView?>(R.id.title)?.apply {
            text = mTitleText
        }
        titleBarView = view.findViewById<LinearLayout?>(R.id.title_bar).apply {
            mTitleBackgroundColor?.let { setBackgroundColor(it) }
            mTitleBackgroundAlpha?.let { background.alpha = it }
        }
        view.findViewById<ImageView>(R.id.close).let {
            it.setOnClickListener { mConsole.hide() }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mCloseButton = it
        }
        view.findViewById<ImageView>(R.id.move_or_resize).let {
            it.setOnClickListener {
                mMoveCursor?.run {
                    if (visibility == View.VISIBLE) {
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
        view.findViewById<ImageView>(R.id.minimize).let {
            it.setOnClickListener { window.collapse() }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mMinimizeButton = it
        }
    }

    private fun setUpConsole(view: View, window: ResizableExpandableFloatyWindow) {
        view.findViewById<FloatingConsoleView>(R.id.console).apply {
            setConsole(mConsole)
            setWindow(window)
        }
        initConsoleTitleBar(view, window)
    }

    override fun getResizerView(expandedView: View): View? {
        return expandedView.findViewById<View?>(R.id.resizer).also { mResizer = it }
    }

    override fun getMoveCursorView(expandedView: View): View? {
        return expandedView.findViewById<View?>(R.id.move_cursor).also { mMoveCursor = it }
    }

    fun setTitle(title: CharSequence?) {
        mTitleText = title
        mTitleView?.apply { post { text = title } }
    }

    fun setTitleBackgroundColor(@ColorInt color: Int) {
        mTitleBackgroundColor = color
        titleBarView?.apply { post { setBackgroundColor(color) } }
    }

    fun setTitleBackgroundAlpha(alpha: Int) {
        mTitleBackgroundAlpha = alpha
        titleBarView?.apply { post { background.alpha = alpha } }
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

    fun setTitleTextColor(color: Int) {
        mTitleView?.setTextColor(color)
    }

    internal fun setCloseButton(resId: Int) {
        mCloseButton?.apply {
            post { setImageDrawable(ResourcesCompat.getDrawable(mConsole.uiHandler.context.resources, resId, null)) }
        }
    }

    fun clearStates() {
        mTitleBackgroundColor = null
        mTitleBackgroundAlpha = null
        mtTitleIconsTint = null
    }

}