package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

class AceLspFileTypesPreference : MaterialPreference {

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
        summaryProvider = SummaryProvider<AceLspFileTypesPreference> {
            AceEditorLspPreferences.getFileTypes()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: prefContext.getString(R.string.text_none)
        }
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        val types = SUPPORTED_FILE_TYPES
        val current = AceEditorLspPreferences.getFileTypes().toSet()
        val checked = types.indices.filter { types[it] in current }.toTypedArray()
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_ace_lsp_file_types)
                .content(R.string.summary_ace_lsp_file_types)
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_use_default)) { dialog ->
                            dialog.setSelectedIndices(types.indices.filter { types[it] in AceEditorLspPreferences.DEFAULT_FILE_TYPES }.toTypedArray())
                        },
                    ),
                )
                .items(types)
                .itemsCallbackMultiChoice(checked) { _, which, _ ->
                    AceEditorLspPreferences.setFileTypes(which.map { types[it] })
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
        private val SUPPORTED_FILE_TYPES = listOf(
            ".js",
            ".mjs",
            ".cjs",
            ".jsx",
            ".ts",
            ".tsx",
            ".d.ts",
            ".json",
            ".auto.js",
            ".node.js",
        )
    }
}
