package org.autojs.autojs.core.accessibility

import android.content.Context
import android.os.Build
import android.security.advancedprotection.AdvancedProtectionManager
import org.autojs.autojs6.R

/** Reports the global mode, never a claim that a particular service is restricted. */
object AdvancedProtectionNotice {
    fun summary(context: Context): String {
        val enabled = if (Build.VERSION.SDK_INT >= 36) runCatching {
            context.getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled
        }.getOrNull() else null
        val status = when (enabled) {
            true -> R.string.a11y_advanced_protection_on
            false -> R.string.a11y_advanced_protection_off
            null -> R.string.a11y_advanced_protection_unknown
        }
        return context.getString(status) + "\n\n" + context.getString(R.string.a11y_advanced_protection_explanation)
    }
}
