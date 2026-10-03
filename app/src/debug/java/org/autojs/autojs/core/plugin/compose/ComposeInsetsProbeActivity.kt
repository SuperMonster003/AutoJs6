package org.autojs.autojs.core.plugin.compose

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import java.io.Closeable

/** Isolated APK fixture using the same content-inset owner as ScriptExecuteActivity. */
class ComposeInsetsProbeActivity : ComponentActivity(), ComposeInsetsHost {
    private lateinit var insets: ComposeActivityInsets
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        insets = ComposeActivityInsets(window, findViewById(android.R.id.content))
    }
    override fun acquireComposeInsets(): Closeable = insets.acquire()
}
