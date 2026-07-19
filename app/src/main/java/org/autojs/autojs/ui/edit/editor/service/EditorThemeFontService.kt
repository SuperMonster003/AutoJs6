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
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreferences
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils

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

    private val aceFontManager = AceEditorFontManager.get(context)

    var currentTheme: Theme? = null
        private set

    fun loadCurrentTheme(): Observable<Theme?> = Themes.getCurrent(context, currentEditor().engineType)

    fun applyEditorPreferences() {
        applyPreferredTextSize()
        currentEditor().notifyDisplayPreferencesChanged()
        applyPreferredAceFont()
    }

    fun applyPreferredTextSize() {
        val editor = currentEditor()
        editor.setTextSizeSp(getEditorTextSize(editor.getTextSizeSp().toInt()).toFloat())
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

            val textColor = resolveImeBarForegroundColor(it)
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

    fun inputMethodBarBackgroundColor(theme: Theme): Int = theme.imeBarBackgroundColor

    fun inputMethodBarForegroundColor(theme: Theme): Int = resolveImeBarForegroundColor(theme)

    private fun resolveImeBarForegroundColor(theme: Theme): Int {
        val themeTone = theme.foregroundColor
        if (!isChromaticThemeTone(themeTone)) {
            return theme.imeBarForegroundColor
        }
        return ColorUtils.adjustColorForContrast(
            theme.imeBarBackgroundColor,
            themeTone,
            minimumContrast = 3.0,
        )
    }

    private fun isChromaticThemeTone(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val channelSpread = maxOf(red, green, blue) - minOf(red, green, blue)
        if (channelSpread < CHROMATIC_CHANNEL_SPREAD_THRESHOLD) {
            return false
        }
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        return hsv[1] >= CHROMATIC_SATURATION_THRESHOLD
    }

    fun currentAceFont(): String = AceEditorFontPreferences.getId()

    fun applyPreferredAceFont() {
        applyAceFont(currentAceFont(), persist = false)
    }

    fun persistAndApplyAceFont(fontId: String) {
        applyAceFont(fontId, persist = true)
    }

    fun previewAceFont(fontId: String) {
        applyAceFont(fontId, persist = false)
    }

    fun aceEditorFontLabel(fontId: String): String = aceFontManager.displayName(fontId, context)

    private fun applyAceFont(fontId: String, persist: Boolean) {
        if (persist) {
            AceEditorFontPreferences.setId(fontId)
        }
        (currentEditor() as? AceScriptEditorEngine)?.setAceEditorFont(fontId)
    }

    private companion object {
        const val CHROMATIC_CHANNEL_SPREAD_THRESHOLD = 36
        const val CHROMATIC_SATURATION_THRESHOLD = 0.14f
    }
}
