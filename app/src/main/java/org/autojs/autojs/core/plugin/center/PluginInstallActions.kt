package org.autojs.autojs.core.plugin.center

import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LifecycleCoroutineScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R

object PluginInstallActions {

    private val apkMimeTypes = arrayOf("application/vnd.android.package-archive")

    fun installFromLocalFile(pickApkLauncher: ActivityResultLauncher<Array<String>>) {
        pickApkLauncher.launch(apkMimeTypes)
    }

    fun showInstallFromUrlDialog(context: android.content.Context, scope: LifecycleCoroutineScope) {
        MaterialDialog.Builder(context)
            .title(R.string.text_install_plugin_from_url)
            .content(R.string.instruction_install_plugin_from_url)
            .input(null, null) { d, input ->
                val positiveButton = d.getActionButton(DialogAction.POSITIVE)
                when {
                    input.isNullOrBlank() -> {
                        positiveButton.setOnClickListener(null)
                        positiveButton.setTextColor(d.context.getColor(R.color.dialog_button_unavailable))
                    }
                    else -> {
                        positiveButton.setOnClickListener {
                            d.dismiss()
                            val url = input.trim().toString()
                            scope.launch {
                                runCatching {
                                    PluginInstaller.installFromUrlWithPrompt(context, url)
                                }.onFailure { e ->
                                    MaterialDialog.Builder(context)
                                        .title(R.string.text_failed_to_retrieve)
                                        .content(e.message ?: e.toString())
                                        .positiveText(R.string.dialog_button_dismiss)
                                        .show()
                                }
                            }
                        }
                        positiveButton.setTextColor(d.context.getColor(R.color.dialog_button_attraction))
                    }
                }
            }
            .alwaysCallInputCallback()
            .widgetThemeColor()
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.dialog_button_retrieve)
            .positiveColorRes(R.color.dialog_button_unavailable)
            .autoDismiss(false)
            .cancelable(false)
            .show()
    }
}
