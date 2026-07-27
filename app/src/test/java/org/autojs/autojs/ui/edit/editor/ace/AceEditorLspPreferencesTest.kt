package org.autojs.autojs.ui.edit.editor.ace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AceEditorLspPreferencesTest {

    @Test
    fun lspIsEnabledByDefaultForNewPreferences() {
        assertTrue(AceEditorLspPreferences.DEFAULT_ENABLED)
    }

    @Test
    fun typeScriptDeclarationsRequireDeclarationFileType() {
        assertTrue(AceEditorLspPreferences.matchesFileType("main.ts", listOf(".ts")))
        assertFalse(AceEditorLspPreferences.matchesFileType("main.ts", listOf(".d.ts")))

        assertTrue(AceEditorLspPreferences.matchesFileType("types.d.ts", listOf(".d.ts")))
        assertFalse(AceEditorLspPreferences.matchesFileType("types.d.ts", listOf(".ts")))

        assertTrue(AceEditorLspPreferences.matchesFileType("types.d.mts", listOf(".d.mts")))
        assertFalse(AceEditorLspPreferences.matchesFileType("types.d.mts", listOf(".mts")))
        assertTrue(AceEditorLspPreferences.matchesFileType("types.d.cts", listOf(".d.cts")))
        assertFalse(AceEditorLspPreferences.matchesFileType("types.d.cts", listOf(".cts")))
    }

    @Test
    fun defaultFileTypesCoverSupportedJavaScriptAndTypeScriptSuffixes() {
        assertTrue(
            AceEditorLspPreferences.DEFAULT_FILE_TYPES.containsAll(
                listOf(".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx", ".d.ts", ".json"),
            ),
        )
        assertFalse(AceEditorLspPreferences.DEFAULT_FILE_TYPES.contains(".mts"))
        assertFalse(AceEditorLspPreferences.DEFAULT_FILE_TYPES.contains(".cts"))
        assertEquals(
            AceEditorLspPreferences.DEFAULT_FILE_TYPES,
            AceEditorLspPreferences.normalizeFileTypes(AceEditorLspPreferences.DEFAULT_FILE_TYPES),
        )
    }

    @Test
    fun declarationGroupsAreDisabledByDefaultAndUseSupportedOrder() {
        assertTrue(AceEditorLspPreferences.DEFAULT_DECLARATION_GROUPS.isEmpty())
        assertEquals(
            listOf("android", "libraries", "resources", "main-app"),
            AceEditorLspPreferences.SUPPORTED_DECLARATION_GROUPS,
        )
        assertEquals(
            listOf("android", "libraries", "resources", "main-app"),
            AceEditorLspPreferences.normalizeDeclarationGroups(
                listOf(" MAIN-APP ", "resources", "unknown", "libraries", "android", "android"),
            ),
        )
        assertEquals(
            listOf("android", "main-app"),
            AceEditorLspPreferences.normalizeDeclarationGroups("main-app;ANDROID,unknown"),
        )
    }
}
