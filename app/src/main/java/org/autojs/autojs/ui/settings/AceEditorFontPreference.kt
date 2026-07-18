package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontDialog
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreferences
import org.autojs.autojs.util.DialogUtils

class AceEditorFontPreference : MaterialPreference {

    private var fontPreviewHandler: ((String) -> Unit)? = null

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
        useCurrentFontSummaryProvider()
    }

    fun setAvailableForCurrentEngine(available: Boolean, unavailableSummary: CharSequence) {
        if (available) {
            useCurrentFontSummaryProvider()
        } else {
            summaryProvider = SummaryProvider<AceEditorFontPreference> {
                unavailableSummary
            }
        }
    }

    private fun useCurrentFontSummaryProvider() {
        summaryProvider = SummaryProvider<AceEditorFontPreference> {
            val manager = AceEditorFontManager.get(prefContext)
            manager.displayName(currentFontId(), prefContext)
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        DialogUtils.buildAndShowAdaptive {
            AceEditorFontDialog.build(
                context = prefContext,
                currentFontId = currentFontId(),
                onSelected = { fontId ->
                    AceEditorFontPreferences.setId(fontId)
                    notifyChanged()
                },
                onPreviewRequested = fontPreviewHandler,
            )
        }
    }

    fun setFontPreviewHandler(handler: ((String) -> Unit)?) {
        fontPreviewHandler = handler
    }

    private fun currentFontId(): String = AceEditorFontPreferences.getId()

}
