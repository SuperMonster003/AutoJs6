package org.autojs.autojs.core.floaty

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.ui.inflater.Exceptions
import org.autojs.autojs6.R

/**
 * Modified by SuperMonster003 as of Mar 27, 2022.
 * Transformed by SuperMonster003 on Mar 27, 2022.
 */
class RawWindow(rawFloaty: RawFloaty, context: Context) : FloatyWindow() {

    interface RawFloaty {
        fun inflateWindowView(service: Context?, parent: ViewGroup?): View
    }

    private val mInflateException = VolatileDispose<RuntimeException>()

    var contentView: View

    // @Reference to aiselp (https://github.com/aiselp) on Mar 27, 2023.
    //  ! https://github.com/kkevsekk1/AutoX/pull/529/commits/782f1c3c12dee64d9b1ad70aba462afaf60313a4#diff-b8e544bc658140f04a7fa7171cc938032f18c92495ca25bb08e2d2eb546b70f5
    init {
        val windowView = View.inflate(context, R.layout.raw_window, null) as ViewGroup
        contentView = rawFloaty.inflateWindowView(context, windowView)

        val mWindowLayoutParams = createWindowLayoutParams()
        super.setWindowView(windowView)
        super.setWindowManager(context.getSystemService(FloatyService.WINDOW_SERVICE) as WindowManager)
        super.setWindowLayoutParams(mWindowLayoutParams)
        super.setWindowBridge(super.onCreateWindowBridge(mWindowLayoutParams))
    }

    override fun onCreate(floatyService: FloatyService, windowManager: WindowManager) {
        try {
            super.onCreate(floatyService, windowManager)
        } catch (e: RuntimeException) {
            mInflateException.setAndNotify(e)
            return
        }
        mInflateException.setAndNotify(Exceptions.NO_EXCEPTION)
    }

    override fun onCreateView(floatyService: FloatyService): View = super.getWindowView()

    override fun onCreateWindowLayoutParams(): LayoutParams = super.getWindowLayoutParams()

    // fun waitForCreation(): RuntimeException = mInflateException.blockedGetOrThrow(ScriptInterruptedException::class.java)

    private fun createWindowLayoutParams() = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        WindowTypeCompat.getWindowType(),
        LayoutParams.FLAG_LAYOUT_NO_LIMITS or LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

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