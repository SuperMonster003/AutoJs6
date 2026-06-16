package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.core.pref.Pref

object AceEditorDisplayPreferences {

    const val KEY_ACE_WORD_WRAP_ENABLED = "key_\$_ace_word_wrap_enabled"
    const val KEY_ACE_LINE_NUMBERS_ENABLED = "key_\$_ace_line_numbers_enabled"

    private const val DEFAULT_WORD_WRAP_ENABLED = false
    private const val DEFAULT_LINE_NUMBERS_ENABLED = true

    fun isWordWrapEnabled(): Boolean {
        return Pref.getBoolean(KEY_ACE_WORD_WRAP_ENABLED, DEFAULT_WORD_WRAP_ENABLED)
    }

    fun setWordWrapEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_ACE_WORD_WRAP_ENABLED, enabled)
    }

    fun isLineNumbersEnabled(): Boolean {
        return Pref.getBoolean(KEY_ACE_LINE_NUMBERS_ENABLED, DEFAULT_LINE_NUMBERS_ENABLED)
    }

    fun setLineNumbersEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_ACE_LINE_NUMBERS_ENABLED, enabled)
    }
}
