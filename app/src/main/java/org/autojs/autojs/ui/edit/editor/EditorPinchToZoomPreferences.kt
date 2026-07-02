package org.autojs.autojs.ui.edit.editor

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

object EditorPinchToZoomPreferences {

    fun migrateLegacyPreferenceIfNeeded() {
        val enabledKey = key(R.string.key_editor_pinch_to_zoom_change_text_size_enabled)
        if (Pref.containsKey(enabledKey)) {
            return
        }
        val legacyKey = legacyStrategyKey() ?: return
        Pref.putBoolean(enabledKey, legacyKey != key(R.string.key_editor_pinch_to_zoom_disable))
    }

    fun isChangeTextSizeEnabled(): Boolean {
        val enabledKey = key(R.string.key_editor_pinch_to_zoom_change_text_size_enabled)
        if (Pref.containsKey(enabledKey)) {
            return Pref.getBoolean(enabledKey, true)
        }
        return legacyStrategyKey() != key(R.string.key_editor_pinch_to_zoom_disable)
    }

    private fun legacyStrategyKey(): String? {
        return runCatching {
            Pref.getStringOrNull(key(R.string.key_editor_pinch_to_zoom_strategy))
        }.getOrNull()
    }
}
