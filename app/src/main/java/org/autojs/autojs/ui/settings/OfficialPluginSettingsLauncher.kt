package org.autojs.autojs.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginSettingsActivity
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R

internal enum class PluginSettingsAvailability {
    AVAILABLE,
    INSTALL_REQUIRED,
    APPLICATION_ENABLE_REQUIRED,
    PLUGIN_ENABLE_REQUIRED,
    UPDATE_REQUIRED,
}

internal data class PluginSettingsFacts(
    val installed: Boolean,
    val applicationEnabled: Boolean,
    val pluginEnabled: Boolean,
    val official: Boolean,
    val settingsEntryAvailable: Boolean,
)

internal object PluginSettingsPolicy {

    fun availability(facts: PluginSettingsFacts): PluginSettingsAvailability = when {
        !facts.installed -> PluginSettingsAvailability.INSTALL_REQUIRED
        !facts.applicationEnabled -> PluginSettingsAvailability.APPLICATION_ENABLE_REQUIRED
        !facts.pluginEnabled -> PluginSettingsAvailability.PLUGIN_ENABLE_REQUIRED
        !facts.official || !facts.settingsEntryAvailable ->
            PluginSettingsAvailability.UPDATE_REQUIRED
        else -> PluginSettingsAvailability.AVAILABLE
    }
}

/**
 * One official plugin's parameterless settings contract (an exported activity behind the plugin
 * permission answering [action]) and the strings of the recovery guides shown when it cannot be
 * opened.
 */
internal data class OfficialPluginSettingsTarget(
    val packageName: String,
    val displayName: String,
    val action: String,
    val permission: String,
    @StringRes val title: Int,
    @StringRes val installRequired: Int,
    @StringRes val pluginEnableRequired: Int,
    @StringRes val applicationEnableRequired: Int,
    @StringRes val settingsUnavailable: Int,
)

/** Opens an official plugin's parameterless settings contract or an exact recovery guide. */
internal object OfficialPluginSettingsLauncher {

    fun launch(context: Context, target: OfficialPluginSettingsTarget) {
        val inspection = inspect(context, target)
        when (inspection.availability) {
            PluginSettingsAvailability.AVAILABLE -> {
                val component = inspection.component ?: return showUpdateGuide(context, target)
                val launched = Intent(target.action)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setComponent(component)
                    .startSafely(context, true)
                if (!launched) showUpdateGuide(context, target)
            }
            PluginSettingsAvailability.INSTALL_REQUIRED -> showInstallGuide(context, target)
            PluginSettingsAvailability.APPLICATION_ENABLE_REQUIRED ->
                showApplicationEnableGuide(context, target)
            PluginSettingsAvailability.PLUGIN_ENABLE_REQUIRED -> showPluginEnableGuide(context, target)
            PluginSettingsAvailability.UPDATE_REQUIRED -> showUpdateGuide(context, target)
        }
    }

    private fun inspect(context: Context, target: OfficialPluginSettingsTarget): Inspection {
        val packageManager = context.packageManager
        val applicationInfo = applicationInfo(packageManager, target.packageName)
            ?: return Inspection(PluginSettingsAvailability.INSTALL_REQUIRED)
        val applicationEnabled = isApplicationEnabled(packageManager, target.packageName, applicationInfo)
        val pluginEnabled = PluginEnableStore.isEnabled(context, target.packageName)
        val official = runCatching {
            PluginTrustManager.resolveTrustInfo(context, target.packageName).isOfficial
        }.getOrDefault(false)
        val component = settingsComponent(packageManager, target)
        val facts = PluginSettingsFacts(
            installed = true,
            applicationEnabled = applicationEnabled,
            pluginEnabled = pluginEnabled,
            official = official,
            settingsEntryAvailable = component != null,
        )
        return Inspection(PluginSettingsPolicy.availability(facts), component)
    }

    private fun applicationInfo(packageManager: PackageManager, packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrNull()

    private fun isApplicationEnabled(
        packageManager: PackageManager,
        packageName: String,
        applicationInfo: ApplicationInfo,
    ): Boolean = when (packageManager.getApplicationEnabledSetting(packageName)) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        -> false
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        else -> applicationInfo.enabled
    }

    private fun settingsComponent(packageManager: PackageManager, target: OfficialPluginSettingsTarget): ComponentName? {
        val intent = Intent(target.action)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(target.packageName)
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrDefault(emptyList())
        val entries = resolved.mapNotNull { resolveInfo ->
            resolveInfo.activityInfo?.takeIf { activity ->
                activity.packageName == target.packageName &&
                    activity.exported &&
                    activity.enabled &&
                    activity.permission == target.permission
            }?.let { activity -> ComponentName(activity.packageName, activity.name) }
        }.distinct()
        return entries.singleOrNull()
    }

    private fun showInstallGuide(context: Context, target: OfficialPluginSettingsTarget) {
        guide(context, target, target.installRequired, R.string.text_plugin_center) {
            openPluginCenter(context, target)
        }
    }

    private fun showPluginEnableGuide(context: Context, target: OfficialPluginSettingsTarget) {
        guide(context, target, target.pluginEnableRequired, R.string.text_enable_plugin) {
            PluginSettingsActivity.launch(
                context = context,
                packageName = target.packageName,
                title = target.displayName,
                source = PluginIndexSource.OFFICIAL,
            )
        }
    }

    private fun showApplicationEnableGuide(context: Context, target: OfficialPluginSettingsTarget) {
        guide(context, target, target.applicationEnableRequired, R.string.text_go_to_settings) {
            IntentUtils.launchAppDetailsSettings(context, target.packageName)
        }
    }

    private fun showUpdateGuide(context: Context, target: OfficialPluginSettingsTarget) {
        guide(context, target, target.settingsUnavailable, R.string.text_plugin_center) {
            openPluginCenter(context, target)
        }
    }

    private fun openPluginCenter(context: Context, target: OfficialPluginSettingsTarget) {
        PluginCenterActivity.launch(
            context,
            sourceScope = PluginIndexSource.OFFICIAL,
            initialQuery = target.displayName,
        )
    }

    private fun guide(
        context: Context,
        target: OfficialPluginSettingsTarget,
        @StringRes content: Int,
        @StringRes positive: Int,
        action: () -> Unit,
    ) {
        MaterialDialog.Builder(context)
            .title(target.title)
            .content(content)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(positive)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> action() }
            .show()
    }

    private data class Inspection(
        val availability: PluginSettingsAvailability,
        val component: ComponentName? = null,
    )
}
