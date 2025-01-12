package org.autojs.autojs.core.floaty

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.ui.inflater.Exceptions
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableFloaty
import org.autojs.autojs.ui.enhancedfloaty.ResizableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge.DefaultImpl
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.ui.enhancedfloaty.gesture.ResizeGesture
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatyWindowBinding

/**
 * Created by Stardust on Dec 5, 2017.
 * Modified by SuperMonster003 as of Jun 10, 2022.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 */
class BaseResizableFloatyWindow(context: Context, viewSupplier: ViewSupplier) : ResizableFloatyWindow(BaseResizableFloaty(context, viewSupplier)) {

    private val mInflateException = VolatileDispose<RuntimeException>()
    private val mOffset = context.resources.getDimensionPixelSize(R.dimen.floaty_window_offset)

    private val mFloaty by lazy { floaty as BaseResizableFloaty }
    private val mCloseButton by lazy { mFloaty.getCloseButtonView(rootView) }
    private val mMoveCursor by lazy { mFloaty.getMoveCursorView(rootView) }
    private val mResizer by lazy { mFloaty.getResizerView(rootView) }

    val rootView by lazy { mFloaty.rootView }

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

    fun waitForCreation(): RuntimeException? = mInflateException.blockedGetOrThrow(ScriptInterruptedException::class.java)

    override fun onCreateWindowBridge(params: LayoutParams): WindowBridge {
        return object : DefaultImpl(params, windowManager, windowView) {
            override fun getX() = super.getX() + mOffset

            override fun getY() = super.getY() + mOffset

            override fun updatePosition(x: Int, y: Int) = super.updatePosition(x - mOffset, y - mOffset)
        }
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
        windowView?.requestLayout()
    }

    private class BaseResizableFloaty(private val context: Context, private val contentViewSupplier: ViewSupplier) : ResizableFloaty {

        private val floatyWindowBinding = FloatyWindowBinding.inflate(LayoutInflater.from(context))

        val rootView = floatyWindowBinding.root

        override fun inflateView(floatyService: FloatyService, resizableFloatyWindow: ResizableFloatyWindow): FrameLayout {
            contentViewSupplier.inflate(context, floatyWindowBinding.container)
            return rootView
        }

        override fun getResizerView(view: View): ImageView = floatyWindowBinding.resizer

        override fun getMoveCursorView(view: View): ImageView = floatyWindowBinding.moveCursor

        override fun getCloseButtonView(view: View): ImageView = floatyWindowBinding.close

    }

    interface ViewSupplier {
        fun inflate(context: Context, parent: ViewGroup?): View
    }

}