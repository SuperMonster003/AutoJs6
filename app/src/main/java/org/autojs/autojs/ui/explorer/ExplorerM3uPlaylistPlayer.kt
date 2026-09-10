package org.autojs.autojs.ui.explorer

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.explorer.ExplorerM3uPlaylistPolicy
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicBoolean

internal object ExplorerM3uPlaylistPlayerUi {

    private val dialogShowing = AtomicBoolean(false)

    fun showUnavailableDialog(context: Context) {
        if (!dialogShowing.compareAndSet(false, true)) return
        try {
            MaterialDialog.Builder(context)
                .title(R.string.text_play)
                .content(R.string.hint_m3u_player_plugin_unavailable)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .neutralText(R.string.plugin_three_terra_player_name)
                .neutralColorRes(R.color.dialog_button_hint)
                .positiveText(R.string.plugin_three_ember_player_name)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onNeutral { _, _ -> openPluginCenter(context, ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE) }
                .onPositive { _, _ -> openPluginCenter(context, ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE) }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }

    private fun openPluginCenter(context: Context, packageName: String) {
        PluginCenterActivity.launch(
            context = context,
            sourceScope = PluginIndexSource.OFFICIAL,
            initialQuery = packageName,
        )
    }
}
