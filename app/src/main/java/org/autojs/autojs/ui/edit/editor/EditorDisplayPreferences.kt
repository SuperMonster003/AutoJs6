package org.autojs.autojs.ui.edit.editor

import org.autojs.autojs.core.pref.Pref

object EditorDisplayPreferences {

    const val KEY_WORD_WRAP_ENABLED = "key_\$_ace_word_wrap_enabled"
    const val KEY_WORD_WRAP_INDENT_STYLE = "key_\$_editor_word_wrap_indent_style"
    const val KEY_LINE_NUMBERS_ENABLED = "key_\$_ace_line_numbers_enabled"
    const val KEY_PRINT_MARGIN_ENABLED = "key_\$_ace_print_margin_enabled"
    const val KEY_INDENT_GUIDES_ENABLED = "key_\$_ace_indent_guides_enabled"
    const val KEY_BREAKPOINT_MARKERS_ENABLED = "key_\$_editor_breakpoint_markers_enabled"
    const val KEY_FOLD_MARKERS_ENABLED = "key_\$_editor_fold_markers_enabled"
    const val KEY_GUTTER_WIDTH_MODE = "key_\$_editor_gutter_width_mode"
    const val KEY_FONT_LIGATURES_ENABLED = "key_\$_ace_font_ligatures_enabled"
    const val KEY_FONT_STYLES_ENABLED = "key_\$_ace_font_styles_enabled"

    // Ace display options added with Ace 1.44.0 (read by the Ace plugin; the host only relays changes).
    // zh-CN: 随 Ace 1.44.0 加入的 Ace 显示选项 (由 Ace 插件读取; 宿主只转发变更).
    const val KEY_HIGHLIGHT_INDENT_GUIDES_ENABLED = "key_\$_ace_highlight_indent_guides_enabled"
    const val KEY_SVG_GUTTER_ICONS_ENABLED = "key_\$_ace_svg_gutter_icons_enabled"
    const val KEY_CUSTOM_SCROLLBAR_ENABLED = "key_\$_ace_custom_scrollbar_enabled"
    const val KEY_SCROLLBAR_AUTO_HIDE_ENABLED = "key_\$_ace_scrollbar_auto_hide_enabled"
    const val KEY_WHITESPACES_IN_SELECTION_ENABLED = "key_\$_ace_whitespaces_in_selection_enabled"
    const val KEY_TOUCH_MAGNIFIER_ENABLED = "key_\$_ace_touch_magnifier_enabled"

    // Ace smart hint policy keys (read by the Ace plugin; the host only relays changes).
    const val KEY_SMART_HINT_COMPLETION_TRIGGER = "key_\$_ace_smart_hint_completion_trigger"
    const val KEY_SMART_HINT_COMPLETION_MIN_CHARS = "key_\$_ace_smart_hint_completion_min_chars"
    const val KEY_SMART_HINT_COMPLETION_DELAY = "key_\$_ace_smart_hint_completion_delay"
    const val KEY_SMART_HINT_SIGNATURE_TRIGGER = "key_\$_ace_smart_hint_signature_trigger"
    const val KEY_SMART_HINT_ARROW_OVERLOAD_CYCLING = "key_\$_ace_smart_hint_arrow_overload_cycling"
    const val KEY_SMART_HINT_SYMBOL_HIGHLIGHT = "key_\$_ace_symbol_highlight_enabled"
    const val KEY_SMART_HINT_INLINE_COMPLETION_PREVIEW = "key_\$_ace_inline_completion_preview_enabled"

    const val WORD_WRAP_INDENT_STYLE_CONTINUATION = "continuation"
    const val WORD_WRAP_INDENT_STYLE_CLASSIC = "classic"
    const val GUTTER_WIDTH_MODE_DYNAMIC = "dynamic"
    const val GUTTER_WIDTH_MODE_FIXED = "fixed"

    private const val DEFAULT_WORD_WRAP_ENABLED = false
    private const val DEFAULT_WORD_WRAP_INDENT_STYLE = WORD_WRAP_INDENT_STYLE_CONTINUATION
    private const val DEFAULT_LINE_NUMBERS_ENABLED = true
    private const val DEFAULT_PRINT_MARGIN_ENABLED = false
    private const val DEFAULT_INDENT_GUIDES_ENABLED = true
    private const val DEFAULT_BREAKPOINT_MARKERS_ENABLED = true
    private const val DEFAULT_FOLD_MARKERS_ENABLED = true
    private const val DEFAULT_GUTTER_WIDTH_MODE = GUTTER_WIDTH_MODE_DYNAMIC
    private const val DEFAULT_FONT_LIGATURES_ENABLED = true
    private const val DEFAULT_FONT_STYLES_ENABLED = true

    fun isWordWrapEnabled(): Boolean {
        return Pref.getBoolean(KEY_WORD_WRAP_ENABLED, DEFAULT_WORD_WRAP_ENABLED)
    }

    fun setWordWrapEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_WORD_WRAP_ENABLED, enabled)
    }

    fun wordWrapIndentStyle(): String {
        return Pref.getString(KEY_WORD_WRAP_INDENT_STYLE, DEFAULT_WORD_WRAP_INDENT_STYLE)
            .takeIf { it in WORD_WRAP_INDENT_STYLES }
            ?: DEFAULT_WORD_WRAP_INDENT_STYLE
    }

    fun setWordWrapIndentStyle(style: String) {
        Pref.putString(
            KEY_WORD_WRAP_INDENT_STYLE,
            style.takeIf { it in WORD_WRAP_INDENT_STYLES } ?: DEFAULT_WORD_WRAP_INDENT_STYLE,
        )
    }

    fun isLineNumbersEnabled(): Boolean {
        return Pref.getBoolean(KEY_LINE_NUMBERS_ENABLED, DEFAULT_LINE_NUMBERS_ENABLED)
    }

    fun setLineNumbersEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_LINE_NUMBERS_ENABLED, enabled)
    }

    fun isPrintMarginEnabled(): Boolean {
        return Pref.getBoolean(KEY_PRINT_MARGIN_ENABLED, DEFAULT_PRINT_MARGIN_ENABLED)
    }

    fun setPrintMarginEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_PRINT_MARGIN_ENABLED, enabled)
    }

    fun isIndentGuidesEnabled(): Boolean {
        return Pref.getBoolean(KEY_INDENT_GUIDES_ENABLED, DEFAULT_INDENT_GUIDES_ENABLED)
    }

    fun setIndentGuidesEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_INDENT_GUIDES_ENABLED, enabled)
    }

    fun isBreakpointMarkersEnabled(): Boolean {
        return Pref.getBoolean(KEY_BREAKPOINT_MARKERS_ENABLED, DEFAULT_BREAKPOINT_MARKERS_ENABLED)
    }

    fun setBreakpointMarkersEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_BREAKPOINT_MARKERS_ENABLED, enabled)
    }

    fun isFoldMarkersEnabled(): Boolean {
        return Pref.getBoolean(KEY_FOLD_MARKERS_ENABLED, DEFAULT_FOLD_MARKERS_ENABLED)
    }

    fun setFoldMarkersEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_FOLD_MARKERS_ENABLED, enabled)
    }

    fun gutterWidthMode(): String {
        return normalizeGutterWidthMode(Pref.getString(KEY_GUTTER_WIDTH_MODE, DEFAULT_GUTTER_WIDTH_MODE))
    }

    fun setGutterWidthMode(mode: String) {
        Pref.putString(KEY_GUTTER_WIDTH_MODE, normalizeGutterWidthMode(mode))
    }

    fun isFontLigaturesEnabled(): Boolean {
        return Pref.getBoolean(KEY_FONT_LIGATURES_ENABLED, DEFAULT_FONT_LIGATURES_ENABLED)
    }

    fun setFontLigaturesEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_FONT_LIGATURES_ENABLED, enabled)
    }

    fun isFontStylesEnabled(): Boolean {
        return Pref.getBoolean(KEY_FONT_STYLES_ENABLED, DEFAULT_FONT_STYLES_ENABLED)
    }

    fun setFontStylesEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_FONT_STYLES_ENABLED, enabled)
    }

    fun normalizeGutterWidthMode(mode: String?): String {
        return mode?.takeIf { it in GUTTER_WIDTH_MODES } ?: DEFAULT_GUTTER_WIDTH_MODE
    }

    private val WORD_WRAP_INDENT_STYLES = setOf(
        WORD_WRAP_INDENT_STYLE_CONTINUATION,
        WORD_WRAP_INDENT_STYLE_CLASSIC,
    )

    private val GUTTER_WIDTH_MODES = setOf(
        GUTTER_WIDTH_MODE_DYNAMIC,
        GUTTER_WIDTH_MODE_FIXED,
    )
}
