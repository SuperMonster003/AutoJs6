package org.autojs.autojs.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginSettingsActivity
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.plugin.ai.provider.api.AiProviderSettingsContract

internal enum class AiPluginSettingsAvailability {
    AVAILABLE,
    INSTALL_REQUIRED,
    APPLICATION_ENABLE_REQUIRED,
    PLUGIN_ENABLE_REQUIRED,
    UPDATE_REQUIRED,
}

internal data class AiPluginSettingsFacts(
    val installed: Boolean,
    val applicationEnabled: Boolean,
    val pluginEnabled: Boolean,
    val official: Boolean,
    val settingsEntryAvailable: Boolean,
)

internal object AiPluginSettingsPolicy {

    fun availability(facts: AiPluginSettingsFacts): AiPluginSettingsAvailability = when {
        !facts.installed -> AiPluginSettingsAvailability.INSTALL_REQUIRED
        !facts.applicationEnabled -> AiPluginSettingsAvailability.APPLICATION_ENABLE_REQUIRED
        !facts.pluginEnabled -> AiPluginSettingsAvailability.PLUGIN_ENABLE_REQUIRED
        !facts.official || !facts.settingsEntryAvailable ->
            AiPluginSettingsAvailability.UPDATE_REQUIRED
        else -> AiPluginSettingsAvailability.AVAILABLE
    }
}

/** Opens the official plugin's parameterless settings contract or an exact recovery guide. */
internal object AiPluginSettingsLauncher {

    fun launch(context: Context) {
        val inspection = inspect(context)
        when (inspection.availability) {
            AiPluginSettingsAvailability.AVAILABLE -> {
                val component = inspection.component ?: return showUpdateGuide(context)
                val launched = Intent(AiProviderSettingsContract.ACTION_OPEN_SETTINGS)
                    .addCategory(AiProviderSettingsContract.CATEGORY_DEFAULT)
                    .setComponent(component)
                    .startSafely(context, true)
                if (!launched) showUpdateGuide(context)
            }
            AiPluginSettingsAvailability.INSTALL_REQUIRED -> showInstallGuide(context)
            AiPluginSettingsAvailability.APPLICATION_ENABLE_REQUIRED ->
                showApplicationEnableGuide(context)
            AiPluginSettingsAvailability.PLUGIN_ENABLE_REQUIRED -> showPluginEnableGuide(context)
            AiPluginSettingsAvailability.UPDATE_REQUIRED -> showUpdateGuide(context)
        }
    }

    private fun inspect(context: Context): Inspection {
        val packageManager = context.packageManager
        val applicationInfo = applicationInfo(packageManager)
            ?: return Inspection(AiPluginSettingsAvailability.INSTALL_REQUIRED)
        val applicationEnabled = isApplicationEnabled(packageManager, applicationInfo)
        val pluginEnabled = PluginEnableStore.isEnabled(
            context,
            ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
        )
        val official = runCatching {
            PluginTrustManager.resolveTrustInfo(
                context,
                ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
            ).isOfficial
        }.getOrDefault(false)
        val component = settingsComponent(packageManager)
        val facts = AiPluginSettingsFacts(
            installed = true,
            applicationEnabled = applicationEnabled,
            pluginEnabled = pluginEnabled,
            official = official,
            settingsEntryAvailable = component != null,
        )
        return Inspection(AiPluginSettingsPolicy.availability(facts), component)
    }

    private fun applicationInfo(packageManager: PackageManager): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
    }.getOrNull()

    private fun isApplicationEnabled(
        packageManager: PackageManager,
        applicationInfo: ApplicationInfo,
    ): Boolean = when (
        packageManager.getApplicationEnabledSetting(ThreeStoneAiOfficialPlugin.PACKAGE_NAME)
    ) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        -> false
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        else -> applicationInfo.enabled
    }

    private fun settingsComponent(packageManager: PackageManager): ComponentName? {
        val intent = Intent(AiProviderSettingsContract.ACTION_OPEN_SETTINGS)
            .addCategory(AiProviderSettingsContract.CATEGORY_DEFAULT)
            .setPackage(ThreeStoneAiOfficialPlugin.PACKAGE_NAME)
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
                activity.packageName == ThreeStoneAiOfficialPlugin.PACKAGE_NAME &&
                    activity.exported &&
                    activity.enabled &&
                    activity.permission == AiProviderSettingsContract.PLUGIN_PERMISSION
            }?.let { activity -> ComponentName(activity.packageName, activity.name) }
        }.distinct()
        return entries.singleOrNull()
    }

    private fun showInstallGuide(context: Context) {
        guide(context, R.string.prompt_ai_plugin_install_required, R.string.text_plugin_center) {
            openPluginCenter(context)
        }
    }

    private fun showPluginEnableGuide(context: Context) {
        guide(context, R.string.prompt_ai_plugin_enable_required, R.string.text_enable_plugin) {
            PluginSettingsActivity.launch(
                context = context,
                packageName = ThreeStoneAiOfficialPlugin.PACKAGE_NAME,
                title = ThreeStoneAiOfficialPlugin.DISPLAY_NAME,
                source = PluginIndexSource.OFFICIAL,
            )
        }
    }

    private fun showApplicationEnableGuide(context: Context) {
        guide(
            context,
            R.string.prompt_ai_plugin_application_enable_required,
            R.string.text_go_to_settings,
        ) {
            IntentUtils.launchAppDetailsSettings(context, ThreeStoneAiOfficialPlugin.PACKAGE_NAME)
        }
    }

    private fun showUpdateGuide(context: Context) {
        guide(context, R.string.prompt_ai_plugin_settings_unavailable, R.string.text_plugin_center) {
            openPluginCenter(context)
        }
    }

    private fun openPluginCenter(context: Context) {
        PluginCenterActivity.launch(
            context,
            sourceScope = PluginIndexSource.OFFICIAL,
            initialQuery = ThreeStoneAiOfficialPlugin.DISPLAY_NAME,
        )
    }

    private fun guide(
        context: Context,
        content: Int,
        positive: Int,
        action: () -> Unit,
    ) {
        MaterialDialog.Builder(context)
            .title(R.string.text_ai_settings)
            .content(content)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(positive)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> action() }
            .show()
    }

    private data class Inspection(
        val availability: AiPluginSettingsAvailability,
        val component: ComponentName? = null,
    )
}
