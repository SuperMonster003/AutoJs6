package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences

object AceEditorDisplayPreferences {

    const val KEY_ACE_WORD_WRAP_ENABLED = EditorDisplayPreferences.KEY_WORD_WRAP_ENABLED
    const val KEY_ACE_LINE_NUMBERS_ENABLED = EditorDisplayPreferences.KEY_LINE_NUMBERS_ENABLED
    const val KEY_ACE_FONT_LIGATURES_ENABLED = EditorDisplayPreferences.KEY_FONT_LIGATURES_ENABLED
    const val KEY_ACE_FONT_STYLES_ENABLED = EditorDisplayPreferences.KEY_FONT_STYLES_ENABLED

    fun isWordWrapEnabled(): Boolean {
        return EditorDisplayPreferences.isWordWrapEnabled()
    }

    fun setWordWrapEnabled(enabled: Boolean) {
        EditorDisplayPreferences.setWordWrapEnabled(enabled)
    }

    fun wordWrapIndentStyle(): String {
        return EditorDisplayPreferences.wordWrapIndentStyle()
    }

    fun setWordWrapIndentStyle(style: String) {
        EditorDisplayPreferences.setWordWrapIndentStyle(style)
    }

    fun isLineNumbersEnabled(): Boolean {
        return EditorDisplayPreferences.isLineNumbersEnabled()
    }

    fun setLineNumbersEnabled(enabled: Boolean) {
        EditorDisplayPreferences.setLineNumbersEnabled(enabled)
    }

    fun isBreakpointMarkersEnabled(): Boolean {
        return EditorDisplayPreferences.isBreakpointMarkersEnabled()
    }

    fun setBreakpointMarkersEnabled(enabled: Boolean) {
        EditorDisplayPreferences.setBreakpointMarkersEnabled(enabled)
    }

    fun isFoldMarkersEnabled(): Boolean {
        return EditorDisplayPreferences.isFoldMarkersEnabled()
    }

    fun setFoldMarkersEnabled(enabled: Boolean) {
        EditorDisplayPreferences.setFoldMarkersEnabled(enabled)
    }

    fun gutterWidthMode(): String {
        return EditorDisplayPreferences.gutterWidthMode()
    }

    fun setGutterWidthMode(mode: String) {
        EditorDisplayPreferences.setGutterWidthMode(mode)
    }

    fun isFontLigaturesEnabled(): Boolean {
        return EditorDisplayPreferences.isFontLigaturesEnabled()
    }

    fun setFontLigaturesEnabled(enabled: Boolean) {
        EditorDisplayPreferences.setFontLigaturesEnabled(enabled)
    }

    fun isFontStylesEnabled(): Boolean {
        return EditorDisplayPreferences.isFontStylesEnabled()
    }

    fun setFontStylesEnabled(enabled: Boolean) {
        EditorDisplayPreferences.setFontStylesEnabled(enabled)
    }
}
