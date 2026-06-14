package org.autojs.autojs.ui.edit.editor.engine

import org.autojs.autojs.core.pref.Pref

object EditorEnginePreferences {

    const val KEY_EDITOR_ENGINE = "key_editor_engine"

    @JvmStatic
    fun get(): EditorEngineType {
        return EditorEngineType.fromPreferenceValue(Pref.getString(KEY_EDITOR_ENGINE, EditorEngineType.NATIVE.preferenceValue))
    }

    @JvmStatic
    fun set(type: EditorEngineType) {
        Pref.putString(KEY_EDITOR_ENGINE, type.preferenceValue)
    }
}
