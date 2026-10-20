package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.TextSizeSettingDialogBuilder
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs6.R

class EditorTextSizePreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        summaryProvider = SummaryProvider<EditorTextSizePreference> {
            prefContext.getString(R.string.text_text_size_current_value, currentValue())
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        DialogUtils.buildAndShowAdaptive {
            TextSizeSettingDialogBuilder(prefContext)
                .initialValue(currentValue())
                .callback { value ->
                    EditorKeys.EDITOR_TEXT_SIZE.set(value)
                    notifyChanged()
                }
                .build()
        }
    }

    private fun currentValue(): Int {
        return EditorKeys.EDITOR_TEXT_SIZE.get()
    }
}
