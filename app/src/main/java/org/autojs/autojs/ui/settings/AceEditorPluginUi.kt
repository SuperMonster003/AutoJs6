package org.autojs.autojs.ui.settings

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.editor.AceEditorPluginStateMonitor
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.plugin.editor.api.EditorPluginContract
import java.util.concurrent.atomic.AtomicLong

object AceEditorPluginUi {

    private val lastToastChangeId = AtomicLong(0L)

    fun showRequiredDialog(context: Context) {
        MaterialDialog.Builder(context)
            .title(R.string.text_ace_editor_plugin_required)
            .content(R.string.prompt_ace_editor_plugin_required)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_plugin_center)
            .positiveColorRes(R.color.dialog_button_attraction)
            .widgetThemeColor()
            .onPositive { _, _ ->
                PluginCenterActivity.launch(
                    context = context,
                    sourceScope = PluginIndexSource.OFFICIAL,
                    initialQuery = EditorPluginContract.DEFAULT_PACKAGE_NAME,
                )
            }
            .show()
    }

    fun showUnavailableToastOnce(
        context: Context,
        change: AceEditorPluginStateMonitor.Change,
    ) {
        if (change.current.available) return
        while (true) {
            val previousId = lastToastChangeId.get()
            if (change.id <= previousId) return
            if (lastToastChangeId.compareAndSet(previousId, change.id)) break
        }
        val messageRes = when {
            change.cause == AceEditorPluginStateMonitor.Cause.PACKAGE_REMOVED ->
                R.string.text_ace_editor_plugin_uninstalled_fallback_native
            change.current.status == AceEditorPluginStateMonitor.Status.DISABLED ->
                R.string.text_ace_editor_plugin_disabled_fallback_native
            else -> R.string.text_ace_editor_plugin_unavailable_fallback_native
        }
        ViewUtils.showToast(context, messageRes, true)
    }
}
