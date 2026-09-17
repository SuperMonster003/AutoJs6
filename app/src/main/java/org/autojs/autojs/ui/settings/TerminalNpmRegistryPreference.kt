package org.autojs.autojs.ui.settings

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.terminal.TerminalNodeEnvironment
import org.autojs.autojs.core.terminal.TerminalPreferences
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * npm registry used by new terminal sessions: npmjs.org (npm default), npmmirror.com, or a custom
 * `https://` URL entered in a follow-up dialog. The summary shows the effective URL.
 * zh-CN: 新终端会话使用的 npm registry: npmjs.org (npm 默认), npmmirror.com, 或在后续对话框中输入的自定义 `https://` URL. 摘要显示实际生效的 URL.
 */
class TerminalNpmRegistryPreference : MaterialListPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        useUrlSummary()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        useUrlSummary()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        useUrlSummary()
    }

    constructor(context: Context) : super(context) {
        useUrlSummary()
    }

    private fun useUrlSummary() {
        summaryProvider = SummaryProvider<TerminalNpmRegistryPreference> {
            TerminalPreferences.npmRegistry ?: TerminalNodeEnvironment.DEFAULT_REGISTRY
        }
    }

    override fun onItemSelectionRequested(dialog: MaterialDialog, itemKey: String): Boolean {
        if (itemKey != context.getString(R.string.key_terminal_npm_registry_custom)) {
            return super.onItemSelectionRequested(dialog, itemKey)
        }
        dialog.dismiss()
        promptCustomUrl()
        return false
    }

    fun promptCustomUrl() {
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_npm_registry_custom_url)
            .content(R.string.text_terminal_npm_registry_https_only)
            .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            .input(context.getString(R.string.hint_terminal_npm_registry_custom_url), TerminalPreferences.npmRegistryCustomUrl.orEmpty(), false) { d, input ->
                val sanitized = TerminalNodeEnvironment.sanitizeRegistry(input.toString())
                if (sanitized == null) {
                    ViewUtils.showToast(context, R.string.text_terminal_npm_registry_https_only)
                    return@input
                }
                TerminalPreferences.npmRegistryCustomUrl = sanitized
                Pref.putString(key, context.getString(R.string.key_terminal_npm_registry_custom))
                notifyChanged()
                d.dismiss()
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_save)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)
            .onNegative { d, _ -> d.dismiss() }
            .widgetThemeColor()
            .showAdaptive()
    }

}
