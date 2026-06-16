package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context

object AceEditorAssets {

    private const val EDITOR_ASSET_PATH = "editor/ace-builds-1.4.12/autojs6_editor.html"

    fun hasRequired(context: Context): Boolean {
        if (AceEditorTestHooks.shouldForceAssetMissing()) {
            return false
        }
        return runCatching {
            context.assets.open(EDITOR_ASSET_PATH).close()
        }.isSuccess
    }
}
