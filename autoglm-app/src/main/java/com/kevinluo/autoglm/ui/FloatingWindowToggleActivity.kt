package com.kevinluo.autoglm.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.kevinluo.autoglm.util.Logger

/**
 * Transparent activity to toggle floating window and collapse notification panel.
 * This activity finishes immediately after toggling the floating window.
 *
 * Uses Activity instead of AppCompatActivity to avoid bringing the app task to foreground.
 */
class FloatingWindowToggleActivity : Activity() {
    /**
     * Called when the activity is created.
     * Handles the toggle action and finishes immediately.
     *
     * @param savedInstanceState The saved instance state bundle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.d(TAG, "Toggle activity started, action: ${intent.action}")

        // Check overlay permission
        if (!FloatingWindowService.canDrawOverlays(this)) {
            Logger.w(TAG, "No overlay permission")
            val mainIntent =
                Intent(this, com.kevinluo.autoglm.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            startActivity(mainIntent)
            finish()
            return
        }

        when (intent.action) {
            Companion.ACTION_SHOW -> FloatingWindowStateManager.enableByUser(this)
            Companion.ACTION_HIDE -> FloatingWindowStateManager.disableByUser()
            Companion.ACTION_TOGGLE -> FloatingWindowStateManager.toggleByUser(this)
            else -> FloatingWindowStateManager.toggleByUser(this)
        }

        // Finish immediately
        finish()
    }

    companion object {
        private const val TAG = "FloatingWindowToggle"
        const val ACTION_TOGGLE = "com.kevinluo.autoglm.ACTION_TOGGLE_FLOATING"
        const val ACTION_SHOW = "com.kevinluo.autoglm.ACTION_SHOW_FLOATING"
        const val ACTION_HIDE = "com.kevinluo.autoglm.ACTION_HIDE_FLOATING"
    }
}
