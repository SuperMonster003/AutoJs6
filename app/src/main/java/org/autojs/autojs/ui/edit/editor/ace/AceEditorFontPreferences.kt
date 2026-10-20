package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.core.pref.keys.EditorKeys

object AceEditorFontPreferences {

    const val KEY_ACE_EDITOR_FONT = "key_\$_ace_editor_font"
    private val VALID_FONT_ID = Regex("^[a-z0-9][a-z0-9_]{0,63}$")

    @JvmStatic
    fun getId(): String {
        return EditorKeys.ACE_EDITOR_FONT.get()
            .takeIf(VALID_FONT_ID::matches)
            ?: AceEditorFont.IOSEVKA.preferenceValue
    }

    @JvmStatic
    fun get(): AceEditorFont {
        return AceEditorFont.fromPreferenceValue(getId())
    }

    @JvmStatic
    fun set(font: AceEditorFont) {
        setId(font.preferenceValue)
    }

    @JvmStatic
    fun setId(fontId: String) {
        require(VALID_FONT_ID.matches(fontId)) { "Invalid ACE editor font id: $fontId" }
        EditorKeys.ACE_EDITOR_FONT.set(fontId)
    }
}
