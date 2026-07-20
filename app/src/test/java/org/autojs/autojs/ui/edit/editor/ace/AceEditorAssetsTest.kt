package org.autojs.autojs.ui.edit.editor.ace

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceEditorAssetsTest {

    @Test
    fun requiredAssetsCoverEditorBridgeAndBundledLanguageService() {
        val requiredPaths = AceEditorAssets.requiredAssetPaths

        assertEquals(requiredPaths.distinct(), requiredPaths)
        assertTrue(
            requiredPaths.containsAll(
                listOf(
                    "$ASSET_ROOT/autojs6_editor.html",
                    "$ASSET_ROOT/src-min-noconflict/ace.js",
                    "$ASSET_ROOT/autojs6/autojs6_ace_bridge.js",
                    "$ASSET_ROOT/autojs6/autojs6_lsp_client.js",
                    "$ASSET_ROOT/autojs6/autojs6_ts_language_service.js",
                    "$ASSET_ROOT/autojs6/typescript/typescriptServices.js",
                    "$ASSET_ROOT/autojs6/typescript/lib.es2020.d.ts",
                    "$ASSET_ROOT/autojs6/types/lib.autojs6.d.ts",
                    "$ASSET_ROOT/autojs6/types/lib.autojs6.extra.d.ts",
                ),
            ),
        )
        assertTrue(requiredPaths.all { it.startsWith("$ASSET_ROOT/") })
    }

    @Test
    fun validationSucceedsOnlyAfterEveryRequiredAssetCanBeOpened() {
        val openedPaths = mutableListOf<String>()

        val result = AceEditorAssets.validateRequiredAssets { assetPath ->
            openedPaths += assetPath
        }

        assertTrue(result.isComplete)
        assertTrue(result.missingAssetPaths.isEmpty())
        assertEquals(AceEditorAssets.requiredAssetPaths, openedPaths)
    }

    @Test
    fun validationReportsEveryMissingAssetAndForcesFallback() {
        val missingPaths = setOf(
            "$ASSET_ROOT/autojs6/autojs6_lsp_client.js",
            "$ASSET_ROOT/autojs6/typescript/typescriptServices.js",
            "$ASSET_ROOT/autojs6/types/lib.autojs6.d.ts",
        )

        val result = AceEditorAssets.validateRequiredAssets { assetPath ->
            if (assetPath in missingPaths) {
                throw FileNotFoundException(assetPath)
            }
        }

        assertFalse(result.isComplete)
        assertEquals(
            AceEditorAssets.requiredAssetPaths.filter { it in missingPaths },
            result.missingAssetPaths,
        )
    }

    private companion object {
        const val ASSET_ROOT = "editor/ace-builds-1.4.12"
    }
}
