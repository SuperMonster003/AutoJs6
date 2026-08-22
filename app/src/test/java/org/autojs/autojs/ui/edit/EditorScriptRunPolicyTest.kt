package org.autojs.autojs.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorScriptRunPolicyTest {

    @Test
    fun javaDocumentsFollowTheJvmSourceFeatureFlag() {
        listOf("Main.java", "Main.JAVA").forEach { fileName ->
            assertEquals(
                fileName,
                org.autojs.autojs6.BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
                EditorScriptRunPolicy.isRunEnabledForDocument(fileName),
            )
        }
    }

    @Test
    fun kotlinAndJvmArtifactsCannotRunFromTheEditor() {
        listOf(
            "Main.kt",
            "Main.KT",
            "Main.kts",
            "Main.KTS",
            "Main.class",
            "Main.jar",
            "Main.dex",
        ).forEach { fileName ->
            assertFalse(fileName, EditorScriptRunPolicy.isRunEnabledForDocument(fileName))
        }
    }

    @Test
    fun existingEditorDocumentKindsRemainRunnable() {
        listOf(null, "", "main.js", "main.node.js", "main.ts", "main.auto", "README")
            .forEach { fileName ->
                assertTrue(fileName, EditorScriptRunPolicy.isRunEnabledForDocument(fileName))
            }
    }

    @Test
    fun temporaryFilesPreserveRecognizedJvmExtensions() {
        mapOf(
            "Main.java" to ".java",
            "Main.JAVA" to ".JAVA",
            "Main.Kt" to ".Kt",
            "Main.kTs" to ".kTs",
            "Main.CLASS" to ".CLASS",
        ).forEach { (fileName, expected) ->
            assertEquals(expected, EditorScriptRunPolicy.temporaryScriptExtensionWithDot(fileName))
        }
    }

    @Test
    fun existingTemporaryFileExtensionBehaviorDoesNotRegress() {
        assertEquals(".node.js", EditorScriptRunPolicy.temporaryScriptExtensionWithDot("main.NODE.JS"))
        assertEquals(".js", EditorScriptRunPolicy.temporaryScriptExtensionWithDot("main.js"))
        assertEquals(".ts", EditorScriptRunPolicy.temporaryScriptExtensionWithDot("main.ts"))
        assertEquals(".js", EditorScriptRunPolicy.temporaryScriptExtensionWithDot("main.auto"))
        assertEquals(".js", EditorScriptRunPolicy.temporaryScriptExtensionWithDot(null))
    }
}
