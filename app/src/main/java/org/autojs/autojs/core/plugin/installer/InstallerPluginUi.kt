package org.autojs.autojs.core.plugin.installer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal enum class InstallerPluginAvailability {
    MISSING,
    DISABLED,
    UNAUTHORIZED,
    UNAVAILABLE,
}

internal object InstallerPluginAvailabilityPolicy {

    fun resolve(installed: Boolean, enabled: Boolean, authorized: Boolean): InstallerPluginAvailability = when {
        !installed -> InstallerPluginAvailability.MISSING
        !enabled -> InstallerPluginAvailability.DISABLED
        !authorized -> InstallerPluginAvailability.UNAUTHORIZED
        else -> InstallerPluginAvailability.UNAVAILABLE
    }
}

/** Foreground guide shown when a package needs the installer plugin and the plugin cannot be used. */
internal object InstallerPluginUi {

    private val dialogShowing = AtomicBoolean(false)

    fun showUnavailableDialog(context: Context, format: PackageInstallRouter.PackageFormat) {
        if (!dialogShowing.compareAndSet(false, true)) return
        val applicationInfo = findApplicationInfo(context)
        val displayName = applicationInfo
            ?.runCatching { loadLabel(context.packageManager).toString() }
            ?.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: ThreeSetupInstallerOfficialPlugin.DISPLAY_NAME
        val availability = InstallerPluginAvailabilityPolicy.resolve(
            installed = applicationInfo != null,
            enabled = applicationInfo?.enabled == true &&
                PluginEnableStore.isEnabled(context.applicationContext, ThreeSetupInstallerOfficialPlugin.PACKAGE_NAME, true),
            authorized = applicationInfo != null &&
                PluginTrustManager.isAuthorized(context.applicationContext, ThreeSetupInstallerOfficialPlugin.PACKAGE_NAME),
        )
        val reason = when (availability) {
            InstallerPluginAvailability.MISSING -> context.getString(
                R.string.error_installer_plugin_required_for_package_format,
                format.extension.uppercase(Locale.ROOT),
                displayName,
            )
            InstallerPluginAvailability.DISABLED -> context.getString(R.string.error_plugin_is_not_enabled_in_plugin_center, displayName)
            InstallerPluginAvailability.UNAUTHORIZED -> context.getString(R.string.error_plugin_is_not_authorized_in_plugin_center, displayName)
            InstallerPluginAvailability.UNAVAILABLE -> context.getString(R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch)
        }
        try {
            MaterialDialog.Builder(context)
                .title(R.string.text_install)
                .content(reason)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = context,
                        sourceScope = PluginIndexSource.OFFICIAL,
                        initialQuery = ThreeSetupInstallerOfficialPlugin.PACKAGE_NAME,
                    )
                }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }

    private fun findApplicationInfo(context: Context): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                ThreeSetupInstallerOfficialPlugin.PACKAGE_NAME,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(ThreeSetupInstallerOfficialPlugin.PACKAGE_NAME, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrNull()
}
