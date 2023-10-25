package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.content.pm.ShortcutManagerCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.ShortcutUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Sep 25, 2022.
 */
class LauncherShortcutsPreference : MaterialPreference {

    private val mDialog = MaterialDialog.Builder(prefContext)
        .customView(R.layout.select_launcher_shortcut, true)
        .build()
        .also { dialog ->
            val view = dialog.customView as LinearLayout

            view.findViewById<LinearLayout?>(R.id.launcher_shortcut_settings).setOnClickListener {
                dialog.dismiss()
                ShortcutUtils.requestPinShortcut(
                    prefContext,
                    R.string.id_launcher_shortcut_settings,
                    "org.autojs.autojs.ui.settings.PreferencesActivity",
                    R.string.text_app_shortcut_settings_long_label,
                    R.string.text_app_shortcut_settings_short_label,
                    R.mipmap.ic_app_shortcut_settings_adaptive,
                )
            }

            view.findViewById<LinearLayout?>(R.id.launcher_shortcut_docs).setOnClickListener {
                dialog.dismiss()
                ShortcutUtils.requestPinShortcut(
                    prefContext,
                    R.string.id_launcher_shortcut_docs,
                    "org.autojs.autojs.ui.doc.DocumentationActivity",
                    R.string.text_app_shortcut_docs_long_label,
                    R.string.text_app_shortcut_docs_short_label,
                    R.mipmap.ic_app_shortcut_docs_adaptive,
                )
            }

            view.findViewById<LinearLayout?>(R.id.launcher_shortcut_log).setOnClickListener {
                dialog.dismiss()
                ShortcutUtils.requestPinShortcut(
                    prefContext,
                    R.string.id_launcher_shortcut_log,
                    "org.autojs.autojs.ui.log.LogActivity",
                    R.string.text_app_shortcut_log_long_label,
                    R.string.text_app_shortcut_log_short_label,
                    R.mipmap.ic_app_shortcut_log_adaptive,
                )
            }
        }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(prefContext)) {
            showShortcutsSelectionDialog()
        } else {
            showPinShortcutNotSupportedDialog()
        }
        super.onClick()
    }

    private fun showShortcutsSelectionDialog() {
        if (!mDialog.isShowing) mDialog.show()
    }

    private fun showPinShortcutNotSupportedDialog() {
        MaterialDialog.Builder(prefContext)
            .title(R.string.text_prompt)
            .content(R.string.text_pin_shortcut_not_unsupported)
            .positiveText(R.string.dialog_button_dismiss)
            .build()
            .show()
    }

}
