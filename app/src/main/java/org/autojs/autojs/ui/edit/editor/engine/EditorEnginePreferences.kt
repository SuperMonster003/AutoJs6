package org.autojs.autojs.ui.edit.editor.engine

import org.autojs.autojs.core.pref.Pref

object EditorEnginePreferences {

    const val KEY_EDITOR_ENGINE = "key_\$_editor_engine"
    private const val KEY_SEEN_EDITOR_SWITCH_UNDO_NOTICE = "key_\$_seen_editor_switch_undo_notice"
    private const val KEY_SEEN_ACE_FALLBACK_NOTICE = "key_\$_seen_ace_fallback_notice"
    private const val KEY_SEEN_ACE_LARGE_FILE_NOTICE = "key_\$_seen_ace_large_file_notice"

    @JvmStatic
    fun get(): EditorEngineType {
        return EditorEngineType.fromPreferenceValue(Pref.getString(KEY_EDITOR_ENGINE, EditorEngineType.NATIVE.preferenceValue))
    }

    @JvmStatic
    fun set(type: EditorEngineType) {
        Pref.putString(KEY_EDITOR_ENGINE, type.preferenceValue)
    }

    fun consumeEditorSwitchUndoNotice(): Boolean {
        return consumeOnce(KEY_SEEN_EDITOR_SWITCH_UNDO_NOTICE)
    }

    fun consumeAceFallbackNotice(): Boolean {
        return consumeOnce(KEY_SEEN_ACE_FALLBACK_NOTICE)
    }

    fun consumeAceLargeFileNotice(): Boolean {
        return consumeOnce(KEY_SEEN_ACE_LARGE_FILE_NOTICE)
    }

    private fun consumeOnce(key: String): Boolean {
        if (Pref.getBoolean(key, false)) {
            return false
        }
        Pref.putBoolean(key, true)
        return true
    }
}
