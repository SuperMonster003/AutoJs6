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
    fun defaultFileTypesCoverEveryRoutedLanguageSuffix() {
        assertTrue(
            AceEditorLspPreferences.DEFAULT_FILE_TYPES.containsAll(
                listOf(
                    ".js",
                    ".mjs",
                    ".cjs",
                    ".jsx",
                    ".ts",
                    ".tsx",
                    ".mts",
                    ".cts",
                    ".d.ts",
                    ".d.mts",
                    ".d.cts",
                    ".json",
                    ".py",
                    ".lua",
                    ".java",
                    ".kt",
                    ".kts",
                ),
            ),
        )
        assertEquals(
            AceEditorLspPreferences.DEFAULT_FILE_TYPES,
            AceEditorLspPreferences.normalizeFileTypes(AceEditorLspPreferences.DEFAULT_FILE_TYPES),
        )
    }

    @Test
    fun legacyDefaultSelectionMigratesWithoutOverridingNewExplicitChoices() {
        val legacyDefault = ".js,.mjs,.cjs,.jsx,.ts,.tsx,.d.ts,.json,.auto.js,.node.js"
        val revisionTwoDefault =
            ".js,.mjs,.cjs,.jsx,.ts,.tsx,.mts,.cts,.d.ts,.d.mts,.d.cts,.json,.auto.js,.node.js"

        assertEquals(
            AceEditorLspPreferences.DEFAULT_FILE_TYPES,
            AceEditorLspPreferences.resolveStoredFileTypes(legacyDefault, revision = 1),
        )
        assertEquals(
            AceEditorLspPreferences.normalizeFileTypes(legacyDefault),
            AceEditorLspPreferences.resolveStoredFileTypes(legacyDefault, revision = 2),
        )
        assertEquals(
            AceEditorLspPreferences.DEFAULT_FILE_TYPES,
            AceEditorLspPreferences.resolveStoredFileTypes(revisionTwoDefault, revision = 2),
        )
        assertEquals(
            listOf(".js", ".ts"),
            AceEditorLspPreferences.resolveStoredFileTypes(".js,.ts", revision = 1),
        )
    }

    @Test
    fun languageSupportMatrixAndSemanticSwitchesMatchTheAcePluginContract() {
        val matrix = AceEditorLspPreferences.LANGUAGE_SUPPORT_MATRIX

        assertEquals(
            listOf("javascript", "jsx", "typescript", "tsx", "json", "python", "lua", "java", "kotlin"),
            matrix.map { it.id },
        )
        assertEquals(
            AceEditorLspPreferences.SyntaxSupport.FULL,
            matrix.single { it.id == "tsx" }.syntax,
        )
        assertEquals(
            AceEditorLspPreferences.LocalCompletionSupport.P2_PLUS,
            matrix.single { it.id == "kotlin" }.localCompletion,
        )
        assertEquals(
            AceEditorLspPreferences.SemanticSupport.SINGLE_FILE_DIAGNOSTICS,
            matrix.single { it.id == "java" }.semantic,
        )
        assertEquals(
            AceEditorLspPreferences.SemanticSupport.SYNTAX_DIAGNOSTICS_ONLY,
            matrix.single { it.id == "json" }.semantic,
        )
        assertEquals(
            listOf("typescript", "python", "lua", "java", "kotlin"),
            AceEditorLspPreferences.SUPPORTED_SEMANTIC_LANGUAGES,
        )
        assertEquals(
            listOf("typescript", "python", "lua", "java"),
            AceEditorLspPreferences.AVAILABLE_SEMANTIC_LANGUAGES,
        )
        assertTrue(AceEditorLspPreferences.defaultSemanticEnabled("typescript"))
        assertTrue(AceEditorLspPreferences.defaultSemanticEnabled("python"))
        assertTrue(AceEditorLspPreferences.defaultSemanticEnabled("lua"))
        assertTrue(AceEditorLspPreferences.defaultSemanticEnabled("java"))
        assertFalse(AceEditorLspPreferences.defaultSemanticEnabled("kotlin"))
        assertFalse(AceEditorLspPreferences.isSemanticAvailable("kotlin"))
        assertEquals(3, AceEditorLspPreferences.FILE_TYPES_REVISION)
    }

    @Test
    fun semanticRoutingKeepsFileTypeAllowlistSeparateFromLanguageRecognition() {
        assertEquals(
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_TYPESCRIPT,
            AceEditorLspPreferences.semanticLanguageForDocument("src/types.d.mts"),
        )
        assertEquals(
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_PYTHON,
            AceEditorLspPreferences.semanticLanguageForDocument("src/main.PY?revision=1"),
        )
        assertEquals(
            AceEditorLspPreferences.SEMANTIC_LANGUAGE_KOTLIN,
            AceEditorLspPreferences.semanticLanguageForDocument("build.kts"),
        )
        assertEquals(null, AceEditorLspPreferences.semanticLanguageForDocument("custom.foo"))
        assertTrue(AceEditorLspPreferences.matchesFileType("custom.foo", listOf(".foo")))
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
        assertEquals(
            AceEditorLspPreferences.SUPPORTED_DECLARATION_GROUPS,
            AceEditorLspPreferences.resolveDeclarationGroups(listOf("main-app")),
        )
    }
}
