package org.autojs.autojs.ui.settings

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.network.GitHubTokenStore
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class GitHubTokenPreference : MaterialPreference {

    private var dialog: MaterialDialog? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        refreshSummary()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        refreshSummary()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        refreshSummary()
    }

    constructor(context: Context) : super(context) {
        refreshSummary()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        refreshSummary()
        super.onBindViewHolder(holder)
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        if (dialog?.isShowing == true) {
            return
        }

        val hasToken = GitHubTokenStore.hasToken(context)
        val builder = MaterialDialog.Builder(context)
            .title(R.string.text_github_token)
            .content(R.string.description_github_token_preference)
            .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            .input(context.getString(R.string.hint_github_token), "", false) { d, input ->
                GitHubTokenStore.setToken(context, input?.toString())
                refreshSummary()
                d.dismiss()
                ViewUtils.showToast(context, R.string.text_done)
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.dialog_button_save)
            .positiveColorRes(R.color.dialog_button_attraction)
            .widgetThemeColor()

        if (hasToken) {
            builder
                .neutralText(R.string.dialog_button_clear)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { d, _ ->
                    GitHubTokenStore.clearToken(context)
                    refreshSummary()
                    d.dismiss()
                    ViewUtils.showToast(context, R.string.text_done)
                }
        }

        dialog = builder.showAdaptive()
        super.onClick()
    }

    private fun refreshSummary() {
        val nextSummary = GitHubTokenStore.getTokenSuffix(context)
            ?.let { context.getString(R.string.summary_github_token_set, it) }
            ?: context.getString(R.string.summary_github_token_not_set)
        if (summary?.toString() != nextSummary) {
            summary = nextSummary
        }
    }
}
