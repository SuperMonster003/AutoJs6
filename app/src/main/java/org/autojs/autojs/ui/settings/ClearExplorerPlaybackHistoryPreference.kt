package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.explorer.session.ExplorerPlaybackHistoryStore
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class ClearExplorerPlaybackHistoryPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(
                context.getString(
                    R.string.text_confirm_to_clear_the_history_of,
                    context.getString(R.string.text_clear_media_playback_history),
                ),
            )
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                val clearedCount = ExplorerPlaybackHistoryStore.clearAll(context)
                ViewUtils.showToast(
                    context,
                    if (clearedCount > 0) R.string.text_history_has_been_cleared else R.string.text_no_history,
                )
            }
            .widgetThemeColor()
            .showAdaptive()
    }
}
