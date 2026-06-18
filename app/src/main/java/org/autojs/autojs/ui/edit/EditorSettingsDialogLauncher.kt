package org.autojs.autojs.ui.edit

import android.content.Context
import org.autojs.autojs.ui.settings.CodeEditorSettingsDialog

object EditorSettingsDialogLauncher {

    fun show(
        context: Context,
        initialPreferenceKey: String? = null,
        recordAceActionFavorites: Boolean = false,
        suppressInitialActionRecord: Boolean = false,
    ) {
        CodeEditorSettingsDialog.show(
            context = context,
            initialPreferenceKey = initialPreferenceKey,
            recordAceActionFavorites = recordAceActionFavorites,
            suppressInitialActionRecord = suppressInitialActionRecord,
        )
    }
}
