package org.autojs.autojs.theme.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs.ui.widget.AdaptiveTitleToolbar
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.applyColorFilterWith
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout

/**
 * Created by Stardust on Mar 5, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
open class ThemeColorToolbar : AdaptiveTitleToolbar, ThemeColorMutable {

    private data class EditorThemeColors(
        val background: Int,
        val foreground: Int,
    )

    private var editorThemeColors: EditorThemeColors? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        ThemeColorManager.add(this)
    }

    override fun setThemeColor(themeColor: ThemeColor) {
        onceGlobalLayout { applyThemeColor(themeColor) }
    }

    private fun applyThemeColor(themeColor: ThemeColor) {
        editorThemeColors?.let { editorColors ->
            applyColors(editorColors.background, editorColors.foreground)
            return
        }
        val background = themeColor.colorPrimary
        val foreground = ViewUtils.getDayOrNightColorByLuminance(context, background)
        applyColors(background, foreground)
    }

    /**
     * Keeps editor-owned colors independent from later global ThemeColorManager notifications.
     */
    fun setEditorThemeColors(@ColorInt background: Int, @ColorInt foreground: Int) {
        editorThemeColors = EditorThemeColors(background, foreground)
        applyColors(background, foreground)
        post {
            editorThemeColors?.let { applyColors(it.background, it.foreground) }
        }
    }

    fun clearEditorThemeColors() {
        editorThemeColors = null
        applyThemeColor(ThemeColorManager.currentThemeColor)
    }

    override fun setNavigationIcon(icon: Drawable?) {
        super.setNavigationIcon(tintForEditorIfNeeded(icon))
    }

    override fun setCollapseIcon(icon: Drawable?) {
        super.setCollapseIcon(tintForEditorIfNeeded(icon))
    }

    override fun setOverflowIcon(icon: Drawable?) {
        super.setOverflowIcon(tintForEditorIfNeeded(icon))
    }

    private fun applyColors(@ColorInt background: Int, @ColorInt foreground: Int) {
        setBackgroundColor(background)
        setSubtitleTextColor(foreground)
        setTitleTextColor(foreground)

        super.setNavigationIcon(tint(navigationIcon, foreground))
        super.setCollapseIcon(tint(collapseIcon, foreground))
        super.setOverflowIcon(tint(overflowIcon, foreground))
    }

    private fun tintForEditorIfNeeded(icon: Drawable?): Drawable? =
        editorThemeColors?.let { tint(icon, it.foreground) } ?: icon

    private fun tint(icon: Drawable?, @ColorInt color: Int): Drawable? =
        icon?.mutate()?.applyColorFilterWith(color)
}
