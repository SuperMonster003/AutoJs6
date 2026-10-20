package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.theme.preference.MaterialPreference

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 20, 2026.
 */
class EditorLargeFileModeThresholdPreference : MaterialPreference {

    private var dialog: MaterialDialog? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        useThresholdSummaryProvider()
    }

    fun setAvailableForCurrentEngine(
        available: Boolean,
        unavailableSummary: CharSequence,
        onUnavailableClick: () -> Unit,
    ) {
        setAvailableForCurrentContext(available, onUnavailableClick)
        if (available) {
            useThresholdSummaryProvider()
        } else {
            summaryProvider = SummaryProvider<EditorLargeFileModeThresholdPreference> { unavailableSummary }
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        if (dialog?.isShowing == true) {
            return
        }
        dialog = EditorThresholdDialogBuilder(
            context = prefContext,
            mode = EditorThresholdDialogBuilder.Mode.LARGE_FILE_MODE,
        ) {
            notifyChanged()
        }.show()
        super.onClick()
    }

    override fun onDetached() {
        dialog?.dismiss()
        dialog = null
        super.onDetached()
    }

    private fun useThresholdSummaryProvider() {
        summaryProvider = SummaryProvider<EditorLargeFileModeThresholdPreference> {
            EditorThresholdSettings.formatLargeFileModeSummary(prefContext, EditorKeys.largeFileModeThresholdBytes)
        }
    }
}
