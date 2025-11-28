package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.pm.ShortcutManagerCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.doc.DocumentationActivity
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.util.ShortcutUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.SelectLauncherShortcutBinding

/**
 * Created by SuperMonster003 on Sep 25, 2022.
 */
class LauncherShortcutsPreference : MaterialPreference {

    private val mDialog by lazy {

        val binding = SelectLauncherShortcutBinding.inflate(LayoutInflater.from(prefContext))

        val dialog = MaterialDialog.Builder(prefContext)
            .customView(binding.root, false)
            .build()

        dialog.window?.apply {
            setBackgroundDrawable(null)
            setDimAmount(0.64f)
        }

        binding.launcherShortcutSettings.setOnClickListener {
            ShortcutUtils.requestPinShortcut(
                prefContext,
                R.string.id_launcher_shortcut_settings,
                PreferencesActivity::class.java.name,
                R.string.text_app_shortcut_settings_long_label,
                R.string.text_app_shortcut_settings_short_label,
                R.mipmap.ic_app_shortcut_settings_adaptive,
            ).also { dialog.dismiss() }
        }

        binding.launcherShortcutDocs.setOnClickListener {
            ShortcutUtils.requestPinShortcut(
                prefContext,
                R.string.id_launcher_shortcut_docs,
                DocumentationActivity::class.java.name,
                R.string.text_app_shortcut_docs_long_label,
                R.string.text_app_shortcut_docs_short_label,
                R.mipmap.ic_app_shortcut_docs_adaptive,
            ).also { dialog.dismiss() }
        }

        binding.launcherShortcutLog.setOnClickListener {
            ShortcutUtils.requestPinShortcut(
                prefContext,
                R.string.id_launcher_shortcut_log,
                LogActivity::class.java.name,
                R.string.text_app_shortcut_log_long_label,
                R.string.text_app_shortcut_log_short_label,
                R.mipmap.ic_app_shortcut_log_adaptive,
            ).also { dialog.dismiss() }
        }

        binding.launcherShortcutPluginCenter.setOnClickListener {
            ShortcutUtils.requestPinShortcut(
                prefContext,
                R.string.id_launcher_shortcut_plugin_center,
                PluginCenterActivity::class.java.name,
                R.string.text_app_shortcut_plugin_center_long_label,
                R.string.text_app_shortcut_plugin_center_short_label,
                R.mipmap.ic_app_shortcut_plugin_center_adaptive,
            ).also { dialog.dismiss() }
        }

        return@lazy dialog
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(prefContext)) {
            showShortcutsSelectionDialog()
        } else {
            ShortcutUtils.showPinShortcutNotSupportedDialog(prefContext)
        }
        super.onClick()
    }

    private fun showShortcutsSelectionDialog() {
        if (!mDialog.isShowing) mDialog.show()
    }

}
