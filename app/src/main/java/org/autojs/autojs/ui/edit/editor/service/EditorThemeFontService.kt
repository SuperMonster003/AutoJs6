package org.autojs.autojs.ui.edit.editor.service

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import io.reactivex.Observable
import org.autojs.autojs.core.pref.Pref.getEditorTextSize
import org.autojs.autojs.core.pref.Pref.setEditorTextSize
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFont
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreferences
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class EditorThemeFontService(
    private val context: Context,
    private val activity: Activity,
    private val currentEditor: () -> ScriptEditorEngine,
    private val inputMethodEnhanceBar: View,
    private val loadingBarContainer: View,
    private val loadingBarText: TextView,
    private val completionBars: List<CodeCompletionBar>,
    private val showFunctionsButtons: List<ImageView>,
    private val invalidateHost: () -> Unit,
) {

    var currentTheme: Theme? = null
        private set

    fun loadCurrentTheme(): Observable<Theme?> = Themes.getCurrent(context)

    fun applyEditorPreferences() {
        val editor = currentEditor()
        editor.setTextSizeSp(getEditorTextSize(editor.getTextSizeSp().toInt()).toFloat())
        editor.notifyDisplayPreferencesChanged()
        applyPreferredAceFont()
    }

    fun applyTheme(theme: Theme?): Theme? {
        return theme?.also {
            currentTheme = it

            val appThemeColor = ThemeColorManager.colorPrimary
            val imeBarBackgroundColor = it.imeBarBackgroundColor
            currentEditor().setTheme(it)

            inputMethodEnhanceBar.setBackgroundColor(imeBarBackgroundColor)
            loadingBarContainer.setBackgroundColor(imeBarBackgroundColor)

            val loadingTextColor = ColorUtils.adjustColorForContrast(imeBarBackgroundColor, appThemeColor, 3.6)
            loadingBarText.setTextColor(loadingTextColor)

            val textColor = it.imeBarForegroundColor
            completionBars.forEach { bar -> bar.setTextColor(textColor) }
            showFunctionsButtons.forEach { button -> button.setColorFilter(textColor) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ViewUtils.setNavigationBarBackgroundColor(activity, imeBarBackgroundColor)
            } else {
                val adjustedImageContrastColor = ColorUtils.adjustColorForContrast(
                    Color.WHITE,
                    ColorUtils.applyAlpha(imeBarBackgroundColor, 1.0),
                    2.3,
                )
                ViewUtils.setNavigationBarBackgroundColor(activity, adjustedImageContrastColor)
            }
            invalidateHost()
        }
    }

    fun persistAndApplyTheme(theme: Theme): Theme {
        applyTheme(theme)
        Themes.setCurrent(context, theme.name)
        return theme
    }

    fun setTextSize(value: Int) {
        setEditorTextSize(value)
        currentEditor().setTextSizeSp(value.toFloat())
    }

    fun currentTextSize(): Int = currentEditor().getTextSizeSp().toInt()

    fun availableAceFonts(): List<AceEditorFont> = AceEditorFont.entries.toList()

    fun currentAceFont(): AceEditorFont = AceEditorFontPreferences.get()

    fun applyPreferredAceFont() {
        applyAceFont(currentAceFont(), persist = false)
    }

    fun persistAndApplyAceFont(font: AceEditorFont) {
        applyAceFont(font, persist = true)
    }

    fun aceEditorFontLabel(font: AceEditorFont): String {
        return when (font) {
            AceEditorFont.JETBRAINS_MONO -> context.getString(R.string.entry_ace_editor_font_jetbrains_mono)
            AceEditorFont.SOURCE_CODE_PRO -> context.getString(R.string.entry_ace_editor_font_source_code_pro)
            AceEditorFont.SYSTEM_MONOSPACE -> context.getString(R.string.entry_ace_editor_font_system_monospace)
        }
    }

    private fun applyAceFont(font: AceEditorFont, persist: Boolean) {
        if (persist) {
            AceEditorFontPreferences.set(font)
        }
        (currentEditor() as? AceScriptEditorEngine)?.setAceEditorFont(font)
    }
}
