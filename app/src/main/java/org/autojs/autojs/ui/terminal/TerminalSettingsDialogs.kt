package org.autojs.autojs.ui.terminal

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.terminal.TerminalPreferences
import org.autojs.autojs.ui.settings.TerminalNpmRegistryPreference
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R

object TerminalSettingsDialogs {
    fun showTextSize(context: Context, onChanged: () -> Unit = {}) {
        val sizes = intArrayOf(8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32)
        val selected = sizes.indexOfFirst { it >= TerminalPreferences.textSizeSp }.takeIf { it >= 0 } ?: sizes.lastIndex
        MaterialDialog.Builder(context)
            .title(R.string.text_text_size)
            .items(sizes.map { "$it sp" })
            .itemsCallbackSingleChoice(selected) { dialog, _, index, _ ->
                TerminalPreferences.textSizeSp = sizes[index]
                onChanged()
                dialog.dismiss()
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .widgetThemeColor()
            .show()
    }

    fun showNpmRegistry(context: Context) {
        val keys = context.resources.getStringArray(R.array.keys_terminal_npm_registry)
        val current = Pref.getStringOrNull(R.string.key_terminal_npm_registry) ?: context.getString(R.string.default_key_terminal_npm_registry)
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_npm_registry)
            .items(context.resources.getStringArray(R.array.values_terminal_npm_registry).toList())
            .itemsCallbackSingleChoice(keys.indexOf(current).coerceAtLeast(0)) { dialog, _, index, _ ->
                dialog.dismiss()
                if (keys[index] == context.getString(R.string.key_terminal_npm_registry_custom)) {
                    TerminalNpmRegistryPreference(context).apply {
                        key = context.getString(R.string.key_terminal_npm_registry)
                    }.promptCustomUrl()
                } else {
                    Pref.putString(R.string.key_terminal_npm_registry, keys[index])
                }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .widgetThemeColor()
            .show()
    }
}
