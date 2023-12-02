package org.autojs.autojs.core.floaty

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.ui.inflater.Exceptions
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.ResizableFloaty
import org.autojs.autojs.ui.enhancedfloaty.ResizableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge.DefaultImpl
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.ui.enhancedfloaty.gesture.ResizeGesture
import org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatyWindowBinding
import org.autojs.autojs6.databinding.RawWindowBinding

/**
 * Created by Stardust on Dec 5, 2017.
 * Modified by SuperMonster003 as of Jun 10, 2022.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 */
class BaseResizableFloatyWindow(context: Context, viewSupplier: ViewSupplier) : FloatyWindow() {

    interface ViewSupplier {
        fun inflate(context: Context?, parent: ViewGroup?): View
    }

    private var rawWindowBinding: RawWindowBinding

    private val mInflateException = VolatileDispose<RuntimeException>()
    private var mOffset = context.resources.getDimensionPixelSize(R.dimen.floaty_window_offset)
    private val mFloaty = MyFloaty(context, viewSupplier)
    val rootView = mFloaty.rootView
    private var mCloseButton = mFloaty.getCloseButtonView(rootView)
    private var mMoveCursor = mFloaty.getMoveCursorView(rootView)
    private var mResizer = mFloaty.getResizerView(rootView)


    // @Reference to aiselp (https://github.com/aiselp) on Mar 27, 2023.
    //  ! https://github.com/kkevsekk1/AutoX/pull/529/commits/782f1c3c12dee64d9b1ad70aba462afaf60313a4#diff-b8e544bc658140f04a7fa7171cc938032f18c92495ca25bb08e2d2eb546b70f5
    init {
        val layoutParams = createWindowLayoutParams()

        rawWindowBinding = RawWindowBinding.inflate(LayoutInflater.from(context))

        val windowView = rawWindowBinding.root
        val params = ViewGroup.LayoutParams(-2, -2)
        windowView.addView(rootView, params)
        windowView.isFocusableInTouchMode = true
        super.setWindowLayoutParams(layoutParams)
        super.setWindowView(windowView)
        super.setWindowManager(context.getSystemService(FloatyService.WINDOW_SERVICE) as WindowManager)
        super.setWindowBridge(super.onCreateWindowBridge(layoutParams))
    }

    private fun createWindowLayoutParams() = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        WindowTypeCompat.getPhoneWindowType(),
        LayoutParams.FLAG_LAYOUT_NO_LIMITS or LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // fun waitForCreation(): RuntimeException = mInflateException.blockedGetOrThrow(ScriptInterruptedException::class.java)

    override fun onCreateWindowBridge(params: LayoutParams): WindowBridge {
        return object : DefaultImpl(params, windowManager, windowView) {
            override fun getX() = super.getX() + mOffset

            override fun getY() = super.getY() + mOffset

            override fun updatePosition(x: Int, y: Int) = super.updatePosition(x - mOffset, y - mOffset)
        }
    }

    override fun onCreateWindowLayoutParams(): LayoutParams {
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

    override fun onViewCreated(view: View?) {
        super.onViewCreated(view)
        initGesture()
    }

    private fun initGesture() {
        enableResize()
        enableMove()
    }

    private fun enableResize() {
        ResizeGesture.enableResize(mResizer, rootView, windowBridge)
    }

    private fun enableMove() {
        DragGesture(windowBridge, mMoveCursor).apply { pressedAlpha = 1.0f }
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
        windowLayoutParams.apply {
            flags = flags or LayoutParams.FLAG_NOT_FOCUSABLE
            updateWindowLayoutParams(this)
        }
    }

    fun requestWindowFocus() {
        windowLayoutParams.apply {
            flags = flags and LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            updateWindowLayoutParams(this)
        }
        windowView.requestLayout()
    }

    private class MyFloaty(context: Context, contentViewSupplier: ViewSupplier) : ResizableFloaty {

        private val floatyWindowBinding = FloatyWindowBinding.inflate(LayoutInflater.from(context))

        val rootView = floatyWindowBinding.root

        init {
            contentViewSupplier.inflate(context, floatyWindowBinding.container)
        }

        override fun inflateView(floatyService: FloatyService, resizableFloatyWindow: ResizableFloatyWindow) = rootView

        override fun getResizerView(view: View): ImageView = floatyWindowBinding.resizer

        override fun getMoveCursorView(view: View): ImageView = floatyWindowBinding.moveCursor

        override fun getCloseButtonView(view: View): ImageView = floatyWindowBinding.close

    }
}