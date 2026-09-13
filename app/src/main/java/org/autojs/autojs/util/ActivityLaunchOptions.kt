package org.autojs.autojs.util

import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat

/** Options for the app that sends an Activity PendingIntent in response to a visible UI action. */
internal object ActivityLaunchOptions {

    fun forVisibleSender(): ActivityOptionsCompat? {
        if (Build.VERSION.SDK_INT < 34) return null
        val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
            if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            } else {
                // Android 14 / 15 require an opt-in but do not support the visible-only mode.
                @Suppress("DEPRECATION")
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            },
        )
        // ActivityResultLauncher forwards toBundle() to startIntentSenderForResult().
        // Modifying a Bundle returned by ActivityOptionsCompat.makeBasic() is not supported.
        return object : ActivityOptionsCompat() {
            override fun toBundle(): Bundle = options.toBundle()
        }
    }
}
