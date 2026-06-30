package org.autojs.autojs.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class EditorThemePreference : MaterialPreference {

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
            Themes.getCurrentThemeName(prefContext) ?: prefContext.getString(R.string.text_default)
        }
    }

    @SuppressLint("CheckResult")
    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        Themes.getAllThemes(prefContext)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::showThemeDialog) {
                ViewUtils.showToast(prefContext, it.message ?: it.toString(), true)
            }
    }

    private fun showThemeDialog(themes: List<Theme>) {
        val current = Themes.getCurrentThemeName(prefContext)
        val selectedIndex = themes.indexOfFirst { it.name == current }.coerceAtLeast(0)
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_editor_theme)
                .items(themes)
                .choiceWidgetThemeColor()
                .itemsCallbackSingleChoice(selectedIndex) { _, _, which, _ ->
                    Themes.setCurrent(prefContext, themes[which].name)
                    notifyChanged()
                    true
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    fun refreshSummary() {
        notifyChanged()
    }
}
