package org.autojs.autojs.ui.edit.editor

import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

object EditorPinchToZoomPreferences {

    fun migrateLegacyPreferenceIfNeeded() {
        if (EditorKeys.EDITOR_PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED.isStored()) {
            return
        }
        val legacyKey = legacyStrategyKey() ?: return
        EditorKeys.EDITOR_PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED.set(legacyKey != key(R.string.key_editor_pinch_to_zoom_disable))
    }

    fun isChangeTextSizeEnabled(): Boolean {
        if (EditorKeys.EDITOR_PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED.isStored()) {
            return EditorKeys.EDITOR_PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED.get()
        }
        return legacyStrategyKey() != key(R.string.key_editor_pinch_to_zoom_disable)
    }

    private fun legacyStrategyKey(): String? {
        return runCatching {
            MiscStateKeys.EDITOR_PINCH_TO_ZOOM_STRATEGY.storedOrNull()
        }.getOrNull()
    }
}
