package org.autojs.autojs.ui.edit.editor.service

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.reactivex.Observable
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreferences
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.ui.edit.theme.EditorChromePalette
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.widget.ToolbarMenuItem
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils

class EditorThemeFontService(
    private val context: Context,
    private val activity: Activity,
    private val currentEditor: () -> ScriptEditorEngine,
    private val editorToolbar: ThemeColorToolbar,
    private val toolbarMenuContainer: View,
    private val editorStatusBar: View,
    private val editorStatusTextViews: List<TextView>,
    private val inputMethodEnhanceBar: View,
    private val loadingBarContainer: View,
    private val loadingBarText: TextView,
    private val completionBars: List<CodeCompletionBar>,
    private val showFunctionsButtons: List<ImageView>,
    private val invalidateHost: () -> Unit,
    private val onPaletteChanged: (background: Int, foreground: Int) -> Unit,
) {

    data class PreviewProgressColors(
        val indicatorColor: Int,
        val trackColor: Int,
    )

    private val aceFontManager = AceEditorFontManager.get(context)

    var currentTheme: Theme? = null
        private set

    fun loadCurrentTheme(): Observable<Theme?> = Themes.getCurrent(context, currentEditor().engineType)

    fun loadCurrentThemeImmediatelyAvailable(): Theme? =
        Themes.getCurrentImmediatelyAvailable(context, currentEditor().engineType)

    fun applyEditorPreferences() {
        applyPreferredTextSize()
        currentEditor().notifyDisplayPreferencesChanged()
        applyPreferredAceFont()
    }

    fun applyPreferredTextSize() {
        val editor = currentEditor()
        editor.setTextSizeSp((EditorKeys.EDITOR_TEXT_SIZE.storedOrNull() ?: editor.getTextSizeSp().toInt()).toFloat())
    }

    fun applyTheme(theme: Theme?): Theme? {
        return theme?.also {
            currentTheme = it

            val appThemeColor = ThemeColorManager.colorPrimary
            val imeBarBackgroundColor = it.imeBarBackgroundColor
            currentEditor().setTheme(it)

            editorStatusBar.setBackgroundColor(it.backgroundColor)
            inputMethodEnhanceBar.setBackgroundColor(imeBarBackgroundColor)
            loadingBarContainer.setBackgroundColor(imeBarBackgroundColor)

            val loadingTextColor = ColorUtils.adjustColorForContrast(imeBarBackgroundColor, appThemeColor, 3.6)
            loadingBarText.setTextColor(loadingTextColor)

            val textColor = resolveImeBarForegroundColor(it)
            applyEditorChrome(it, textColor)
            editorStatusTextViews.forEach { textView -> textView.setTextColor(textColor) }
            completionBars.forEach { bar -> bar.setTextColor(textColor) }
            showFunctionsButtons.forEach { button -> button.setColorFilter(textColor) }

            val navigationBarBackgroundColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                imeBarBackgroundColor
            } else {
                ColorUtils.adjustColorForContrast(
                    Color.WHITE,
                    ColorUtils.applyAlpha(imeBarBackgroundColor, 1.0),
                    2.3,
                )
            }
            ViewUtils.setNavigationBarBackgroundColor(activity, navigationBarBackgroundColor)
            ViewUtils.setNavigationBarIconLight(
                activity,
                ColorUtils.isLuminanceDark(navigationBarBackgroundColor),
            )
            invalidateHost()
        }
    }

    fun persistAndApplyTheme(theme: Theme): Theme {
        applyTheme(theme)
        Themes.setCurrent(context, theme.name)
        return theme
    }

    fun setTextSize(value: Int) {
        EditorKeys.EDITOR_TEXT_SIZE.set(value)
        currentEditor().setTextSizeSp(value.toFloat())
    }

    fun currentTextSize(): Int = currentEditor().getTextSizeSp().toInt()

    fun inputMethodBarBackgroundColor(theme: Theme): Int = theme.imeBarBackgroundColor

    fun inputMethodBarForegroundColor(theme: Theme): Int = resolveImeBarForegroundColor(theme)

    fun applyCurrentEditorChrome() {
        currentTheme?.let { theme ->
            applyEditorChrome(theme, resolveImeBarForegroundColor(theme))
        }
    }

    fun currentEditorChromeColors(): EditorChromePalette.Colors? = currentTheme?.let { theme ->
        EditorChromePalette.Colors(
            background = theme.backgroundColor,
            foreground = resolveImeBarForegroundColor(theme),
        )
    }

    fun applyMenuColors(menu: Menu) {
        currentTheme?.let { theme ->
            val foreground = resolveImeBarForegroundColor(theme)
            EditorChromePalette.update(theme.backgroundColor, foreground)
            EditorChromePalette.applyTo(menu)
        }
    }

    fun applyToolbarMenuItemColors(root: View) {
        currentTheme?.let { theme ->
            applyToolbarMenuItemColors(root, resolveImeBarForegroundColor(theme))
        }
    }

    fun editorPreviewProgressColors(backgroundColor: Int): PreviewProgressColors {
        val indicatorColor = appThemeColorForContrast(backgroundColor, minimumContrast = 2.3)
        return PreviewProgressColors(
            indicatorColor = indicatorColor,
            trackColor = ColorUtils.applyAlpha(indicatorColor, 0.20),
        )
    }

    fun fontFilterCategoryColor(backgroundColor: Int): Int =
        appThemeColorForContrast(backgroundColor, minimumContrast = 3.2)

    fun editorPreviewFallbackForegroundColor(backgroundColor: Int): Int =
        appThemeColorForContrast(backgroundColor, minimumContrast = 3.0)

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

    private fun applyEditorChrome(theme: Theme, @androidx.annotation.ColorInt foreground: Int) {
        EditorChromePalette.update(theme.backgroundColor, foreground)
        onPaletteChanged(theme.backgroundColor, foreground)
        editorToolbar.setEditorThemeColors(theme.backgroundColor, foreground)
        applyToolbarMenuItemColors(toolbarMenuContainer, foreground)

        ViewUtils.setStatusBarBackgroundColor(activity, theme.backgroundColor)
        ViewUtils.setStatusBarIconLight(
            activity,
            ColorUtils.isLuminanceDark(theme.backgroundColor),
        )
    }

    private fun applyToolbarMenuItemColors(
        root: View,
        @androidx.annotation.ColorInt foreground: Int,
    ) {
        if (root is ToolbarMenuItem) {
            root.setEditorForegroundColor(foreground)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                applyToolbarMenuItemColors(root.getChildAt(index), foreground)
            }
        }
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

    private fun appThemeColorForContrast(backgroundColor: Int, minimumContrast: Double): Int {
        return ColorUtils.adjustColorForContrast(
            backgroundColor,
            ThemeColorManager.colorPrimary,
            minimumContrast,
        )
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
