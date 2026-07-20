package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context

object AceEditorAssets {

    private const val EDITOR_ASSET_ROOT = "editor/ace-builds-1.4.12"

    /**
     * Assets that must be packaged together for the ACE editor and its bundled
     * TypeScript language service to be considered available.
     *
     * Keep this list explicit: checking only the HTML entry point can leave the
     * app selecting ACE even when a script or declaration file required during
     * startup was omitted from the APK.
     */
    internal val requiredAssetPaths = listOf(
        "$EDITOR_ASSET_ROOT/autojs6_editor.html",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6.css",
        "$EDITOR_ASSET_ROOT/src-min-noconflict/ace.js",
        "$EDITOR_ASSET_ROOT/src-min-noconflict/ext-language_tools.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs_indices.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_indices.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_completer.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_lsp_client.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_tooltip.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_signature_help.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_ace_bridge.js",
        "$EDITOR_ASSET_ROOT/autojs6/autojs6_ts_language_service.js",
        "$EDITOR_ASSET_ROOT/autojs6/typescript/typescriptServices.js",
        "$EDITOR_ASSET_ROOT/autojs6/typescript/lib.es2020.d.ts",
        "$EDITOR_ASSET_ROOT/autojs6/types/lib.autojs6.d.ts",
        "$EDITOR_ASSET_ROOT/autojs6/types/lib.autojs6.extra.d.ts",
    )

    internal data class ValidationResult(
        val missingAssetPaths: List<String>,
    ) {
        val isComplete: Boolean
            get() = missingAssetPaths.isEmpty()
    }

    fun hasRequired(context: Context): Boolean {
        if (AceEditorTestHooks.shouldForceAssetMissing()) {
            return false
        }
        return validateRequiredAssets { assetPath ->
            context.assets.open(assetPath).use { }
        }.isComplete
    }

    internal fun validateRequiredAssets(openAsset: (String) -> Unit): ValidationResult {
        val missingAssetPaths = requiredAssetPaths.filter { assetPath ->
            runCatching { openAsset(assetPath) }.isFailure
        }
        return ValidationResult(missingAssetPaths)
    }
}
