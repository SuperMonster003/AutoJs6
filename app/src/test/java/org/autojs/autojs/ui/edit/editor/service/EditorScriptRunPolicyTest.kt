package org.autojs.autojs.ui.edit.editor.service

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorScriptRunPolicyTest {

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
}
