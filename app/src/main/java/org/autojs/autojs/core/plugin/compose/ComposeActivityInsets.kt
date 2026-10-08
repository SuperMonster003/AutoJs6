package org.autojs.autojs.core.plugin.compose

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.Closeable

/** Implemented by Activity mounts that can make the host content the single IME inset owner. */
interface ComposeInsetsHost {
    fun acquireComposeInsets(): Closeable
}

/** Main-thread leases preserve legacy script insets whenever no Compose view is attached. */
class ComposeActivityInsets(private val window: Window, private val content: View) {
    private var leases = 0
    private var savedSoftInputMode = 0
    private var lastInsets: WindowInsetsCompat? = null

    init {
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            lastInsets = insets
            apply(insets)
            // The host consumes both system bars and IME. Plugin Scaffold/fields must not add them again.
            WindowInsetsCompat.CONSUMED
        }
    }

    fun acquire(): Closeable {
        if (leases++ == 0) {
            savedSoftInputMode = window.attributes.softInputMode
            window.setSoftInputMode(
                savedSoftInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv() or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
            update()
        }
        var released = false
        return Closeable {
            if (!released) {
                released = true
                if (--leases == 0) {
                    window.setSoftInputMode(savedSoftInputMode)
                    update()
                }
            }
        }
    }

    private fun update() {
        lastInsets?.let(::apply)
        ViewCompat.requestApplyInsets(content)
    }

    private fun apply(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = if (leases > 0) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
        content.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime))
    }
}
