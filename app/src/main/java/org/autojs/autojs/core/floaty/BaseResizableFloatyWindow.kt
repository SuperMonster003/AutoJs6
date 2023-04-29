package org.autojs.autojs.core.floaty

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.ResizableFloaty
import org.autojs.autojs.ui.enhancedfloaty.ResizableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge.DefaultImpl
import org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.ui.inflater.inflaters.Exceptions
import org.autojs.autojs6.R


/**
 * Created by Stardust on 2017/12/5.
 * Modified by SuperMonster003 as of Jun 10, 2022.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 */
class BaseResizableFloatyWindow(context: Context, viewSupplier: ViewSupplier) : FloatyWindow() {

    interface ViewSupplier {
        fun inflate(context: Context?, parent: ViewGroup?): View
    }

    private val mInflateException = VolatileDispose<RuntimeException>()
    private var mCloseButton: View
    private var mOffset = context.resources.getDimensionPixelSize(R.dimen.floaty_window_offset)
    private val mFloaty = MyFloaty(context, viewSupplier)
    val rootView = mFloaty.rootView
    private var mMoveCursor = mFloaty.getMoveCursorView(rootView)!!
    private var mResizer = mFloaty.getResizerView(rootView)!!


    // @Reference to aiselp (https://github.com/aiselp) on Mar 27, 2023.
    //  ! https://github.com/kkevsekk1/AutoX/pull/529/commits/782f1c3c12dee64d9b1ad70aba462afaf60313a4#diff-b8e544bc658140f04a7fa7171cc938032f18c92495ca25bb08e2d2eb546b70f5
    init {
        val layoutParams = createWindowLayoutParams()
        val windowView = View.inflate(context, R.layout.raw_window, null as ViewGroup?) as ViewGroup
        val params = ViewGroup.LayoutParams(-2, -2)
        windowView.addView(rootView, params)
        windowView.isFocusableInTouchMode = true
        mCloseButton = windowView.findViewById(R.id.close)
        super.setWindowLayoutParams(layoutParams)
        super.setWindowView(windowView)
        super.setWindowManager(context.getSystemService(FloatyService.WINDOW_SERVICE) as WindowManager)
        super.setWindowBridge(super.onCreateWindowBridge(layoutParams))
    }

    @Suppress("DEPRECATION")
    private fun createWindowLayoutParams() = WindowManager
        .LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowTypeCompat.getWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    // fun waitForCreation(): RuntimeException = mInflateException.blockedGetOrThrow(ScriptInterruptedException::class.java)

    override fun onCreateWindowBridge(params: WindowManager.LayoutParams): WindowBridge {
        return object : DefaultImpl(params, windowManager, windowView) {
            override fun getX() = super.getX() + mOffset

            override fun getY() = super.getY() + mOffset

            override fun updatePosition(x: Int, y: Int) = super.updatePosition(x - mOffset, y - mOffset)
        }
    }

    override fun onCreateWindowLayoutParams(): WindowManager.LayoutParams {
        return super.getWindowLayoutParams()
    }

    override fun onCreate(service: FloatyService, manager: WindowManager) {
        try {
            super.onCreate(service, manager)
        } catch (e: RuntimeException) {
            mInflateException.setAndNotify(e)
            return
        }
        mInflateException.setAndNotify(Exceptions.NO_EXCEPTION)
    }

    fun setOnCloseButtonClickListener(listener: View.OnClickListener?) {
        mCloseButton.setOnClickListener(listener)
    }

    var isAdjustEnabled: Boolean
        get() = mMoveCursor.visibility == View.VISIBLE
        set(enabled) {
            if (!enabled) {
                mMoveCursor.visibility = View.GONE
                mResizer.visibility = View.GONE
                mCloseButton.visibility = View.GONE
            } else {
                mMoveCursor.visibility = View.VISIBLE
                mResizer.visibility = View.VISIBLE
                mCloseButton.visibility = View.VISIBLE
            }
        }

    override fun onCreateView(floatyService: FloatyService?): View = super.getWindowView()

    fun disableWindowFocus() {
        val windowLayoutParams = windowLayoutParams
        windowLayoutParams.flags = windowLayoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        updateWindowLayoutParams(windowLayoutParams)
    }

    fun requestWindowFocus() {
        val windowLayoutParams = windowLayoutParams
        windowLayoutParams.flags = windowLayoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        updateWindowLayoutParams(windowLayoutParams)
        windowView.requestLayout()
    }

    private class MyFloaty(context: Context, contentViewSupplier: ViewSupplier) : ResizableFloaty {
        val rootView: View = View.inflate(context, R.layout.floaty_window, null)
        val container: FrameLayout = rootView.findViewById(R.id.container)

        init {
            contentViewSupplier.inflate(context, container)
        }

        override fun inflateView(floatyService: FloatyService, resizableFloatyWindow: ResizableFloatyWindow) = rootView

        override fun getResizerView(view: View): View? {
            return view.findViewById(R.id.resizer)
        }

        override fun getMoveCursorView(view: View): View? {
            return view.findViewById(R.id.move_cursor)
        }
    }
}