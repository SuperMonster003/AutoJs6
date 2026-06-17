package org.autojs.autojs.ui.edit.editor

import org.autojs.autojs.core.pref.Pref

object EditorDisplayPreferences {

    const val KEY_WORD_WRAP_ENABLED = "key_\$_ace_word_wrap_enabled"
    const val KEY_WORD_WRAP_INDENT_STYLE = "key_\$_editor_word_wrap_indent_style"
    const val KEY_LINE_NUMBERS_ENABLED = "key_\$_ace_line_numbers_enabled"
    const val KEY_BREAKPOINT_MARKERS_ENABLED = "key_\$_editor_breakpoint_markers_enabled"
    const val KEY_FOLD_MARKERS_ENABLED = "key_\$_editor_fold_markers_enabled"

    const val WORD_WRAP_INDENT_STYLE_CONTINUATION = "continuation"
    const val WORD_WRAP_INDENT_STYLE_DEFAULT = "default"
    const val WORD_WRAP_INDENT_STYLE_CLASSIC = "classic"

    private const val DEFAULT_WORD_WRAP_ENABLED = false
    private const val DEFAULT_WORD_WRAP_INDENT_STYLE = WORD_WRAP_INDENT_STYLE_CONTINUATION
    private const val DEFAULT_LINE_NUMBERS_ENABLED = true
    private const val DEFAULT_BREAKPOINT_MARKERS_ENABLED = true
    private const val DEFAULT_FOLD_MARKERS_ENABLED = true

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

    private val WORD_WRAP_INDENT_STYLES = setOf(
        WORD_WRAP_INDENT_STYLE_CONTINUATION,
        WORD_WRAP_INDENT_STYLE_DEFAULT,
        WORD_WRAP_INDENT_STYLE_CLASSIC,
    )
}
