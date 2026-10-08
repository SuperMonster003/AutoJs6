package org.autojs.autojs.core.floaty

import android.content.Context
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
class RawWindow @JvmOverloads constructor(
    private val context: Context,
    private val supplier: BaseResizableFloatyWindow.ViewSupplier,
    private val configureParameters: ((LayoutParams) -> Unit)? = null,
) : FloatyWindow() {

    private val mInflateException = VolatileDispose<RuntimeException>()
    internal var creationObserver: ((Throwable?) -> Unit)? = null
    internal var closeObserver: (() -> Unit)? = null
    private var managedClosed = false

    var contentView: View? = null

    override fun onCreate(floatyService: FloatyService, windowManager: WindowManager) {
        if (managedClosed) return
        try {
            super.onCreate(floatyService, windowManager)
        } catch (e: RuntimeException) {
            mInflateException.setAndNotify(e)
            creationObserver?.invoke(e)
            return
        } catch (e: LinkageError) {
            if (creationObserver == null) throw e
            mInflateException.setAndNotify(IllegalStateException("Floating window creation failed", e))
            creationObserver?.invoke(e)
            return
        }
        mInflateException.setAndNotify(Exceptions.NO_EXCEPTION)
        creationObserver?.invoke(null)
    }

    override fun close() {
        if (creationObserver != null || closeObserver != null) managedClosed = true
        val observer = closeObserver.also { closeObserver = null; creationObserver = null }
        try { super.close() } finally { observer?.invoke() }
    }

    override fun onCreateView(floatyService: FloatyService): View {
        val windowView = View.inflate(context, R.layout.raw_window, null) as ViewGroup
        contentView = supplier.inflate(context, windowView)
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
        ).apply { gravity = Gravity.TOP or Gravity.START; configureParameters?.invoke(this) }
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
