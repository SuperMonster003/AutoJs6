package org.autojs.autojs.ui.edit.editor.service

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorScriptRunPolicyTest {

    @Test
    fun javaAndKotlinDocumentsFollowTheJvmSourceFeatureFlag() {
        listOf("Main.java", "Main.JAVA", "Main.kt", "Main.KT").forEach { fileName ->
            assertEquals(
                org.autojs.autojs6.BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
                EditorScriptRunPolicy.isRunEnabledForDocument(fileName, null, null),
            )
        }
    }

    @Test
    fun kotlinScriptsAndJvmArtifactsCannotRunFromEditor() {
        listOf("Main.kts", "Main.class", "Main.jar", "Main.dex").forEach { fileName ->
            assertEquals(false, EditorScriptRunPolicy.isRunEnabledForDocument(fileName, null, null))
        }
    }

    @Test
    fun javaTemporaryBufferKeepsJavaRouting() {
        assertEquals(
            ".java",
            EditorScriptRunPolicy.temporaryExtensionWithDot("Main.java", null, null),
        )
    }

    @Test
    fun kotlinTemporaryBufferKeepsKotlinRouting() {
        assertEquals(
            ".kt",
            EditorScriptRunPolicy.temporaryExtensionWithDot("Main.kt", null, null),
        )
    }

    @Test
    fun fileUriPathPreservesLuaRouting() {
        assertEquals(
            ".lua",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = null,
                uriPath = "/storage/emulated/0/Scripts/main.lua",
                editorName = "main",
            ),
        )
    }

    @Test
    fun contentDisplayNameTakesPriorityOverOpaqueUriPath() {
        assertEquals(
            ".lua",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "current.lua",
                uriPath = "/document/stale.js",
                editorName = "stale.node.js",
            ),
        )
    }

    @Test
    fun luaRecognitionIsCaseInsensitive() {
        assertEquals(
            ".lua",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "MAIN.LUA",
                uriPath = null,
                editorName = null,
            ),
        )
    }

    @Test
    fun pythonRecognitionIsCaseInsensitive() {
        assertEquals(
            ".py",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "MAIN.PY",
                uriPath = null,
                editorName = null,
            ),
        )
    }

    @Test
    fun nodeJsKeepsItsCompoundExtension() {
        assertEquals(
            ".node.js",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "worker.node.js",
                uriPath = null,
                editorName = null,
            ),
        )
    }

    @Test
    fun bunMarkersKeepTheirCompoundExtensions() {
        mapOf(
            "worker.bun.js" to ".bun.js",
            "worker.BUN.TS" to ".bun.ts",
            "component.bun.tsx" to ".bun.tsx",
        ).forEach { (fileName, expectedExtension) ->
            assertEquals(
                expectedExtension,
                EditorScriptRunPolicy.temporaryExtensionWithDot(
                    documentDisplayName = fileName,
                    uriPath = null,
                    editorName = null,
                ),
            )
        }

        assertEquals(
            ".bun.ts",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "document",
                uriPath = "/storage/emulated/0/Scripts/worker.bun.ts",
                editorName = "worker",
            ),
        )
    }

    @Test
    fun quickJsMarkersKeepTheirCompoundExtensions() {
        mapOf(
            "worker.quickjs.js" to ".quickjs.js",
            "module.QUICKJS.MJS" to ".quickjs.mjs",
        ).forEach { (fileName, expectedExtension) ->
            assertEquals(
                expectedExtension,
                EditorScriptRunPolicy.temporaryExtensionWithDot(
                    documentDisplayName = fileName,
                    uriPath = null,
                    editorName = null,
                ),
            )
        }

        assertEquals(
            ".quickjs.mjs",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "document",
                uriPath = "/storage/emulated/0/Scripts/worker.quickjs.mjs",
                editorName = "worker",
            ),
        )
    }

    @Test
    fun ordinaryJavaScriptKeepsItsExtension() {
        assertEquals(
            ".js",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "main.js",
                uriPath = null,
                editorName = null,
            ),
        )
    }

    @Test
    fun unknownDocumentFallsBackToJavaScript() {
        assertEquals(
            ".js",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "notes.txt",
                uriPath = "/document/opaque",
                editorName = "notes",
            ),
        )
    }

    @Test
    fun unknownDisplayNameDoesNotHideLuaUriPath() {
        assertEquals(
            ".lua",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "document",
                uriPath = "/storage/emulated/0/Scripts/main.lua",
                editorName = "main",
            ),
        )
    }

    @Test
    fun unknownEarlierCandidatesDoNotHideLuaEditorName() {
        assertEquals(
            ".lua",
            EditorScriptRunPolicy.temporaryExtensionWithDot(
                documentDisplayName = "document",
                uriPath = "/document/opaque",
                editorName = "main.lua",
            ),
        )
    }
}
