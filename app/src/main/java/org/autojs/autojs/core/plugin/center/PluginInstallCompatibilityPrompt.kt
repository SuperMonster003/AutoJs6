package org.autojs.autojs.core.plugin.center

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R

/**
 * Applies all pre-download compatibility checks in a safe order.
 *
 * A matrix-selected downgrade must never reach Android's package installer as
 * an ordinary update. The user first confirms the destructive uninstall, then
 * returns to Plugin Center after system uninstallation to install the matched
 * build. Normal installs and upgrades continue through the existing ABI gate.
 */
object PluginInstallCompatibilityPrompt {

    fun runBeforeDownload(
        context: Context,
        item: PluginCenterItem,
        onContinue: () -> Unit,
    ) {
        if (!item.requiresDowngradeReinstall) {
            PluginNativeAlignmentPrompt.runBeforeAction(context, item, download = true) {
                PluginAbiCompatibilityPrompt.runBeforeDownload(context, item, onContinue)
            }
            return
        }

        val installedVersion = item.versionSummary
        val matchedVersion = item.updatableVersionSummary ?: return
        MaterialDialog.Builder(context)
            .title(R.string.text_reinstall_required)
            .content(
                context.getString(
                    R.string.prompt_plugin_matched_version_requires_reinstall,
                    installedVersion,
                    matchedVersion,
                ),
            )
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_uninstall)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { dialog, _ ->
                dialog.dismiss()
                item.uninstall(context)
            }
            .showAdaptive()
    }
}
