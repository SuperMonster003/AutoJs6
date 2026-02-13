package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.util.IntentUtils.startSafely

/**
 * Created by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) on Feb 13, 2026.
 */
object PluginWakeManager {

    private const val SP = "plugin_center_wake"
    private const val ACTION_OCR_WAKE = "org.autojs.plugin.PADDLE_OCR.WAKE"
    private const val WAKE_ACTIVITY_META = "org.autojs.plugin.WAKE_ACTIVITY"

    private val storeContext by lazy { GlobalAppContext.get() }

    fun tryAutoWakeIfNeeded(context: Context, packageName: String): Boolean {
        if (isAutoWakeAttempted(packageName)) return false
        val intent = buildWakeIntent(context, packageName) ?: return false
        val started = intent.startSafely(context, true)
        markAutoWakeAttempted(packageName)
        return started
    }

    fun buildWakeIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA) }.getOrNull()
        val metaWakeActivity = appInfo?.metaData?.getString(WAKE_ACTIVITY_META)
        val metaComponent = metaWakeActivity?.let { className ->
            val fullName = if (className.startsWith(".")) packageName + className else className
            ComponentName(packageName, fullName)
        }
        val wakeComponent = metaComponent ?: run {
            val implicitIntent = Intent(ACTION_OCR_WAKE).setPackage(packageName)
            val info = pm.queryIntentActivities(implicitIntent, 0).firstOrNull()
            info?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        }
        return when {
            wakeComponent != null -> Intent().setComponent(wakeComponent)
            else -> Intent(ACTION_OCR_WAKE).setPackage(packageName).takeIf { it.resolveActivity(pm) != null }
        }
    }

    private fun isAutoWakeAttempted(packageName: String): Boolean {
        val sp = storeContext.getSharedPreferences(SP, Context.MODE_PRIVATE)
        return sp.getBoolean(key(packageName), false)
    }

    private fun markAutoWakeAttempted(packageName: String) {
        val sp = storeContext.getSharedPreferences(SP, Context.MODE_PRIVATE)
        sp.edit { putBoolean(key(packageName), true) }
    }

    fun clearAutoWakeAttempt(packageName: String) {
        val sp = storeContext.getSharedPreferences(SP, Context.MODE_PRIVATE)
        sp.edit { remove(key(packageName)) }
    }

    private fun key(packageName: String) = "key_\$_auto_wake_attempted_\$_$packageName"
}
