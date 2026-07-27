package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

class AceLspDeclarationGroupsPreference : MaterialPreference {

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
        summaryProvider = SummaryProvider<AceLspDeclarationGroupsPreference> {
            AceEditorLspPreferences.getDeclarationGroups()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: prefContext.getString(R.string.summary_ace_lsp_declaration_groups_disabled)
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        val groups = SUPPORTED_DECLARATION_GROUPS
        val current = AceEditorLspPreferences.getDeclarationGroups().toSet()
        val checked = groups.indices.filter { groups[it] in current }.toTypedArray()
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_ace_lsp_declaration_groups)
                .content(R.string.description_ace_lsp_declaration_groups_preference)
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_use_default)) { dialog ->
                            dialog.setSelectedIndices(
                                groups.indices
                                    .filter { groups[it] in AceEditorLspPreferences.DEFAULT_DECLARATION_GROUPS }
                                    .toTypedArray(),
                            )
                        },
                    ),
                )
                .items(groups)
                .itemsCallbackMultiChoice(checked) { _, which, _ ->
                    AceEditorLspPreferences.setDeclarationGroups(which.map { groups[it] })
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

    companion object {
        private val SUPPORTED_DECLARATION_GROUPS = AceEditorLspPreferences.SUPPORTED_DECLARATION_GROUPS
    }
}
