package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

class AceSemanticLanguagesPreference : MaterialPreference {

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
        summaryProvider = SummaryProvider<AceSemanticLanguagesPreference> {
            val enabled = AceEditorLspPreferences.AVAILABLE_SEMANTIC_LANGUAGES.count(
                AceEditorLspPreferences::isSemanticEnabled,
            )
            prefContext.getString(
                R.string.summary_ace_semantic_languages,
                enabled,
                AceEditorLspPreferences.AVAILABLE_SEMANTIC_LANGUAGES.size,
            )
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) return
        val languages = AceEditorLspPreferences.SUPPORTED_SEMANTIC_LANGUAGES
        val checked = languages.indices
            .filter { AceEditorLspPreferences.isSemanticEnabled(languages[it]) }
            .toTypedArray()
        val disabled = languages.indices
            .filter { !AceEditorLspPreferences.isSemanticAvailable(languages[it]) }
            .toTypedArray()
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_ace_semantic_languages)
                .content(R.string.description_ace_semantic_languages_preference)
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(
                            prefContext.getString(R.string.dialog_button_use_default),
                        ) { dialog ->
                            dialog.setSelectedIndices(
                                languages.indices.filter {
                                    AceEditorLspPreferences.defaultSemanticEnabled(languages[it])
                                }.toTypedArray(),
                            )
                        },
                    ),
                )
                .items(languages.map(::languageLabel))
                .itemsDisabledIndices(*disabled)
                .itemsCallbackMultiChoice(checked) { _, which, _ ->
                    val selected = which.mapTo(mutableSetOf()) { languages[it] }
                    languages.forEach { language ->
                        AceEditorLspPreferences.setSemanticEnabled(
                            language,
                            language in selected && AceEditorLspPreferences.isSemanticAvailable(language),
                        )
                    }
                    notifyChanged()
                    true
                }
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    private fun languageLabel(language: String): String {
        val label = LANGUAGE_NAMES.getValue(language)
        return if (AceEditorLspPreferences.isSemanticAvailable(language)) {
            label
        } else {
            prefContext.getString(R.string.format_ace_semantic_language_unavailable, label)
        }
    }

    private companion object {
        val LANGUAGE_NAMES = mapOf(
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_TYPESCRIPT to "TypeScript / JavaScript",
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_PYTHON to "Python",
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_LUA to "Lua",
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_JAVA to "Java",
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_KOTLIN to "Kotlin",
        )
    }
}
