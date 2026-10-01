package org.autojs.autojs.core.plugin.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.terminal.api.TerminalActions
import org.autojs.plugin.terminal.api.TerminalIds

/**
 * Reads the package facts of the official terminal plugin without binding it (plugin roadmap
 * D25): installation, Android and Plugin Center enablement, authorization, the exported service
 * and entry Activity behind the plugin permission, and the `requiresHostVersion` meta-data. The
 * policy turns them into the state and route every host entry follows.
 */
object TerminalPluginInspector {

    private const val META_REQUIRES_HOST_VERSION = "requiresHostVersion"

    fun inspect(context: Context, packageName: String = ThreeShellTerminalOfficialPlugin.PACKAGE_NAME): TerminalInspection {
        val pm = context.packageManager
        val applicationInfo = applicationInfo(pm, packageName)
            ?: return TerminalInspection(TerminalPluginFacts(installed = false, hostVersion = hostVersion()))
        val services = contractServices(context, packageName)
        val required = services.mapNotNull { service ->
            service.metaData?.get(META_REQUIRES_HOST_VERSION)?.let(PluginCapabilityResolver::coerceLong)
        }.maxOrNull() ?: TerminalIds.REQUIRED_HOST_VERSION_CODE
        val facts = TerminalPluginFacts(
            installed = true,
            applicationEnabled = isApplicationEnabled(pm, packageName, applicationInfo),
            serviceDeclared = services.isNotEmpty(),
            entryDeclared = resolveEntry(context, packageName) != null,
            requiredHostVersion = required,
            hostVersion = hostVersion(),
            authorized = runCatching { PluginTrustManager.isAuthorized(context, packageName) }.getOrDefault(false),
            pluginEnabled = PluginEnableStore.isEnabled(context, packageName, PluginDefaultEnabledPolicy.isEnabledByDefault(TerminalIds.ENGINE)),
        )
        return TerminalInspection(facts)
    }

    /** The single exported `TERMINAL_OPEN` Activity of [packageName] behind the plugin permission, or null. */
    fun resolveEntry(context: Context, packageName: String = ThreeShellTerminalOfficialPlugin.PACKAGE_NAME): ComponentName? =
        resolveActivity(context, packageName, TerminalActions.OPEN_TERMINAL)

    /** The single exported `TERMINAL_SETTINGS` Activity of [packageName] behind the plugin permission, or null. */
    fun resolveSettings(context: Context, packageName: String = ThreeShellTerminalOfficialPlugin.PACKAGE_NAME): ComponentName? =
        resolveActivity(context, packageName, TerminalActions.OPEN_SETTINGS)

    fun hostVersion(): Long = BuildConfig.VERSION_CODE.toLong()

    private fun resolveActivity(context: Context, packageName: String, action: String): ComponentName? {
        val intent = Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setPackage(packageName)
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrDefault(emptyList())
        return resolved.mapNotNull { info ->
            info.activityInfo?.takeIf { activity ->
                activity.packageName == packageName && activity.exported && activity.enabled && activity.permission == TerminalActions.PLUGIN_PERMISSION
            }?.let { ComponentName(it.packageName, it.name) }
        }.distinct().singleOrNull()
    }

    private fun contractServices(context: Context, packageName: String) = run {
        val intent = Intent(TerminalActions.SERVICE_ACTION).addCategory(TerminalActions.SERVICE_CATEGORY).setPackage(packageName)
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentServices(intent, flags)
            }
        }.getOrDefault(emptyList())
        resolved.mapNotNull { info ->
            info.serviceInfo?.takeIf { it.exported && it.enabled && it.permission == TerminalActions.PLUGIN_PERMISSION }
        }
    }

    private fun applicationInfo(pm: PackageManager, packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrNull()

    private fun isApplicationEnabled(pm: PackageManager, packageName: String, applicationInfo: ApplicationInfo): Boolean =
        when (runCatching { pm.getApplicationEnabledSetting(packageName) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> applicationInfo.enabled
        }
}
