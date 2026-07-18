package org.autojs.autojs.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.theme.preference.MaterialDialogPreference
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class EditorThemePreference : MaterialPreference {

    private var themePreviewHandler: ((Theme) -> Unit)? = null

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
        summaryProvider = SummaryProvider<EditorThemePreference> {
            Themes.getCurrentThemeName(prefContext, EditorEnginePreferences.get()) ?: prefContext.getString(R.string.text_default)
        }
    }

    @SuppressLint("CheckResult")
    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        Themes.getAllThemes(prefContext, EditorEnginePreferences.get())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::showThemeDialog) {
                ViewUtils.showToast(prefContext, it.message ?: it.toString(), true)
            }
    }

    private fun showThemeDialog(themes: List<Theme>) {
        val engineType = EditorEnginePreferences.get()
        val current = Themes.getCurrentThemeName(prefContext, engineType)
        val selectedIndex = themes.indexOfFirst { it.name == current }.coerceAtLeast(0)
        val defaultIndex = themes.indexOfFirst { it.name == Themes.getDefaultThemeName(prefContext, engineType) }.coerceAtLeast(0)
        fun previewFromDialog(dialog: MaterialDialog) {
            val selectedTheme = themes.getOrNull(dialog.selectedIndex) ?: themes[selectedIndex]
            dialog.dismiss()
            themePreviewHandler?.invoke(selectedTheme)
        }
        DialogUtils.buildAndShowAdaptive {
            val builder = MaterialDialog.Builder(prefContext)
                .title(R.string.text_editor_theme)
                .items(themes)
                .choiceWidgetThemeColor()
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_use_default)) { dialog ->
                            dialog.selectedIndex = defaultIndex
                        },
                        MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_details)) {
                            MaterialDialogPreference.displayDetailsOptionMenuItemSpecDialog(
                                context = prefContext,
                                dialogTitle = prefContext.getString(R.string.text_editor_theme),
                                longClickPrompt = prefContext.getString(R.string.description_editor_theme_dialog),
                            )
                        },
                    ),
                )
                .itemsCallbackSingleChoice(selectedIndex) { _, _, which, _ ->
                    Themes.setCurrent(prefContext, themes[which].name)
                    notifyChanged()
                    true
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)

            if (themePreviewHandler != null) {
                builder
                    .neutralText(R.string.dialog_button_preview)
                    .neutralColorRes(R.color.dialog_button_hint)
                    .onNeutral { dialog, _ -> previewFromDialog(dialog) }
            }

            builder
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    fun setThemePreviewHandler(handler: ((Theme) -> Unit)?) {
        themePreviewHandler = handler
    }

    fun refreshSummary() {
        notifyChanged()
    }
}
