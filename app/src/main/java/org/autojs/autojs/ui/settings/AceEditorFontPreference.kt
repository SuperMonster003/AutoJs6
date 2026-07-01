package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFont
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontDialog
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreferences
import org.autojs.autojs.util.DialogUtils

class AceEditorFontPreference : MaterialPreference {

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
        summaryProvider = SummaryProvider<AceEditorFontPreference> {
            currentFont().displayName(prefContext)
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        DialogUtils.buildAndShowAdaptive {
            AceEditorFontDialog.build(prefContext, currentFont()) { font ->
                AceEditorFontPreferences.set(font)
                notifyChanged()
            }
        }
    }

    private fun currentFont(): AceEditorFont = AceEditorFontPreferences.get()

}
