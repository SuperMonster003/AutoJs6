package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.autojs.autojs.util.IntentUtils.startSafely

/**
 * Created by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) on Feb 13, 2026.
 */
object PluginWakeManager {

    private const val ACTION_WAKE = "org.autojs.plugin.action.WAKE"
    private const val WAKE_ACTIVITY_META = "org.autojs.plugin.WAKE_ACTIVITY"

    fun tryAutoWakeIfNeeded(context: Context, packageName: String): Boolean {
        val intent = buildWakeIntent(context, packageName) ?: return false
        return intent.startSafely(context, true)
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
            val implicitIntent = Intent(ACTION_WAKE).setPackage(packageName)
            val info = pm.queryIntentActivities(implicitIntent, 0).firstOrNull()
            info?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        }
        return when {
            wakeComponent != null -> Intent().setComponent(wakeComponent)
            else -> Intent(ACTION_WAKE).setPackage(packageName).takeIf { it.resolveActivity(pm) != null }
        }
    }
}
