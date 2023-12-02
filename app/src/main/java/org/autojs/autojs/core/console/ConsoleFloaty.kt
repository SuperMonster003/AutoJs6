package org.autojs.autojs.core.console

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
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
import org.autojs.autojs6.databinding.FloatingConsoleExpandBinding
import org.autojs.autojs6.databinding.FloatingWindowCollapseBinding

/**
 * Created by Stardust on Apr 20, 2017.
 */
class ConsoleFloaty(private val mConsole: ConsoleImpl) : AbstractResizableExpandableFloaty() {

    private lateinit var expandedBinding: FloatingConsoleExpandBinding
    private lateinit var collapsedBinding: FloatingWindowCollapseBinding

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
        return FloatingWindowCollapseBinding.inflate(LayoutInflater.from(mContextWrapper)).also { collapsedBinding = it }.root
    }

    private fun ensureContextWrapper(context: Context) {
        mContextWrapper ?: ContextThemeWrapper(context, R.style.ConsoleTheme).also { mContextWrapper = it }
    }

    override fun inflateExpandedView(service: FloatyService, window: ResizableExpandableFloatyWindow): View {
        ensureContextWrapper(service)
        return FloatingConsoleExpandBinding.inflate(LayoutInflater.from(mContextWrapper)).also { expandedBinding = it }.root.also { view ->
            expandedView = view
            setUpConsole(window)
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

    private fun initConsoleTitleBar(window: ResizableExpandableFloatyWindow) {
        mTitleView = expandedBinding.title.apply {
            text = mTitleText
        }
        titleBarView = expandedBinding.titleBar.apply {
            mTitleBackgroundColor?.let { setBackgroundColor(it) }
            mTitleBackgroundAlpha?.let { background.alpha = it }
        }
        expandedBinding.close.let {
            it.setOnClickListener { mConsole.hide() }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mCloseButton = it
        }
        expandedBinding.moveOrResize.let {
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
        expandedBinding.minimize.let {
            it.setOnClickListener { window.collapse() }
            mtTitleIconsTint?.let { color -> it.imageTintList = ColorStateList.valueOf(color) }
            mMinimizeButton = it
        }
    }

    private fun setUpConsole(window: ResizableExpandableFloatyWindow) {
        expandedBinding.console.apply {
            setConsole(mConsole)
            setWindow(window)
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
            post { setImageDrawable(ResourcesCompat.getDrawable(mConsole.uiHandler.applicationContext.resources, resId, null)) }
        }
    }

    fun clearStates() {
        mTitleBackgroundColor = null
        mTitleBackgroundAlpha = null
        mtTitleIconsTint = null
    }

}