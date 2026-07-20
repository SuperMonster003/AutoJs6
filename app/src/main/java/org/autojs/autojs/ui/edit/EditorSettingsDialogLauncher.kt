package org.autojs.autojs.ui.edit

import android.content.Context
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.settings.CodeEditorSettingsDialog

object EditorSettingsDialogLauncher {

    fun show(
        context: Context,
        initialPreferenceKey: String? = null,
        recordAceActionFavorites: Boolean = false,
        suppressInitialActionRecord: Boolean = false,
        themePreviewHandler: ((Theme) -> Unit)? = null,
        fontPreviewHandler: ((String) -> Unit)? = null,
    ) {
        CodeEditorSettingsDialog.show(
            context = context,
            initialPreferenceKey = initialPreferenceKey,
            recordAceActionFavorites = recordAceActionFavorites,
            suppressInitialActionRecord = suppressInitialActionRecord,
            themePreviewHandler = themePreviewHandler,
            fontPreviewHandler = fontPreviewHandler,
        )
    }
}
