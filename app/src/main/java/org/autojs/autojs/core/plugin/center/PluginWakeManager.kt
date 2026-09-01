package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
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
        val implicitIntent = Intent(ACTION_WAKE)
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val implicitComponent = queryWakeActivities(pm, implicitIntent)
            .firstOrNull { it.isWakeActivityEnabled() }
            ?.let { ComponentName(it.packageName, it.name) }
        val wakeComponent = implicitComponent ?: resolveMetadataWakeComponent(pm, packageName)
        return wakeComponent?.let { component ->
            Intent(ACTION_WAKE)
                .setComponent(component)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
    }

    private fun resolveMetadataWakeComponent(pm: PackageManager, packageName: String): ComponentName? {
        val packageInfo = runCatching {
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_META_DATA or
                    PackageManager.MATCH_DISABLED_COMPONENTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        }.getOrNull() ?: return null
        val configuredName = packageInfo.applicationInfo?.metaData?.getString(WAKE_ACTIVITY_META) ?: return null
        val declaredNames = packageInfo.activities
            .orEmpty()
            .filter { it.isWakeActivityEnabled() }
            .map { it.name }
        val className = selectWakeActivityClassName(packageName, configuredName, declaredNames) ?: return null
        return ComponentName(packageName, className)
    }

    private fun queryWakeActivities(pm: PackageManager, intent: Intent): List<ActivityInfo> {
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
        return result.mapNotNull { it.activityInfo }
    }

    private fun ActivityInfo.isWakeActivityEnabled(): Boolean = enabled && applicationInfo.enabled

    internal fun selectWakeActivityClassName(
        packageName: String,
        configuredName: String,
        declaredNames: List<String>,
    ): String? {
        val configured = configuredName.trim().takeIf(String::isNotEmpty) ?: return null
        val conventionalName = when {
            configured.startsWith('.') -> packageName + configured
            '.' !in configured -> "$packageName.$configured"
            else -> configured
        }
        return declaredNames.firstOrNull { it == conventionalName }
            ?: when {
                configured.startsWith('.') -> declaredNames.singleOrNull { it.endsWith(configured) }
                '.' !in configured -> declaredNames.singleOrNull { it.substringAfterLast('.') == configured }
                else -> null
            }
    }
}
