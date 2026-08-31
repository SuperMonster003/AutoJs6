package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences.LanguageSupport
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences.LocalCompletionSupport
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences.SemanticSupport
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences.SyntaxSupport
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R

class AceLanguageSupportMatrixPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        summaryProvider = SummaryProvider<AceLanguageSupportMatrixPreference> {
            prefContext.getString(
                R.string.summary_ace_language_support_matrix,
                AceEditorLspPreferences.LANGUAGE_SUPPORT_MATRIX.size,
            )
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) return
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_ace_language_support_matrix)
                .content(R.string.description_ace_language_support_matrix_preference)
                .items(AceEditorLspPreferences.LANGUAGE_SUPPORT_MATRIX.map(::matrixRow))
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .widgetThemeColor()
                .build()
        }
    }

    private fun matrixRow(support: LanguageSupport): String {
        return prefContext.getString(
            R.string.format_ace_language_support_matrix_row,
            LANGUAGE_NAMES.getValue(support.id),
            supportText(support.syntax),
            booleanSupportText(support.keywordCompletion),
            booleanSupportText(support.snippets),
            supportText(support.localCompletion),
            supportText(support.semantic),
        )
    }

    private fun booleanSupportText(supported: Boolean): String = prefContext.getString(
        if (supported) R.string.text_ace_support_supported else R.string.text_ace_support_none,
    )

    private fun supportText(support: SyntaxSupport): String = prefContext.getString(
        when (support) {
            SyntaxSupport.FULL -> R.string.text_ace_support_full
            SyntaxSupport.PARTIAL -> R.string.text_ace_support_partial
        },
    )

    private fun supportText(support: LocalCompletionSupport): String = prefContext.getString(
        when (support) {
            LocalCompletionSupport.SUPPORTED -> R.string.text_ace_support_supported
            LocalCompletionSupport.P2_PLUS -> R.string.text_ace_support_p2_plus
            LocalCompletionSupport.NONE -> R.string.text_ace_support_none
        },
    )

    private fun supportText(support: SemanticSupport): String = prefContext.getString(
        when (support) {
            SemanticSupport.FULL -> R.string.text_ace_support_full
            SemanticSupport.SYNTAX_DIAGNOSTICS_ONLY -> R.string.text_ace_support_syntax_diagnostics_only
            SemanticSupport.SINGLE_FILE_DIAGNOSTICS -> R.string.text_ace_support_single_file_diagnostics
            SemanticSupport.NONE -> R.string.text_ace_support_none
        },
    )

    private companion object {
        val LANGUAGE_NAMES = mapOf(
            "javascript" to "JavaScript",
            "jsx" to "JSX",
            "typescript" to "TypeScript",
            "tsx" to "TSX",
            "json" to "JSON",
            "python" to "Python",
            "lua" to "Lua",
            "java" to "Java",
            "kotlin" to "Kotlin",
        )
    }
}
