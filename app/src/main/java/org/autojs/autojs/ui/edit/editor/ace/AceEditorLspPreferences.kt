package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.core.pref.Pref

object AceEditorLspPreferences {
    const val KEY_ACE_LSP_ENABLED = "key_\$_ace_lsp_enabled"

    @JvmStatic
    fun isEnabled(): Boolean {
        return Pref.getBoolean(KEY_ACE_LSP_ENABLED, false)
    }

    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        Pref.putBoolean(KEY_ACE_LSP_ENABLED, enabled)
    }
}
