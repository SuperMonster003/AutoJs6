package org.autojs.autojs.core.floaty

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.ui.inflater.Exceptions
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat
import org.autojs.autojs6.R

/**
 * Modified by SuperMonster003 as of Mar 27, 2022.
 * Transformed by SuperMonster003 on Mar 27, 2022.
 */
class RawWindow(private val supplier: BaseResizableFloatyWindow.ViewSupplier) : FloatyWindow() {

    private val mInflateException = VolatileDispose<RuntimeException>()

    lateinit var contentView: View

    override fun onCreate(floatyService: FloatyService, windowManager: WindowManager) {
        try {
            super.onCreate(floatyService, windowManager)
        } catch (e: RuntimeException) {
            mInflateException.setAndNotify(e)
            return
        }
        mInflateException.setAndNotify(Exceptions.NO_EXCEPTION)
    }

    override fun onCreateView(floatyService: FloatyService): View {
        val windowView = View.inflate(floatyService, R.layout.raw_window, null) as ViewGroup
        contentView = supplier.inflate(floatyService, windowView)
        return windowView
    }

    override fun onCreateWindowLayoutParams(): LayoutParams {
        @Suppress("DEPRECATION")
        val flags = LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_FULLSCREEN or
                LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_TRANSLUCENT_STATUS
        return LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            WindowTypeCompat.getWindowType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    fun waitForCreation(): RuntimeException? = mInflateException.blockedGetOrThrow(ScriptInterruptedException::class.java)

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

    fun setTouchable(touchable: Boolean) {
        windowLayoutParams.apply {
            flags = if (touchable) {
                flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                flags or LayoutParams.FLAG_NOT_TOUCHABLE
            }
            updateWindowLayoutParams(this)
        }
    }
}