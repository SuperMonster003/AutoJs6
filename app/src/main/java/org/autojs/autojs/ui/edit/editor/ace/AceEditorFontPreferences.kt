package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.core.pref.Pref

object AceEditorFontPreferences {

    const val KEY_ACE_EDITOR_FONT = "key_\$_ace_editor_font"

    @JvmStatic
    fun get(): AceEditorFont {
        return AceEditorFont.fromPreferenceValue(
            Pref.getString(KEY_ACE_EDITOR_FONT, AceEditorFont.IOSEVKA.preferenceValue),
        )
    }

    @JvmStatic
    fun set(font: AceEditorFont) {
        Pref.putString(KEY_ACE_EDITOR_FONT, font.preferenceValue)
    }
}
