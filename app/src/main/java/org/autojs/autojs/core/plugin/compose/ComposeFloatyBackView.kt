package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import java.io.Closeable

/** Back dispatch belongs to this owned overlay, including when a Compose child has native focus. */
internal class ComposeFloatyBackView(context: Context, private val owners: ComposeFloatyOwners) : FrameLayout(context) {
    private var callbackObserver: Closeable? = null
    private var systemBack: Closeable? = null
    private var trackingBack = false

    init {
        isFocusableInTouchMode = true
        // This fallback target must not focus a background SearchBar or display an IME.
        descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        callbackObserver = owners.observeBackCallbacks(::refreshBackRegistration)
    }

    override fun onDetachedFromWindow() {
        callbackObserver?.close(); callbackObserver = null
        systemBack?.close(); systemBack = null
        trackingBack = false
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshBackRegistration()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) refreshBackRegistration()
    }

    private fun canDispatch() = isAttachedToWindow &&
        owners.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
        owners.onBackPressedDispatcher.hasEnabledCallbacks()

    private fun dispatchBack() { if (canDispatch()) owners.onBackPressedDispatcher.onBackPressed() }

    private fun refreshBackRegistration() {
        if (!canDispatch()) {
            systemBack?.close(); systemBack = null
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && systemBack == null) systemBack = Api33.register(this, ::dispatchBack)
        // Raw windows may have no focused child; adjustable windows may initially focus chrome.
        // Keep an existing descendant focus, and make only this overlay's Back path reachable.
        var focused: View? = rootView.findFocus()
        while (focused != null && focused !== this) focused = focused.parent as? View
        if (focused !== this) requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (super.dispatchKeyEvent(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && canDispatch()) {
            trackingBack = true
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && trackingBack) {
            trackingBack = false
            if (!event.isCanceled) dispatchBack()
            return true
        }
        return false
    }

    @RequiresApi(33)
    private object Api33 {
        fun register(view: View, invoke: () -> Unit): Closeable? {
            val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
            val callback = android.window.OnBackInvokedCallback { invoke() }
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            return Closeable { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    }
}
